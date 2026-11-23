package Server.extract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 파이썬 extractor 를 실행해 PDF 의 글자를 {@code List<Item>} 으로 받아 온다.
 *
 * <pre>
 * ExtractorClient client = ExtractorClient.fromEnv();
 * try {
 *     List&lt;Item&gt; items = client.extract(Path.of("lecture.pdf"));
 * } catch (ExtractionException e) {
 *     // e.getErrorType() 으로 원인 구분, e.getMessage() 는 사용자에게 보여 줄 문장
 * }
 * </pre>
 *
 * 내부적으로 extractor 폴더에서 다음 명령을 실행한다.
 * <pre>uv run --frozen python -m extractor &lt;파일&gt; -o &lt;임시 JSON&gt;</pre>
 * 종료 코드 0 이면 임시 JSON 을, 2 이면 표준 오류의 오류 JSON 을 읽는다.
 *
 * <p>설정 (.env 또는 환경 변수, 모두 생략 가능)
 * <ul>
 *   <li>EXTRACTOR_DIR — extractor 폴더 (기본 ../extractor, 실행 위치 기준)</li>
 *   <li>UV_PATH — uv 실행 파일 (기본: PATH 와 흔한 설치 위치에서 찾음)</li>
 *   <li>EXTRACTOR_TIMEOUT_SEC — 최대 실행 시간 (기본 600초)</li>
 * </ul>
 */
public final class ExtractorClient implements TextExtractor {

    /** extractor 가 사용자 오류(손상·암호·미지원 파일 등)로 끝났을 때의 종료 코드. */
    private static final int EXIT_USER_ERROR = 2;

    private static final ObjectMapper JSON = new ObjectMapper()
            // extractor 쪽에 필드가 늘어나도 자바 코드가 깨지지 않게 한다.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path extractorDir;
    private final String uvPath;
    private final Duration timeout;

    public ExtractorClient(Path extractorDir, String uvPath, Duration timeout) {
        this.extractorDir = extractorDir.toAbsolutePath().normalize();
        this.uvPath = uvPath;
        this.timeout = timeout;
    }

    /** .env(없으면 환경 변수) 설정으로 만든다. */
    public static ExtractorClient fromEnv() {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String dir = valueOr(env.get("EXTRACTOR_DIR"), "../extractor");
        String uv = valueOr(env.get("UV_PATH"), null);
        long sec = Long.parseLong(valueOr(env.get("EXTRACTOR_TIMEOUT_SEC"), "600"));
        return new ExtractorClient(Path.of(dir), uv != null ? uv : findUv(), Duration.ofSeconds(sec));
    }

    /**
     * 파일에서 글자를 추출한다. 시간이 걸릴 수 있으므로 Swing 의 화면 스레드(EDT)에서 직접 부르지 말 것.
     */
    @Override
    public List<Item> extract(Path file) throws ExtractionException {
        if (!Files.isRegularFile(extractorDir.resolve("pyproject.toml"))) {
            throw new ExtractionException("EXTRACTOR_NOT_FOUND",
                    "extractor 폴더를 찾을 수 없습니다: " + extractorDir + " (.env 의 EXTRACTOR_DIR 확인)");
        }
        Path out = null;
        Path err = null;
        try {
            out = Files.createTempFile("extract-", ".json");
            err = Files.createTempFile("extract-", ".err");
            int exit = run(file.toAbsolutePath(), out, err);
            if (exit == 0) {
                return parseItems(Files.readString(out, StandardCharsets.UTF_8));
            }
            String stderr = Files.readString(err, StandardCharsets.UTF_8);
            if (exit == EXIT_USER_ERROR) {
                throw parseError(stderr);
            }
            throw new ExtractionException("EXTRACTOR_FAILED",
                    "텍스트 추출 중 알 수 없는 오류가 발생했습니다 (종료 코드 " + exit + ").\n" + tail(stderr, 20));
        } catch (IOException e) {
            throw new ExtractionException("EXTRACTOR_FAILED", "텍스트 추출 중 입출력 오류: " + e.getMessage(), e);
        } finally {
            deleteQuietly(out);
            deleteQuietly(err);
        }
    }

    private int run(Path file, Path out, Path err) throws ExtractionException, IOException {
        ProcessBuilder pb = new ProcessBuilder(
                uvPath, "run", "--frozen", "python", "-m", "extractor",
                file.toString(), "-o", out.toString())
                .directory(extractorDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                // 표준 오류는 파일로 받는다 (파이프로 받으면 버퍼가 차서 멈출 수 있음).
                .redirectError(err.toFile());
        // 윈도우에서도 오류 메시지가 UTF-8 로 나오게 한다.
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ExtractionException("UV_NOT_FOUND",
                    "uv 를 실행할 수 없습니다 (" + uvPath + "). extractor/setup 스크립트로 설치했는지, "
                            + ".env 의 UV_PATH 가 맞는지 확인하세요.", e);
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ExtractionException("TIMEOUT",
                        "텍스트 추출이 " + timeout.toSeconds() + "초 안에 끝나지 않아 중단했습니다.");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ExtractionException("TIMEOUT", "텍스트 추출이 취소되었습니다.", e);
        }
        return process.exitValue();
    }

    /** 실제로 실행할 uv 경로 (설정 확인용). */
    public String uvPath() {
        return uvPath;
    }

    /** extractor 출력 JSON → 항목 목록. */
    static List<Item> parseItems(String json) throws IOException {
        return JSON.readValue(json, new TypeReference<List<Item>>() { });
    }

    /** 표준 오류에서 {"error": ..., "message": ...} 줄을 찾아 예외로 만든다. */
    static ExtractionException parseError(String stderr) {
        String[] lines = stderr.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.startsWith("{")) {
                continue;
            }
            try {
                JsonNode node = JSON.readTree(line);
                if (node.hasNonNull("error")) {
                    return new ExtractionException(node.get("error").asText(), node.path("message").asText());
                }
            } catch (IOException ignored) {
                // JSON 이 아닌 줄은 건너뛴다.
            }
        }
        return new ExtractionException("EXTRACTOR_FAILED", "텍스트 추출에 실패했습니다.\n" + tail(stderr, 20));
    }

    /**
     * uv 위치를 찾는다. 이클립스를 Dock/시작 메뉴에서 켜면 터미널의 PATH 를 못 받는 경우가 있어
     * 흔한 설치 위치도 직접 확인한다. 못 찾으면 "uv" 를 그대로 돌려준다 (실행 시 UV_NOT_FOUND).
     */
    static String findUv() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = windows ? "uv.exe" : "uv";
        List<Path> candidates = new ArrayList<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                if (!dir.isBlank()) {
                    candidates.add(Path.of(dir, exe));
                }
            }
        }
        String home = System.getProperty("user.home");
        candidates.add(Path.of(home, ".local", "bin", exe));   // uv 공식 설치 스크립트 위치
        candidates.add(Path.of(home, ".cargo", "bin", exe));
        if (!windows) {
            candidates.add(Path.of("/opt/homebrew/bin/uv"));
            candidates.add(Path.of("/usr/local/bin/uv"));
        }
        for (Path p : candidates) {
            if (Files.isExecutable(p)) {
                return p.toString();
            }
        }
        return "uv";
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String tail(String text, int maxLines) {
        String[] lines = text.strip().split("\\R");
        int from = Math.max(0, lines.length - maxLines);
        return String.join("\n", java.util.Arrays.copyOfRange(lines, from, lines.length));
    }

    private static void deleteQuietly(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // 임시 파일이라 지우지 못해도 동작에는 영향 없음
            }
        }
    }
}
