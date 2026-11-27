package Server.http;

import Server.service.AnalysisService;
import Server.service.ResultDetail;
import Server.service.ResultSummary;
import Server.service.UploadResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 동시 분석 요청: 여러 업로드를 한꺼번에 보내도 서버가 동시에 나눠 처리하고,
 * 요청끼리 파일·결과가 섞이지 않는지 본다 (DB·extractor 대신 가짜 서비스).
 */
class ConcurrentUploadTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** ApiServer 가 요청을 나눠 받는 스레드 수 */
    private static final int THREADS = 4;

    @TempDir
    Path tmp;
    private HttpServer server;
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * 오래 걸리는 분석 흉내: 요청 N개가 모두 안에 들어올 때까지 기다렸다가 끝낸다.
     * 서버가 한 번에 하나씩만 처리하면 다 모이지 못해 시간 초과로 실패한다.
     */
    static class SlowService implements AnalysisService {
        final CountDownLatch allInside;
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        final AtomicInteger nextId = new AtomicInteger(100);
        /** 결과 번호 → 받은 파일 이름 + 내용 */
        final Map<Integer, String> received = new ConcurrentHashMap<>();
        volatile boolean timedOut;

        SlowService(int expected) {
            allInside = new CountDownLatch(expected);
        }

        @Override
        public UploadResult analyze(String originalName, Path uploaded) {
            int now = running.incrementAndGet();
            maxRunning.accumulateAndGet(now, Math::max);
            try {
                String content = Files.readString(uploaded);
                allInside.countDown();
                if (!allInside.await(10, TimeUnit.SECONDS)) {
                    timedOut = true;
                }
                // 기다리는 동안 다른 요청이 내 임시 파일을 건드리지 않았는지 다시 읽어 본다
                String after = Files.readString(uploaded);
                int id = nextId.getAndIncrement();
                received.put(id, originalName + "|" + content + "|" + content.equals(after));
                return new UploadResult(id, id, originalName, 1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                running.decrementAndGet();
            }
        }

        @Override
        public Optional<ResultDetail> getResult(int resultId) {
            return Optional.empty();
        }

        @Override
        public List<ResultSummary> listResults(String fileQuery) {
            return List.of();
        }

        @Override
        public boolean deleteResult(int resultId) {
            return false;
        }
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private CompletableFuture<HttpResponse<String>> uploadAsync(String fileName, String body) {
        String q = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        return http.sendAsync(HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.getAddress().getPort() + "/documents?filename=" + q))
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    void uploadsAreAnalyzedAtTheSameTimeWithoutMixingUp() throws Exception {
        SlowService service = new SlowService(THREADS);
        server = ApiServer.start(0, service, 1024 * 1024, tmp);

        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 1; i <= THREADS; i++) {
            // 일부러 같은 파일 이름: 이름이 같아도 임시 파일이 겹치지 않아야 함
            calls.add(uploadAsync("강의.pdf", "%PDF-file-" + i));
        }

        Set<Integer> ids = new HashSet<>();
        for (CompletableFuture<HttpResponse<String>> c : calls) {
            HttpResponse<String> res = c.get(30, TimeUnit.SECONDS);
            assertEquals(201, res.statusCode(), res.body());
            JsonNode body = JSON.readTree(res.body());
            ids.add(body.get("result_id").asInt());
        }

        assertEquals(false, service.timedOut, "요청 4개가 동시에 안에 있었어야 함 (하나씩이면 시간 초과)");
        assertEquals(THREADS, service.maxRunning.get(), "동시에 실행된 분석 수");
        assertEquals(THREADS, ids.size(), "결과 번호가 모두 다름");

        Set<String> contents = new HashSet<>();
        for (String r : service.received.values()) {
            String[] parts = r.split("\\|");
            assertEquals("강의.pdf", parts[0]);
            assertEquals("true", parts[2], "처리하는 동안 내 파일이 바뀌지 않음");
            contents.add(parts[1]);
        }
        assertEquals(Set.of("%PDF-file-1", "%PDF-file-2", "%PDF-file-3", "%PDF-file-4"), contents,
                "각 요청이 자기 파일을 받음");
    }

    @Test
    void moreUploadsThanThreadsWaitAndAllFinish() throws Exception {
        // 스레드보다 많은 요청: 넘는 것은 기다렸다가 처리되고, 모두 성공한다
        int uploads = THREADS * 2;
        SlowService service = new SlowService(THREADS);   // 처음 4개가 모이면 나머지는 바로 끝남
        server = ApiServer.start(0, service, 1024 * 1024, tmp);

        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 1; i <= uploads; i++) {
            calls.add(uploadAsync("자료" + i + ".pdf", "%PDF-" + i));
        }
        Set<Integer> ids = new HashSet<>();
        for (CompletableFuture<HttpResponse<String>> c : calls) {
            HttpResponse<String> res = c.get(60, TimeUnit.SECONDS);
            assertEquals(201, res.statusCode(), res.body());
            ids.add(JSON.readTree(res.body()).get("result_id").asInt());
        }
        assertEquals(uploads, ids.size());
        assertTrue(service.maxRunning.get() <= THREADS, "동시에 실행되는 분석은 스레드 수 이하");
        assertEquals(uploads, service.received.size());
    }
}
