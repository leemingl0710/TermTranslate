package Server.service;

import Server.extract.ExtractionException;

import java.nio.file.Path;
import java.sql.SQLException;
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
}
