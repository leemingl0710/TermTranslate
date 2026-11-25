package Server.translate;

import Server.db.AnalysisResultDao;
import Server.extract.Item;
import com.termtranslator.Config;
import com.termtranslator.core.Models;
import com.termtranslator.core.Models.BundleOutput;
import com.termtranslator.core.Models.Page;
import com.termtranslator.core.Models.PageResult;
import com.termtranslator.core.Models.PipelineResult;
import com.termtranslator.core.Models.Quiz;
import com.termtranslator.core.Models.TermEntry;
import com.termtranslator.core.Models.TermMatch;
import com.termtranslator.core.Models.UnitResult;
import com.termtranslator.core.TermDictionary;
import com.termtranslator.core.TranslationPipeline;
import com.termtranslator.eval.Evaluator;
import com.termtranslator.eval.Evaluator.EvalResult;
import com.termtranslator.llm.LlmClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 번역 파이프라인(com.termtranslator 패키지)으로 업로드 처리 3단계(번역·요약·개념·시험 문제·용어)를 채운다.
 * com.termtranslator 는 번역 담당 모듈(term-translator)을 복사해 온 것이고, 이 클래스가 서버와 이어 주는 연결부다.
 *
 * <pre>
 * 추출 문단(Item) → 페이지별 글자 → 용어 사전 매칭
 *   → LLM 요청: 묶음(기본 4페이지)마다 1회 + 단원 전체 1회
 *   → 번역 / 3줄 요약 / 개념 설명 / 페이지 간 연결 / 단원 요약 / 퀴즈 / 체크리스트 / 용어
 *   → Translation (analysis_results 의 칸 4개 + 용어 목록)
 * </pre>
 *
 * 용어 사전은 DB 의 terms 테이블(팀 공용)과, 있으면 TERM_DICT_PATH 파일을 합쳐 쓴다.
 * 같은 한국어 용어가 둘 다에 있으면 DB 쪽을 쓴다.
 * 사전에 없는 전공용어는 LLM 이 묶음마다 찾아 주고(newTerms), TermFilter 로 거른 뒤 결과 용어에 붙인다.
 * 저장할 때 사전에 "확인 전" 으로 들어가고, 사람이 용어 사전 화면에서 승인·수정·삭제한다.
 *
 * <p>설정 (.env 또는 환경 변수, LLM_BASE_URL 말고는 모두 생략 가능)
 * <ul>
 *   <li>LLM_BASE_URL — LLM 서버 주소 (OpenAI 호환, 끝은 /v1). 비어 있으면 번역하지 않는다 (지금까지와 같음)</li>
 *   <li>LLM_API_KEY — API 키</li>
 *   <li>LLM_MODEL — 쓸 모델 (없으면 LLM_MODELS 의 첫 번째, 그것도 없으면 gemma-4-26b)</li>
 *   <li>TRANSLATE_BUNDLE_SIZE — 한 번에 보낼 페이지 수 (기본 4)</li>
 *   <li>TRANSLATE_LANGUAGE — 요약·설명·퀴즈 언어 (기본 English. 번역문은 항상 영어)</li>
 *   <li>TRANSLATE_QUIZ_COUNT — 퀴즈 개수 (기본 3)</li>
 *   <li>TERM_DICT_PATH — 추가 용어 사전 파일 (.csv 또는 .tsv, 형식: ko,en,description)</li>
 *   <li>TRANSLATE_DEMO=true — LLM 없이 [DEMO] 가짜 문장으로 흐름만 확인 (공용 DB 에 저장되니 주의)</li>
 *   <li>LLM_RATE_LIMIT_PER_MIN — API 키의 분당 요청 한도 (예: 30). 요청 사이를 띄워 한도를 넘지 않게 한다</li>
 *   <li>그 밖에 LLM_JSON_MODE, LLM_EXTRA_BODY, LLM_TEMPERATURE, LLM_MAX_TOKENS, LLM_TIMEOUT_SEC</li>
 * </ul>
 */
public final class LlmTranslator implements Translator {

    /** DB 용어 사전을 읽는 방법. 실패하면 파일 사전만 쓰고 계속한다. */
    @FunctionalInterface
    public interface TermSource {
        List<AnalysisResultDao.Term> load() throws Exception;
    }

    private final LlmClient client;
    private final String model;
    private final TranslationPipeline.Options options;
    private final TermSource dbTerms;
    private final Path dictFile;

    /**
     * @param dbTerms  DB 용어 사전 (null 이면 쓰지 않음)
     * @param dictFile 추가 용어 사전 파일 (null 이면 쓰지 않음)
     */
    public LlmTranslator(LlmClient client, String model, TranslationPipeline.Options options,
                         TermSource dbTerms, Path dictFile) {
        this.client = client;
        this.model = model;
        this.options = options;
        this.dbTerms = dbTerms;
        this.dictFile = dictFile;
    }

    /** .env(없으면 환경 변수) 설정으로 만든다. LLM_BASE_URL 이 없으면 null (번역하지 않음). */
    public static LlmTranslator fromEnv(TermSource dbTerms) {
        boolean demo = "true".equalsIgnoreCase(Config.get("TRANSLATE_DEMO"));
        if (!demo && Config.get("LLM_BASE_URL") == null) {
            return null;
        }
        LlmClient client = demo ? LlmClient.demo() : LlmClient.fromEnv();
        String model = Config.get("LLM_MODEL");
        if (model == null) {
            model = "gemma-4-26b";
            for (String m : Config.get("LLM_MODELS", "").split(",")) {
                if (!m.isBlank()) {
                    model = m.strip();
                    break;
                }
            }
        }
        TranslationPipeline.Options options = new TranslationPipeline.Options(
                Math.max(1, Integer.parseInt(Config.get("TRANSLATE_BUNDLE_SIZE", "4"))),
                Double.parseDouble(Config.get("LLM_TEMPERATURE", "0.2")),
                Integer.parseInt(Config.get("LLM_MAX_TOKENS", "8192")),
                Config.get("TRANSLATE_LANGUAGE", "English"),
                Math.max(0, Integer.parseInt(Config.get("TRANSLATE_QUIZ_COUNT", "3"))));
        String dict = Config.get("TERM_DICT_PATH");
        return new LlmTranslator(client, model, options, dbTerms, dict == null ? null : Path.of(dict));
    }

    /** 서버 시작 때 보여 줄 설정 요약 */
    public String describe() {
        return (client.isDemo() ? "데모 모드 ([DEMO] 가짜 문장)" : client.endpoint())
                + " | 모델 " + model + " | 묶음 " + options.bundleSize() + "페이지 | 설명 언어 " + options.explainLanguage()
                + (dictFile == null ? "" : " | 추가 사전 " + dictFile);
    }

    @Override
    public Translation translate(String fileName, List<Item> items) {
        List<Page> pages = toPages(items);
        if (pages.isEmpty()) {
            return Translation.empty();
        }
        String tag = "[번역 " + fileName + "] ";
        TermDictionary dictionary = dictionary(tag);
        String allText = pages.stream().map(Page::text).collect(Collectors.joining("\n"));
        List<TermMatch> found = dictionary.match(allText);
        try {
            if (!client.isDemo()) {
                // 연결 확인: LLM 서버가 꺼져 있으면 묶음마다 기다리지 않고 바로 건너뛴다
                List<String> available = client.listModels();
                if (!available.isEmpty() && !available.contains(model)) {
                    System.out.println(tag + "[경고] 모델 '" + model + "' 이(가) 서버 목록에 없습니다: " + available);
                }
            }
            TranslationPipeline pipeline = new TranslationPipeline(client, dictionary, options,
                    line -> System.out.println(tag + line));
            PipelineResult result = pipeline.run(model, pages);
            EvalResult eval = Evaluator.evaluate(result, pages, dictionary);
            List<String> known = dictionary.terms().stream().map(Models.Term::ko).toList();
            List<AnalysisResultDao.Term> newTerms = TermFilter.pick(result.bundles(), allText, known);
            System.out.printf(Locale.ROOT, "%s완료: 페이지 %d개, 용어 일치율 %.1f%% (%d/%d), JSON 실패 %d, 누락 페이지 %d, 새 전공용어 %d개%n",
                    tag, pages.size(), eval.termMatchRate() * 100, eval.termHits(), eval.termTotal(),
                    result.stats().jsonFailed, eval.missingPages(), newTerms.size());
            return toTranslation(result, found, newTerms);
        } catch (IOException e) {
            System.out.println(tag + "LLM 서버를 쓸 수 없어 번역을 건너뜁니다 (용어 목록만 저장): " + e.getMessage());
            return new Translation(null, null, null, null, toTerms(found, null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Translation.empty();
        }
    }

    // ---------------- 입력 ----------------

    /** 추출 문단을 페이지별 글자로 묶는다. 같은 페이지의 문단은 빈 줄로 잇는다. */
    static List<Page> toPages(List<Item> items) {
        Map<Integer, StringBuilder> byPage = new TreeMap<>();
        for (Item item : items == null ? List.<Item>of() : items) {
            if (item == null || item.content() == null || item.content().isBlank()) {
                continue;
            }
            StringBuilder sb = byPage.computeIfAbsent(item.page(), k -> new StringBuilder());
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(item.content().strip());
        }
        List<Page> pages = new ArrayList<>();
        byPage.forEach((page, text) -> pages.add(new Page(page, text.toString())));
        return pages;
    }

    /** DB 사전 + 파일 사전. 같은 한국어 용어는 DB(팀 공용) 쪽을 쓴다. 읽지 못한 쪽은 빼고 진행한다. */
    private TermDictionary dictionary(String tag) {
        Map<String, Models.Term> byKorean = new LinkedHashMap<>();
        if (dbTerms != null) {
            try {
                for (AnalysisResultDao.Term t : dbTerms.load()) {
                    if (t == null || blank(t.korean()) || blank(t.english())) {
                        continue;
                    }
                    String ko = t.korean().strip();
                    byKorean.putIfAbsent(ko, new Models.Term(ko, t.english().strip(),
                            t.explanation() == null ? "" : t.explanation().strip()));
                }
            } catch (Exception e) {
                System.out.println(tag + "DB 용어 사전을 읽지 못했습니다 (파일 사전만 사용): " + e.getMessage());
            }
        }
        if (dictFile != null) {
            try {
                for (Models.Term t : TermDictionary.load(dictFile).terms()) {
                    byKorean.putIfAbsent(t.ko(), t);
                }
            } catch (IOException e) {
                System.out.println(tag + "용어 사전 파일을 읽지 못했습니다: " + dictFile.toAbsolutePath() + " (" + e + ")");
            }
        }
        System.out.println(tag + "용어 사전 " + byKorean.size() + "개");
        return new TermDictionary(new ArrayList<>(byKorean.values()));
    }

    // ---------------- 결과 → 결과 화면 카드 4개 + 용어 ----------------

    static Translation toTranslation(PipelineResult result, List<TermMatch> found) {
        return toTranslation(result, found, List.of());
    }

    /**
     * @param newTerms LLM 이 찾고 TermFilter 가 남긴 새 전공용어. 사전 용어 뒤에 붙는다.
     *                 저장할 때 사전에 없으면 "확인 전" 으로 들어간다 (TermDao.ensureIds).
     */
    static Translation toTranslation(PipelineResult result, List<TermMatch> found,
                                     List<AnalysisResultDao.Term> newTerms) {
        StringBuilder translated = new StringBuilder();
        StringBuilder pageSummaries = new StringBuilder();
        List<String> links = new ArrayList<>();
        StringBuilder concepts = new StringBuilder();
        boolean anyTranslated = false;
        boolean manyBundles = result.bundles().size() > 1;

        for (BundleOutput b : result.bundles()) {
            Map<Integer, PageResult> byPage = new HashMap<>();
            if (b.result() != null) {
                for (PageResult p : nl(b.result().pages())) {
                    if (p != null) {
                        byPage.putIfAbsent(p.page(), p);
                    }
                }
            }
            for (int number : b.pageNumbers()) {
                PageResult p = byPage.get(number);
                if (p == null || blank(p.translation())) {
                    block(translated, "[Page " + number + "]", "(This page could not be translated. Please try again later.)");
                    continue;
                }
                anyTranslated = true;
                block(translated, "[Page " + number + "]", p.translation());
                List<String> lines = nl(p.summary()).stream().filter(s -> !blank(s)).map(s -> "- " + s.strip()).toList();
                block(pageSummaries, "[Page " + number + "]", String.join("\n", lines));
                if (!blank(p.linksToPrevious())) {
                    links.add("Page " + number + ": " + p.linksToPrevious().strip());
                }
            }
            if (b.result() != null) {
                String heading = !manyBundles ? null
                        : (b.pageNumbers().size() == 1 ? "Page " : "Pages ") + range(b.pageNumbers());
                block(concepts, heading, b.result().conceptExplanation());
            }
        }

        UnitResult u = result.unit();
        StringBuilder summary = new StringBuilder();
        StringBuilder explanation = new StringBuilder();
        StringBuilder exam = new StringBuilder();
        if (u != null) {
            block(summary, null, u.unitSummary());
            // 결과 카드에는 첫 줄만 보이므로 핵심 개념은 한 줄로
            List<String> key = nl(u.keyConcepts()).stream().filter(s -> !blank(s)).map(String::strip).toList();
            if (!key.isEmpty()) {
                block(explanation, null, "Key concepts: " + String.join(" · ", key));
            }
            exam(exam, u);
        }
        block(summary, "Page summaries", pageSummaries.toString());
        block(explanation, "Concept explanation", concepts.toString());
        block(explanation, "How the pages connect", String.join("\n", links));

        List<AnalysisResultDao.Term> terms = new ArrayList<>(toTerms(found, u));
        terms.addAll(newTerms == null ? List.of() : newTerms);
        return new Translation(anyTranslated ? translated.toString() : null, orNull(explanation), orNull(summary),
                orNull(exam), terms);
    }

    /** 퀴즈 문제 → 체크리스트 → 정답 순서 (문제를 풀기 전에 정답이 보이지 않게) */
    private static void exam(StringBuilder exam, UnitResult u) {
        StringBuilder questions = new StringBuilder();
        List<String> answers = new ArrayList<>();
        int n = 0;
        for (Quiz q : nl(u.quizzes())) {
            if (q == null || blank(q.question())) {
                continue;
            }
            n++;
            StringBuilder one = new StringBuilder("Q" + n + ". " + q.question().strip());
            List<String> options = nl(q.options());
            String answer = q.answer() == null ? "" : q.answer().strip();
            String answerLabel = answer;
            for (int k = 0; k < options.size(); k++) {
                String option = options.get(k) == null ? "" : options.get(k).strip();
                char label = (char) ('A' + k);
                one.append("\n   ").append(label).append(". ").append(option);
                if (option.equals(answer)) {
                    answerLabel = label + ". " + option;
                }
            }
            block(questions, null, one.toString());
            answers.add("Q" + n + ". " + answerLabel + (blank(q.explanation()) ? "" : " — " + q.explanation().strip()));
        }
        List<String> checklist = nl(u.checklist()).stream().filter(s -> !blank(s)).map(s -> "[ ] " + s.strip()).toList();
        block(exam, null, questions.toString());
        block(exam, "Self-check", String.join("\n", checklist));
        block(exam, "Answers", String.join("\n", answers));
    }

    /** 자료에 나온 사전 용어 (등장 횟수 순). 설명은 LLM 의 쉬운 설명이 있으면 그것, 없으면 사전 설명. */
    static List<AnalysisResultDao.Term> toTerms(List<TermMatch> found, UnitResult u) {
        Map<String, String> easy = new HashMap<>();
        if (u != null) {
            for (TermEntry t : nl(u.terms())) {
                if (t != null && !blank(t.ko()) && !blank(t.easyExplanation())) {
                    easy.put(t.ko().strip(), t.easyExplanation().strip());
                }
            }
        }
        List<AnalysisResultDao.Term> terms = new ArrayList<>();
        for (TermMatch m : found) {
            String ko = m.term().ko();
            String explanation = easy.getOrDefault(ko, m.term().description());
            terms.add(new AnalysisResultDao.Term(ko, m.term().primaryEnglish(), blank(explanation) ? null : explanation));
        }
        return terms;
    }

    // ---------------- 도우미 ----------------

    /** 빈 줄로 구분된 단락 하나를 붙인다. 본문이 비어 있으면 아무것도 하지 않는다. */
    private static void block(StringBuilder sb, String heading, String body) {
        if (blank(body)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        if (heading != null) {
            sb.append(heading).append('\n');
        }
        sb.append(body.strip());
    }

    private static String range(List<Integer> numbers) {
        if (numbers.isEmpty()) {
            return "";
        }
        int first = numbers.get(0);
        int last = numbers.get(numbers.size() - 1);
        return first == last ? String.valueOf(first) : first + "-" + last;
    }

    private static String orNull(StringBuilder sb) {
        return sb.length() == 0 ? null : sb.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static <T> List<T> nl(List<T> list) {
        return list == null ? List.of() : list;
    }
}
