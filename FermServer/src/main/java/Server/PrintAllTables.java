package Server;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Connects to the database and prints every row of every table in the "public" schema.
 */
public final class PrintAllTables {

    static final String SCHEMA = "public";
    private static final int MAX_VALUE_LENGTH = 50;

    private PrintAllTables() {
    }

    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("DB 연결 성공!\n");
            printAll(conn, System.out);
        } catch (SQLException e) {
            System.err.println("DB 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Returns the names of all tables in the public schema, alphabetically. */
    public static List<String> listTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, SCHEMA, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        tables.sort(String::compareTo);
        return tables;
    }

    /** Prints every table in the public schema to {@code out}. */
    public static void printAll(Connection conn, PrintStream out) throws SQLException {
        List<String> tables = listTables(conn);
        if (tables.isEmpty()) {
            out.println("public 스키마에 테이블이 없습니다.");
            return;
        }
        for (String table : tables) {
            printTable(conn, table, out);
        }
        out.println("총 " + tables.size() + "개 테이블 출력 완료.");
    }

    /** Prints the header, all rows and the row count of one table. */
    public static void printTable(Connection conn, String table, PrintStream out) throws SQLException {
        String sql = "SELECT * FROM " + quote(SCHEMA) + "." + quote(table);
        out.println("==================== " + table + " ====================");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();

            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) header.append(" | ");
                header.append(md.getColumnLabel(i));
            }
            out.println(header);
            out.println("-".repeat(header.length()));

            int count = 0;
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) row.append(" | ");
                    row.append(format(rs.getString(i)));
                }
                out.println(row);
                count++;
            }
            if (count == 0) {
                out.println("(데이터 없음)");
            }
            out.println("→ " + count + "행\n");
        }
    }

    /** NULL -> "NULL", long text truncated, newlines flattened. */
    static String format(String value) {
        if (value == null) return "NULL";
        String v = value.replace('\n', ' ').replace('\r', ' ');
        return v.length() > MAX_VALUE_LENGTH ? v.substring(0, MAX_VALUE_LENGTH) + "..." : v;
    }

    /** Wraps an identifier in double quotes, escaping embedded quotes. */
    static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
