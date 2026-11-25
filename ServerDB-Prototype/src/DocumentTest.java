
import java.sql.Connection;
import java.util.Scanner;
import java.nio.charset.Charset;

public class DocumentTest {

    public static void main(String[] args) {

        try (Scanner sc = new Scanner(
                System.in, Charset.forName("MS949"))) {

            System.out.print("MySQL 비밀번호 입력: ");
            String dbPassword = sc.nextLine();

            try (Connection conn =
                    DBConnection.getConnection(dbPassword)) {

                DocumentDAO documentDAO = new DocumentDAO();

                // 사용자 번호 1의 문서 조회
                int userId = 1;

                System.out.println("===== 사용자 문서 목록 =====");

                documentDAO.getDocumentsByUser(conn, userId);

                System.out.println("===== 조회 완료 =====");

            }

        } catch (Exception e) {
            System.out.println("오류 발생: " + e.getMessage());
        }
    }
}
