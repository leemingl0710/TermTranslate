
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.OptionalInt;

public class UserDAO {

    private final Connection conn;

    // MySQL 연결 객체 전달받기
    public UserDAO(Connection conn) {
        this.conn = conn;
    }

    // 1. 아이디 중복 확인
    public boolean existsByUsername(String username)
            throws SQLException {

        String sql = "SELECT 1 FROM users WHERE username = ?";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // 2. 회원가입
    public int register(String username, String password)
            throws SQLException {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                "아이디를 입력해 주세요.");
        }

        if (username.codePointCount(0, username.length()) > 50) {
            throw new IllegalArgumentException(
                "아이디는 50자 이하로 입력해 주세요.");
        }

        if (password == null ||
                password.length() < 8 ||
                password.length() > 128) {
            throw new IllegalArgumentException(
                "비밀번호는 8~128자로 입력해 주세요.");
        }

        String passwordHash =
                AuthService.hashPassword(password);

        String sql = "INSERT INTO users " +
                     "(username, password_hash) VALUES (?, ?)";

        try (PreparedStatement pstmt =
                conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        throw new SQLException("회원 ID 생성 실패");
    }

    // 3. 로그인
    public OptionalInt login(String username, String password)
            throws SQLException {

        String sql = "SELECT user_id, password_hash " +
                     "FROM users WHERE username = ?";

        try (PreparedStatement pstmt =
                conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {
                    int userId = rs.getInt("user_id");
                    String savedHash =
                            rs.getString("password_hash");

                    if (AuthService.verifyPassword(
                            password, savedHash)) {

                        return OptionalInt.of(userId);
                    }
                }
            }
        }

        return OptionalInt.empty();
    }
}
