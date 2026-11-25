import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    // 인증 토큰 유효기간 10로 테스트용
    private static final long TOKEN_EXPIRATION = 10_000L;

    // 사용자 번호와 토큰 만료 시간 저장..
    private static class Session {

        private final int userId;
        private final long expiresAt;

        public Session(int userId, long expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    // 인증 토큰 저장
    private static final Map<String, Session> sessions =
            new ConcurrentHashMap<>();

    private static final SecureRandom random = new SecureRandom();

    // 인증 토큰 생성
    private static String createToken() {

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    // 인증 토큰 유효성 확인용
    private static Integer getAuthenticatedUserId(String token) {

        if (token == null) {
            return null;
        }

        Session session = sessions.get(token);

        if (session == null) {
            return null;
        }

        if (System.currentTimeMillis() >= session.expiresAt) {

            sessions.remove(token, session);

            System.out.println("만료된 인증 토큰 삭제");

            return null;
        }

        return session.userId;
    }

    // 만료된 인증 토큰 정리
    private static void removeExpiredSessions() {

        long currentTime = System.currentTimeMillis();

        sessions.entrySet().removeIf(
            entry -> currentTime >= entry.getValue().expiresAt
        );
    }

    public static void main(String[] args) {

        int port = 5000;

        // MySQL 비밀번호 환경변수 가져오기
        String dbPassword = System.getenv("DB_PASSWORD");

        if (dbPassword == null || dbPassword.isBlank()) {
            System.out.println("DB_PASSWORD 환경변수가 없습니다.");
            return;
        }

        // 서버 시작 전 MySQL 연결 확인
        try (Connection conn =
                DBConnection.getConnection(dbPassword)) {

            System.out.println("MySQL 연결 성공!");

        } catch (SQLException e) {

            System.out.println(
                "MySQL 연결 실패: " + e.getMessage()
            );

            return;
        }

        // Java 서버 시작
        try (ServerSocket serverSocket =
                new ServerSocket(
                    port,
                    50,
                    InetAddress.getLoopbackAddress()
                )) {

            System.out.println("===== Java 서버 시작 =====");
            System.out.println("서버 포트: " + port);
            System.out.println("인증 토큰 유효기간: " +
                    TOKEN_EXPIRATION / 1000 + "초");
            System.out.println("클라이언트 연결 대기 중...");

            while (true) {

                try (Socket clientSocket =
                        serverSocket.accept()) {

                    clientSocket.setSoTimeout(60000);

                    System.out.println("클라이언트 연결 성공!");

                    BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                            clientSocket.getInputStream(),
                            StandardCharsets.UTF_8
                        )
                    );

                    PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(
                            clientSocket.getOutputStream(),
                            StandardCharsets.UTF_8
                        ), true
                    );

                    // 클라이언트 요청 확인
                    String message = in.readLine();

                    // 만료된 인증 토큰 정리
                    removeExpiredSessions();

                    // 로그인 기능
                    if ("LOGIN".equals(message)) {

                        String username = in.readLine();
                        String password = in.readLine();

                        if (username == null || password == null) {

                            out.println("LOGIN_FAILED");
                            continue;
                        }

                        try (Connection conn =
                                DBConnection.getConnection(dbPassword)) {

                            UserDAO userDAO = new UserDAO(conn);

                            OptionalInt userId =
                                    userDAO.login(username, password);

                            if (userId.isPresent()) {

                                String token = createToken();

                                // 인증 토큰 만료 시간 설정
                                long expiresAt =
                                        System.currentTimeMillis()
                                        + TOKEN_EXPIRATION;

                                // 사용자 번호와 만료 시간 저장
                                sessions.put(
                                    token,
                                    new Session(
                                        userId.getAsInt(),
                                        expiresAt
                                    )
                                );

                                out.println(
                                    "LOGIN_SUCCESS:" +
                                    userId.getAsInt() +
                                    ":" + token
                                );

                                System.out.println("로그인 성공");

                            } else {

                                out.println("LOGIN_FAILED");
                                System.out.println("로그인 실패");
                            }

                        } catch (SQLException e) {

                            out.println("SERVER_ERROR");
                            System.out.println("로그인 DB 처리 오류");
                        }

                    // 전공용어 검색 기능
                    } else if ("SEARCH".equals(message)) {

                        String keyword = in.readLine();

                        if (keyword == null || keyword.isBlank()) {

                            out.println("SEARCH_FAILED");
                            continue;
                        }

                        try (Connection conn =
                                DBConnection.getConnection(dbPassword)) {

                            TermDAO termDAO = new TermDAO();

                            String result =
                                    termDAO.searchTermsForClient(
                                        conn, keyword
                                    );

                            out.println(
                                "SEARCH_RESULT:" +
                                result.replace("\r", "")
                                      .replace("\n", " | ")
                            );

                            System.out.println("전공용어 검색 완료");

                        } catch (SQLException e) {

                            out.println("SERVER_ERROR");
                            System.out.println("전공용어 검색 DB 오류");
                        }

                    // 문서 등록 기능
                    } else if ("ADD_DOCUMENT".equals(message)) {

                        String token = in.readLine();

                        Integer userId =
                                getAuthenticatedUserId(token);

                        if (userId == null) {

                            out.println("UNAUTHORIZED");
                            continue;
                        }

                        String fileName = in.readLine();
                        String filePath = in.readLine();

                        if (fileName == null || fileName.isBlank() ||
                                filePath == null || filePath.isBlank()) {

                            out.println("DOCUMENT_FAILED");
                            continue;
                        }

                        try (Connection conn =
                                DBConnection.getConnection(dbPassword)) {

                            DocumentDAO documentDAO = new DocumentDAO();

                            int documentId = documentDAO.addDocument(
                                conn,
                                userId,
                                fileName,
                                filePath
                            );

                            out.println("DOCUMENT_ADDED:" + documentId);

                            System.out.println("문서 정보 등록 완료");

                        } catch (SQLException e) {

                            out.println("SERVER_ERROR");
                            System.out.println("문서 등록 DB 오류");
                        }

                    // 사용자별 문서 조회 기능
                    } else if ("GET_DOCUMENTS".equals(message)) {

                        String token = in.readLine();

                        Integer userId =
                                getAuthenticatedUserId(token);

                        if (userId == null) {

                            out.println("UNAUTHORIZED");
                            continue;
                        }

                        try (Connection conn =
                                DBConnection.getConnection(dbPassword)) {

                            DocumentDAO documentDAO = new DocumentDAO();

                            String result =
                                    documentDAO.getDocumentsForClient(
                                        conn, userId
                                    );

                            out.println(
                                "DOCUMENT_LIST:" +
                                result.replace("\r", "")
                                      .replace("\n", " | ")
                            );

                            System.out.println("문서 조회 완료");

                        } catch (SQLException e) {

                            out.println("SERVER_ERROR");
                            System.out.println("문서 조회 DB 오류");
                        }

                    // 사용자별 AI 분석 결과 조회 기능
                    } else if ("GET_ANALYSIS_RESULTS".equals(message)) {

                        String token = in.readLine();

                        Integer userId =
                                getAuthenticatedUserId(token);

                        if (userId == null) {

                            out.println("UNAUTHORIZED");
                            continue;
                        }

                        try (Connection conn =
                                DBConnection.getConnection(dbPassword)) {

                            AnalysisResultDAO analysisResultDAO =
                                    new AnalysisResultDAO();

                            String result =
                                    analysisResultDAO
                                        .getAnalysisResultsForClient(
                                            conn, userId
                                        );

                            // 여러 줄의 분석 결과를 한 줄로 전송
                            String encodedResult =
                                    Base64.getEncoder().encodeToString(
                                        result.getBytes(
                                            StandardCharsets.UTF_8
                                        )
                                    );

                            out.println(
                                "ANALYSIS_RESULT:" + encodedResult
                            );

                            System.out.println("AI 분석 결과 조회 완료");

                        } catch (SQLException e) {

                            out.println("SERVER_ERROR");
                            System.out.println("AI 분석 결과 조회 DB 오류");
                        }

                    // 로그아웃 기능
                    } else if ("LOGOUT".equals(message)) {

                        String token = in.readLine();

                        Integer userId =
                                getAuthenticatedUserId(token);

                        if (userId == null) {

                            out.println("UNAUTHORIZED");

                        } else {

                            sessions.remove(token);

                            out.println("LOGOUT_SUCCESS");

                            System.out.println("로그아웃 성공");
                        }

                    // 알 수 없는 요청 처리
                    } else {

                        out.println("UNKNOWN_REQUEST");
                    }

                } catch (IOException e) {

                    System.out.println(
                        "클라이언트 처리 오류: " +
                        e.getMessage()
                    );
                }
            }

        } catch (IOException e) {

            System.out.println(
                "서버 실행 오류: " + e.getMessage()
            );
        }
    }
}