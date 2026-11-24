package Server;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens connections to the Neon PostgreSQL database.
 *
 * Connection info is read from the .env file in the project root
 * (copy .env.example to .env and fill in your own values).
 * If .env is missing, OS environment variables with the same names are used.
 */
public final class DBConnection {

    private static final Dotenv ENV = Dotenv.configure().ignoreIfMissing().load();

    static {
        // ./mvnw exec:java 로 실행하면 JDBC 드라이버 자동 등록이 안 되어
        // "No suitable driver found" 가 나는 경우가 있어 직접 등록한다 (이클립스 실행에는 영향 없음).
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            // 드라이버가 없으면 getConnection 에서 "No suitable driver" 오류로 알려진다
        }
    }

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(require("DB_URL"), require("DB_USER"), require("DB_PASSWORD"));
    }

    /** true if DB_URL, DB_USER and DB_PASSWORD are all set (.env or environment variables). */
    public static boolean isConfigured() {
        return has("DB_URL") && has("DB_USER") && has("DB_PASSWORD");
    }

    private static boolean has(String key) {
        String value = ENV.get(key); // falls back to System.getenv(key)
        return value != null && !value.isBlank();
    }

    private static String require(String key) {
        if (!has(key)) {
            throw new IllegalStateException(
                    key + " 값이 없습니다. 프로젝트 최상위 폴더에서 .env.example 을 복사해 .env 를 만들고 "
                            + key + " 를 채워 주세요. (터미널: cp .env.example .env)");
        }
        return ENV.get(key).trim();
    }
}
