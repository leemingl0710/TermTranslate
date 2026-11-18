package Server.http;

import Server.extract.ExtractionException;
import Server.extract.Item;
import Server.service.AnalysisService;
import Server.service.ResultDetail;
import Server.service.UploadResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTTP 동작 테스트. DB·extractor 대신 가짜 서비스를 쓴다. */
class ApiServerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @TempDir
    Path tmp;
    private HttpServer server;
    private String base;
    private FakeService fake;

    /** 받은 파일 이름·내용을 기록하고 정해진 결과를 돌려주는 가짜 서비스. */
    static class FakeService implements AnalysisService {
        String receivedName;
        byte[] receivedBytes;
        ExtractionException failWith;

        @Override
        public UploadResult analyze(String originalName, Path uploaded) throws ExtractionException {
            receivedName = originalName;
            try {
                receivedBytes = Files.readAllBytes(uploaded);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (failWith != null) {
                throw failWith;
            }
            return new UploadResult(42, 7, originalName, 3);
        }

        @Override
        public Optional<ResultDetail> getResult(int resultId) {
            if (resultId != 42) {
                return Optional.empty();
            }
            Item item = new Item("a.pdf", "pdf", 1, Item.Origin.TEXT, "자료구조 개요",
                    List.of(72.0, 72.0, 270.0, 94.0), null);
            return Optional.of(new ResultDetail(42, 7, "a.pdf", "2026-11-18T16:00",
                    List.of(item), null, null, null, null, List.of()));
        }
    }

    @BeforeEach
    void start() throws Exception {
        fake = new FakeService();
        server = ApiServer.start(0, fake, 1024, tmp);   // 테스트용 최대 크기 1KB
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> upload(String fileName, byte[] body) throws Exception {
        String q = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        return http.send(HttpRequest.newBuilder(URI.create(base + "/documents?filename=" + q))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void health() throws Exception {
        HttpResponse<String> res = get("/health");
        assertEquals(200, res.statusCode());
        assertEquals("ok", JSON.readTree(res.body()).get("status").asText());
    }

    @Test
    void uploadSuccess() throws Exception {
        HttpResponse<String> res = upload("강의 자료.pdf", "%PDF-test".getBytes());
        assertEquals(201, res.statusCode());
        JsonNode body = JSON.readTree(res.body());
        assertEquals(42, body.get("result_id").asInt());
        assertEquals(7, body.get("document_id").asInt());
        assertEquals(3, body.get("item_count").asInt());
        assertEquals("강의 자료.pdf", fake.receivedName);          // 한글·공백 이름이 그대로 전달
        assertEquals("%PDF-test", new String(fake.receivedBytes));
        try (var left = Files.list(tmp)) {
            assertFalse(left.findAny().isPresent(), "임시 파일이 남으면 안 됨");
        }
    }

    @Test
    void uploadRejectsNonPdf() throws Exception {
        HttpResponse<String> res = upload("notes.txt", "hi".getBytes());
        assertEquals(400, res.statusCode());
        assertEquals("UnsupportedFormatError", JSON.readTree(res.body()).get("error").asText());
    }

    @Test
    void uploadRequiresFilename() throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(base + "/documents"))
                .POST(HttpRequest.BodyPublishers.ofByteArray("x".getBytes())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
    }

    @Test
    void uploadRejectsEmptyBody() throws Exception {
        assertEquals(400, upload("a.pdf", new byte[0]).statusCode());
    }

    @Test
    void uploadTooLarge() throws Exception {
        HttpResponse<String> res = upload("big.pdf", new byte[2048]);
        assertEquals(413, res.statusCode());
        assertEquals("FILE_TOO_LARGE", JSON.readTree(res.body()).get("error").asText());
    }

    @Test
    void extractionUserErrorIs400() throws Exception {
        fake.failWith = new ExtractionException("EncryptedFileError", "암호가 걸린 PDF입니다: a.pdf");
        HttpResponse<String> res = upload("a.pdf", "%PDF".getBytes());
        assertEquals(400, res.statusCode());
        JsonNode body = JSON.readTree(res.body());
        assertEquals("EncryptedFileError", body.get("error").asText());
        assertEquals("암호가 걸린 PDF입니다: a.pdf", body.get("message").asText());
    }

    @Test
    void extractionServerErrorIs500() throws Exception {
        fake.failWith = new ExtractionException("UV_NOT_FOUND", "uv 를 실행할 수 없습니다");
        assertEquals(500, upload("a.pdf", "%PDF".getBytes()).statusCode());
    }

    @Test
    void resultFound() throws Exception {
        HttpResponse<String> res = get("/results/42");
        assertEquals(200, res.statusCode());
        JsonNode body = JSON.readTree(res.body());
        assertEquals("a.pdf", body.get("file_name").asText());
        JsonNode item = body.get("items").get(0);
        assertEquals("자료구조 개요", item.get("content").asText());
        assertEquals("text", item.get("origin").asText());
        assertEquals(1, item.get("page").asInt());
        assertTrue(body.get("summary").isNull());
    }

    @Test
    void resultNotFound() throws Exception {
        assertEquals(404, get("/results/999").statusCode());
        assertEquals(400, get("/results/abc").statusCode());
    }

    @Test
    void wrongMethod() throws Exception {
        assertEquals(405, get("/documents").statusCode());
    }
}
