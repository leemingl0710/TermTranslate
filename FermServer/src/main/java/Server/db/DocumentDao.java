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
}
