package Server.extract;

/**
 * 추출 실패. {@link #getErrorType()} 으로 원인을 구분해 사용자에게 알맞은 메시지를 보여 준다.
 *
 * <p>errorType 값
 * <ul>
 *   <li>extractor 가 알려 주는 오류: InputNotFoundError, UnsupportedFormatError,
 *       CorruptedFileError, EncryptedFileError, ConversionError</li>
 *   <li>자바 쪽에서 판단한 오류: UV_NOT_FOUND, TIMEOUT, EXTRACTOR_FAILED</li>
 * </ul>
 * getMessage() 는 사용자에게 그대로 보여 줘도 되는 한국어 문장이다.
 */
public class ExtractionException extends Exception {

    private final String errorType;

    public ExtractionException(String errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

    public ExtractionException(String errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }
}
