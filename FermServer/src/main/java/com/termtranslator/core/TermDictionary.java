package com.termtranslator.core;

import com.termtranslator.core.Models.Term;
import com.termtranslator.core.Models.TermMatch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 전공용어 사전. CSV 형식 (UTF-8, 첫 줄은 헤더):
 *   ko,en,description
 *   전단응력,shear stress,단면에 평행하게 작용하는 응력
 * en 에 "strain|deformation" 처럼 | 로 허용할 대체 번역을 적을 수 있다 (첫 번째가 표준 번역).
 */
public final class TermDictionary {

    private final List<Term> terms; // 긴 용어 우선 정렬

    public TermDictionary(List<Term> terms) {
        this.terms = new ArrayList<>(terms);
        this.terms.sort(Comparator.comparingInt((Term t) -> t.ko().length()).reversed());
    }

    /**
     * CSV(쉼표) 또는 TSV(탭) 파일을 읽는다. 탭이 들어 있는 줄은 TSV 로 처리.
     * # 으로 시작하는 줄은 주석, 첫 칸이 "ko" 또는 "한국어" 로 시작하는 줄은 헤더로 보고 건너뛴다.
     */
    public static TermDictionary load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Term> terms = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("﻿")) line = line.substring(1);
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            List<String> cols = line.contains("\t") ? List.of(line.split("\t", -1)) : parseCsvLine(line);
            if (cols.size() < 2 || cols.get(0).isBlank() || cols.get(1).isBlank()) continue;
            String first = cols.get(0).trim().toLowerCase();
            if (first.equals("ko") || first.startsWith("한국어")) continue;
            terms.add(new Term(cols.get(0).trim(), cols.get(1).trim(), cols.size() > 2 ? cols.get(2).trim() : ""));
        }
        return new TermDictionary(terms);
    }

    public int size() {
        return terms.size();
    }

    /** 사전에 든 용어 전체 (긴 용어 순). 다른 사전과 합칠 때 쓴다. */
    public List<Term> terms() {
        return List.copyOf(terms);
    }

    /**
     * 본문에 등장하는 사전 용어를 찾는다.
     * 긴 용어를 먼저 매칭하고 그 자리를 지워서, "전단응력" 안의 "응력" 이 따로 잡히지 않게 한다.
     * 결과는 등장 횟수가 많은 순.
     */
    public List<TermMatch> match(String text) {
        StringBuilder masked = new StringBuilder(text);
        List<TermMatch> result = new ArrayList<>();
        for (Term t : terms) {
            int count = 0;
            int idx = masked.indexOf(t.ko());
            while (idx >= 0) {
                count++;
                for (int k = idx; k < idx + t.ko().length(); k++) masked.setCharAt(k, '\u0000');
                idx = masked.indexOf(t.ko(), idx + t.ko().length());
            }
            if (count > 0) result.add(new TermMatch(t, count));
        }
        result.sort(Comparator.comparingInt(TermMatch::count).reversed());
        return result;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString()); cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
