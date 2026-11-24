package Server.http;

import Server.DBConnection;
import Server.db.TermDao;
import Server.extract.ExtractionException;
import Server.extract.ExtractorClient;
import Server.extract.JavaExtractor;
import Server.extract.TextExtractor;
import Server.service.AnalysisService;
import Server.service.DefaultAnalysisService;
import Server.service.ResultDetail;
import Server.service.UploadResult;
import Server.translate.LlmTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP 서버 (자바 기본 HttpServer). 같은 PC 의 Swing 앱만 접속하도록 localhost 에서만 연다.
 *
 * <pre>
 * GET  /health                       서버 확인          → 200 {"status":"ok"}
 * POST /documents?filename=a.pdf     업로드·분석 (pdf, pptx, ppt) (본문=파일) → 201 {"result_id":..,"document_id":..,"file_name":..,"item_count":..}
 * GET  /results/{id}                 결과 조회          → 200 {...}
 * 실패: {"error": "종류", "message": "사용자에게 보여 줄 문장"}
 * </pre>
 *
 * 실행: 이클립스에서 이 클래스를 Java Application 으로 실행, 또는 터미널에서 ./mvnw -q compile exec:java
 *
 * <p>설정 (.env, 모두 생략 가능): SERVER_PORT(8080), UPLOAD_DIR(uploads), MAX_UPLOAD_MB(50),
 * UPLOAD_USERNAME(admin), EXTRACTOR_ENGINE(python | java, JavaExtractor 참고)
 * + extractor 설정 (ExtractorClient 참고) + 번역 설정 (LlmTranslator 참고,
 * LLM_BASE_URL 이 비어 있으면 번역하지 않는다)
 */
public final class ApiServer {

    static final ObjectMapper JSON = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /** 사용자가 올린 파일이 문제인 경우 (400). 나머지 추출 오류는 서버 문제 (500). */
    private static final Set<String> CLIENT_ERRORS = Set.of(
            "InputNotFoundError", "UnsupportedFormatError", "CorruptedFileError",
            "EncryptedFileError", "ConversionError");

    private final AnalysisService service;
    private final long maxUploadBytes;
    private final Path tempDir;

    private ApiServer(AnalysisService service, long maxUploadBytes, Path tempDir) {
        this.service = service;
        this.maxUploadBytes = maxUploadBytes;
        this.tempDir = tempDir;
    }

    public static void main(String[] args) throws IOException {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        int port = Integer.parseInt(env.get("SERVER_PORT", "8080"));
        Path uploadDir = Path.of(env.get("UPLOAD_DIR", "uploads"));
        long maxMb = Long.parseLong(env.get("MAX_UPLOAD_MB", "50"));
        String uploader = env.get("UPLOAD_USERNAME", "admin");

        // 글자 추출: 기본은 파이썬 extractor. EXTRACTOR_ENGINE=java 면 자바 추출기 (pdftotext + Windows OCR)
        boolean javaEngine = "java".equalsIgnoreCase(env.get("EXTRACTOR_ENGINE", "python").strip());
        TextExtractor extractor = javaEngine ? new JavaExtractor() : ExtractorClient.fromEnv();
        String extractorInfo = javaEngine
                ? "자바 추출기 (pdftotext + Windows OCR, EXTRACTOR_ENGINE=java)"
                : "파이썬 extractor (uv: " + ((ExtractorClient) extractor).uvPath() + ")";
        // 번역·요약 (Server.translate.LlmTranslator). LLM_BASE_URL 이 없으면 null → 번역 없이 추출 결과만 저장.
        // 용어 사전은 DB 의 terms 테이블을 분석할 때마다 새로 읽는다.
        LlmTranslator translator = LlmTranslator.fromEnv(() -> {
            try (Connection conn = DBConnection.getConnection()) {
                return TermDao.loadAll(conn);
            }
        });
        AnalysisService service = new DefaultAnalysisService(
                extractor, DBConnection::getConnection, uploadDir, uploader, translator);
        HttpServer server = start(port, service, maxMb * 1024 * 1024, uploadDir.resolve("tmp"));

        System.out.println("서버 시작: http://localhost:" + server.getAddress().getPort());
        System.out.println("  업로드 폴더: " + uploadDir.toAbsolutePath().normalize());
        System.out.println("  업로드 사용자: " + uploader + ", 최대 " + maxMb + "MB");
        System.out.println("  글자 추출: " + extractorInfo);
        System.out.println("  번역: " + (translator == null
                ? "꺼짐 (.env 의 LLM_BASE_URL 이 비어 있음 → 추출 결과만 저장)" : translator.describe()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
    }

    /** 서버를 시작한다. port 가 0 이면 빈 포트를 고른다 (테스트용). */
    public static HttpServer start(int port, AnalysisService service, long maxUploadBytes, Path tempDir)
            throws IOException {
        Files.createDirectories(tempDir);
        ApiServer api = new ApiServer(service, maxUploadBytes, tempDir);
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/health", ex -> api.handle(ex, "GET", api::health));
        server.createContext("/documents", ex -> api.handle(ex, "POST", api::upload));
        server.createContext("/results/", ex -> api.handle(ex, "GET", api::result));
        // 추출이 오래 걸릴 수 있어 요청을 여러 스레드에서 나눠 처리한다.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        server.setExecutor(pool);
        server.start();
        return server;
    }

    // ---------------- 요청 처리 ----------------

    @FunctionalInterface
    private interface Handler {
        Response handle(HttpExchange ex) throws Exception;
    }

    private record Response(int status, Object body) {
    }

    /** HTTP 오류 응답으로 바로 끝낼 때 던진다. */
    private static final class HttpError extends Exception {
        final int status;
        final String error;

        HttpError(int status, String error, String message) {
            super(message);
            this.status = status;
            this.error = error;
        }
    }

    private void handle(HttpExchange ex, String method, Handler handler) throws IOException {
        long started = System.currentTimeMillis();
        Response res;
        try {
            if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
                throw new HttpError(405, "METHOD_NOT_ALLOWED", method + " 요청만 받습니다.");
            }
            res = handler.handle(ex);
        } catch (HttpError e) {
            res = error(e.status, e.error, e.getMessage());
        } catch (ExtractionException e) {
            int status = CLIENT_ERRORS.contains(e.getErrorType()) ? 400 : 500;
            res = error(status, e.getErrorType(), e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            res = error(500, "SERVER_ERROR", "서버 오류가 발생했습니다: " + e.getMessage());
        }
        send(ex, res);
        System.out.printf("%s %s → %d (%dms)%n", ex.getRequestMethod(), ex.getRequestURI(),
                res.status(), System.currentTimeMillis() - started);
    }

    private Response health(HttpExchange ex) {
        return new Response(200, Map.of("status", "ok"));
    }

    private Response upload(HttpExchange ex) throws Exception {
        String fileName = queryParam(ex, "filename")
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .orElseThrow(() -> new HttpError(400, "BAD_REQUEST", "filename 값이 필요합니다."));
        if (!isSupported(fileName)) {
            throw new HttpError(400, "UnsupportedFormatError", "PDF·PPT 파일만 지원합니다: " + fileName);
        }
        String declared = ex.getRequestHeaders().getFirst("Content-Length");
        if (declared != null && Long.parseLong(declared) > maxUploadBytes) {
            throw tooLarge();
        }

        Path temp = Files.createTempFile(tempDir, "upload-", ".part");
        try {
            long size = copyLimited(ex.getRequestBody(), temp);
            if (size == 0) {
                throw new HttpError(400, "BAD_REQUEST", "파일 내용이 비어 있습니다.");
            }
            UploadResult result = service.analyze(fileName, temp);
            return new Response(201, result);
        } finally {
            Files.deleteIfExists(temp);   // 서비스가 옮겼으면 이미 없음
        }
    }

    private Response result(HttpExchange ex) throws Exception {
        String idText = ex.getRequestURI().getPath().substring("/results/".length());
        int id;
        try {
            id = Integer.parseInt(idText);
        } catch (NumberFormatException e) {
            throw new HttpError(400, "BAD_REQUEST", "결과 번호가 올바르지 않습니다: " + idText);
        }
        Optional<ResultDetail> detail = service.getResult(id);
        if (detail.isEmpty()) {
            throw new HttpError(404, "NOT_FOUND", "결과가 없습니다: " + id);
        }
        return new Response(200, detail.get());
    }

    // ---------------- 도우미 ----------------

    /** 받을 수 있는 확장자. 진짜 형식(파일 내용)은 extractor 가 다시 확인한다. */
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".pdf", ".pptx", ".ppt");

    static boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private HttpError tooLarge() {
        return new HttpError(413, "FILE_TOO_LARGE",
                "파일이 너무 큽니다. 최대 " + (maxUploadBytes / 1024 / 1024) + "MB 까지 올릴 수 있습니다.");
    }

    /** 요청 본문을 파일로 저장한다. 최대 크기를 넘으면 중단. */
    private long copyLimited(InputStream in, Path target) throws IOException, HttpError {
        long total = 0;
        byte[] buf = new byte[64 * 1024];
        try (InputStream body = in; OutputStream out = Files.newOutputStream(target)) {
            int n;
            while ((n = body.read(buf)) != -1) {
                total += n;
                if (total > maxUploadBytes) {
                    throw tooLarge();
                }
                out.write(buf, 0, n);
            }
        }
        return total;
    }

    private static Optional<String> queryParam(HttpExchange ex, String name) {
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) {
            return Optional.empty();
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return Optional.of(eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return Optional.empty();
    }

    private static Response error(int status, String error, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        return new Response(status, body);
    }

    private static void send(HttpExchange ex, Response res) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(res.body());
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(res.status(), bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
