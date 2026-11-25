
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;
import java.nio.charset.Charset;

public class AnalysisResultTest {

    public static void main(String[] args) {

        try (Scanner sc = new Scanner(
                System.in, Charset.forName("MS949"))) {

            System.out.print("MySQL 비밀번호 입력: ");
            String dbPassword = sc.nextLine();

            try (Connection conn =
                    DBConnection.getConnection(dbPassword)) {

                AnalysisResultDAO dao = new AnalysisResultDAO();

                // 테스트 데이터가 실제 DB에 남지 않도록 설정
                conn.setAutoCommit(false);

                try {
                    int documentId = 1;

                    String translatedText = "Test translation";
                    String explanation = "테스트용 설명";
                    String summary = "테스트용 요약";

                    // 1. 본인 문서 저장 테스트
                    System.out.println(
                        "===== 사용자 1: 본인 문서 저장 ====="
                    );

                    int resultId = dao.addAnalysisResult(
                        conn,
                        documentId,
                        1,
                        translatedText,
                        explanation,
                        summary
                    );

                    System.out.println(
                        "저장 성공! 분석 번호: " + resultId
                    );

                    // 2. 타인 문서 저장 테스트
                    System.out.println(
                        "\n===== 사용자 2: 타인 문서 저장 ====="
                    );

                    try {
                        dao.addAnalysisResult(
                            conn,
                            documentId,
                            2,
                            translatedText,
                            explanation,
                            summary
                        );

                        throw new IllegalStateException(
                            "권한 검사 실패: 타인 문서에 저장됨"
                        );

                    } catch (SQLException e) {
                        if (!e.getMessage().contains("접근 거부")) {
                            throw e;
                        }

                        System.out.println(
                            "접근 거부 확인: " + e.getMessage()
                        );
                    }

                    System.out.println(
                        "\n===== 권한 검사 테스트 성공 ====="
                    );

                } finally {
                    // 테스트 중 추가한 분석 결과 취소
                    conn.rollback();

                    System.out.println(
                        "테스트 데이터 롤백 완료"
                    );
                }

            } catch (Exception e) {
                System.out.println(
                    "테스트 오류: " + e.getMessage()
                );
            }

        } catch (Exception e) {
            System.out.println(
                "프로그램 오류: " + e.getMessage()
            );
        }
    }
}
