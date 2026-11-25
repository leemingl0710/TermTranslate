package ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 이력·용어 사전 요청을 가짜 서버로 확인한다 (주소, 방식, 본문, 응답 읽기, 오류). */
class ApiClientHistoryTermsTest {

    private HttpServer server;
    private ApiClient client;
    /** 받은 요청: "방식 경로?쿼리 본문" */
    private final List<String> requests = new ArrayList<>();

    private static void reply(HttpExchange ex, int status, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    private void record(HttpExchange ex) throws IOException {
        String q = ex.getRequestURI().getQuery();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath()
                + (q == null ? "" : "?" + q) + (body.isEmpty() ? "" : " " + body));
    }

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/results", ex -> {
            record(ex);
            reply(ex, 200, """
                    [{"result_id":17,"document_id":17,"file_name":"1_자바 실습.pdf","analyzed_at":"2026-11-25T02:57:53",
                      "item_count":803,"translated":true,"term_count":2,"extra":1},
                     {"result_id":14,"document_id":14,"file_name":"2_파일입출력.pdf","analyzed_at":null,
                      "item_count":444,"translated":false,"term_count":0}]
                    """);
        });
        server.createContext("/results/", ex -> {
            record(ex);
            if (ex.getRequestURI().getPath().endsWith("/999")) {
                reply(ex, 404, "{\"error\":\"NOT_FOUND\",\"message\":\"결과가 없습니다: 999\"}");
            } else {
                reply(ex, 200, "{\"deleted\":true}");
            }
        });
        server.createContext("/terms", ex -> {
            record(ex);
            if ("POST".equals(ex.getRequestMethod())) {
                if (requests.get(requests.size() - 1).contains("\"응력\"")) {
                    reply(ex, 409, "{\"error\":\"DUPLICATE_TERM\",\"message\":\"이미 있는 용어입니다: 응력\"}");
                } else {
                    reply(ex, 201, "{\"term_id\":5,\"korean\":\"변형률\",\"english\":\"strain\",\"explanation\":null}");
                }
            } else {
                reply(ex, 200, "[{\"term_id\":1,\"korean\":\"응력\",\"english\":\"stress\",\"explanation\":\"단위 면적당 힘\"}]");
            }
        });
        server.createContext("/terms/", ex -> {
            record(ex);
            if ("PUT".equals(ex.getRequestMethod())) {
                reply(ex, 200, "{\"term_id\":1,\"korean\":\"응력\",\"english\":\"stress!\",\"explanation\":null}");
            } else {
                reply(ex, 200, "{\"deleted\":true}");
            }
        });
        server.start();
        client = new ApiClient("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void listResults() throws Exception {
        List<ApiClient.ResultSummary> rows = client.listResults(" 자바 실습 ");
        assertEquals("GET /results?q=자바 실습", requests.get(0), "앞뒤 공백 없이, 한글·공백은 인코딩해서 보냄");
        assertEquals(2, rows.size());
        ApiClient.ResultSummary first = rows.get(0);
        assertEquals(17, first.resultId());
        assertEquals("1_자바 실습.pdf", first.fileName());
        assertEquals(803, first.itemCount());
        assertTrue(first.translated());
        assertEquals(2, first.termCount());
        assertNull(rows.get(1).analyzedAt());
        assertFalse(rows.get(1).translated());

        client.listResults("");
        assertEquals("GET /results", requests.get(1), "빈 검색어면 q 없이");
    }

    @Test
    void deleteResult() throws Exception {
        client.deleteResult(17);
        assertEquals("DELETE /results/17", requests.get(0));
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class, () -> client.deleteResult(999));
        assertEquals(404, e.getStatus());
        assertEquals("결과가 없습니다: 999", e.getMessage());
    }

    @Test
    void termRequests() throws Exception {
        List<ApiClient.TermEntry> found = client.searchTerms("응력");
        assertEquals("GET /terms?q=응력", requests.get(0));
        assertEquals("stress", found.get(0).english());

        ApiClient.TermEntry added = client.addTerm("변형률", "strain", null);
        assertEquals("POST /terms {\"korean\":\"변형률\",\"english\":\"strain\",\"explanation\":null}", requests.get(1));
        assertEquals(5, added.termId());

        ApiClient.TermEntry updated = client.updateTerm(1, "응력", "stress!", "");
        assertTrue(requests.get(2).startsWith("PUT /terms/1 {\"korean\":\"응력\""));
        assertEquals("stress!", updated.english());

        client.deleteTerm(1);
        assertEquals("DELETE /terms/1", requests.get(3));
    }

    @Test
    void duplicateTermError() {
        ApiClient.ApiException e = assertThrows(ApiClient.ApiException.class,
                () -> client.addTerm("응력", "stress", null));
        assertEquals(409, e.getStatus());
        assertEquals("DUPLICATE_TERM", e.getError());
        assertEquals("이미 있는 용어입니다: 응력", e.getMessage());
    }
}
