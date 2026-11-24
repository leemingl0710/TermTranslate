package com.termtranslator.eval;

import com.termtranslator.core.Models.*;
import com.termtranslator.core.TermDictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 자동 평가: 전공용어 번역 일치율, 한국어 잔존율, 영어 난이도(Flesch), 누락 페이지 */
public final class Evaluator {

    public record MissedTerm(int page, String ko, String expectedEn) {}

    public record EvalResult(
            int termHits, int termTotal, double termMatchRate,
            double koreanResidueRate, double fleschReadingEase,
            int missingPages, List<MissedTerm> missedTerms) {}

    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");

    private Evaluator() {}

    public static EvalResult evaluate(PipelineResult result, List<Page> pages, TermDictionary dict) {
        Map<Integer, String> translations = new HashMap<>();
        for (BundleOutput b : result.bundles()) {
            if (b.result() == null || b.result().pages() == null) continue;
            for (PageResult pr : b.result().pages()) {
                if (pr != null && pr.translation() != null) translations.put(pr.page(), pr.translation());
            }
        }

        int hits = 0, total = 0, missing = 0;
        List<MissedTerm> missed = new ArrayList<>();
        StringBuilder allEnglish = new StringBuilder();

        for (Page p : pages) {
            String tr = translations.get(p.number());
            if (tr == null || tr.isBlank()) {
                missing++;
            } else {
                allEnglish.append(tr).append('\n');
            }
            String lower = normalize(tr);
            for (TermMatch m : dict.match(p.text())) {
                total++;
                boolean found = m.term().englishVariants().stream()
                        .anyMatch(v -> containsTerm(lower, normalize(v)));
                if (found) hits++;
                else missed.add(new MissedTerm(p.number(), m.term().ko(), m.term().primaryEnglish()));
            }
        }

        String english = allEnglish.toString();
        return new EvalResult(hits, total, total == 0 ? 1.0 : (double) hits / total,
                koreanResidue(english), fleschReadingEase(english), missing, missed);
    }

    /** 소문자화 + 둥근 따옴표/다양한 하이픈/연속 공백 통일 (Hooke’s law == Hooke's law) */
    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[‘’ʼ`]", "'")
                .replaceAll("[‐-―]", "-")
                .replaceAll("\\s+", " ");
    }

    /** 단어 경계 기준 포함 여부. "stress" 는 "stresses" 에는 맞고 "distress" 에는 안 맞는다. */
    static boolean containsTerm(String text, String term) {
        Pattern p = Pattern.compile("(?<![a-z])" + Pattern.quote(term));
        return p.matcher(text).find();
    }

    /** 번역문 글자 중 한글 비율 (0 이 이상적) */
    static double koreanResidue(String text) {
        long hangul = text.codePoints().filter(c -> c >= 0xAC00 && c <= 0xD7A3).count();
        long latin = text.codePoints().filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).count();
        return hangul + latin == 0 ? 0 : (double) hangul / (hangul + latin);
    }

    /** Flesch Reading Ease. 높을수록 쉬움 (60~70 = 평이한 영어, 30 미만 = 매우 어려움) */
    static double fleschReadingEase(String text) {
        int words = 0, syllables = 0;
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            words++;
            syllables += syllables(m.group().toLowerCase(Locale.ROOT));
        }
        long sentences = Pattern.compile("[.!?]+(\\s|$)").matcher(text).results().count();
        if (words == 0) return 0;
        sentences = Math.max(1, sentences);
        return 206.835 - 1.015 * ((double) words / sentences) - 84.6 * ((double) syllables / words);
    }

    static int syllables(String w) {
        if (w.length() <= 3) return 1;
        String s = w.replaceAll("(?:[^laeiouy]es|ed|[^laeiouy]e)$", "").replaceAll("^y", "");
        int count = 0;
        boolean prevVowel = false;
        for (char c : s.toCharArray()) {
            boolean v = "aeiouy".indexOf(c) >= 0;
            if (v && !prevVowel) count++;
            prevVowel = v;
        }
        return Math.max(1, count);
    }
}
