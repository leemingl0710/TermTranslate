package ui.platform;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ui.ApiClient;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 결과 화면을 창 없이(JPanel 만) 만들어 페이지 이동·파일 선택이 실제 데이터로 되는지 본다. */
class ResultPageTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static ApiClient.AnalysisResult threePages() {
        return new ApiClient.AnalysisResult(7, "강의1.pdf", "2026-11-25",
                List.of(PagedResultTest.item(1, "Intro"), PagedResultTest.item(2, "Middle"),
                        PagedResultTest.item(3, "End")),
                "[Page 1]\n소개\n\n[Page 2]\n중간\n\n둘째 문단\n\n[Page 3]\n끝",
                "Key concepts: a\n\nHow the pages connect\nPage 2: builds on 1",
                "Unit summary.\n\nPage summaries\n[Page 1]\n- intro bullet",
                null,
                List.of(new ApiClient.Term("소개", "introduction", "시작 부분")));
    }

    private static ApiClient.AnalysisResult secondFile() {
        return new ApiClient.AnalysisResult(8, "b.pptx", null,
                List.of(PagedResultTest.item(1, "Slide one")), null, null, null, null, null);
    }

    private static void onEdt(Runnable r) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error.set(t);
            }
        });
        if (error.get() instanceof AssertionError a) {
            throw a;
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

    @Test
    void emptyStateBeforeAnyResult() throws Exception {
        onEdt(() -> {
            ResultPage page = new ResultPage();
            assertEquals("Page 0 / 0", page.pageLabelText());
            assertEquals(ResultPage.EMPTY_TEXT, page.originalTextValue());
            page.showResults(List.of());
            assertEquals("Page 0 / 0", page.pageLabelText());
            assertEquals(0, page.fileSelector().getItemCount());
        });
    }

    @Test
    void walksThroughRealPages() throws Exception {
        onEdt(() -> {
            ResultPage page = new ResultPage();
            page.showResult(threePages());

            assertEquals("Page 1 / 3", page.pageLabelText());
            assertTrue(page.subtitleText().contains("강의1.pdf"));
            assertEquals("Intro", page.originalTextValue());
            assertEquals("소개", page.translationTextValue());
            assertEquals("- intro bullet", page.explanationTextValue());
            assertTrue(page.summaryTextValue().startsWith("Unit summary."));
            assertEquals("소개 — introduction: 시작 부분", page.termsTextValue());

            page.clickNext();
            assertEquals("Page 2 / 3", page.pageLabelText());
            assertEquals("Middle", page.originalTextValue());
            assertEquals("중간\n\n둘째 문단", page.translationTextValue());
            assertEquals("Connection to previous pages: builds on 1", page.explanationTextValue());

            page.clickNext();
            page.clickNext(); // 끝에서 더 가지 않는다
            assertEquals("Page 3 / 3", page.pageLabelText());
            assertEquals(PagedResult.NOT_AVAILABLE, page.explanationTextValue());

            page.clickPrevious();
            assertEquals("Page 2 / 3", page.pageLabelText());
        });
    }

    @Test
    void fileSelectorKeepsSelectionOrder() throws Exception {
        onEdt(() -> {
            ResultPage page = new ResultPage();
            page.showResults(List.of(threePages(), secondFile()));

            assertEquals(2, page.fileSelector().getItemCount());
            assertEquals("1. 강의1.pdf", page.fileSelector().getItemAt(0));
            assertEquals("2. b.pptx", page.fileSelector().getItemAt(1));
            assertEquals("Page 1 / 3", page.pageLabelText());

            page.fileSelector().setSelectedIndex(1);
            assertEquals("Page 1 / 1", page.pageLabelText());
            assertEquals("Slide one", page.originalTextValue());
            assertEquals(PagedResult.NOT_AVAILABLE, page.translationTextValue());
            assertEquals(PagedResult.NOT_AVAILABLE, page.summaryTextValue());
            assertEquals(PagedResult.NOT_AVAILABLE, page.termsTextValue());
            assertTrue(page.subtitleText().contains("b.pptx"));

            page.fileSelector().setSelectedIndex(0);
            assertEquals("Intro", page.originalTextValue());
            assertFalse(page.pageLabelText().contains("p."));
        });
    }

    @Test
    void skippedPageNumberIsShownNextToPosition() throws Exception {
        onEdt(() -> {
            ResultPage page = new ResultPage();
            page.showResult(new ApiClient.AnalysisResult(1, "x.pdf", null,
                    List.of(PagedResultTest.item(1, "a"), PagedResultTest.item(3, "c")),
                    null, null, null, null, null));
            page.clickNext();
            assertEquals("Page 2 / 2  (p.3)", page.pageLabelText());
        });
    }

    @Test
    void quizAndChecklistFollowSelectedFile() throws Exception {
        onEdt(() -> {
            ResultPage page = new ResultPage();
            assertEquals(QuizPanel.NO_QUIZ, page.quizPanel().messageText());
            assertEquals(ChecklistPanel.NO_CHECKLIST, page.checklistPanel().countText());

            ApiClient.AnalysisResult withExam = new ApiClient.AnalysisResult(9, "c.pdf", null,
                    List.of(PagedResultTest.item(1, "x")), null, null, null, ExamContentTest.SAMPLE, null);
            page.showResults(List.of(withExam, secondFile()));
            assertEquals("Question 1 of 2", page.quizPanel().counterText());
            assertEquals("Self-check (0 / 3 done)", page.checklistPanel().countText());

            page.fileSelector().setSelectedIndex(1);
            assertTrue(page.quizPanel().showingMessage(), "퀴즈 없는 파일");
            assertEquals(ChecklistPanel.NO_CHECKLIST, page.checklistPanel().countText());
        });
    }
}
