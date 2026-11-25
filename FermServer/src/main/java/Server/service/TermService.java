package Server.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** 팀 공용 용어 사전(terms) 관리. 테스트에서는 가짜 구현으로 바꿔 끼운다. */
public interface TermService {

    /** 한국어·영어에 query 가 들어간 용어. query 가 비어 있으면 전체 (최대 개수 제한). */
    List<TermEntry> search(String query) throws SQLException;

    /** 새 용어를 넣고 저장된 값을 돌려준다. */
    TermEntry add(String korean, String english, String explanation) throws SQLException, TermException;

    /** 용어를 고친다. 없는 번호면 empty. */
    Optional<TermEntry> update(int termId, String korean, String english, String explanation)
            throws SQLException, TermException;

    /** 용어를 지운다. 없는 번호면 false. */
    boolean delete(int termId) throws SQLException;
}
