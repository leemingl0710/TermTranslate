
import java.sql.Connection;
import java.util.Scanner;
import java.nio.charset.Charset;

public class TermTest {

    public static void main(String[] args) {

        try (Scanner sc = new Scanner(
                System.in, Charset.forName("MS949"))) {

            System.out.print("MySQL 비밀번호 입력: ");
            String dbPassword = sc.nextLine();

            try (Connection conn =
                    DBConnection.getConnection(dbPassword)) {

                TermDAO termDAO = new TermDAO();

                // 테스트 데이터 자동 저장 방지
                conn.setAutoCommit(false);

                try {

                    // 1. 전체 전공용어 조회
                    System.out.println(
                        "\n===== DB 전체 데이터 ====="
                    );

                    termDAO.getAllTerms(conn);

                    // 2. 전공용어 검색
                    System.out.print("\n검색할 전공용어 입력: ");
                    String keyword = sc.nextLine();

                    System.out.println("\n===== 검색 결과 =====");
                    termDAO.searchTerm(conn, keyword);

                    // 3. 테스트용 전공용어 등록
                    System.out.println("\n===== INSERT 테스트 =====");

                    int testId = termDAO.addTerm(
                        conn,
                        "테스트전공용어",
                        "TestTerm",
                        "테스트용 설명"
                    );

                    if (testId <= 0) {
                        throw new Exception("테스트 데이터 등록 실패");
                    }

                    System.out.println("등록 성공! ID: " + testId);

                    // 4. 테스트용 전공용어 수정
                    System.out.println("\n===== UPDATE 테스트 =====");

                    boolean updated = termDAO.updateTerm(
                        conn,
                        testId,
                        "수정테스트",
                        "UpdatedTest",
                        "수정 기능 확인용 설명"
                    );

                    if (!updated) {
                        throw new Exception("테스트 데이터 수정 실패");
                    }

                    System.out.println("수정 성공!");

                    // 5. 테스트용 전공용어 삭제
                    System.out.println("\n===== DELETE 테스트 =====");

                    boolean deleted = termDAO.deleteTerm(
                        conn,
                        testId
                    );

                    if (!deleted) {
                        throw new Exception("테스트 데이터 삭제 실패");
                    }

                    System.out.println("삭제 성공!");

                    // 6. 테스트 완료
                    System.out.println("\n===== 테스트 결과 =====");
                    System.out.println("INSERT 성공");
                    System.out.println("UPDATE 성공");
                    System.out.println("DELETE 성공");

                } finally {

                    // 테스트 변경사항 되돌리기
                    conn.rollback();

                    System.out.println(
                        "\n테스트 변경사항을 롤백했습니다."
                    );
                }

            } catch (Exception e) {
                System.out.println(
                    "DB 처리 오류: " + e.getMessage()
                );
            }

        } catch (Exception e) {
            System.out.println(
                "프로그램 오류: " + e.getMessage()
            );
        }
    }
}




