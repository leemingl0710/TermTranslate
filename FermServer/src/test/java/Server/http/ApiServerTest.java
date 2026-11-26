package Server.http;

import Server.extract.ExtractionException;
import Server.extract.Item;
import Server.service.AnalysisService;
import Server.service.ResultDetail;
import Server.service.ResultSummary;
import Server.service.TermEntry;
import Server.service.TermException;
import Server.service.TermService;
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
import java.util.ArrayList;
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
    private FakeTerms terms;

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

        String listedQuery;
        Integer deletedId;

        @Override
        public List<ResultSummary> listResults(String fileQuery) {
            listedQuery = fileQuery;
            return List.of(
                    new ResultSummary(43, 8, "강의 2장.pdf", "2026-11-25T10:00", 120, true, 3),
                    new ResultSummary(42, 7, "a.pdf", "2026-11-18T16:00", 1, false, 0));
        }

        @Override
        public boolean deleteResult(int resultId) {
            if (resultId != 42) {
                return false;
            }
            deletedId = resultId;
            return true;
        }
    }

    /** 메모리에 용어를 들고 있는 가짜 용어 사전. 규칙은 진짜와 같게: 한국어 필수, 중복 금지. */
    static class FakeTerms implements TermService {
        final List<TermEntry> rows = new ArrayList<>(List.of(
                new TermEntry(1, "응력", "stress", "단위 면적당 힘", true),
                new TermEntry(2, "직렬화", "serialization", null, false)));
        String searched;
        Boolean searchedUnverified;

        @Override
        public List<TermEntry> search(String query, boolean unverifiedOnly) {
            searched = query;
            searchedUnverified = unverifiedOnly;
            return rows.stream().filter(t -> !unverifiedOnly || !t.verified())
                    .filter(t -> t.korean().contains(query) || (t.english() != null
                            && t.english().contains(query))).toList();
        }

        @Override
        public Optional<TermEntry> approve(int termId) {
            for (int i = 0; i < rows.size(); i++) {
                TermEntry t = rows.get(i);
                if (t.termId() == termId) {
                    TermEntry ok = new TermEntry(termId, t.korean(), t.english(), t.explanation(), true);
                    rows.set(i, ok);
                    return Optional.of(ok);
                }
            }
            return Optional.empty();
        }

        @Override
        public TermEntry add(String korean, String english, String explanation) throws TermException {
            check(korean, -1);
            TermEntry t = new TermEntry(rows.size() + 1, korean.strip(), english, explanation, true);
            rows.add(t);
            return t;
        }

        @Override
        public Optional<TermEntry> update(int termId, String korean, String english, String explanation)
                throws TermException {
            check(korean, termId);
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).termId() == termId) {
                    TermEntry t = new TermEntry(termId, korean.strip(), english, explanation, true);
                    rows.set(i, t);
                    return Optional.of(t);
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean delete(int termId) {
            return rows.removeIf(t -> t.termId() == termId);
        }

        private void check(String korean, int selfId) throws TermException {
            if (korean == null || korean.isBlank()) {
                throw new TermException(TermException.BAD_REQUEST, "한국어 용어를 입력하세요.");
            }
            if (rows.stream().anyMatch(t -> t.korean().equals(korean.strip()) && t.termId() != selfId)) {
                throw new TermException(TermException.DUPLICATE_TERM, "이미 있는 용어입니다: " + korean);
            }
        }
    }

    @BeforeEach
    void start() throws Exception {
        fake = new FakeService();
        terms = new FakeTerms();
        server = ApiServer.start(0, fake, terms, 1024, tmp);   // 테스트용 최대 크기 1KB
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
    void acceptsPdfAndPptExtensions() {
        assertTrue(ApiServer.isSupported("강의.pdf"));
        assertTrue(ApiServer.isSupported("강의.PPTX"));
        assertTrue(ApiServer.isSupported("old.ppt"));
        assertTrue(ApiServer.isSupported("칠판.JPG"));
        assertTrue(ApiServer.isSupported("phone.jpeg"));
        assertTrue(ApiServer.isSupported("capture.png"));
        assertFalse(ApiServer.isSupported("scan.gif"));
        assertFalse(ApiServer.isSupported("notes.txt"));
        assertFalse(ApiServer.isSupported("report.docx"));
        assertFalse(ApiServer.isSupported("pdf"));
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

    // ---------------- 이력 ----------------

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json").method(method, body).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void listResultsWithQuery() throws Exception {
        HttpResponse<String> res = get("/results?q=" + URLEncoder.encode("강의 2", StandardCharsets.UTF_8));
        assertEquals(200, res.statusCode());
        assertEquals("강의 2", fake.listedQuery);
        JsonNode list = JSON.readTree(res.body());
        assertEquals(2, list.size());
        JsonNode first = list.get(0);
        assertEquals(43, first.get("result_id").asInt());
        assertEquals("강의 2장.pdf", first.get("file_name").asText());
        assertEquals(120, first.get("item_count").asInt());
        assertTrue(first.get("translated").asBoolean());
        assertEquals(3, first.get("term_count").asInt());
    }

    @Test
    void listResultsWithoutQuery() throws Exception {
        assertEquals(200, get("/results").statusCode());
        assertEquals("", fake.listedQuery);
        assertEquals(404, get("/resultsX").statusCode());
    }

    @Test
    void deleteResult() throws Exception {
        HttpResponse<String> res = send("DELETE", "/results/42", null);
        assertEquals(200, res.statusCode());
        assertTrue(JSON.readTree(res.body()).get("deleted").asBoolean());
        assertEquals(42, fake.deletedId);
        assertEquals(404, send("DELETE", "/results/999", null).statusCode());
        assertEquals(400, send("DELETE", "/results/abc", null).statusCode());
        assertEquals(405, send("PUT", "/results/42", "{}").statusCode());
    }

    // ---------------- 용어 사전 ----------------

    @Test
    void searchTerms() throws Exception {
        HttpResponse<String> res = get("/terms?q=" + URLEncoder.encode("응력", StandardCharsets.UTF_8));
        assertEquals(200, res.statusCode());
        JsonNode list = JSON.readTree(res.body());
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).get("term_id").asInt());
        assertEquals("stress", list.get(0).get("english").asText());
        assertEquals("단위 면적당 힘", list.get(0).get("explanation").asText());
        assertTrue(list.get(0).get("verified").asBoolean());
        assertEquals(false, terms.searchedUnverified, "unverified 가 없으면 전체");
        assertEquals(200, get("/terms").statusCode());
        assertEquals("", terms.searched);
    }

    @Test
    void searchUnverifiedOnly() throws Exception {
        JsonNode list = JSON.readTree(get("/terms?unverified=true").body());
        assertEquals(true, terms.searchedUnverified);
        assertEquals(1, list.size());
        assertEquals("직렬화", list.get(0).get("korean").asText());
        assertFalse(list.get(0).get("verified").asBoolean());
    }

    @Test
    void approveTerm() throws Exception {
        HttpResponse<String> res = send("POST", "/terms/2/approve", null);
        assertEquals(200, res.statusCode());
        JsonNode body = JSON.readTree(res.body());
        assertEquals("직렬화", body.get("korean").asText());
        assertTrue(body.get("verified").asBoolean());
        assertTrue(terms.rows.get(1).verified());

        assertEquals(404, send("POST", "/terms/99/approve", null).statusCode());
        assertEquals(400, send("POST", "/terms/abc/approve", null).statusCode());
        assertEquals(404, send("POST", "/terms/2/other", null).statusCode());
    }

    @Test
    void addTerm() throws Exception {
        HttpResponse<String> res = send("POST", "/terms",
                "{\"korean\":\"변형률\",\"english\":\"strain\",\"explanation\":\"늘어난 비율\"}");
        assertEquals(201, res.statusCode());
        JsonNode body = JSON.readTree(res.body());
        assertEquals("변형률", body.get("korean").asText());
        assertEquals(3, body.get("term_id").asInt());
        assertTrue(body.get("verified").asBoolean(), "사람이 넣은 용어는 확인됨");
        assertEquals(3, terms.rows.size());
    }

    @Test
    void addTermErrors() throws Exception {
        HttpResponse<String> blank = send("POST", "/terms", "{\"korean\":\" \",\"english\":\"x\"}");
        assertEquals(400, blank.statusCode());
        assertEquals("BAD_REQUEST", JSON.readTree(blank.body()).get("error").asText());

        HttpResponse<String> dup = send("POST", "/terms", "{\"korean\":\"응력\"}");
        assertEquals(409, dup.statusCode());
        assertEquals("DUPLICATE_TERM", JSON.readTree(dup.body()).get("error").asText());

        assertEquals(400, send("POST", "/terms", "not json").statusCode());
        assertEquals(400, send("POST", "/terms", "").statusCode());
    }

    @Test
    void updateAndDeleteTerm() throws Exception {
        HttpResponse<String> res = send("PUT", "/terms/1",
                "{\"korean\":\"응력\",\"english\":\"stress (force per area)\"}");
        assertEquals(200, res.statusCode());
        assertEquals("stress (force per area)", JSON.readTree(res.body()).get("english").asText());
        assertEquals(404, send("PUT", "/terms/99", "{\"korean\":\"없음\"}").statusCode());

        assertEquals(200, send("DELETE", "/terms/1", null).statusCode());
        assertEquals(404, send("DELETE", "/terms/1", null).statusCode());
        assertEquals(404, send("POST", "/terms/1", "{}").statusCode(), "POST 는 /terms/{id}/approve 만");
        assertEquals(405, send("PATCH", "/terms/1", "{}").statusCode());
    }

    @Test
    void termsOffWithoutService() throws Exception {
        HttpServer plain = ApiServer.start(0, fake, 1024, tmp.resolve("plain"));
        try {
            String url = "http://localhost:" + plain.getAddress().getPort() + "/terms";
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, res.statusCode());
        } finally {
            plain.stop(0);
        }
    }
}
