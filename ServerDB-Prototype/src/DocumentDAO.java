import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DocumentDAO {

    // 문서 정보 등록
    public int addDocument(
            Connection conn,
            int userId,
            String fileName,
            String filePath) throws SQLException {

        String sql = "INSERT INTO documents " +
                     "(user_id, file_name, file_path) " +
                     "VALUES (?, ?, ?)";

        try (PreparedStatement pstmt =
                conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, userId);
            pstmt.setString(2, fileName);
            pstmt.setString(3, filePath);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 1) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }

            throw new SQLException("문서 등록 실패");
        }
    }

    // 사용자별로 문서 조회
    public void getDocumentsByUser(
            Connection conn,
            int userId) throws SQLException {

        String sql = "SELECT document_id, file_name, " +
                     "file_path, uploaded_at " +
                     "FROM documents WHERE user_id = ?";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    System.out.println(
                        "문서 번호: " + rs.getInt("document_id")
                    );
                    System.out.println(
                        "파일 이름: " + rs.getString("file_name")
                    );
                    System.out.println(
                        "파일 경로: " + rs.getString("file_path")
                    );
                    System.out.println(
                        "업로드 날짜: " + rs.getTimestamp("uploaded_at")
                    );
                    System.out.println("--------------------");
                }
            }
        }
    }

        // 클라이언트용 사용자별로 문서 조회
    public String getDocumentsForClient(Connection conn, int userId)
            throws SQLException {

        String sql = "SELECT document_id, file_name, file_path, uploaded_at " +
                    "FROM documents WHERE user_id = ? " +
                    "ORDER BY document_id DESC";

        StringBuilder result = new StringBuilder();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    result.append("문서 번호: ")
                        .append(rs.getInt("document_id")).append(" / ");

                    result.append("파일 이름: ")
                        .append(rs.getString("file_name")).append(" / ");

                    result.append("파일 경로: ")
                        .append(rs.getString("file_path")).append(" / ");

                    result.append("업로드 날짜: ")
                        .append(rs.getTimestamp("uploaded_at"))
                        .append("\n");
                }
            }
        }

        if (result.length() == 0) {
            return "등록된 문서가 없습니다.";
        }

        return result.toString();
    }

}
