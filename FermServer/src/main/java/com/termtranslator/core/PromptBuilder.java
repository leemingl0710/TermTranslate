package com.termtranslator.core;

import com.termtranslator.core.Models.Page;
import com.termtranslator.core.Models.TermMatch;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM 프롬프트 생성.
 * 지시문은 영어로 작성한다 (중간 크기 오픈 모델은 영어 지시를 더 안정적으로 따름).
 *
 * 번역(translation)은 항상 쉬운 영어이고, 요약·연결·개념 설명·체크리스트·퀴즈는
 * explainLanguage (기본 English) 로 만든다 → "다국어 설명 지원" 기능.
 * 다음 묶음에 넘기는 bundleSummary 는 문맥 품질을 위해 항상 영어.
 */
public final class PromptBuilder {

    public static final String SYSTEM = """
            You are a study assistant for international university students who study their major in Korea.
            You translate Korean lecture materials into clear, easy English (CEFR B1-B2: short sentences, common words),
            while keeping technical terms precise and consistent with the given glossary.
            Never leave Korean text in the English translation.
            Always reply with a single valid JSON object and nothing else (no markdown, no code fences).""";

    private static final int MAX_UNIT_TERMS = 30;

    private PromptBuilder() {}

    private static boolean isEnglish(String lang) {
        return lang == null || lang.isBlank() || lang.trim().equalsIgnoreCase("english");
    }

    private static String langNote(String lang) {
        return isEnglish(lang) ? "easy English" : "easy " + lang.trim();
    }

    /** 묶음 요청: 쉬운 영어 번역 + 3줄 요약 + 페이지 간 연관 + 개념 설명 + 묶음 요약 */
    public static String bundlePrompt(List<Page> pages, List<TermMatch> terms, String previousSummary, String explainLanguage) {
        String pageNumbers = pages.stream().map(p -> String.valueOf(p.number())).collect(Collectors.joining(", "));
        String lang = langNote(explainLanguage);
        StringBuilder sb = new StringBuilder();

        sb.append("## Task\nProcess the Korean lecture pages below.\n");
        sb.append("Explanation language: ").append(lang).append("\n\n");
        sb.append("For EACH page:\n");
        sb.append("1. \"translation\": Translate the whole page into easy ENGLISH. Keep every fact, formula, number, code and example. ")
          .append("Do not skip or shorten content.\n");
        sb.append("2. \"summary\": Exactly 3 short sentences in ").append(lang).append(" with the key points of the page.\n");
        sb.append("3. \"linksToPrevious\": 1-2 sentences in ").append(lang)
          .append(" on how this page uses or builds on concepts from earlier pages ")
          .append("(earlier pages in this set or the previous-section summary). Use \"\" if there is no clear link.\n\n");
        sb.append("For the whole set of pages:\n");
        sb.append("4. \"conceptExplanation\": Explain the main concepts and how they connect, in ").append(lang).append(" (4-8 sentences). ")
          .append("Do not just list definitions; explain why and how the ideas relate to each other.\n");
        sb.append("5. \"bundleSummary\": 3-5 ENGLISH sentences summarizing these pages. It will be used as context for the next pages.\n");
        if (!isEnglish(explainLanguage)) {
            sb.append("When you write in ").append(explainLanguage.trim())
              .append(", put the English glossary term in parentheses after each technical term.\n");
        }
        sb.append('\n');

        if (!terms.isEmpty()) {
            sb.append("## Required terminology\n");
            sb.append("These Korean terms MUST be translated with exactly the given English term:\n");
            for (TermMatch m : terms) {
                sb.append("- ").append(m.term().ko()).append(" -> ").append(m.term().primaryEnglish());
                if (!m.term().description().isBlank()) sb.append("  (meaning: ").append(m.term().description()).append(")");
                sb.append('\n');
            }
            sb.append('\n');
        }

        if (previousSummary != null && !previousSummary.isBlank()) {
            sb.append("## Summary of the previous section\n").append(previousSummary.trim()).append("\n\n");
        }

        sb.append("## Pages\n");
        for (Page p : pages) {
            sb.append("=== PAGE ").append(p.number()).append(" ===\n").append(p.text().trim()).append("\n\n");
        }

        sb.append("## Output format\n");
        sb.append("{\"pages\":[{\"page\":1,\"translation\":\"...\",\"summary\":[\"...\",\"...\",\"...\"],\"linksToPrevious\":\"...\"}],");
        sb.append("\"conceptExplanation\":\"...\",\"bundleSummary\":\"...\"}\n");
        sb.append("Return one object in \"pages\" for every page number: ").append(pageNumbers).append(".\n");
        return sb.toString();
    }

    /** 단원 요청: 단원 핵심 요약 + 핵심 개념 + 용어 목록 + 시험 대비 체크리스트 + 자동 퀴즈 */
    public static String unitPrompt(List<String> bundleSummaries, List<TermMatch> terms, String explainLanguage, int quizCount) {
        String lang = langNote(explainLanguage);
        StringBuilder sb = new StringBuilder();
        sb.append("## Task\nBelow are summaries of consecutive sections of one lecture unit, in order.\n");
        sb.append("Explanation language: ").append(lang).append("\n");
        sb.append("1. \"unitSummary\": Summarize the whole unit in ").append(lang).append(" (5-8 sentences), showing how the sections connect.\n");
        sb.append("2. \"keyConcepts\": The 5-10 most important concepts of the unit, each as a short phrase in ").append(lang).append(".\n");
        sb.append("3. \"terms\": For every glossary term below, give {\"ko\",\"en\",\"easyExplanation\"}. ");
        sb.append("easyExplanation is ONE short sentence in ").append(lang).append(" explaining the meaning in the context of this unit. ");
        sb.append("Keep \"ko\" and \"en\" exactly as given.\n");
        sb.append("4. \"checklist\": 8-12 self-check items for exam review in ").append(lang);
        sb.append(isEnglish(explainLanguage)
                ? ", each starting with \"I can\" (e.g. \"I can explain the difference between stress and strain.\").\n"
                : ", each written as \"I can ...\" statement in that language.\n");
        if (quizCount > 0) {
            sb.append("5. \"quizzes\": ").append(quizCount).append(" multiple-choice review questions in ").append(lang)
              .append(". Each has \"question\", exactly 4 \"options\", \"answer\" (copy the correct option text exactly) ")
              .append("and a 1-2 sentence \"explanation\". Test understanding of concepts, not memorization of wording.\n");
        }
        sb.append('\n');

        sb.append("## Section summaries\n");
        for (int i = 0; i < bundleSummaries.size(); i++) {
            sb.append(i + 1).append(". ").append(bundleSummaries.get(i).trim()).append('\n');
        }
        sb.append('\n');

        if (!terms.isEmpty()) {
            sb.append("## Glossary terms\n");
            terms.stream().limit(MAX_UNIT_TERMS).forEach(m -> {
                sb.append("- ko: ").append(m.term().ko()).append(" | en: ").append(m.term().primaryEnglish());
                if (!m.term().description().isBlank()) sb.append(" | meaning: ").append(m.term().description());
                sb.append('\n');
            });
            sb.append('\n');
        }

        sb.append("## Output format\n");
        sb.append("{\"unitSummary\":\"...\",\"keyConcepts\":[\"...\"],");
        sb.append("\"terms\":[{\"ko\":\"...\",\"en\":\"...\",\"easyExplanation\":\"...\"}],\"checklist\":[\"I can ...\"]");
        if (quizCount > 0) {
            sb.append(",\"quizzes\":[{\"question\":\"...\",\"options\":[\"...\",\"...\",\"...\",\"...\"],\"answer\":\"...\",\"explanation\":\"...\"}]");
        }
        sb.append("}\n");
        return sb.toString();
    }

    public static String jsonRetryMessage() {
        return "Your previous reply was not a valid JSON object in the required format. "
                + "Reply again with ONLY the JSON object, no other text.";
    }
}
