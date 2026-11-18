package Server.service;

import Server.db.AnalysisResultDao;
import Server.db.DocumentDao;
import Server.db.UserDao;
import Server.extract.ExtractionException;
import Server.extract.Item;
import Server.extract.TextExtractor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 업로드 처리 순서.
 * <ol>
 *   <li>파일을 업로드 폴더에 저장</li>
 *   <li>extractor 실행 → List&lt;Item&gt;</li>
 *   <li>(번역·요약·개념·용어 — 아직 없음. 만들어지면 이 자리에서 호출)</li>
 *   <li>트랜잭션 한 번에 documents + analysis_results + analysis_terms 저장</li>
 *   <li>result_id 반환</li>
 * </ol>
 * 2~4 중 실패하면 DB 에는 아무것도 남지 않고, 저장한 파일도 지운다.
 */
public final class DefaultAnalysisService implements AnalysisService {

    /** DB 연결을 여는 방법 (보통 DBConnection::getConnection). */
    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final TextExtractor extractor;
    private final ConnectionFactory db;
    private final Path uploadDir;
    private final String uploaderUsername;

    /**
     * @param uploaderUsername 로그인이 생기기 전까지 모든 업로드를 이 사용자로 저장한다 (기본 admin).
     */
    public DefaultAnalysisService(TextExtractor extractor, ConnectionFactory db,
                                  Path uploadDir, String uploaderUsername) {
        this.extractor = extractor;
        this.db = db;
        this.uploadDir = uploadDir.toAbsolutePath().normalize();
        this.uploaderUsername = uploaderUsername;
    }

    @Override
    public UploadResult analyze(String originalName, Path uploaded) throws ExtractionException, SQLException {
        // 1. 저장: uploads/<무작위 폴더>/<원래 이름> (같은 이름끼리 겹치지 않고, 추출 결과에도 원래 이름이 남음)
        //    파일 이름에 경로 문자(/ \ : 등)가 섞여 있어도 업로드 폴더 밖으로 나가지 않게 정리
        String safeName = originalName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (safeName.isBlank() || safeName.startsWith(".")) {
            safeName = "upload" + safeName;   // "..", ".hidden" 같은 이름 방지
        }
        Path folder = uploadDir.resolve(UUID.randomUUID().toString());
        Path saved = folder.resolve(safeName);
        try {
            Files.createDirectories(folder);
            Files.move(uploaded, saved, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 저장하지 못했습니다", e);
        }

        boolean stored = false;
        try {
            // 2. 추출
            List<Item> items = extractor.extract(saved);

            // 3. 번역·요약 등 — TODO: 팀원 모듈이 생기면 여기서 호출하고 아래 null 자리를 채운다.
            String translatedText = null;
            String explanation = null;
            String summary = null;
            String examQuestions = null;
            List<Integer> termIds = List.of();

            // 4. 한 트랜잭션으로 저장
            try (Connection conn = db.open()) {
                conn.setAutoCommit(false);
                try {
                    int userId = UserDao.findIdByUsername(conn, uploaderUsername)
                            .orElseThrow(() -> new SQLException("업로드 사용자 '" + uploaderUsername
                                    + "' 가 DB 에 없습니다. Server.tools.CreateUser 로 만들어 주세요."));
                    int documentId = DocumentDao.insert(conn, userId, originalName, saved.toString());
                    int resultId = AnalysisResultDao.insert(conn, documentId, toJson(items),
                            translatedText, explanation, summary, examQuestions);
                    AnalysisResultDao.linkTerms(conn, resultId, termIds);
                    conn.commit();
                    stored = true;
                    // 5. 결과
                    return new UploadResult(resultId, documentId, originalName, items.size());
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                }
            }
        } finally {
            if (!stored) {
                deleteQuietly(saved);
                deleteQuietly(folder);
            }
        }
    }

    @Override
    public Optional<ResultDetail> getResult(int resultId) throws SQLException {
        try (Connection conn = db.open()) {
            return AnalysisResultDao.findById(conn, resultId).map(row -> new ResultDetail(
                    row.resultId(), row.documentId(), row.fileName(),
                    row.analyzedAt() == null ? null : row.analyzedAt().toString(),
                    fromJson(row.sourceTextJson()),
                    row.translatedText(), row.explanation(), row.summary(), row.examQuestions(),
                    row.terms()));
        }
    }

    private static String toJson(List<Item> items) {
        try {
            return JSON.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Item> fromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Item>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 source_text 를 읽을 수 없습니다", e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // 지우지 못해도 다음 동작에는 영향 없음
        }
    }
}
