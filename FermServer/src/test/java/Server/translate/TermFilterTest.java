package Server.translate;

import Server.db.AnalysisResultDao.Term;
import com.termtranslator.core.Models.BundleOutput;
import com.termtranslator.core.Models.BundleResult;
import com.termtranslator.core.Models.PageResult;
import com.termtranslator.core.Models.TermEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LLM 이 준 용어 후보 중 전공용어만 남기는지 확인한다. */
class TermFilterTest {

    private static final String SOURCE = """
            스트림은 데이터가 흐르는 통로이다.
            객체를 바이트로 바꾸는 것을 직렬화라고 한다.
            예외 처리는 try-catch 로 한다. 파일을 열고 예제를 실행한다.
            String fileName = "a.txt";
            """;

    private static BundleOutput bundle(TermEntry... terms) {
        BundleResult r = new BundleResult(List.of(new PageResult(1, "t", List.of(), "")), "c", "b", List.of(terms));
        return new BundleOutput(List.of(1), List.of(), r, false);
    }

    private static TermEntry t(String ko, String en) {
        return new TermEntry(ko, en, "설명");
    }

    private static List<String> koreans(List<Term> terms) {
        return terms.stream().map(Term::korean).toList();
    }

    @Test
    void keepsSubjectTermsInOrder() {
        List<Term> out = TermFilter.pick(List.of(
                bundle(t("스트림", "stream"), t("직렬화", "serialization")),
                bundle(t("예외 처리", "exception handling"))), SOURCE, List.of());
        assertEquals(List.of("스트림", "직렬화", "예외 처리"), koreans(out));
        assertEquals("serialization", out.get(1).english());
        assertEquals("설명", out.get(1).explanation());
    }

    @Test
    void dropsGeneralWordsAndInventedTerms() {
        List<Term> out = TermFilter.pick(List.of(bundle(
                t("파일", "file"),            // 일반 단어
                t("예제", "example"),         // 일반 단어
                t("다형성", "polymorphism"),   // 원문에 없음 (LLM 이 지어냄)
                t("fileName", "file name"),  // 코드 이름 (한글 없음)
                t("스트림", "stream"))), SOURCE, List.of());
        assertEquals(List.of("스트림"), koreans(out));
    }

    @Test
    void dropsBadShapes() {
        String src = SOURCE + " 바이트로 바꾸는 것을 직렬화라고 한다";
        assertNull(TermFilter.accept(t("직렬화라고 한다", "serialization"), src.replace(" ", "")), "문장");
        assertNull(TermFilter.accept(t("스트림", ""), SOURCE.replaceAll("\\s", "")), "영어 없음");
        assertNull(TermFilter.accept(t("스트림", "스트림"), SOURCE.replaceAll("\\s", "")), "영어 칸에 한글");
        assertNull(TermFilter.accept(t("스트림", "stream()"), SOURCE.replaceAll("\\s", "")), "코드 기호");
        assertNull(TermFilter.accept(t("try-catch(e)", "try catch"), SOURCE.replaceAll("\\s", "")), "괄호");
        assertNull(TermFilter.accept(t("흐", "flow"), SOURCE.replaceAll("\\s", "")), "한 글자");
        assertFalse(TermFilter.isKoreanTerm("아주 긴 다섯 단어 용어"), "다섯 단어");
        assertTrue(TermFilter.isKoreanTerm("예외 처리"));
        assertFalse(TermFilter.isKoreanTerm("추상적인 개념"), "꾸미는 말 + 일반 단어");
        assertFalse(TermFilter.isKoreanTerm("간단한 예제"));
        assertTrue(TermFilter.isKoreanTerm("부모 디렉터리"));
    }

    @Test
    void ignoresSpacingDifferencesWithSource() {
        // 원문은 "예외 처리", LLM 은 "예외처리" 로 줘도 원문에 있는 것으로 본다
        assertEquals(List.of("예외처리"),
                koreans(TermFilter.pick(List.of(bundle(t("예외처리", "exception handling"))), SOURCE, List.of())));
    }

    @Test
    void skipsKnownAndDuplicates() {
        List<Term> out = TermFilter.pick(List.of(
                bundle(t("스트림", "stream"), t("직렬화", "serialization")),
                bundle(t("직렬화", "serialization"), t(" 예외  처리 ", "exception handling"))),
                SOURCE, List.of("스트림", "예외처리"));
        assertEquals(List.of("직렬화"), koreans(out), "사전에 있는 용어(띄어쓰기 무시)와 중복은 뺌");
    }

    @Test
    void capsCountAndExplanation() {
        StringBuilder src = new StringBuilder();
        List<TermEntry> many = new ArrayList<>();
        for (int i = 0; i < TermFilter.MAX_PER_DOCUMENT + 5; i++) {
            String ko = "용어" + (char) ('가' + i);
            src.append(ko).append(' ');
            many.add(t(ko, "term " + i));
        }
        List<Term> out = TermFilter.pick(List.of(bundle(many.toArray(TermEntry[]::new))), src.toString(), List.of());
        assertEquals(TermFilter.MAX_PER_DOCUMENT, out.size());

        Term longEx = TermFilter.accept(new TermEntry("스트림", "stream", "가".repeat(500)), "스트림");
        assertEquals(TermFilter.MAX_EXPLANATION, longEx.explanation().length());
    }

    @Test
    void emptyInputs() {
        assertTrue(TermFilter.pick(null, null, null).isEmpty());
        assertTrue(TermFilter.pick(List.of(new BundleOutput(List.of(1), List.of(), null, true)), SOURCE, List.of()).isEmpty(),
                "실패한 묶음은 건너뜀");
    }
}
