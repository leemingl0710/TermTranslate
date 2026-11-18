package Server.tools;

import Server.DBConnection;
import Server.db.UserDao;

import java.sql.Connection;

/**
 * 사용자 한 명을 DB 에 만든다 (비밀번호는 해시해서 저장).
 *
 * <pre>
 * 이클립스: Run Configurations → Arguments 에 "admin 1234"
 * 터미널:   ./mvnw -q compile exec:java -Dexec.mainClass=Server.tools.CreateUser -Dexec.args="admin 1234"
 * </pre>
 * 이미 있는 아이디면 건드리지 않는다.
 */
public final class CreateUser {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("사용법: CreateUser <아이디> <비밀번호>");
            System.exit(1);
        }
        try (Connection conn = DBConnection.getConnection()) {
            boolean created = UserDao.create(conn, args[0], args[1]);
            System.out.println(created
                    ? "사용자 생성: " + args[0] + " (user_id=" + UserDao.findIdByUsername(conn, args[0]).getAsInt() + ")"
                    : "이미 있는 아이디라 건너뜀: " + args[0]);
        }
    }
}
