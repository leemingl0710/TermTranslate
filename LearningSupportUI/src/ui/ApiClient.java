package ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.List;

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

    /** 추출된 문단 하나. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @JsonProperty("page") int page,
            @JsonProperty("origin") String origin,
            @JsonProperty("content") String content) {
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
                    .timeout(Duration.ofMinutes(10))   // 스캔 PDF 는 글자 인식에 오래 걸릴 수 있음
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

    private <T> T send(HttpRequest req, Class<T> type) throws ApiException {
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
