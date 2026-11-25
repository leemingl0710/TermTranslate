package com.termtranslator.core;

import com.termtranslator.core.Models.BundleResult;
import com.termtranslator.core.Models.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 묶음 요청이 새 전공용어를 묻고, 응답의 newTerms 를 읽는지 확인한다. */
class NewTermsPromptTest {

    @Test
    void bundlePromptAsksForNewTerms() {
        String prompt = PromptBuilder.bundlePrompt(List.of(new Page(1, "직렬화는 객체를 바이트로 바꾸는 것이다.")),
                List.of(), null, "English");
        assertTrue(prompt.contains("\"newTerms\""), "새 용어 요청");
        assertTrue(prompt.contains("Up to " + PromptBuilder.MAX_NEW_TERMS), "개수 제한");
        assertTrue(prompt.contains("Do NOT include: everyday words"), "일반 단어 제외 기준");
        assertTrue(prompt.contains("\"newTerms\":[{\"ko\""), "출력 형식에 newTerms");
    }

    @Test
    void parsesNewTerms() {
        BundleResult r = BundleResult.fromJson(Json.parseObject("""
                {"pages":[{"page":1,"translation":"t","summary":["a"],"linksToPrevious":""}],
                 "conceptExplanation":"c","bundleSummary":"b",
                 "newTerms":[{"ko":"직렬화","en":"serialization","easyExplanation":"Turning an object into bytes."}]}
                """));
        assertEquals(1, r.newTerms().size());
        assertEquals("직렬화", r.newTerms().get(0).ko());
        assertEquals("serialization", r.newTerms().get(0).en());
    }

    @Test
    void missingNewTermsIsEmpty() {
        BundleResult r = BundleResult.fromJson(Json.parseObject("""
                {"pages":[{"page":1,"translation":"t"}],"newTerms":"none"}
                """));
        assertTrue(r.newTerms().isEmpty(), "없거나 형식이 틀려도 번역은 계속");
    }
}
