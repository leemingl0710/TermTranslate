package ui.platform;

import java.util.Set;

/**
 * 업로드 실패를 오류 코드로 나눠, 다시 시도하면 될지와 사용자가 할 일을 알려 준다.
 *
 * <p>파일 자체가 문제인 경우(손상, 암호, 형식, 크기)는 다시 보내도 똑같이 실패하므로 다시 시도하지 않는다.
 * 그 밖의 오류(서버 꺼짐, 통신 오류, 서버 내부 오류, 모르는 오류)는 다시 시도할 수 있다.
 */
final class FailureAdvice {

    /** 서버(ApiServer, ApiClient)가 보내는 코드 중 파일을 바꿔야 하는 것 */
    private static final Set<String> FILE_PROBLEMS = Set.of(
            "UnsupportedFormatError", "CorruptedFileError", "EncryptedFileError",
            "FILE_TOO_LARGE", "FILE_READ_ERROR", "BAD_REQUEST");

    /** 연결 문제: 서버를 확인하라고 안내 */
    private static final Set<String> CONNECTION_PROBLEMS = Set.of(
            "SERVER_UNAVAILABLE", "NETWORK_ERROR", "CANCELLED");

    private FailureAdvice() {
    }

    /** 같은 파일을 다시 보내면 성공할 수도 있는가 */
    static boolean retryable(String errorCode) {
        return errorCode == null || !FILE_PROBLEMS.contains(errorCode);
    }

    /** 사용자가 할 일 (목록과 끝 안내 창에 붙임) */
    static String hint(String errorCode) {
        if (errorCode == null) {
            return "Retry Failed 로 다시 시도할 수 있어요";
        }
        if (CONNECTION_PROBLEMS.contains(errorCode)) {
            return "서버를 확인한 뒤 Retry Failed 를 누르세요";
        }
        return switch (errorCode) {
            case "ConversionError" -> "잠시 후 Retry Failed 로 다시 시도하세요";
            case "CorruptedFileError" -> "파일을 다시 저장하거나 PDF 로 바꿔서 올리세요";
            case "EncryptedFileError" -> "암호를 풀고 다시 올리세요";
            case "UnsupportedFormatError" -> "PDF·PPT·PPTX·PNG·JPG 만 올릴 수 있어요";
            case "FILE_TOO_LARGE" -> "파일을 나눠서 올리세요";
            case "FILE_READ_ERROR" -> "파일이 있는지 확인하고 다시 선택하세요";
            case "BAD_REQUEST" -> "파일 내용을 확인하고 다시 선택하세요";
            default -> "Retry Failed 로 다시 시도할 수 있어요";
        };
    }
}
