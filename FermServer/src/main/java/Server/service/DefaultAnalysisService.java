package Server.service;

import Server.db.AnalysisResultDao;
import Server.db.DocumentDao;
import Server.db.TermDao;
import Server.db.UserDao;
import Server.extract.ExtractionException;
import Server.extract.Item;
import Server.extract.TextExtractor;
import Server.translate.Translation;
import Server.translate.Translator;
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
import java.sql.Savepoint;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * 업로드 처리 순서.
 * <ol>
 *   <li>파일을 업로드 폴더에 저장</li>
 *   <li>extractor 실행 → List&lt;Item&gt;</li>
 *   <li>번역·요약·개념·시험 문제·용어 ({@link Translator}, 실제 구현은 {@link Server.translate.LlmTranslator}).
 *       번역기가 없거나(LLM 설정 없음) 실패하면 그 칸들은 null 로 두고 계속한다</li>
 *   <li>트랜잭션 한 번에 documents + analysis_results + terms(새 용어) + analysis_terms 저장</li>
 *   <li>result_id 반환</li>
 * </ol>
 * 2, 4 에서 실패하면 DB 에는 아무것도 남지 않고, 저장한 파일도 지운다.
 * 3(번역)이 실패해도 추출 결과는 저장한다.
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
    private final Translator translator;

    /** 번역 없이 (3단계를 건너뛰고) 추출 결과만 저장한다. */
    public DefaultAnalysisService(TextExtractor extractor, ConnectionFactory db,
                                  Path uploadDir, String uploaderUsername) {
        this(extractor, db, uploadDir, uploaderUsername, null);
    }

    /**
     * @param uploaderUsername 로그인이 생기기 전까지 모든 업로드를 이 사용자로 저장한다 (기본 admin).
     * @param translator       3단계 번역·요약. null 이면 건너뛴다.
     */
    public DefaultAnalysisService(TextExtractor extractor, ConnectionFactory db,
                                  Path uploadDir, String uploaderUsername, Translator translator) {
        this.extractor = extractor;
        this.db = db;
        this.uploadDir = uploadDir.toAbsolutePath().normalize();
        this.uploaderUsername = uploaderUsername;
        this.translator = translator;
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

            // 3. 번역·요약·개념·시험 문제·용어. DB 를 열기 전에 끝낸다 (LLM 을 기다리는 동안 연결을 잡고 있지 않도록).
            Translation translation = translate(originalName, items);

            // 4. 한 트랜잭션으로 저장
            try (Connection conn = db.open()) {
                conn.setAutoCommit(false);
                try {
                    int userId = UserDao.findIdByUsername(conn, uploaderUsername)
                            .orElseThrow(() -> new SQLException("업로드 사용자 '" + uploaderUsername
                                    + "' 가 DB 에 없습니다. Server.tools.CreateUser 로 만들어 주세요."));
                    int documentId = DocumentDao.insert(conn, userId, originalName, saved.toString());
                    int resultId = AnalysisResultDao.insert(conn, documentId, toJson(items),
                            translation.translatedText(), translation.explanation(),
                            translation.summary(), translation.examQuestions());
                    AnalysisResultDao.linkTerms(conn, resultId, saveTerms(conn, translation.terms()));
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

    /** 이력 화면에 한 번에 보여 줄 최대 개수. */
    static final int MAX_HISTORY = 200;

    @Override
    public List<ResultSummary> listResults(String fileQuery) throws SQLException {
        try (Connection conn = db.open()) {
            OptionalInt userId = UserDao.findIdByUsername(conn, uploaderUsername);
            if (userId.isEmpty()) {
                return List.of();
            }
            return AnalysisResultDao.listByUser(conn, userId.getAsInt(), fileQuery, MAX_HISTORY).stream()
                    .map(s -> new ResultSummary(s.resultId(), s.documentId(), s.fileName(),
                            s.analyzedAt() == null ? null : s.analyzedAt().toString(),
                            s.itemCount(), s.translated(), s.termCount()))
                    .toList();
        }
    }

    @Override
    public boolean deleteResult(int resultId) throws SQLException {
        AnalysisResultDao.DocumentRef doc;
        try (Connection conn = db.open()) {
            conn.setAutoCommit(false);
            try {
                OptionalInt userId = UserDao.findIdByUsername(conn, uploaderUsername);
                Optional<AnalysisResultDao.DocumentRef> found = userId.isEmpty() ? Optional.empty()
                        : AnalysisResultDao.findDocument(conn, resultId, userId.getAsInt());
                if (found.isEmpty()) {
                    conn.rollback();
                    return false;
                }
                doc = found.get();
                // 문서를 지우면 분석 결과·용어 연결도 함께 지워진다 (ON DELETE CASCADE)
                DocumentDao.delete(conn, doc.documentId());
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
        deleteUploadedFile(doc.filePath());
        return true;
    }

    /**
     * DB 에서 지운 뒤 업로드 폴더의 파일과 그 무작위 폴더를 지운다.
     * 업로드 폴더 밖을 가리키는 경로는 건드리지 않는다. 지우지 못해도 DB 삭제는 그대로 둔다.
     */
    private void deleteUploadedFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        Path file = Path.of(filePath).toAbsolutePath().normalize();
        Path folder = file.getParent();
        if (folder == null || !folder.startsWith(uploadDir) || folder.equals(uploadDir)) {
            return;
        }
        deleteQuietly(file);
        deleteQuietly(folder);   // 비어 있을 때만 지워진다
    }

    /** 3단계. 번역기가 없거나 실패하면 빈 결과 (번역 오류로 업로드 전체가 실패하지 않게 한다). */
    private Translation translate(String fileName, List<Item> items) {
        if (translator == null || items.isEmpty()) {
            return Translation.empty();
        }
        try {
            Translation t = translator.translate(fileName, items);
            return t == null ? Translation.empty() : t;
        } catch (RuntimeException e) {
            System.out.println("[번역 " + fileName + "] 실패, 추출 결과만 저장합니다: " + e);
            return Translation.empty();
        }
    }

    /**
     * 용어를 사전(terms)에 넣고 term_id 를 받는다. 실패해도 업로드는 계속한다:
     * 저장점(savepoint)까지 되돌리고 용어 없이 저장한다.
     */
    private static List<Integer> saveTerms(Connection conn, List<AnalysisResultDao.Term> terms) {
        if (terms.isEmpty()) {
            return List.of();
        }
        Savepoint savepoint = null;
        try {
            savepoint = conn.setSavepoint();
            List<Integer> ids = TermDao.ensureIds(conn, terms);
            conn.releaseSavepoint(savepoint);
            return ids;
        } catch (SQLException e) {
            try {
                if (savepoint != null) {
                    conn.rollback(savepoint);
                }
            } catch (SQLException ignored) {
                // 되돌리지 못하면 이어지는 저장에서 오류가 나고 전체가 되돌려진다
            }
            System.out.println("[번역] 용어를 사전에 저장하지 못해 용어 없이 저장합니다: " + e.getMessage());
            return List.of();
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
