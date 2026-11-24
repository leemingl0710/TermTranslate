package com.termtranslator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 설정값 읽기: 1) 시스템 환경변수  2) 실행 폴더의 .env 파일  순서로 찾는다.
 * .env 형식: KEY=VALUE (한 줄에 하나, # 은 주석). API 키를 소스코드에 쓰지 않기 위해 사용.
 * .env 는 .gitignore 에 들어 있어 git 에 올라가지 않는다.
 */
public final class Config {

    private static final Map<String, String> DOTENV = loadDotEnv(Path.of(".env"));

    private Config() {}

    public static String get(String key) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) return v.trim();
        v = DOTENV.get(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public static String get(String key, String fallback) {
        String v = get(key);
        return v == null ? fallback : v;
    }

    public static boolean dotEnvLoaded() {
        return !DOTENV.isEmpty();
    }

    private static Map<String, String> loadDotEnv(Path file) {
        Map<String, String> map = new HashMap<>();
        if (!Files.isRegularFile(file)) return map;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith("﻿")) line = line.substring(1);
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
                int eq = t.indexOf('=');
                String key = t.substring(0, eq).trim();
                String value = t.substring(eq + 1).trim();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException e) {
            System.err.println(".env 파일을 읽지 못했습니다: " + e.getMessage());
        }
        return map;
    }
}
