package Server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** analysis_results / analysis_terms 테이블. */
public final class AnalysisResultDao {

    private AnalysisResultDao() {
    }

    /** 분석 결과에 붙은 용어 한 개 (Translation 카드). */
    public record Term(String korean, String english, String explanation) {
    }

    /** 결과 조회용: analysis_results 한 행 + 파일 이름 + 용어 목록. */
    public record Row(int resultId, int documentId, String fileName, LocalDateTime analyzedAt,
                      String sourceTextJson, String translatedText, String explanation,
                      String summary, String examQuestions, List<Term> terms) {
    }

    /**
     * 분석 결과를 넣고 result_id 를 돌려준다.
     * 번역·요약 등은 아직 없으면 null 로 둔다.
     */
    public static int insert(Connection conn, int documentId, String sourceTextJson,
                             String translatedText, String explanation,
                             String summary, String examQuestions) throws SQLException {
        String sql = "INSERT INTO analysis_results "
                + "(document_id, source_text, translated_text, explanation, summary, exam_questions) "
                + "VALUES (?, ?::jsonb, ?, ?, ?, ?) RETURNING result_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, documentId);
            ps.setString(2, sourceTextJson);
            ps.setString(3, translatedText);
            ps.setString(4, explanation);
            ps.setString(5, summary);
            ps.setString(6, examQuestions);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** 결과에 나온 용어들을 연결한다 (용어 사전 terms 에 이미 있는 term_id). */
    public static void linkTerms(Connection conn, int resultId, List<Integer> termIds) throws SQLException {
        if (termIds.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO analysis_terms (result_id, term_id) VALUES (?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int termId : termIds) {
                ps.setInt(1, resultId);
                ps.setInt(2, termId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static Optional<Row> findById(Connection conn, int resultId) throws SQLException {
        String sql = "SELECT r.result_id, r.document_id, d.file_name, r.analyzed_at, r.source_text::text, "
                + "r.translated_text, r.explanation, r.summary, r.exam_questions "
                + "FROM analysis_results r JOIN documents d ON d.document_id = r.document_id "
                + "WHERE r.result_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resultId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp at = rs.getTimestamp(4);
                return Optional.of(new Row(
                        rs.getInt(1), rs.getInt(2), rs.getString(3),
                        at == null ? null : at.toLocalDateTime(),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        findTerms(conn, resultId)));
            }
        }
    }

    /** 이력 화면 한 줄: 결과 번호, 파일 이름, 분석 시각, 문단 수, 번역 여부, 용어 수. */
    public record Summary(int resultId, int documentId, String fileName, LocalDateTime analyzedAt,
                          int itemCount, boolean translated, int termCount) {
    }

    /** 결과가 속한 문서 (삭제용). filePath 는 업로드 폴더 안 저장 위치. */
    public record DocumentRef(int documentId, String filePath) {
    }

    /**
     * 한 사용자의 분석 결과 목록 (최근 것부터).
     *
     * @param fileQuery 파일 이름에 들어간 글자 (대소문자 무시). 비어 있으면 전체
     */
    public static List<Summary> listByUser(Connection conn, int userId, String fileQuery, int limit)
            throws SQLException {
        boolean filter = fileQuery != null && !fileQuery.isBlank();
        String sql = "SELECT r.result_id, r.document_id, d.file_name, r.analyzed_at, "
                + "CASE WHEN jsonb_typeof(r.source_text) = 'array' THEN jsonb_array_length(r.source_text) ELSE 0 END, "
                + "r.translated_text IS NOT NULL, "
                + "(SELECT count(*) FROM analysis_terms t WHERE t.result_id = r.result_id) "
                + "FROM analysis_results r JOIN documents d ON d.document_id = r.document_id "
                + "WHERE d.user_id = ? "
                + (filter ? "AND d.file_name ILIKE ? ESCAPE '\\' " : "")
                + "ORDER BY r.analyzed_at DESC, r.result_id DESC LIMIT ?";
        List<Summary> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, userId);
            if (filter) {
                ps.setString(i++, TermDao.likePattern(fileQuery.strip()));
            }
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp at = rs.getTimestamp(4);
                    rows.add(new Summary(rs.getInt(1), rs.getInt(2), rs.getString(3),
                            at == null ? null : at.toLocalDateTime(),
                            rs.getInt(5), rs.getBoolean(6), rs.getInt(7)));
                }
            }
        }
        return rows;
    }

    /** 이 사용자의 결과라면 그 결과가 속한 문서를 돌려준다. */
    public static Optional<DocumentRef> findDocument(Connection conn, int resultId, int userId)
            throws SQLException {
        String sql = "SELECT d.document_id, d.file_path FROM analysis_results r "
                + "JOIN documents d ON d.document_id = r.document_id "
                + "WHERE r.result_id = ? AND d.user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resultId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new DocumentRef(rs.getInt(1), rs.getString(2))) : Optional.empty();
            }
        }
    }

    private static List<Term> findTerms(Connection conn, int resultId) throws SQLException {
        String sql = "SELECT t.korean_term, t.english_term, t.explanation "
                + "FROM analysis_terms a JOIN terms t ON t.term_id = a.term_id "
                + "WHERE a.result_id = ? ORDER BY t.korean_term";
        List<Term> terms = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, resultId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    terms.add(new Term(rs.getString(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        return terms;
    }
}
