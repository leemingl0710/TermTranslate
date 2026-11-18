package ui;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 가짜 서버를 띄워 ApiClient 의 요청·응답 처리를 확인한다. */
class ApiClientTest {

    private HttpServer server;
    private ApiClient client;
    private String receivedName;
    private byte[] receivedBody;

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String json) throws java.io.IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/health", ex -> reply(ex, 200, "{\"status\":\"ok\"}"));
        server.createContext("/documents", ex -> {
            String q = ex.getRequestURI().getRawQuery();
            receivedName = URLDecoder.decode(q.substring(q.indexOf('=') + 1), StandardCharsets.UTF_8);
            receivedBody = ex.getRequestBody().readAllBytes();
            if (receivedName.startsWith("enc")) {
                reply(ex, 400, "{\"error\":\"EncryptedFileError\",\"message\":\"암호가 걸린 PDF입니다: enc.pdf\"}");
            } else {
                reply(ex, 201, "{\"result_id\":5,\"document_id\":3,\"file_name\":\"" + receivedName
                        + "\",\"item_count\":2,\"new_field\":true}");
            }
        });
        server.createContext("/results/", ex -> reply(ex, 200, """
                {"result_id":5,"document_id":3,"file_name":"강의 1장.pdf","analyzed_at":"2026-11-18T07:16",
                 "items":[{"source_file":"강의 1장.pdf","source_format":"pdf","page":1,"origin":"text",
                           "content":"자료구조 개요","bbox":[1,2,3,4],"confidence":null}],
                 "translated_text":null,"explanation":null,"summary":null,"exam_questions":null,"terms":[]}
                """));
        server.start();
        client = new ApiClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void health() {
        assertTrue(client.isServerUp());
    }

    @Test
    void uploadSendsFileAndKoreanName(@TempDir Path tmp) throws Exception {
        Path pdf = Files.writeString(tmp.resolve("강의 1장.pdf"), "%PDF-test");
        ApiClient.UploadResult r = client.upload(pdf);
        assertEquals(5, r.resultId());
        assertEquals(2, r.itemCount());
        assertEquals("강의 1장.pdf", receivedName);   // 공백이 + 로 바뀌지 않아야 함
        assertEquals("%PDF-test", new String(receivedBody));
    }

    @Test
    void uploadErrorMessageFromServer(@TempDir Path tmp) throws Exception {
        Path pdf = Files.writeString(tmp.resolve("enc.pdf"), "%PDF");
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class, () -> client.upload(pdf));
        assertEquals(400, e.getStatus());
        assertEquals("EncryptedFileError", e.getError());
        assertEquals("암호가 걸린 PDF입니다: enc.pdf", e.getMessage());
    }

    @Test
    void getResult() throws Exception {
        ApiClient.AnalysisResult r = client.getResult(5);
        assertEquals("강의 1장.pdf", r.fileName());
        assertEquals(1, r.items().size());
        assertEquals("자료구조 개요", r.items().get(0).content());
        assertNull(r.summary());
    }

    @Test
    void serverDown(@TempDir Path tmp) throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        ApiClient down = new ApiClient("http://localhost:" + closedPort);
        assertFalse(down.isServerUp());
        Path pdf = Files.writeString(tmp.resolve("a.pdf"), "%PDF");
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class, () -> down.upload(pdf));
        assertEquals("SERVER_UNAVAILABLE", e.getError());
    }
}
