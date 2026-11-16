package Server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PrintAllTablesTest {

    private static final List<String> EXPECTED_TABLES =
            List.of("analysis_results", "api_logs", "documents", "terms", "users");

    @BeforeEach
    void requireDbConfig() {
        assumeTrue(DBConnection.isConfigured(),
                "DB 설정이 없어 테스트를 건너뜁니다. .env.example 을 복사해 .env 를 만들고 "
                        + "DB_URL / DB_USER / DB_PASSWORD 를 채워 주세요.");
    }

    @Test
    void connectionIsValid() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            assertTrue(conn.isValid(5), "DB 연결이 유효하지 않습니다.");
        }
    }

    @Test
    void allExpectedTablesExist() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            List<String> tables = PrintAllTables.listTables(conn);
            for (String expected : EXPECTED_TABLES) {
                assertTrue(tables.contains(expected),
                        "public 스키마에 테이블이 없습니다: " + expected + " (발견된 테이블: " + tables + ")");
            }
        }
    }

    @Test
    void printAllDoesNotThrow() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
            assertDoesNotThrow(() -> PrintAllTables.printAll(conn, out));
            String text = buf.toString(StandardCharsets.UTF_8);
            for (String expected : EXPECTED_TABLES) {
                assertTrue(text.contains(expected), "출력에 테이블 이름이 없습니다: " + expected);
            }
            System.out.println(text);
        }
    }
}
