package Server.service;

import Server.db.TermDao;
import Server.service.DefaultAnalysisService.ConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 용어 사전 관리 (Neon terms 테이블).
 * 여기서 넣은 용어는 다음 분석부터 번역기(LlmTranslator)의 사전으로 쓰인다.
 *
 * <p>규칙: 한국어 용어는 꼭 있어야 하고 100자 이하, 영어도 100자 이하 (DB 칸 크기).
 * 같은 한국어 용어는 하나만 (DB UNIQUE). 앞뒤 공백은 지우고, 빈 영어·설명은 null 로 저장한다.
 */
public final class DefaultTermService implements TermService {

    /** 한 번에 돌려주는 최대 개수. */
    static final int MAX_ROWS = 500;

    private final ConnectionFactory db;

    public DefaultTermService(ConnectionFactory db) {
        this.db = db;
    }

    @Override
    public List<TermEntry> search(String query) throws SQLException {
        try (Connection conn = db.open()) {
            return TermDao.search(conn, query, MAX_ROWS).stream().map(DefaultTermService::toEntry).toList();
        }
    }

    @Override
    public TermEntry add(String korean, String english, String explanation) throws SQLException, TermException {
        Clean c = clean(korean, english, explanation);
        try (Connection conn = db.open()) {
            int id = TermDao.insert(conn, c.korean, c.english, c.explanation);
            return new TermEntry(id, c.korean, c.english, c.explanation);
        } catch (SQLException e) {
            throw duplicateOr(e, c.korean);
        }
    }

    @Override
    public Optional<TermEntry> update(int termId, String korean, String english, String explanation)
            throws SQLException, TermException {
        Clean c = clean(korean, english, explanation);
        try (Connection conn = db.open()) {
            if (!TermDao.update(conn, termId, c.korean, c.english, c.explanation)) {
                return Optional.empty();
            }
            return Optional.of(new TermEntry(termId, c.korean, c.english, c.explanation));
        } catch (SQLException e) {
            throw duplicateOr(e, c.korean);
        }
    }

    @Override
    public boolean delete(int termId) throws SQLException {
        try (Connection conn = db.open()) {
            return TermDao.delete(conn, termId);
        }
    }

    // ---------------- 도우미 ----------------

    record Clean(String korean, String english, String explanation) {
    }

    /** 입력을 다듬고 검사한다. DB 를 열기 전에 부른다. */
    static Clean clean(String korean, String english, String explanation) throws TermException {
        String ko = korean == null ? "" : korean.strip();
        String en = english == null ? "" : english.strip();
        String ex = explanation == null ? "" : explanation.strip();
        if (ko.isEmpty()) {
            throw new TermException(TermException.BAD_REQUEST, "한국어 용어를 입력하세요.");
        }
        if (ko.length() > TermDao.MAX_LENGTH) {
            throw new TermException(TermException.BAD_REQUEST,
                    "한국어 용어는 " + TermDao.MAX_LENGTH + "자 이하로 입력하세요.");
        }
        if (en.length() > TermDao.MAX_LENGTH) {
            throw new TermException(TermException.BAD_REQUEST,
                    "영어 용어는 " + TermDao.MAX_LENGTH + "자 이하로 입력하세요.");
        }
        return new Clean(ko, en.isEmpty() ? null : en, ex.isEmpty() ? null : ex);
    }

    /** 같은 한국어 용어가 이미 있어서 난 오류면 TermException 으로 바꾼다. 그 밖의 DB 오류는 그대로. */
    private static SQLException duplicateOr(SQLException e, String korean) throws TermException {
        if (TermDao.UNIQUE_VIOLATION.equals(e.getSQLState())) {
            throw new TermException(TermException.DUPLICATE_TERM, "이미 있는 용어입니다: " + korean);
        }
        return e;
    }

    private static TermEntry toEntry(TermDao.Entry e) {
        return new TermEntry(e.termId(), e.korean(), e.english(), e.explanation());
    }
}
