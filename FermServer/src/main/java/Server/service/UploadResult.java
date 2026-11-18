package Server.service;

/** 업로드·분석이 끝난 뒤 클라이언트에 돌려주는 값. */
public record UploadResult(int resultId, int documentId, String fileName, int itemCount) {
}
