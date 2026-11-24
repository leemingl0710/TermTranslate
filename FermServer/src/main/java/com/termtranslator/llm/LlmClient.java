package com.termtranslator.llm;

import com.termtranslator.Config;
import com.termtranslator.core.Json;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환 Chat Completions API 클라이언트 (vLLM, Ollama, LiteLLM, TGI 등).
 * 내부 LLM 서비스의 형식이 다르면 이 클래스의 chat() 만 수정하면 된다.
 *
 * 환경변수 (VS Code 에서는 프로젝트 루트의 .env 파일에 적으면 자동으로 읽힘)
 *   LLM_BASE_URL     예) http://llm.school.ac.kr/v1   (없으면 데모 모드)
 *   LLM_API_KEY      API 키 (없으면 Authorization 헤더 생략)
 *   LLM_JSON_MODE    true/false  response_format=json_object 사용 여부 (기본 true)
 *   LLM_EXTRA_BODY   요청 본문에 그대로 합칠 JSON. 예) {"chat_template_kwargs":{"enable_thinking":false}}
 *   LLM_TIMEOUT_SEC  요청 타임아웃 초 (기본 300)
 *   LLM_RATE_LIMIT_PER_MIN  API 키의 분당 요청 한도 (예: 30). 요청을 시작하는 간격을 띄워 한도를 넘지 않게 한다
 *                    (여유를 두고 한도보다 1번 적게: 30 이면 약 2.1초 간격). 0 이면 제한 없음 (기본 0)
 */
public final class LlmClient {

    public record Message(String role, String content) {
        public static Message system(String c) { return new Message("system", c); }
        public static Message user(String c) { return new Message("user", c); }
        public static Message assistant(String c) { return new Message("assistant", c); }
    }

    public record ChatResult(String content, long latencyMs, int promptTokens, int completionTokens) {}

    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final boolean jsonMode;
    private final Map<String, Object> extraBody;
    private final Duration timeout;
    private final boolean demo;
    /** 요청 시작 사이의 최소 간격 (분당 한도에서 계산). 0 이면 제한 없음 */
    private final long minIntervalMs;
    /** 다음 요청을 시작해도 되는 시각. 여러 스레드가 같은 클라이언트를 써도 한도를 지키도록 this 로 잠근다 */
    private long nextSlotMs;

    private LlmClient(String baseUrl, String apiKey, boolean jsonMode, Map<String, Object> extraBody,
                      Duration timeout, boolean demo, int requestsPerMinute) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.jsonMode = jsonMode;
        this.extraBody = extraBody;
        this.timeout = timeout;
        this.demo = demo;
        // 네트워크 지연이 들쭉날쭉해도 서버 쪽 1분 창에 한도를 넘지 않도록 한도보다 1번 적게 잡는다 (30 → 약 2.07초 간격)
        int safePerMinute = Math.max(1, requestsPerMinute - 1);
        this.minIntervalMs = requestsPerMinute > 0 ? (60_000L + safePerMinute - 1) / safePerMinute : 0;
        this.http = demo ? null : HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(ProxySelector.getDefault())
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /** 실제 API 없이 동작하는 데모 클라이언트. 결과는 모두 [DEMO] 표시가 붙은 가짜 문장이다. */
    public static LlmClient demo() {
        return new LlmClient("", "", false, null, Duration.ZERO, true, 0);
    }

    public static LlmClient fromEnv() {
        String base = Config.get("LLM_BASE_URL");
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("환경변수 LLM_BASE_URL 이 설정되지 않았습니다. "
                    + ".env 파일에 LLM_BASE_URL=http://서버주소/v1 을 적거나, --demo 옵션으로 데모 모드를 쓰세요.");
        }
        boolean json = !"false".equalsIgnoreCase(env("LLM_JSON_MODE", "true"));
        Map<String, Object> extra = null;
        String extraRaw = Config.get("LLM_EXTRA_BODY");
        if (extraRaw != null && !extraRaw.isBlank()) {
            try {
                extra = Json.parseObject(extraRaw);
            } catch (Exception e) {
                throw new IllegalStateException("LLM_EXTRA_BODY 가 올바른 JSON 객체가 아닙니다: " + extraRaw, e);
            }
        }
        long timeoutSec = Long.parseLong(env("LLM_TIMEOUT_SEC", "300"));
        int perMinute = Integer.parseInt(env("LLM_RATE_LIMIT_PER_MIN", "0"));
        return new LlmClient(base.trim(), Config.get("LLM_API_KEY"), json, extra, Duration.ofSeconds(timeoutSec),
                false, perMinute);
    }

    /** 분당 요청 한도 (LLM_RATE_LIMIT_PER_MIN) 에 맞춰, 앞 요청을 시작한 뒤 최소 간격이 지날 때까지 기다린다. */
    private void waitForSlot() throws InterruptedException {
        if (minIntervalMs <= 0) return;
        long waitMs;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long start = Math.max(now, nextSlotMs);
            nextSlotMs = start + minIntervalMs;
            waitMs = start - now;
        }
        if (waitMs > 0) Thread.sleep(waitMs);
    }

    /** 한도 초과(429)일 때 기다릴 시간: 서버가 Retry-After(초)를 주면 그만큼, 없으면 30초·60초 */
    private static long retryAfterMs(HttpResponse<?> res, int attempt) {
        String header = res.headers().firstValue("Retry-After").orElse("").trim();
        try {
            return Math.min(120, Math.max(1, Long.parseLong(header))) * 1000L;
        } catch (NumberFormatException e) {
            return 30_000L * attempt;
        }
    }

    public boolean isDemo() {
        return demo;
    }

    public String endpoint() {
        return demo ? "(데모 모드)" : baseUrl + "/chat/completions";
    }

    public ChatResult chat(String model, List<Message> messages, double temperature, int maxTokens)
            throws IOException, InterruptedException {
        if (demo) {
            long start = System.nanoTime();
            String content = DemoResponder.respond(messages);
            int promptChars = messages.stream().mapToInt(m -> m.content().length()).sum();
            return new ChatResult(content, (System.nanoTime() - start) / 1_000_000, promptChars / 3, content.length() / 4);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Object> msgs = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", m.role());
            mm.put("content", m.content());
            msgs.add(mm);
        }
        body.put("messages", msgs);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (jsonMode) body.put("response_format", Map.of("type", "json_object"));
        if (extraBody != null) body.putAll(extraBody);

        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(endpoint()))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
        if (!apiKey.isBlank()) rb.header("Authorization", "Bearer " + apiKey);
        HttpRequest request = rb.build();

        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            waitForSlot();
            long backoffMs = 2000L * attempt;
            long start = System.nanoTime();
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                int code = res.statusCode();
                if (code == 429) {
                    backoffMs = retryAfterMs(res, attempt);
                    lastError = new IOException("LLM 요청 한도 초과 (HTTP 429). .env 의 LLM_RATE_LIMIT_PER_MIN 을 키 한도에 맞추세요: "
                            + abbreviate(res.body()));
                    if (attempt < MAX_ATTEMPTS) {
                        System.out.println("[LLM] 요청 한도 초과(429) → " + backoffMs / 1000 + "초 뒤 다시 시도");
                    }
                } else if (code >= 500) {
                    lastError = new IOException("LLM 서버 오류 " + code + ": " + abbreviate(res.body()));
                } else if (code >= 400) {
                    throw new IOException("LLM 요청 실패 " + code + ": " + abbreviate(res.body())
                            + (code == 400 && jsonMode ? "\n  (서버가 response_format 을 지원하지 않으면 LLM_JSON_MODE=false 로 설정해 보세요)" : ""));
                } else {
                    return parse(res.body(), latencyMs);
                }
            } catch (java.net.http.HttpTimeoutException | java.net.ConnectException e) {
                lastError = new IOException("서버에 연결할 수 없음 (" + endpoint() + "): " + e, e);
            }
            if (attempt < MAX_ATTEMPTS) Thread.sleep(backoffMs);
        }
        throw lastError;
    }

    /**
     * 서버 연결 확인 + 등록된 모델 ID 목록 (GET {base}/models).
     * 연결 자체가 안 되면 IOException. 서버가 /models 를 지원하지 않으면 빈 목록.
     */
    public List<String> listModels() throws IOException, InterruptedException {
        if (demo) return List.of();
        waitForSlot();   // 모델 목록 조회도 키의 요청 한도에 들어간다
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                .timeout(Duration.ofSeconds(20)).GET();
        if (!apiKey.isBlank()) rb.header("Authorization", "Bearer " + apiKey);
        HttpResponse<String> res;
        try {
            res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
            throw new IOException("LLM 서버에 연결할 수 없습니다: " + baseUrl + " (" + e + ")", e);
        }
        if (res.statusCode() == 401 || res.statusCode() == 403) {
            throw new IOException("API 키가 없거나 잘못되었습니다 (HTTP " + res.statusCode() + "). LLM_API_KEY 를 확인하세요.");
        }
        if (res.statusCode() != 200) return List.of();
        try {
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> m : Json.objList(Json.parseObject(res.body()), "data")) ids.add(Json.str(m, "id"));
            return ids;
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static ChatResult parse(String raw, long latencyMs) throws IOException {
        Map<String, Object> root;
        try {
            root = Json.parseObject(raw);
        } catch (IllegalArgumentException e) {
            throw new IOException("API 응답이 JSON 이 아님: " + abbreviate(raw));
        }
        List<Map<String, Object>> choices = Json.objList(root, "choices");
        if (choices.isEmpty()) throw new IOException("API 응답에 choices 가 없음: " + abbreviate(raw));
        Object msg = choices.get(0).get("message");
        String content = msg instanceof Map ? Json.str((Map<String, Object>) msg, "content") : "";
        Object usageObj = root.get("usage");
        Map<String, Object> usage = usageObj instanceof Map ? (Map<String, Object>) usageObj : Map.of();
        return new ChatResult(content, latencyMs,
                Json.integer(usage, "prompt_tokens", 0), Json.integer(usage, "completion_tokens", 0));
    }

    private static String env(String key, String fallback) {
        return Config.get(key, fallback);
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) + "..." : s);
    }
}
