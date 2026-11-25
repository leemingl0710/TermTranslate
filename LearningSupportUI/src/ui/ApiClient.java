package ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FermServer 와 HTTP 로 통신한다. 모든 메서드는 시간이 걸릴 수 있으니
 * 화면 스레드(EDT)가 아닌 SwingWorker 등에서 부를 것.
 *
 * <p>서버 주소는 기본 http://localhost:8080 이며, 실행 옵션 -Dserver.url=... 로 바꿀 수 있다.
 */
public final class ApiClient {

    /** 서버가 돌려준 실패. getMessage() 는 사용자에게 그대로 보여 줘도 된다. */
    public static final class ApiException extends Exception {
        private final int status;
        private final String error;

        public ApiException(int status, String error, String message) {
            super(message);
            this.status = status;
            this.error = error;
        }

        /** HTTP 상태 코드 (서버에 연결하지 못했으면 0). */
        public int getStatus() {
            return status;
        }

        /** 오류 종류 (예: EncryptedFileError, FILE_TOO_LARGE, SERVER_UNAVAILABLE). */
        public String getError() {
            return error;
        }
    }

    /** POST /documents 응답. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UploadResult(
            @JsonProperty("result_id") int resultId,
            @JsonProperty("document_id") int documentId,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("item_count") int itemCount) {
    }

    /** 추출된 문단 하나. flags: "code"(코드 캡처, 줄바꿈 유지), "table"(표 한 행), "low_confidence". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("page") int page,
            @JsonProperty("origin") String origin,
            @JsonProperty("content") String content,
            @JsonProperty("flags") List<String> flags) {

        public Item {
            flags = flags == null ? List.of() : List.copyOf(flags);
        }
    }

    /** 결과 화면의 용어 하나. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Term(
            @JsonProperty("korean") String korean,
            @JsonProperty("english") String english,
            @JsonProperty("explanation") String explanation) {
    }

    /** GET /results/{id} 응답. 아직 만들어지지 않은 값은 null. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalysisResult(
            @JsonProperty("result_id") int resultId,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("analyzed_at") String analyzedAt,
            @JsonProperty("items") List<Item> items,
            @JsonProperty("translated_text") String translatedText,
            @JsonProperty("explanation") String explanation,
            @JsonProperty("summary") String summary,
            @JsonProperty("exam_questions") String examQuestions,
            @JsonProperty("terms") List<Term> terms) {
    }

    /** GET /results 한 줄 (이력 화면). analyzedAt 은 DB 시각 그대로 (Neon 은 UTC). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultSummary(
            @JsonProperty("result_id") int resultId,
            @JsonProperty("document_id") int documentId,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("analyzed_at") String analyzedAt,
            @JsonProperty("item_count") int itemCount,
            @JsonProperty("translated") boolean translated,
            @JsonProperty("term_count") int termCount) {
    }

    /**
     * 용어 사전 한 줄 (GET/POST/PUT /terms). english·explanation 은 없으면 null.
     * verified: 사람이 넣거나 확인했으면 true, 분석 중 자동으로 들어가 확인 전이면 false
     * (예전 서버라 값이 없으면 null → 확인됨으로 본다).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TermEntry(
            @JsonProperty("term_id") int termId,
            @JsonProperty("korean") String korean,
            @JsonProperty("english") String english,
            @JsonProperty("explanation") String explanation,
            @JsonProperty("verified") Boolean verified) {

        public TermEntry(int termId, String korean, String english, String explanation) {
            this(termId, korean, english, explanation, true);
        }

        /** 자동으로 들어와 아직 사람이 확인하지 않은 용어인가 */
        public boolean needsReview() {
            return Boolean.FALSE.equals(verified);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** -Dserver.url 이 있으면 그 주소, 없으면 http://localhost:8080 */
    public static ApiClient fromSystem() {
        return new ApiClient(System.getProperty("server.url", "http://localhost:8080"));
    }

    /** 서버가 켜져 있는지 확인한다. */
    public boolean isServerUp() {
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 파일을 올리고 분석이 끝날 때까지 기다린다. */
    public UploadResult upload(Path file) throws ApiException {
        String name = URLEncoder.encode(file.getFileName().toString(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(baseUrl + "/documents?filename=" + name))
                    // 스캔 PDF 의 글자 인식 + AI 번역·요약(묶음마다 LLM 호출)까지 기다린다
                    .timeout(Duration.ofMinutes(30))
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofFile(file))
                    .build();
        } catch (IOException e) {
            throw new ApiException(0, "FILE_READ_ERROR", "파일을 읽을 수 없습니다: " + file.getFileName());
        }
        return send(req, UploadResult.class);
    }

    /** 분석 결과를 가져온다. */
    public AnalysisResult getResult(int resultId) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/results/" + resultId))
                .timeout(Duration.ofSeconds(30)).GET().build();
        return send(req, AnalysisResult.class);
    }

    // ---------------- 이력 ----------------

    /** 분석 이력 (최근 것부터). fileQuery: 파일 이름 검색어, 비우면 전체. */
    public List<ResultSummary> listResults(String fileQuery) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/results" + query(fileQuery)))
                .timeout(Duration.ofSeconds(30)).GET().build();
        return send(req, new TypeReference<List<ResultSummary>>() { });
    }

    /** 분석 결과와 그 문서를 지운다. 없는 결과면 ApiException(404). */
    public void deleteResult(int resultId) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/results/" + resultId))
                .timeout(Duration.ofSeconds(30)).DELETE().build();
        send(req, JsonNode.class);
    }

    // ---------------- 용어 사전 ----------------

    /** 한국어·영어에 query 가 들어간 용어. 비우면 전체. */
    public List<TermEntry> searchTerms(String query) throws ApiException {
        return searchTerms(query, false);
    }

    /** unverifiedOnly 면 자동으로 들어와 확인 전인 용어만. */
    public List<TermEntry> searchTerms(String query, boolean unverifiedOnly) throws ApiException {
        String q = query(query);
        if (unverifiedOnly) {
            q += (q.isEmpty() ? "?" : "&") + "unverified=true";
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/terms" + q))
                .timeout(Duration.ofSeconds(30)).GET().build();
        return send(req, new TypeReference<List<TermEntry>>() { });
    }

    /** 자동으로 들어간 용어를 그대로 승인한다. 없는 용어면 ApiException(404). */
    public TermEntry approveTerm(int termId) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/terms/" + termId + "/approve"))
                .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.noBody()).build();
        return send(req, TermEntry.class);
    }

    /** 용어를 추가한다. 같은 한국어 용어가 있으면 ApiException(409, DUPLICATE_TERM). */
    public TermEntry addTerm(String korean, String english, String explanation) throws ApiException {
        return send(jsonRequest("POST", "/terms", korean, english, explanation), TermEntry.class);
    }

    /** 용어를 고친다. */
    public TermEntry updateTerm(int termId, String korean, String english, String explanation) throws ApiException {
        return send(jsonRequest("PUT", "/terms/" + termId, korean, english, explanation), TermEntry.class);
    }

    /** 용어를 지운다. 분석 결과와의 연결도 함께 지워진다. */
    public void deleteTerm(int termId) throws ApiException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/terms/" + termId))
                .timeout(Duration.ofSeconds(30)).DELETE().build();
        send(req, JsonNode.class);
    }

    private HttpRequest jsonRequest(String method, String path, String korean, String english, String explanation)
            throws ApiException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("korean", korean);
        body.put("english", english);
        body.put("explanation", explanation);
        try {
            return HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new ApiException(0, "BAD_REQUEST", "요청을 만들 수 없습니다: " + e.getMessage());
        }
    }

    /** "?q=..." (비어 있으면 ""). 한글·공백도 안전하게. */
    private static String query(String q) {
        if (q == null || q.isBlank()) {
            return "";
        }
        return "?q=" + URLEncoder.encode(q.strip(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private <T> T send(HttpRequest req, TypeReference<T> type) throws ApiException {
        return send(req, JSON.getTypeFactory().constructType(type));
    }

    private <T> T send(HttpRequest req, Class<T> type) throws ApiException {
        return send(req, JSON.getTypeFactory().constructType(type));
    }

    private <T> T send(HttpRequest req, JavaType type) throws ApiException {
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException e) {
            throw serverUnavailable();
        } catch (IOException e) {
            if (e.getCause() instanceof ConnectException) {
                throw serverUnavailable();
            }
            throw new ApiException(0, "NETWORK_ERROR", "서버와 통신 중 오류가 발생했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "CANCELLED", "요청이 취소되었습니다.");
        }

        try {
            if (res.statusCode() / 100 == 2) {
                return JSON.readValue(res.body(), type);
            }
            JsonNode err = JSON.readTree(res.body());
            throw new ApiException(res.statusCode(),
                    err.path("error").asText("HTTP_" + res.statusCode()),
                    err.path("message").asText("서버 오류 (" + res.statusCode() + ")"));
        } catch (IOException e) {
            throw new ApiException(res.statusCode(), "BAD_RESPONSE",
                    "서버 응답을 읽을 수 없습니다 (" + res.statusCode() + ")");
        }
    }

    private ApiException serverUnavailable() {
        return new ApiException(0, "SERVER_UNAVAILABLE",
                "서버에 연결할 수 없습니다 (" + baseUrl + "). FermServer 의 ApiServer 를 먼저 실행하세요.");
    }
}
