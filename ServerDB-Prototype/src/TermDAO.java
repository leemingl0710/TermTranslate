
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TermDAO {

    // 1. 전공용어 등록
    public int addTerm(
            Connection conn,
            String koreanTerm,
            String englishTerm,
            String explanation) throws SQLException {

        String sql = "INSERT INTO terms " +
                     "(korean_term, english_term, explanation) " +
                     "VALUES (?, ?, ?)";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, koreanTerm);
            pstmt.setString(2, englishTerm);
            pstmt.setString(3, explanation);

            if (pstmt.executeUpdate() != 1) {
                throw new SQLException("전공용어 등록 실패");
            }

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

            throw new SQLException("전공용어 ID를 가져오지 못했습니다.");
        }
    }

    // 2. 전공용어 전체 조회
    public void getAllTerms(Connection conn) throws SQLException {

        String sql = "SELECT * FROM terms";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.println("--------------------");
                System.out.println("번호: " + rs.getInt("term_id"));
                System.out.println("한국어: " + rs.getString("korean_term"));
                System.out.println("영어: " + rs.getString("english_term"));
                System.out.println("설명: " + rs.getString("explanation"));
            }
        }
    }

    // 3. 전공용어 검색
    public void searchTerm(Connection conn, String keyword)
            throws SQLException {

        String sql = "SELECT * FROM terms " +
                     "WHERE korean_term LIKE ? " +
                     "OR english_term LIKE ?";

        // 검색어 앞뒤 공백 제거
        String searchKeyword = "%" + keyword.trim() + "%";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setString(1, searchKeyword);
            pstmt.setString(2, searchKeyword);

            // 검색 오류 확인용 출력
            System.out.println("실제 SQL 검색어: [" + searchKeyword + "]");
            System.out.println("실행 SQL: " + pstmt);

            int count = 0;

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    count++;

                    System.out.println("--------------------");
                    System.out.println("번호: " + rs.getInt("term_id"));
                    System.out.println("한국어: " + rs.getString("korean_term"));
                    System.out.println("영어: " + rs.getString("english_term"));
                    System.out.println("설명: " + rs.getString("explanation"));
                }
            }

            System.out.println("검색된 데이터 개수: " + count);

            if (count == 0) {
                System.out.println("검색 결과가 없습니다.");
            }
        }
    }

    // 4. 전공용어 수정
    public boolean updateTerm(
            Connection conn,
            int termId,
            String koreanTerm,
            String englishTerm,
            String explanation) throws SQLException {

        String sql = "UPDATE terms " +
                     "SET korean_term = ?, " +
                     "english_term = ?, " +
                     "explanation = ? " +
                     "WHERE term_id = ?";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setString(1, koreanTerm);
            pstmt.setString(2, englishTerm);
            pstmt.setString(3, explanation);
            pstmt.setInt(4, termId);

            return pstmt.executeUpdate() == 1;
        }
    }

    // 5. 전공용어 삭제
    public boolean deleteTerm(
            Connection conn,
            int termId) throws SQLException {

        String sql = "DELETE FROM terms WHERE term_id = ?";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setInt(1, termId);

            return pstmt.executeUpdate() == 1;
        }
    }

    // 6. 클라이언트용 전공용어 검색
    public String searchTermsForClient(Connection conn, String keyword)
            throws SQLException {

        String sql = "SELECT term_id, korean_term, english_term, explanation " +
                    "FROM terms " +
                    "WHERE korean_term LIKE ? OR english_term LIKE ?";

        String searchKeyword = "%" + keyword.trim() + "%";
        StringBuilder result = new StringBuilder();

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, searchKeyword);
            pstmt.setString(2, searchKeyword);

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    result.append("번호: ").append(rs.getInt("term_id")).append(" / ");
                    result.append("한국어: ").append(rs.getString("korean_term")).append(" / ");
                    result.append("영어: ").append(rs.getString("english_term")).append(" / ");
                    result.append("설명: ").append(rs.getString("explanation")).append("\n");
                }
            }
        }

        if (result.length() == 0) {
            return "검색 결과가 없습니다.";
        }

        return result.toString();
    }


}
