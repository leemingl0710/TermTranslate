package com.termtranslator.core;

import com.termtranslator.core.Models.*;
import com.termtranslator.llm.LlmClient;
import com.termtranslator.llm.LlmClient.ChatResult;
import com.termtranslator.llm.LlmClient.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 단원 하나를 처리하는 파이프라인.
 *
 *   페이지들 → 묶음(bundleSize 페이지)으로 나눔
 *   묶음마다: 사전 매칭 → [앞 묶음 요약 + 용어 + 본문] 으로 LLM 요청 → 번역/요약/연관/개념설명/묶음요약
 *   마지막: 묶음 요약들 + 단원 전체 용어 → 단원 요약 / 용어 목록 / 체크리스트 / 퀴즈
 */
public final class TranslationPipeline {

    public record Options(int bundleSize, double temperature, int maxTokens, String explainLanguage, int quizCount) {}

    private final LlmClient client;
    private final TermDictionary dictionary;
    private final Options opt;
    private final Consumer<String> logger;

    public TranslationPipeline(LlmClient client, TermDictionary dictionary, Options options) {
        this(client, dictionary, options, System.out::println);
    }

    /** logger: 진행 상황 한 줄씩 받을 곳 (콘솔 대신 화면에 보여 줄 때 사용) */
    public TranslationPipeline(LlmClient client, TermDictionary dictionary, Options options, Consumer<String> logger) {
        this.client = client;
        this.dictionary = dictionary;
        this.opt = options;
        this.logger = logger;
    }

    public PipelineResult run(String model, List<Page> pages) throws InterruptedException {
        RunStats stats = new RunStats();
        List<BundleOutput> bundles = new ArrayList<>();
        String previousSummary = "";

        for (int i = 0; i < pages.size(); i += opt.bundleSize()) {
            List<Page> bundle = pages.subList(i, Math.min(i + opt.bundleSize(), pages.size()));
            List<Integer> numbers = bundle.stream().map(Page::number).toList();
            String text = bundle.stream().map(Page::text).collect(Collectors.joining("\n"));
            List<TermMatch> matched = dictionary.match(text);

            log(model, "묶음 페이지 " + numbers + " 처리 중 (사전 용어 " + matched.size() + "개)");
            String prompt = PromptBuilder.bundlePrompt(bundle, matched, previousSummary, opt.explainLanguage());
            BundleResult result = requestJson(model, prompt, BundleResult::fromJson, stats);

            if (result == null) {
                log(model, "  → 응답 실패, 이 묶음은 건너뜀");
                bundles.add(new BundleOutput(numbers, matched, null, true));
            } else {
                bundles.add(new BundleOutput(numbers, matched, result, false));
                if (!result.bundleSummary().isBlank()) previousSummary = result.bundleSummary();
            }
        }

        List<String> summaries = bundles.stream()
                .filter(b -> b.result() != null && !b.result().bundleSummary().isBlank())
                .map(b -> b.result().bundleSummary())
                .toList();
        String allText = pages.stream().map(Page::text).collect(Collectors.joining("\n"));
        List<TermMatch> unitTerms = dictionary.match(allText);

        UnitResult unit = null;
        if (!summaries.isEmpty()) {
            log(model, "단원 요약 · 용어 목록 · 체크리스트 · 퀴즈 생성 중");
            String prompt = PromptBuilder.unitPrompt(summaries, unitTerms, opt.explainLanguage(), opt.quizCount());
            unit = requestJson(model, prompt, UnitResult::fromJson, stats);
        }
        return new PipelineResult(model, opt.explainLanguage(), bundles, unit, stats);
    }

    /** JSON 응답 요청. 형식이 깨지면 한 번 다시 요청한다. 끝내 실패하면 null. */
    private <T> T requestJson(String model, String userPrompt, Function<Map<String, Object>, T> mapper, RunStats stats)
            throws InterruptedException {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(PromptBuilder.SYSTEM));
        messages.add(Message.user(userPrompt));
        stats.jsonRequests++;

        for (int attempt = 1; attempt <= 2; attempt++) {
            ChatResult res;
            try {
                res = client.chat(model, messages, opt.temperature(), opt.maxTokens());
            } catch (java.io.IOException e) {
                log(model, "  → API 호출 실패: " + e.getMessage());
                stats.jsonFailed++;
                return null;
            }
            stats.calls++;
            stats.totalLatencyMs += res.latencyMs();
            stats.promptTokens += res.promptTokens();
            stats.completionTokens += res.completionTokens();

            try {
                T parsed = mapper.apply(Json.parseObject(JsonText.extract(res.content())));
                if (attempt == 1) stats.jsonFirstTryOk++;
                return parsed;
            } catch (RuntimeException e) {
                log(model, "  → JSON 형식 오류 (시도 " + attempt + "): " + e.getMessage());
                messages.add(Message.assistant(res.content()));
                messages.add(Message.user(PromptBuilder.jsonRetryMessage()));
            }
        }
        stats.jsonFailed++;
        return null;
    }

    private void log(String model, String msg) {
        logger.accept("[" + model + "] " + msg);
    }
}
