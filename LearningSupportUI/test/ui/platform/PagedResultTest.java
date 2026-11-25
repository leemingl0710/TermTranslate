package ui.platform;

import org.junit.jupiter.api.Test;
import ui.ApiClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서버 번역기(LlmTranslator.toTranslation) 형식의 글을 페이지별로 잘 나누는지 확인한다. */
class PagedResultTest {

    static ApiClient.Item item(int page, String content, String... flags) {
        return new ApiClient.Item(page, "text", content, List.of(flags));
    }

    static ApiClient.AnalysisResult result(List<ApiClient.Item> items, String translated,
                                           String explanation, String summary, List<ApiClient.Term> terms) {
        return new ApiClient.AnalysisResult(1, "lecture.pdf", "2026-11-25", items,
                translated, explanation, summary, null, terms);
    }

    @Test
    void splitsTranslationOnPageHeadersKeepingBlankLinesInside() {
        String translated = "[Page 1]\n첫 문단입니다.\n\n둘째 문단입니다.\n\n"
                + "[Page 2]\n(This page could not be translated. Please try again later.)\n\n"
                + "[Page 3]\n셋째 페이지.";
        PagedResult r = PagedResult.of(result(
                List.of(item(1, "First."), item(1, "Second."), item(2, "Two"), item(3, "Three")),
                translated, null, null, null));

        assertEquals(3, r.pageCount());
        assertEquals("첫 문단입니다.\n\n둘째 문단입니다.", r.pages().get(0).translation());
        assertEquals("(This page could not be translated. Please try again later.)",
                r.pages().get(1).translation());
        assertEquals("셋째 페이지.", r.pages().get(2).translation());
        assertEquals("First.\n\nSecond.", r.pages().get(0).original());
    }

    @Test
    void headerLikeLineInsideParagraphIsNotASplit() {
        String translated = "[Page 1]\n참고:\n[Page 9]\n위 표기는 원문 그대로.";
        PagedResult r = PagedResult.of(result(List.of(), translated, null, null, null));
        assertEquals(1, r.pageCount());
        assertEquals(1, r.pages().get(0).number());
        assertTrue(r.pages().get(0).translation().contains("[Page 9]"));
    }

    @Test
    void allNullFieldsGiveItemsOnlyPages() {
        PagedResult r = PagedResult.of(result(List.of(item(1, "Only text")), null, null, null, null));
        assertEquals(1, r.pageCount());
        PagedResult.PageView p = r.pages().get(0);
        assertEquals("Only text", p.original());
        assertNull(p.translation());
        assertNull(p.connection());
        assertTrue(p.summaryBullets().isEmpty());
        assertNull(p.explanationText());
        assertNull(r.unitSummary());
        assertNull(r.explanation());
        assertNull(r.conceptText());
        assertNull(r.termsText());
    }

    @Test
    void nullResultAndNullItemsAreEmpty() {
        assertEquals(0, PagedResult.of(null).pageCount());
        PagedResult r = PagedResult.of(new ApiClient.AnalysisResult(0, null, null, null,
                null, null, null, null, null));
        assertEquals(0, r.pageCount());
        assertNull(r.fileName());
    }

    @Test
    void pageNumbersAreUnionOfItemsAndTranslationSorted() {
        // items: 1, 4 (2·3 은 빈 페이지) / 번역: 1, 2, 5
        String translated = "[Page 5]\n다섯\n\n[Page 1]\n하나\n\n[Page 2]\n둘";
        PagedResult r = PagedResult.of(result(
                List.of(item(1, "one"), item(4, "four")), translated, null, null, null));

        assertEquals(List.of(1, 2, 4, 5), r.pages().stream().map(PagedResult.PageView::number).toList());
        assertEquals("", r.pages().get(1).original());
        assertEquals("둘", r.pages().get(1).translation());
        assertEquals("four", r.pages().get(2).original());
        assertNull(r.pages().get(2).translation());
        assertEquals("다섯", r.pages().get(3).translation());
    }

    @Test
    void windowsLineEndingsAreHandled() {
        String translated = "[Page 1]\r\n하나\r\n\r\n줄\r\n\r\n[Page 2]\r\n둘";
        String summary = "Unit.\r\n\r\nPage summaries\r\n[Page 1]\r\n- a\r\n- b";
        String explanation = "Key concepts: x\r\n\r\nHow the pages connect\r\nPage 2: follows 1";
        PagedResult r = PagedResult.of(result(List.of(item(1, "A\r\nB")), translated, explanation, summary, null));

        assertEquals("하나\n\n줄", r.pages().get(0).translation());
        assertEquals("둘", r.pages().get(1).translation());
        assertEquals("A\nB", r.pages().get(0).original());
        assertEquals(List.of("a", "b"), r.pages().get(0).summaryBullets());
        assertEquals("follows 1", r.pages().get(1).connection());
        assertEquals("Unit.", r.unitSummary());
    }

    @Test
    void pageSummariesAndConnectionLinesGoToTheirPages() {
        String summary = "This unit covers sorting.\n\nIt has two parts.\n\n"
                + "Page summaries\n[Page 1]\n- Bubble sort\n- O(n^2)\n\n[Page 3]\n- Merge sort";
        String explanation = "Key concepts: sorting · complexity\n\n"
                + "Concept explanation\nPages 1-2\nSorting puts things in order.\n\nPage 3\nMerge splits.\n\n"
                + "How the pages connect\nPage 2: Extends page 1.\nPage 3: Faster than page 2.";
        PagedResult r = PagedResult.of(result(
                List.of(item(1, "p1"), item(2, "p2"), item(3, "p3")), null, explanation, summary, null));

        assertEquals("This unit covers sorting.\n\nIt has two parts.", r.unitSummary());
        assertEquals(List.of("Bubble sort", "O(n^2)"), r.pages().get(0).summaryBullets());
        assertTrue(r.pages().get(1).summaryBullets().isEmpty());
        assertEquals(List.of("Merge sort"), r.pages().get(2).summaryBullets());
        assertNull(r.pages().get(0).connection());
        assertEquals("Extends page 1.", r.pages().get(1).connection());
        assertEquals("Faster than page 2.", r.pages().get(2).connection());

        // 개념 설명 안의 "Page 3" 머리줄은 연결 줄로 잘못 잡히지 않는다
        assertTrue(r.conceptText().startsWith("Key concepts: sorting"));
        assertTrue(r.conceptText().contains("Merge splits."));
        assertTrue(!r.conceptText().contains("How the pages connect"));
        assertEquals(explanation, r.explanation());

        assertEquals("- Bubble sort\n- O(n^2)", r.pages().get(0).explanationText());
        assertEquals("Connection to previous pages: Extends page 1.", r.pages().get(1).explanationText());
    }

    @Test
    void summaryWithoutPageSummariesHeaderIsAllUnitSummary() {
        PagedResult r = PagedResult.of(result(List.of(item(1, "x")), null, "Key concepts: a", "Just a unit summary.", null));
        assertEquals("Just a unit summary.", r.unitSummary());
        assertTrue(r.pages().get(0).summaryBullets().isEmpty());
        assertEquals("Key concepts: a", r.conceptText());
        assertNull(r.pages().get(0).connection());
    }

    @Test
    void summaryWithOnlyPageSummariesHasNoUnitSummary() {
        PagedResult r = PagedResult.of(result(List.of(), null, null,
                "Page summaries\n[Page 2]\n- only bullet", null));
        assertNull(r.unitSummary());
        assertEquals(1, r.pageCount());
        assertEquals(2, r.pages().get(0).number());
        assertEquals(List.of("only bullet"), r.pages().get(0).summaryBullets());
    }

    @Test
    void codeItemsKeepLineBreaksAndIndentation() {
        String code = "\nfor (int i = 0; i < n; i++) {\n    sum += i;\n}\n";
        PagedResult r = PagedResult.of(result(
                List.of(item(1, "  Intro text  "), item(1, code, "code"), item(1, "   ", "code")),
                null, null, null, null));
        assertEquals("Intro text\n\nfor (int i = 0; i < n; i++) {\n    sum += i;\n}", r.pages().get(0).original());
    }

    @Test
    void termsTextFormatsEachLine() {
        List<ApiClient.Term> terms = List.of(
                new ApiClient.Term("정렬", "sorting", "순서대로 놓기"),
                new ApiClient.Term("배열", "array", null),
                new ApiClient.Term("힙", null, ""),
                new ApiClient.Term(null, "skip", "x"));
        PagedResult r = PagedResult.of(result(List.of(), null, null, null, terms));
        assertEquals("정렬 — sorting: 순서대로 놓기\n배열 — array\n힙", r.termsText());
        assertNull(PagedResult.of(result(List.of(), null, null, null, List.of())).termsText());
    }
}
