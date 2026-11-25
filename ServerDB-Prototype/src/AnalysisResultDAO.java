
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AnalysisResultDAO {

    
    
    // 1. AI 분석 결과 저장 (문서 소유권 확인)
    public int addAnalysisResult(
            Connection conn,
            int documentId,
            int userId,
            String translatedText,
            String explanation,
            String summary) throws SQLException {

        // 문서 소유자 확인
        String checkSql =
            "SELECT document_id FROM documents " +
            "WHERE document_id = ? AND user_id = ?";

        // 분석 결과 저장
        String insertSql =
            "INSERT INTO analysis_results " +
            "(document_id, translated_text, explanation, summary) " +
            "VALUES (?, ?, ?, ?)";

        try (PreparedStatement checkStmt =
                conn.prepareStatement(checkSql)) {

            checkStmt.setInt(1, documentId);
            checkStmt.setInt(2, userId);

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                        "접근 거부: 본인 소유 문서가 아닙니다."
                    );
                }
            }
        }

        try (PreparedStatement pstmt =
                conn.prepareStatement(
                    insertSql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, documentId);
            pstmt.setString(2, translatedText);
            pstmt.setString(3, explanation);
            pstmt.setString(4, summary);

            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 1) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }

            throw new SQLException("AI 분석 결과 저장 실패");
        }
    }



    // 2. 문서별 AI 분석 결과 조회
    public void getAnalysisResultsByDocument(
            Connection conn,
            int documentId,
            int userId) throws SQLException {

        String sql = "SELECT a.result_id, a.translated_text, " +
                     "a.explanation, a.summary, a.analyzed_at " +
                     "FROM analysis_results a " +
                     "JOIN documents d ON a.document_id = d.document_id " +
                     "WHERE a.document_id = ? AND d.user_id = ?";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setInt(1, documentId);
            pstmt.setInt(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    System.out.println(
                        "분석 번호: " + rs.getInt("result_id")
                    );
                    System.out.println(
                        "번역 내용: " + rs.getString("translated_text")
                    );
                    System.out.println(
                        "전공용어 설명: " + rs.getString("explanation")
                    );
                    System.out.println(
                        "문서 요약: " + rs.getString("summary")
                    );
                    System.out.println(
                        "분석 날짜: " + rs.getTimestamp("analyzed_at")
                    );
                    System.out.println("--------------------");
                }
            }
        }
    }


    // 3. 사용자별 AI 분석 결과 조회
    public void getAnalysisResultsByUser(
            Connection conn,
            int userId) throws SQLException {

        String sql =
            "SELECT a.result_id, d.file_name, " +
            "a.translated_text, a.explanation, " +
            "a.summary, a.analyzed_at " +
            "FROM analysis_results a " +
            "JOIN documents d " +
            "ON a.document_id = d.document_id " +
            "WHERE d.user_id = ?";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    System.out.println(
                        "분석 번호: " + rs.getInt("result_id")
                    );
                    System.out.println(
                        "파일 이름: " + rs.getString("file_name")
                    );
                    System.out.println(
                        "번역 내용: " + rs.getString("translated_text")
                    );
                    System.out.println(
                        "전공용어 설명: " + rs.getString("explanation")
                    );
                    System.out.println(
                        "문서 요약: " + rs.getString("summary")
                    );
                    System.out.println(
                        "분석 날짜: " + rs.getTimestamp("analyzed_at")
                    );
                    System.out.println("--------------------");
                }
            }
        }
    }

        // 4. 클라이언트 전달용 사용자별 AI 분석 결과 조회
    public String getAnalysisResultsForClient(
            Connection conn,
            int userId) throws SQLException {

        String sql =
                "SELECT a.result_id, d.file_name, " +
                "a.translated_text, a.explanation, " +
                "a.summary, a.analyzed_at " +
                "FROM analysis_results a " +
                "JOIN documents d " +
                "ON a.document_id = d.document_id " +
                "WHERE d.user_id = ? " +
                "ORDER BY a.analyzed_at DESC";

        StringBuilder result = new StringBuilder();

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {

                    result.append("분석 번호: ")
                        .append(rs.getInt("result_id"))
                        .append("\n");

                    result.append("파일 이름: ")
                        .append(rs.getString("file_name"))
                        .append("\n");

                    result.append("번역 결과: ")
                        .append(rs.getString("translated_text"))
                        .append("\n");

                    result.append("전공용어 설명: ")
                        .append(rs.getString("explanation"))
                        .append("\n");

                    result.append("문서 요약: ")
                        .append(rs.getString("summary"))
                        .append("\n");

                    result.append("분석 날짜: ")
                        .append(rs.getTimestamp("analyzed_at"))
                        .append("\n");

                    result.append("--------------------\n");
                }
            }
        }

        if (result.length() == 0) {
            return "등록된 AI 분석 결과가 없습니다.";
        }

        return result.toString();
    }


}

