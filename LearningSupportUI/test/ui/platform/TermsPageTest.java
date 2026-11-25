package ui.platform;

import org.junit.jupiter.api.Test;
import ui.ApiClient.ApiException;
import ui.ApiClient.TermEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 용어 사전 화면: 가짜 서버로 검색·추가·수정·삭제를 확인한다 (창 없이 JPanel 만). */
class TermsPageTest {

    /** 메모리 용어 사전. 같은 한국어 용어는 409 처럼 거절한다. */
    static class FakeTerms implements TermApi {
        final List<TermEntry> rows = new ArrayList<>(List.of(
                new TermEntry(1, "응력", "stress", "단위 면적당 힘"),
                new TermEntry(2, "변형률", "strain", null),
                new TermEntry(3, "직렬화", "serialization", "객체를 바이트로", false)));
        final List<String> calls = new ArrayList<>();

        @Override
        public List<TermEntry> search(String query, boolean unverifiedOnly) {
            calls.add("search:" + query + (unverifiedOnly ? ":review" : ""));
            return rows.stream().filter(t -> !unverifiedOnly || t.needsReview())
                    .filter(t -> t.korean().contains(query)
                            || (t.english() != null && t.english().contains(query))).toList();
        }

        @Override
        public TermEntry approve(int termId) {
            calls.add("approve:" + termId);
            TermEntry old = rows.stream().filter(r -> r.termId() == termId).findFirst().orElseThrow();
            TermEntry t = new TermEntry(termId, old.korean(), old.english(), old.explanation(), true);
            rows.replaceAll(r -> r.termId() == termId ? t : r);
            return t;
        }

        @Override
        public TermEntry add(String korean, String english, String explanation) throws ApiException {
            calls.add("add:" + korean);
            if (rows.stream().anyMatch(t -> t.korean().equals(korean))) {
                throw new ApiException(409, "DUPLICATE_TERM", "이미 있는 용어입니다: " + korean);
            }
            TermEntry t = new TermEntry(rows.size() + 10, korean, english, explanation);
            rows.add(t);
            return t;
        }

        @Override
        public TermEntry update(int termId, String korean, String english, String explanation) {
            calls.add("update:" + termId + ":" + english);
            TermEntry t = new TermEntry(termId, korean, english, explanation);
            rows.replaceAll(r -> r.termId() == termId ? t : r);
            return t;
        }

        @Override
        public void delete(int termId) {
            calls.add("delete:" + termId);
            rows.removeIf(r -> r.termId() == termId);
        }
    }

    private final FakeTerms api = new FakeTerms();
    private final TermsPage page = new TermsPage(api, Background.DIRECT);

    @Test
    void refreshFillsTable() {
        page.refresh();
        assertEquals(3, page.shownRows().size());
        assertEquals("응력", page.cell(0, 0));
        assertEquals("stress", page.cell(0, 1));
        assertEquals("단위 면적당 힘", page.cell(0, 2));
        assertEquals(TermsPage.VERIFIED, page.cell(0, 3));
        assertEquals("", page.cell(1, 2), "설명이 없으면 빈칸");
        assertEquals(TermsPage.NEEDS_REVIEW, page.cell(2, 3), "자동으로 들어온 용어");
        assertEquals("3 term(s) · 1 need review", page.statusText());
    }

    @Test
    void searchAndEmptyStates() {
        page.setSearchText(" strain ");
        page.refresh();
        assertEquals("search:strain", api.calls.get(0));
        assertEquals(1, page.shownRows().size());

        page.setSearchText("없음");
        page.refresh();
        assertEquals("No term matches \"없음\".", page.statusText());

        api.rows.clear();
        page.setSearchText("");
        page.refresh();
        assertTrue(page.statusText().startsWith("The dictionary is empty"));
    }

    @Test
    void addTerm() {
        page.refresh();
        page.setFormDialog((title, initial) -> Optional.of(new TermsPage.TermForm("탄성", "elasticity", "")));
        page.clickAdd();
        assertTrue(api.calls.contains("add:탄성"));
        assertEquals(4, page.shownRows().size(), "추가 뒤 다시 읽음");
        assertEquals("Added: 탄성", page.statusText());
    }

    @Test
    void addCancelledOrDuplicate() {
        page.refresh();
        page.setFormDialog((title, initial) -> Optional.empty());
        page.clickAdd();
        assertTrue(api.calls.stream().noneMatch(c -> c.startsWith("add:")), "취소하면 보내지 않음");

        page.setFormDialog((title, initial) -> Optional.of(new TermsPage.TermForm("응력", "stress", null)));
        page.clickAdd();
        assertEquals("이미 있는 용어입니다: 응력", page.statusText());
    }

    @Test
    void editSelected() {
        page.refresh();
        page.clickEdit();
        assertEquals("Select a term to edit.", page.statusText());

        List<TermEntry> given = new ArrayList<>();
        page.setFormDialog((title, initial) -> {
            given.add(initial);
            return Optional.of(new TermsPage.TermForm(initial.korean(), "stress (force/area)", initial.explanation()));
        });
        page.selectRow(0);
        page.clickEdit();
        assertEquals("응력", given.get(0).korean(), "고를 줄의 현재 값으로 창을 연다");
        assertTrue(api.calls.contains("update:1:stress (force/area)"));
        assertEquals("Saved: 응력", page.statusText());
    }

    @Test
    void deleteSelected() {
        page.refresh();
        page.clickDelete();
        assertEquals("Select a term to delete.", page.statusText());

        page.selectRow(1);
        page.setConfirmDelete(korean -> false);
        page.clickDelete();
        assertEquals(3, api.rows.size(), "취소하면 지우지 않음");

        page.setConfirmDelete(korean -> korean.equals("변형률"));
        page.clickDelete();
        assertTrue(api.calls.contains("delete:2"));
        assertEquals(2, page.shownRows().size());
    }

    @Test
    void reviewOnlyFilter() {
        page.setReviewOnly(true);
        page.refresh();
        assertEquals("search::review", api.calls.get(0));
        assertEquals(1, page.shownRows().size());
        assertEquals("직렬화", page.cell(0, 0));

        api.rows.removeIf(TermEntry::needsReview);
        page.refresh();
        assertEquals("No term is waiting for review.", page.statusText());
    }

    @Test
    void approveSelected() {
        page.refresh();
        page.clickApprove();
        assertEquals("Select a term to approve.", page.statusText());

        page.selectRow(0);
        page.clickApprove();
        assertEquals("Already verified: 응력", page.statusText());
        assertTrue(api.calls.stream().noneMatch(c -> c.startsWith("approve:")), "확인된 용어는 보내지 않음");

        page.selectRow(2);
        page.clickApprove();
        assertTrue(api.calls.contains("approve:3"));
        assertEquals("Approved: 직렬화", page.statusText());
        assertEquals(TermsPage.VERIFIED, page.cell(2, 3), "다시 읽어서 확인됨으로 보임");
    }
}
