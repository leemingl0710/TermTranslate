package com.termtranslator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 파이프라인에서 쓰는 데이터 타입 모음. 필드명은 LLM 이 돌려주는 JSON 키와 같다. */
public final class Models {
    private Models() {}

    /** 추출된 페이지 본문 (한국어) */
    public record Page(int number, String text) {}

    /** 사전 항목: 한국어 용어, 표준 영어 번역(| 로 대체 표현 구분 가능), 설명 */
    public record Term(String ko, String en, String description) {
        public List<String> englishVariants() {
            return List.of(en.split("\\|")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        public String primaryEnglish() {
            List<String> v = englishVariants();
            return v.isEmpty() ? en : v.get(0);
        }
    }

    /** 사전 매칭 결과: 용어와 등장 횟수 */
    public record TermMatch(Term term, int count) {}

    /** 페이지별 결과 */
    public record PageResult(int page, String translation, List<String> summary, String linksToPrevious) {}

    /**
     * 묶음(여러 페이지) 단위 LLM 응답.
     * newTerms: 사전에 없는데 이 페이지들에 나온 전공용어 후보 (없거나 형식이 틀리면 빈 목록).
     */
    public record BundleResult(List<PageResult> pages, String conceptExplanation, String bundleSummary,
                               List<TermEntry> newTerms) {
        public BundleResult(List<PageResult> pages, String conceptExplanation, String bundleSummary) {
            this(pages, conceptExplanation, bundleSummary, List.of());
        }

        static BundleResult fromJson(Map<String, Object> m) {
            List<PageResult> pages = new ArrayList<>();
            for (Map<String, Object> p : Json.objList(m, "pages")) {
                pages.add(new PageResult(Json.integer(p, "page", -1), Json.str(p, "translation"),
                        Json.strList(p, "summary"), Json.str(p, "linksToPrevious")));
            }
            if (pages.isEmpty()) throw new IllegalArgumentException("\"pages\" 배열이 비어 있음");
            List<TermEntry> newTerms = new ArrayList<>();
            for (Map<String, Object> t : Json.objList(m, "newTerms")) {
                newTerms.add(new TermEntry(Json.str(t, "ko"), Json.str(t, "en"), Json.str(t, "easyExplanation")));
            }
            return new BundleResult(pages, Json.str(m, "conceptExplanation"), Json.str(m, "bundleSummary"), newTerms);
        }
    }

    /** 용어 목록 항목: 한국어 · 영어 · 쉬운 설명 */
    public record TermEntry(String ko, String en, String easyExplanation) {}

    /** 복습 퀴즈 (4지선다) */
    public record Quiz(String question, List<String> options, String answer, String explanation) {}

    /** 단원 단위 LLM 응답 */
    public record UnitResult(String unitSummary, List<String> keyConcepts, List<TermEntry> terms,
                             List<String> checklist, List<Quiz> quizzes) {
        static UnitResult fromJson(Map<String, Object> m) {
            List<TermEntry> terms = new ArrayList<>();
            for (Map<String, Object> t : Json.objList(m, "terms")) {
                terms.add(new TermEntry(Json.str(t, "ko"), Json.str(t, "en"), Json.str(t, "easyExplanation")));
            }
            List<Quiz> quizzes = new ArrayList<>();
            for (Map<String, Object> q : Json.objList(m, "quizzes")) {
                quizzes.add(new Quiz(Json.str(q, "question"), Json.strList(q, "options"),
                        Json.str(q, "answer"), Json.str(q, "explanation")));
            }
            String summary = Json.str(m, "unitSummary");
            if (summary.isBlank()) throw new IllegalArgumentException("\"unitSummary\" 가 비어 있음");
            return new UnitResult(summary, Json.strList(m, "keyConcepts"), terms, Json.strList(m, "checklist"), quizzes);
        }
    }

    /** 묶음 하나의 처리 기록 */
    public record BundleOutput(List<Integer> pageNumbers, List<TermMatch> matchedTerms, BundleResult result, boolean failed) {}

    /** 모델 하나로 단원 전체를 처리한 결과 */
    public record PipelineResult(String model, String explainLanguage, List<BundleOutput> bundles, UnitResult unit, RunStats stats) {}
}
