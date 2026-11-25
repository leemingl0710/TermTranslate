package Server.service;

import Server.extract.ExtractionException;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** HTTP 요청이 부르는 서버 기능. 테스트에서는 가짜 구현으로 바꿔 끼운다. */
public interface AnalysisService {

    /**
     * 업로드된 파일을 분석해 저장한다.
     *
     * @param originalName 사용자가 올린 파일 이름 (documents.file_name)
     * @param uploaded     서버가 임시로 받아 둔 파일. 이 메서드가 옮기거나 지운다.
     */
    UploadResult analyze(String originalName, Path uploaded) throws ExtractionException, SQLException;

    Optional<ResultDetail> getResult(int resultId) throws SQLException;

    /**
     * 업로드 사용자의 분석 결과 목록 (최근 것부터).
     *
     * @param fileQuery 파일 이름에 들어간 글자 (대소문자 무시). null·빈 문자열이면 전체
     */
    List<ResultSummary> listResults(String fileQuery) throws SQLException;

    /**
     * 분석 결과와 그 문서(업로드한 파일 포함)를 지운다. 용어 사전은 그대로 둔다.
     *
     * @return 지웠으면 true, 없는 결과(또는 다른 사용자의 결과)면 false
     */
    boolean deleteResult(int resultId) throws SQLException;
}
