package ui.platform;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 퀴즈 탭과 체크리스트 탭: 창 없이 패널만 만들어 버튼을 눌러 본다. */
class QuizPanelTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private final QuizPanel quiz = new QuizPanel();

    @Test
    void noQuizMessage() {
        assertTrue(quiz.showingMessage());
        assertEquals(QuizPanel.NO_QUIZ, quiz.messageText());
        quiz.show(ExamContent.parse("free text only"));
        assertEquals("free text only", quiz.messageText(), "못 읽은 글은 그대로");
    }

    @Test
    void answerHiddenUntilCheck() {
        quiz.show(ExamContent.parse(ExamContentTest.SAMPLE));
        assertFalse(quiz.showingMessage());
        assertEquals("Question 1 of 2", quiz.counterText());
        assertTrue(quiz.questionText().startsWith("Which stream"));
        assertEquals("B. Character Stream", quiz.optionTexts().get(1));
        assertFalse(quiz.checkEnabled(), "고르기 전에는 Check 못 누름");
        assertEquals(" ", quiz.feedbackText());

        quiz.choose(1);
        assertEquals(" ", quiz.feedbackText(), "고르기만 해서는 정답이 안 보임");
        quiz.clickCheck();
        assertEquals(QuizPanel.CORRECT, quiz.feedbackText());
        assertEquals("Character streams are designed for text data.", quiz.explanationText());
        assertFalse(quiz.optionsEnabled(), "확인한 뒤에는 답을 못 바꿈");
        assertFalse(quiz.checkEnabled());
    }

    @Test
    void wrongAnswerScoreAndTryAgain() {
        quiz.show(ExamContent.parse(ExamContentTest.SAMPLE));
        quiz.choose(1);
        quiz.clickCheck();
        assertFalse(quiz.tryAgainVisible(), "아직 다 안 풀었음");
        assertEquals(" ", quiz.scoreText());

        quiz.clickNext();
        assertEquals("Question 2 of 2", quiz.counterText());
        assertEquals(" ", quiz.feedbackText(), "다음 문제는 새로");
        quiz.choose(0);
        quiz.clickCheck();
        assertEquals(QuizPanel.WRONG_PREFIX + "C. The Serializable interface", quiz.feedbackText());
        assertEquals("Score: 1 / 2", quiz.scoreText());
        assertTrue(quiz.tryAgainVisible());

        quiz.clickPrevious();
        assertEquals(QuizPanel.CORRECT, quiz.feedbackText(), "앞 문제로 가도 결과가 남아 있음");

        quiz.clickTryAgain();
        assertEquals("Question 1 of 2", quiz.counterText());
        assertEquals(" ", quiz.feedbackText());
        assertTrue(quiz.optionsEnabled());
        assertFalse(quiz.tryAgainVisible());
    }

    @Test
    void ungradableQuestionShowsAnswerWithoutScore() {
        quiz.show(ExamContent.parse("""
                Q1. Pick one
                   A. alpha
                   B. beta

                Answers
                Q1. gamma — none of the options"""));
        assertTrue(quiz.checkEnabled(), "정답을 모르는 문제는 고르지 않아도 확인 가능");
        quiz.clickCheck();
        assertEquals(QuizPanel.ANSWER_PREFIX + "gamma", quiz.feedbackText());
        assertEquals(" ", quiz.scoreText(), "채점할 문제가 없으면 점수 없음");
    }

    @Test
    void checklistCountsChecks() {
        ChecklistPanel list = new ChecklistPanel();
        assertEquals(ChecklistPanel.NO_CHECKLIST, list.countText());

        list.show(ExamContent.parse(ExamContentTest.SAMPLE).checklist());
        assertEquals(3, list.itemTexts().size());
        assertEquals("Self-check (0 / 3 done)", list.countText());
        list.toggle(0);
        list.toggle(2);
        assertEquals("Self-check (2 / 3 done)", list.countText());
        list.toggle(0);
        assertEquals("Self-check (1 / 3 done)", list.countText());

        list.show(List.of("new item"));
        assertEquals("Self-check (0 / 1 done)", list.countText(), "새 결과는 처음부터");
    }
}
