import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.Base64;

public class Client {

    public static void main(String[] args) {

        // Windows 한국어 콘솔 입력 처리
        try (Scanner scanner =
                new Scanner(System.in, Charset.forName("MS949"))) {

            System.out.println("===== 전공용어 학습 시스템 =====");

            System.out.print("아이디 입력: ");
            String username = scanner.nextLine();

            System.out.print("비밀번호 입력: ");
            String password = scanner.nextLine();

            // 로그인 요청
            String loginResponse = sendRequest(
                "LOGIN", username, password
            );

            if (loginResponse == null ||
                    !loginResponse.startsWith("LOGIN_SUCCESS:")) {

                if ("LOGIN_FAILED".equals(loginResponse)) {
                    System.out.println("아이디 또는 비밀번호가 틀렸습니다.");
                } else {
                    System.out.println(
                        "로그인 실패 또는 서버 오류: " + loginResponse
                    );
                }
                return;
            }

            // 사용자 번호와 인증 토큰 분리
            String loginData = loginResponse.substring(
                "LOGIN_SUCCESS:".length()
            );

            String[] loginParts = loginData.split(":", 2);

            if (loginParts.length != 2 ||
                    loginParts[1].isBlank()) {
                System.out.println("로그인 응답 형식 오류");
                return;
            }

            String userId = loginParts[0];
            String token = loginParts[1];

            System.out.println("로그인 성공!");
            System.out.println("사용자 번호: " + userId);

            // 메인 메뉴
            while (true) {

                System.out.println();
                System.out.println("===== 메인 메뉴 =====");
                System.out.println("1. 전공용어 검색");
                System.out.println("2. 문서 정보 등록");
                System.out.println("3. 내 문서 목록 조회");
                System.out.println("4. 내 AI 분석 결과 조회");
                System.out.println("0. 종료");
                System.out.print("메뉴 선택: ");

                String choice = scanner.nextLine().trim();

                if ("0".equals(choice)) {

                    String response = sendRequest("LOGOUT", token);

                    if ("LOGOUT_SUCCESS".equals(response)) {
                        System.out.println("로그아웃 성공!");
                    } else if ("UNAUTHORIZED".equals(response)) {
                        System.out.println("이미 인증이 만료되었거나 유효하지 않습니다.");
                    } else {
                        System.out.println("서버 로그아웃 요청 실패");
                    }

                    System.out.println("프로그램을 종료합니다.");
                    break;
                }

                // 전공용어 검색
                if ("1".equals(choice)) {

                    System.out.print("검색할 전공용어: ");
                    String keyword = scanner.nextLine().trim();

                    if (keyword.isEmpty()) {
                        System.out.println("검색어를 입력해 주세요.");
                        continue;
                    }

                    String response = sendRequest(
                        "SEARCH", keyword
                    );

                    if (response != null &&
                            response.startsWith("SEARCH_RESULT:")) {

                        String result = response.substring(
                            "SEARCH_RESULT:".length()
                        );

                        System.out.println("===== 검색 결과 =====");
                        System.out.println(
                            result.replace(" | ", "\n")
                        );

                    } else {
                        System.out.println(
                            "검색 실패 또는 서버 오류: " + response
                        );
                    }

                // 문서 정보 등록
                } else if ("2".equals(choice)) {

                    System.out.print("파일 이름 입력: ");
                    String fileName = scanner.nextLine().trim();

                    System.out.print("파일 경로 입력: ");
                    String filePath = scanner.nextLine().trim();

                    if (fileName.isEmpty() || filePath.isEmpty()) {
                        System.out.println(
                            "파일 이름과 경로를 모두 입력해 주세요."
                        );
                        continue;
                    }

                    String response = sendRequest(
                        "ADD_DOCUMENT", token, fileName, filePath
                    );

                    if (response != null &&
                            response.startsWith("DOCUMENT_ADDED:")) {

                        String documentId = response.substring(
                            "DOCUMENT_ADDED:".length()
                        );

                        System.out.println("문서 정보 등록 성공!");
                        System.out.println("문서 번호: " + documentId);

                    } else if ("UNAUTHORIZED".equals(response)) {

                        System.out.println(
                            "인증이 유효하지 않습니다. 다시 로그인해 주세요."
                        );

                    } else {

                        System.out.println(
                            "문서 등록 실패 또는 서버 오류: " + response
                        );
                    }

                // 내 문서 목록 조회
                } else if ("3".equals(choice)) {

                    String response = sendRequest(
                        "GET_DOCUMENTS", token
                    );

                    if (response != null &&
                            response.startsWith("DOCUMENT_LIST:")) {

                        String result = response.substring(
                            "DOCUMENT_LIST:".length()
                        );

                        System.out.println("===== 내 문서 목록 =====");
                        System.out.println(
                            result.replace(" | ", "\n")
                        );

                    } else if ("UNAUTHORIZED".equals(response)) {

                        System.out.println(
                            "인증이 유효하지 않습니다. 다시 로그인해 주세요."
                        );

                    } else {

                        System.out.println(
                            "문서 조회 실패 또는 서버 오류: " + response
                        );
                    }

                // 내 AI 분석 결과 조회
                } else if ("4".equals(choice)) {

                    String response = sendRequest(
                        "GET_ANALYSIS_RESULTS", token
                    );

                    if (response != null &&
                            response.startsWith("ANALYSIS_RESULT:")) {

                        String encodedResult = response.substring(
                            "ANALYSIS_RESULT:".length()
                        );

                        try {

                            // Base64 데이터를 문자열로 변환
                            byte[] decodedBytes =
                                    Base64.getDecoder().decode(
                                        encodedResult
                                    );

                            String result = new String(
                                decodedBytes,
                                StandardCharsets.UTF_8
                            );

                            System.out.println(
                                "===== 내 AI 분석 결과 ====="
                            );

                            System.out.println(result);

                        } catch (IllegalArgumentException e) {

                            System.out.println(
                                "분석 결과 데이터 형식 오류"
                            );
                        }

                    } else if ("UNAUTHORIZED".equals(response)) {

                        System.out.println(
                            "인증이 유효하지 않습니다. 다시 로그인해 주세요."
                        );

                    } else {

                        System.out.println(
                            "분석 결과 조회 실패 또는 서버 오류: " +
                            response
                        );
                    }

                } else {

                    System.out.println(
                        "올바른 메뉴를 선택해 주세요."
                    );
                }
            }
        }
    }

    // 서버 요청 공통 메소드
    private static String sendRequest(
            String requestType, String... data) {

        try (Socket socket = new Socket("localhost", 5000)) {

            socket.setSoTimeout(60000);

            BufferedReader in = new BufferedReader(
                new InputStreamReader(
                    socket.getInputStream(),
                    StandardCharsets.UTF_8
                )
            );

            PrintWriter out = new PrintWriter(
                new OutputStreamWriter(
                    socket.getOutputStream(),
                    StandardCharsets.UTF_8
                ), true
            );

            out.println(requestType);

            for (String value : data) {
                out.println(value);
            }

            return in.readLine();

        } catch (IOException e) {

            System.out.println(
                "서버 연결 오류: " + e.getMessage()
            );

            return null;
        }
    }
}
