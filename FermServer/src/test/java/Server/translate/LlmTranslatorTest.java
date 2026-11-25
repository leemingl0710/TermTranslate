package Server.translate;

import Server.db.AnalysisResultDao.Term;
import Server.extract.Item;
import com.termtranslator.core.TranslationPipeline;
import com.termtranslator.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 데모 LLM(가짜 응답)으로 돈다: 네트워크·DB 없이 결과 칸이 모두 채워지는지 확인. */
class LlmTranslatorTest {

    private static Item item(int page, String text) {
        return new Item("lecture.pdf", "pdf", page, Item.Origin.TEXT, text, List.of(), null);
    }

    private static final List<Item> ITEMS = List.of(
            item(1, "응력은 단위 면적당 작용하는 내력이다."),
            item(1, "σ = P / A"),
            item(2, "변형률은 원래 길이에 대한 길이 변화의 비율이다."),
            item(3, "전단응력은 단면에 평행하게 작용하는 응력이다."));

    private static LlmTranslator demo(LlmTranslator.TermSource db, Path dictFile) {
        return new LlmTranslator(LlmClient.demo(), "demo-model",
                new TranslationPipeline.Options(2, 0.2, 8192, "English", 2), db, dictFile);
    }

    @Test
    void fillsEveryResultCard() {
        // 데모 응답은 사전 용어가 4개 이상일 때 퀴즈를 만든다
        Translation t = demo(() -> List.of(
                new Term("응력", "stress", "단위 면적당 내력"),
                new Term("내력", "internal force", null),
                new Term("변형률", "strain", null),
                new Term("전단응력", "shear stress", null)), null).translate("lecture.pdf", ITEMS);

        assertNotNull(t.translatedText());
        for (int page = 1; page <= 3; page++) {
            assertTrue(t.translatedText().contains("[Page " + page + "]"), "페이지 " + page + " 번역");
        }
        assertFalse(t.translatedText().contains("could not be translated"));
        assertTrue(t.summary().contains("Page summaries"), "요약: 단원 요약 + 페이지별 3줄 요약");
        assertTrue(t.explanation().contains("Key concepts"), "개념: 핵심 개념 목록");
        assertTrue(t.examQuestions().contains("Q1."), "시험 대비: 퀴즈");
        assertTrue(t.examQuestions().indexOf("Answers") > t.examQuestions().indexOf("Q1."), "정답은 문제 뒤에");
        assertEquals(Set.of("응력", "내력", "변형률", "전단응력"),
                t.terms().stream().map(Term::korean).collect(Collectors.toSet()));
    }

    @Test
    void sameKoreanTermUsesDatabaseFirst(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("terms.csv"), "ko,en,description\n응력,STRESS-FROM-FILE,\n변형률,strain,\n");
        Translation t = demo(() -> List.of(new Term("응력", "stress", null)), file).translate("lecture.pdf", ITEMS);

        Term stress = t.terms().stream().filter(x -> x.korean().equals("응력")).findFirst().orElseThrow();
        assertEquals("stress", stress.english(), "DB 사전이 먼저");
        assertTrue(t.terms().stream().anyMatch(x -> x.korean().equals("변형률")), "파일 사전도 함께 씀");
    }

    @Test
    void brokenDictionaryStillTranslates() {
        Translation t = demo(() -> {
            throw new SQLException("DB 없음 (테스트)");
        }, Path.of("no-such-dictionary.csv")).translate("lecture.pdf", ITEMS);

        assertNotNull(t.translatedText(), "사전 없이도 번역은 한다");
        assertTrue(t.terms().isEmpty());
    }

    @Test
    void nothingToTranslate() {
        Translation t = demo(List::of, null).translate("empty.pdf", List.of(item(1, "   ")));
        assertNull(t.translatedText());
        assertNull(t.summary());
        assertTrue(t.terms().isEmpty());
    }

    @Test
    void itemsAreGroupedByPage() {
        var pages = LlmTranslator.toPages(List.of(item(2, "둘"), item(1, "하나"), item(1, "하나-2")));
        assertEquals(2, pages.size());
        assertEquals(1, pages.get(0).number());
        assertEquals("하나\n\n하나-2", pages.get(0).text());
    }

    @Test
    void newTermsComeAfterDictionaryTerms() {
        com.termtranslator.core.Models.PipelineResult result = new com.termtranslator.core.Models.PipelineResult(
                "m", "English", List.of(), null, new com.termtranslator.core.RunStats());
        List<com.termtranslator.core.Models.TermMatch> found = List.of(new com.termtranslator.core.Models.TermMatch(
                new com.termtranslator.core.Models.Term("응력", "stress", "사전 설명"), 2));
        Translation t = LlmTranslator.toTranslation(result, found,
                List.of(new Term("직렬화", "serialization", "Turning objects into bytes.")));
        assertEquals(List.of("응력", "직렬화"), t.terms().stream().map(Term::korean).toList());
        assertEquals("serialization", t.terms().get(1).english());
    }
}
