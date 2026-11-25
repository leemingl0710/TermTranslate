package ui.platform;

import org.junit.jupiter.api.Test;
import ui.ApiClient.AnalysisResult;
import ui.ApiClient.ApiException;
import ui.ApiClient.ResultSummary;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 이력 화면: 가짜 서버로 목록·검색·보기·삭제를 확인한다 (창 없이 JPanel 만). */
class HistoryPageTest {

    /** 메모리에 이력을 들고 있는 가짜 서버. */
    static class FakeHistory implements HistoryApi {
        final List<ResultSummary> rows = new ArrayList<>(List.of(
                new ResultSummary(17, 17, "1_자바 실습.pdf", "2026-11-25T02:57:53", 803, true, 2),
                new ResultSummary(14, 14, "2_파일입출력.pdf", "2026-11-25T02:27:41", 444, false, 0)));
        final List<String> calls = new ArrayList<>();
        ApiException failList;

        @Override
        public List<ResultSummary> list(String fileQuery) throws ApiException {
            calls.add("list:" + fileQuery);
            if (failList != null) {
                throw failList;
            }
            return rows.stream().filter(r -> r.fileName().contains(fileQuery)).toList();
        }

        @Override
        public AnalysisResult get(int resultId) {
            calls.add("get:" + resultId);
            return new AnalysisResult(resultId, "file" + resultId + ".pdf", null, List.of(),
                    null, null, null, null, List.of());
        }

        @Override
        public void delete(int resultId) {
            calls.add("delete:" + resultId);
            rows.removeIf(r -> r.resultId() == resultId);
        }
    }

    private final FakeHistory api = new FakeHistory();
    private final List<Page> navigated = new ArrayList<>();
    private final List<List<AnalysisResult>> handed = new ArrayList<>();
    private final HistoryPage page = new HistoryPage(navigated::add, api, handed::add, Background.DIRECT);

    @Test
    void refreshShowsServerRowsInOrder() {
        page.refresh();
        assertEquals(List.of("list:"), api.calls);
        assertEquals(List.of(17, 14), page.shownRows().stream().map(ResultSummary::resultId).toList());
        assertEquals("2 record(s)", page.statusText());
    }

    @Test
    void searchSendsQuery() {
        page.setSearchText("  파일 ");
        page.refresh();
        assertEquals(List.of("list:파일"), api.calls);
        assertEquals(1, page.shownRows().size());
    }

    @Test
    void emptyStates() {
        api.rows.clear();
        page.refresh();
        assertTrue(page.statusText().startsWith("No analysis history yet"));
        page.setSearchText("없는파일");
        page.refresh();
        assertEquals("No history matches \"없는파일\".", page.statusText());
    }

    @Test
    void viewOpensResultScreen() {
        page.refresh();
        page.clickView(1);
        assertTrue(api.calls.contains("get:14"));
        assertEquals(1, handed.size());
        assertEquals(14, handed.get(0).get(0).resultId());
        assertEquals(List.of(Page.RESULT), navigated);
    }

    @Test
    void deleteAsksThenRefreshes() {
        page.refresh();
        page.setConfirmDelete(name -> false);
        page.clickDelete(0);
        assertEquals(2, page.shownRows().size(), "취소하면 지우지 않음");

        List<String> asked = new ArrayList<>();
        page.setConfirmDelete(name -> asked.add(name));
        page.clickDelete(0);
        assertEquals(List.of("1_자바 실습.pdf"), asked);
        assertTrue(api.calls.contains("delete:17"));
        assertEquals(List.of(14), page.shownRows().stream().map(ResultSummary::resultId).toList(),
                "지운 뒤 목록을 다시 읽음");
    }

    @Test
    void serverErrorShownInStatus() {
        api.failList = new ApiException(0, "SERVER_UNAVAILABLE", "서버에 연결할 수 없습니다");
        page.refresh();
        assertEquals("서버에 연결할 수 없습니다", page.statusText());
    }

    @Test
    void describeAndLocalTime() {
        String expected = LocalDateTime.parse("2026-11-25T02:57:53").atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        assertEquals(expected, HistoryPage.localTime("2026-11-25T02:57:53.567178"));
        assertEquals("-", HistoryPage.localTime(null));
        assertEquals("어제", HistoryPage.localTime("어제"), "모르는 형식은 그대로");
        assertEquals("Analyzed " + expected + " · 803 paragraphs · Translated · 2 terms",
                HistoryPage.describe(api.rows.get(0)));
        assertTrue(HistoryPage.describe(api.rows.get(1)).endsWith("444 paragraphs · Not translated"));
    }
}
