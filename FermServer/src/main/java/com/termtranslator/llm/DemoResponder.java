package com.termtranslator.llm;

import com.termtranslator.core.Json;
import com.termtranslator.llm.LlmClient.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 데모 모드 응답기: 실제 AI 를 부르지 않고, 프롬프트에 들어 있는 페이지 번호·사전 용어로
 * 형식이 맞는 JSON 을 만든다. 파이프라인·평가·보고서가 끝까지 도는지 확인하는 용도이며,
 * 모든 문장 앞에 [DEMO] 를 붙여 실제 결과와 헷갈리지 않게 한다.
 */
final class DemoResponder {

    private static final Pattern PAGE = Pattern.compile("=== PAGE (\\d+) ===\\n(.*?)(?=\\n=== PAGE |\\n## Output format)", Pattern.DOTALL);
    private static final Pattern BUNDLE_TERM = Pattern.compile("^- (.+?) -> (.+?)(?:  \\(meaning: (.*)\\))?$", Pattern.MULTILINE);
    private static final Pattern UNIT_TERM = Pattern.compile("^- ko: (.+?) \\| en: (.+?)(?: \\| meaning: (.*))?$", Pattern.MULTILINE);

    private DemoResponder() {}

    static String respond(List<Message> messages) {
        String prompt = messages.size() > 1 ? messages.get(1).content() : messages.get(0).content();
        return prompt.contains("=== PAGE ") ? bundle(prompt) : unit(prompt);
    }

    private static String bundle(String prompt) {
        List<String[]> terms = terms(BUNDLE_TERM, prompt);
        List<Object> pages = new ArrayList<>();
        Matcher m = PAGE.matcher(prompt);
        List<Integer> numbers = new ArrayList<>();
        while (m.find()) {
            int no = Integer.parseInt(m.group(1));
            numbers.add(no);
            String text = m.group(2);
            List<String> used = new ArrayList<>();
            for (String[] t : terms) if (text.contains(t[0])) used.add(t[1]);
            String termText = used.isEmpty() ? "no glossary terms" : String.join(", ", used);

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("page", no);
            p.put("translation", "[DEMO] Placeholder translation of page " + no
                    + ". A real model would translate the Korean text here. Glossary terms on this page: " + termText + ".");
            p.put("summary", List.of("[DEMO] Summary line 1 of page " + no + ".",
                    "[DEMO] Summary line 2: " + termText + ".",
                    "[DEMO] Summary line 3 of page " + no + "."));
            p.put("linksToPrevious", numbers.size() > 1 || prompt.contains("## Summary of the previous section")
                    ? "[DEMO] This page builds on the earlier pages." : "");
            pages.add(p);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pages", pages);
        out.put("conceptExplanation", "[DEMO] Concept explanation for pages " + numbers + ".");
        out.put("bundleSummary", "[DEMO] Summary of pages " + numbers + ".");
        return Json.write(out);
    }

    private static String unit(String prompt) {
        List<String[]> terms = terms(UNIT_TERM, prompt);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unitSummary", "[DEMO] Unit summary. A real model would connect all section summaries here.");
        out.put("keyConcepts", terms.stream().limit(6).map(t -> t[1]).toList());

        List<Object> entries = new ArrayList<>();
        for (String[] t : terms) {
            entries.add(Map.of("ko", t[0], "en", t[1], "easyExplanation", "[DEMO] (사전 설명) " + t[2]));
        }
        out.put("terms", entries);
        out.put("checklist", terms.stream().limit(5).map(t -> "[DEMO] I can explain " + t[1] + ".").toList());

        // 용어 뜻 → 영어 용어 맞히기 퀴즈 (보기는 사전 용어에서 4개)
        List<Object> quizzes = new ArrayList<>();
        if (prompt.contains("\"quizzes\"") && terms.size() >= 4) {
            for (int q = 0; q < Math.min(3, terms.size()); q++) {
                String[] ans = terms.get(q);
                List<String> options = new ArrayList<>();
                for (int k = 0; k < 4; k++) options.add(terms.get((q + k) % terms.size())[1]);
                java.util.Collections.rotate(options, q); // 정답 위치를 바꿔 줌
                Map<String, Object> quiz = new LinkedHashMap<>();
                quiz.put("question", "[DEMO] Which term means: " + ans[2] + "?");
                quiz.put("options", options);
                quiz.put("answer", ans[1]);
                quiz.put("explanation", "[DEMO] " + ans[0] + " = " + ans[1] + ".");
                quizzes.add(quiz);
            }
        }
        out.put("quizzes", quizzes);
        return Json.write(out);
    }

    private static List<String[]> terms(Pattern p, String prompt) {
        List<String[]> list = new ArrayList<>();
        Matcher m = p.matcher(prompt);
        while (m.find()) {
            list.add(new String[]{m.group(1).trim(), m.group(2).trim(), m.group(3) == null ? "" : m.group(3).trim()});
        }
        return list;
    }
}
