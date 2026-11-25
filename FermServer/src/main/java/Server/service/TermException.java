package Server.service;

/**
 * 용어 저장 요청이 잘못된 경우. 서버는 errorType 으로 HTTP 상태를 정한다.
 * <ul>
 *   <li>BAD_REQUEST (400) — 한국어 용어가 비었거나 너무 김</li>
 *   <li>DUPLICATE_TERM (409) — 같은 한국어 용어가 이미 있음</li>
 * </ul>
 */
public final class TermException extends Exception {

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String DUPLICATE_TERM = "DUPLICATE_TERM";

    private final String errorType;

    public TermException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
