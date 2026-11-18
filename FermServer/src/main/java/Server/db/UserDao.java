package Server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalInt;

/** users 테이블. */
public final class UserDao {

    private UserDao() {
    }

    /** 아이디(대소문자 구분 없음)로 user_id 를 찾는다. */
    public static OptionalInt findIdByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT user_id FROM users WHERE lower(username) = lower(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? OptionalInt.of(rs.getInt(1)) : OptionalInt.empty();
            }
        }
    }

    /**
     * 사용자를 만든다. 비밀번호는 해시해서 저장한다.
     * 같은 아이디가 이미 있으면 아무것도 하지 않고 false.
     */
    public static boolean create(Connection conn, String username, String password) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?) "
                + "ON CONFLICT ((lower(username))) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordHasher.hash(password));
            return ps.executeUpdate() == 1;
        }
    }
}
