package Server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** documents 테이블. */
public final class DocumentDao {

    private DocumentDao() {
    }

    /** 문서 한 건을 넣고 document_id 를 돌려준다. */
    public static int insert(Connection conn, int userId, String fileName, String filePath) throws SQLException {
        String sql = "INSERT INTO documents (user_id, file_name, file_path) VALUES (?, ?, ?) RETURNING document_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, fileName);
            ps.setString(3, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * 문서를 지운다. 그 문서의 분석 결과(analysis_results)와 용어 연결(analysis_terms)도
     * 외래 키 ON DELETE CASCADE 로 함께 지워진다. 용어 사전(terms)은 그대로 둔다.
     */
    public static boolean delete(Connection conn, int documentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM documents WHERE document_id = ?")) {
            ps.setInt(1, documentId);
            return ps.executeUpdate() == 1;
        }
    }
}
