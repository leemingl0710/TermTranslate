package com.termtranslator.core;

/** 모델 응답에서 JSON 부분만 꺼낸다 (사고 과정 태그, 코드펜스, 앞뒤 설명문 제거). */
public final class JsonText {
    private JsonText() {}

    public static String extract(String content) {
        if (content == null) throw new IllegalArgumentException("빈 응답");
        String s = content.replaceAll("(?s)<think>.*?</think>", "");
        // 닫는 태그 없이 끝까지 생각만 한 경우도 처리
        int openThink = s.indexOf("<think>");
        if (openThink >= 0) s = s.substring(0, openThink);
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("응답에 JSON 객체가 없음");
        return s.substring(start, end + 1);
    }
}
