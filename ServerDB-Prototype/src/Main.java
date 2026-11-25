
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.OptionalInt;

public class Main {

    public static void main(String[] args) {

        try (Scanner sc = new Scanner(System.in)) {

            System.out.print("MySQL 비밀번호 입력: ");

            if (!sc.hasNextLine()) {
                System.out.println("비밀번호를 입력해 주세요.");
                return;
            }

            String dbPassword = sc.nextLine();

            try (Connection conn =
                    DBConnection.getConnection(dbPassword)) {

                if (!conn.isValid(5)) {
                    System.out.println("MySQL 연결 상태 확인 실패!");
                    return;
                }

                System.out.println("MySQL 연결 성공!");

                UserDAO users = new UserDAO(conn);
                runMenu(sc, conn, users);

            } catch (SQLException e) {
                System.out.println("MySQL 연결 실패!");
                System.out.println(e.getMessage());
            }
        }
    }

    private static void runMenu(Scanner sc, Connection conn, UserDAO users) {
        DocumentDAO documents = new DocumentDAO();
        TermDAO terms = new TermDAO();

        OptionalInt loggedInUserId = OptionalInt.empty();

        while (true) {

            System.out.println();

            if (loggedInUserId.isPresent()) {
                System.out.println(
                    "로그인 중인 회원 ID: " +
                    loggedInUserId.getAsInt());

                System.out.println("3. 로그아웃");
                System.out.println("4. 내 문서 조회");
                System.out.println("5. 전공용어 검색");
                System.out.println("6. 내 AI 분석 결과 조회");

            } else {
                System.out.println("1. 회원가입");
                System.out.println("2. 로그인");
            }

            System.out.println("0. 종료");

            String choice = readLine(sc, "선택: ");

            if (choice == null || choice.equals("0")) {
                System.out.println("프로그램을 종료합니다.");
                return;
            }

            if (loggedInUserId.isPresent()) {

                try {
                    switch (choice) {
                        case "3":
                            loggedInUserId = OptionalInt.empty();
                            System.out.println("로그아웃했습니다.");
                            break;
                        case "4":
                            System.out.println("===== 내 문서 =====");
                            documents.getDocumentsByUser(conn, loggedInUserId.getAsInt());
                            break;
                        case "5":
                            String keyword = readLine(sc, "검색할 전공용어: ");
                            if (keyword == null) {
                                return;
                            }
                            if (keyword.isBlank()) {
                                System.out.println("검색어를 입력해 주세요.");
                                break;
                            }
                            terms.searchTerm(conn, keyword);
                            break;
                        case "6":
                            System.out.println("===== 내 AI 분석 결과 =====");

                            AnalysisResultDAO results = new AnalysisResultDAO();

                            results.getAnalysisResultsByUser(
                                conn,
                                loggedInUserId.getAsInt()
                            );
                            break;

                        default:
                            System.out.println("메뉴 번호를 확인해 주세요.");
                    }
                } catch (SQLException e) {
                    System.out.println("조회 중 DB 오류가 발생했습니다. (오류 코드: "
                        + e.getErrorCode() + ")");
                }

                continue;
            }

            if (!choice.equals("1") && !choice.equals("2")) {
                System.out.println("메뉴 번호를 확인해 주세요.");
                continue;
            }

            String username = readLine(sc, "아이디: ");
            if (username == null) return;

            String password = readLine(
                sc, "회원 비밀번호 (가입 시 8~128자): ");
            if (password == null) return;

            try {

                if (choice.equals("1")) {

                    if (users.existsByUsername(username)) {
                        System.out.println(
                            "이미 사용 중인 아이디입니다.");
                        continue;
                    }

                    int userId =
                        users.register(username, password);

                    System.out.println(
                        "회원가입 성공! 회원 ID: " + userId);

                } else {

                    loggedInUserId =
                        users.login(username, password);

                    if (loggedInUserId.isPresent()) {
                        System.out.println("로그인 성공!");
                    } else {
                        System.out.println(
                            "아이디 또는 비밀번호가 일치하지 않습니다.");
                    }
                }

            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());

            } catch (SQLException e) {

                if (e.getErrorCode() == 1062) {
                    System.out.println(
                        "이미 사용 중인 아이디입니다.");
                } else {
                    System.out.println(
                        "회원 처리 중 DB 오류가 발생했습니다. " +
                        "(오류 코드: " +
                        e.getErrorCode() + ")");
                }
            }
        }
    }

    private static String readLine(
            Scanner sc, String prompt) {

        System.out.print(prompt);

        return sc.hasNextLine()
            ? sc.nextLine()
            : null;
    }
}
