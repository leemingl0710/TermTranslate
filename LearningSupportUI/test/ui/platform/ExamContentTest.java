package ui.platform;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서버의 시험 대비 글을 퀴즈·체크리스트·정답으로 나누는지 본다. */
class ExamContentTest {

    /** 서버(LlmTranslator.exam)가 실제로 만드는 모양 (결과 21번에서 줄임) */
    static final String SAMPLE = """
            Q1. Which stream should you use if you want to save a text file?
               A. Byte Stream
               B. Character Stream
               C. Object Stream
               D. Data Stream

            Q2. What must a class implement to be saved with ObjectOutputStream?
               A. The File interface
               B. The InputStream interface
               C. The Serializable interface
               D. The Writer interface

            Self-check
            [ ] I can explain the difference between Byte Streams and Character Streams.
            [ ] I can use the File class to check if a file exists.
            [ ] I can explain what serialization is.

            Answers
            Q1. B. Character Stream — Character streams are designed for text data.
            Q2. C. The Serializable interface — Your class must implement Serializable.""";

    @Test
    void parsesServerFormat() {
        ExamContent e = ExamContent.parse(SAMPLE);
        assertEquals(2, e.quizzes().size());
        ExamContent.Quiz q1 = e.quizzes().get(0);
        assertEquals("Which stream should you use if you want to save a text file?", q1.question());
        assertEquals(List.of("Byte Stream", "Character Stream", "Object Stream", "Data Stream"), q1.options());
        assertEquals(1, q1.answerIndex());
        assertEquals("B. Character Stream", q1.answerLabel());
        assertEquals("Character streams are designed for text data.", q1.explanation());
        assertEquals(2, e.quizzes().get(1).answerIndex());

        assertEquals(3, e.checklist().size());
        assertEquals("I can explain what serialization is.", e.checklist().get(2));
        assertFalse(e.unparsed());
    }

    @Test
    void emptyAndNull() {
        assertTrue(ExamContent.parse(null).isEmpty());
        assertTrue(ExamContent.parse("  \n ").isEmpty());
    }

    @Test
    void unknownFormatKeepsRawText() {
        ExamContent e = ExamContent.parse("Some free text the LLM wrote.\nNo questions here.");
        assertTrue(e.unparsed(), "못 읽으면 원래 글을 보여 줄 수 있게");
        assertEquals("Some free text the LLM wrote.\nNo questions here.", e.raw());
    }

    @Test
    void answerNotMatchingAnyOption() {
        // 서버는 정답이 보기와 다르면 글자 그대로 적는다
        ExamContent e = ExamContent.parse("""
                Q1. Pick one
                   A. alpha
                   B. beta

                Answers
                Q1. gamma — none of the options""");
        ExamContent.Quiz q = e.quizzes().get(0);
        assertFalse(q.gradable());
        assertEquals("gamma", q.answerLabel());
        assertEquals("none of the options", q.explanation());
    }

    @Test
    void answerWithoutLabelIsFoundByOptionText() {
        ExamContent e = ExamContent.parse("""
                Q1. Pick one
                   A. File
                   B. FileWriter

                Answers
                Q1. FileWriter — writes text""");
        assertEquals(1, e.quizzes().get(0).answerIndex(), "앞부분이 같은 보기 중 긴 쪽");
        assertEquals("writes text", e.quizzes().get(0).explanation());
    }

    @Test
    void noAnswersSectionAndNoExplanation() {
        ExamContent e = ExamContent.parse("""
                Q1. Pick one
                   A. x
                   B. y

                Q2. Second
                   A. p
                   B. q

                Answers
                Q2. A. p""");
        assertEquals(-1, e.quizzes().get(0).answerIndex());
        assertNull(e.quizzes().get(0).answerLabel());
        assertEquals(0, e.quizzes().get(1).answerIndex());
        assertNull(e.quizzes().get(1).explanation());
        assertTrue(e.checklist().isEmpty());
    }

    @Test
    void checklistOnlyAndWrappedLines() {
        ExamContent e = ExamContent.parse("""
                Q1. A long question
                that continues here
                   A. one
                   B. two

                Self-check
                [ ] 스트림의 개념을
                설명할 수 있다.
                [ ]
                [ ] 파일을 읽을 수 있다.""");
        assertEquals("A long question that continues here", e.quizzes().get(0).question());
        assertEquals(List.of("스트림의 개념을 설명할 수 있다.", "파일을 읽을 수 있다."), e.checklist(),
                "빈 항목은 빼고, 줄바꿈된 항목은 이어 붙임");
    }

    @Test
    void windowsLineEndings() {
        ExamContent e = ExamContent.parse(SAMPLE.replace("\n", "\r\n"));
        assertEquals(2, e.quizzes().size());
        assertEquals(1, e.quizzes().get(0).answerIndex());
        assertEquals(3, e.checklist().size());
    }
}
