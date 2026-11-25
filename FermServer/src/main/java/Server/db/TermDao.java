package Server.db;

import Server.db.AnalysisResultDao.Term;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** terms 테이블 (팀 공용 용어 사전). */
public final class TermDao {

    /** terms.korean_term / english_term 의 최대 길이 (VARCHAR(100)). 넘는 용어는 저장하지 않는다. */
    public static final int MAX_LENGTH = 100;

    private TermDao() {
    }

    /**
     * 용어 사전 한 줄 (용어 사전 화면용, term_id 포함).
     * verified: 사람이 넣거나 확인한 용어면 true, 분석 중 자동으로 들어가 아직 확인 전이면 false.
     */
    public record Entry(int termId, String korean, String english, String explanation, boolean verified) {
    }

    /** PostgreSQL 에서 같은 값이 이미 있을 때(UNIQUE 위반) 오는 SQLState. */
    public static final String UNIQUE_VIOLATION = "23505";

    /** ILIKE 검색어: 사용자가 친 % _ \ 는 글자 그대로 찾도록 막고, 앞뒤에 % 를 붙인다. */
    static String likePattern(String query) {
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * 한국어 또는 영어에 query 가 들어간 용어 (대소문자 무시, 한국어 가나다순).
     * query 가 비어 있으면 전체. unverifiedOnly 면 확인 전(자동 추가) 용어만.
     */
    public static List<Entry> search(Connection conn, String query, boolean unverifiedOnly, int limit)
            throws SQLException {
        boolean filter = query != null && !query.isBlank();
        List<String> where = new ArrayList<>();
        if (filter) {
            where.add("(korean_term ILIKE ? ESCAPE '\\' OR english_term ILIKE ? ESCAPE '\\')");
        }
        if (unverifiedOnly) {
            where.add("NOT verified");
        }
        String sql = "SELECT term_id, korean_term, english_term, explanation, verified FROM terms "
                + (where.isEmpty() ? "" : "WHERE " + String.join(" AND ", where) + " ")
                + "ORDER BY korean_term LIMIT ?";
        List<Entry> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            if (filter) {
                String p = likePattern(query.strip());
                ps.setString(i++, p);
                ps.setString(i++, p);
            }
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Entry(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getBoolean(5)));
                }
            }
        }
        return rows;
    }

    /** 사람이 넣는 새 용어 (확인됨). term_id 를 돌려준다. 같은 한국어 용어가 있으면 SQLException(23505). */
    public static int insert(Connection conn, String korean, String english, String explanation)
            throws SQLException {
        String sql = "INSERT INTO terms (korean_term, english_term, explanation, verified) VALUES (?, ?, ?, true) "
                + "RETURNING term_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, korean);
            ps.setString(2, english);
            ps.setString(3, explanation);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * 용어를 고친다. 사람이 고친 것이므로 확인됨(verified)으로 바꾼다.
     * 없는 번호면 false. 다른 용어와 한국어가 겹치면 SQLException(23505).
     */
    public static boolean update(Connection conn, int termId, String korean, String english, String explanation)
            throws SQLException {
        String sql = "UPDATE terms SET korean_term = ?, english_term = ?, explanation = ?, verified = true "
                + "WHERE term_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, korean);
            ps.setString(2, english);
            ps.setString(3, explanation);
            ps.setInt(4, termId);
            return ps.executeUpdate() == 1;
        }
    }

    /** 자동으로 들어간 용어를 그대로 승인한다 (확인됨으로). 승인한 용어를 돌려주고, 없는 번호면 null. */
    public static Entry approve(Connection conn, int termId) throws SQLException {
        String sql = "UPDATE terms SET verified = true WHERE term_id = ? "
                + "RETURNING term_id, korean_term, english_term, explanation, verified";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, termId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? new Entry(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5))
                        : null;
            }
        }
    }

    /** 용어를 지운다. 분석 결과와의 연결(analysis_terms)도 함께 지워진다. 없는 번호면 false. */
    public static boolean delete(Connection conn, int termId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM terms WHERE term_id = ?")) {
            ps.setInt(1, termId);
            return ps.executeUpdate() == 1;
        }
    }

    /** 번역에 쓸 사전: 영어 번역이 적힌 용어 전체 (확인 전 용어도 포함해 바로 쓴다). */
    public static List<Term> loadAll(Connection conn) throws SQLException {
        String sql = "SELECT korean_term, english_term, explanation FROM terms "
                + "WHERE english_term IS NOT NULL AND english_term <> '' ORDER BY term_id";
        List<Term> terms = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                terms.add(new Term(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
        return terms;
    }

    /**
     * 용어들의 term_id 를 돌려준다. 사전에 없는 한국어 용어는 "확인 전(verified=false)" 으로 새로 넣고,
     * 이미 있으면 기존 값(영어·설명·확인 여부)을 그대로 둔다. 길이가 맞지 않는 용어는 건너뛴다.
     */
    public static List<Integer> ensureIds(Connection conn, List<Term> terms) throws SQLException {
        String insert = "INSERT INTO terms (korean_term, english_term, explanation, verified) VALUES (?, ?, ?, false) "
                + "ON CONFLICT (korean_term) DO NOTHING";
        String select = "SELECT term_id FROM terms WHERE korean_term = ?";
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ins = conn.prepareStatement(insert);
             PreparedStatement sel = conn.prepareStatement(select)) {
            for (Term t : terms) {
                String ko = t == null || t.korean() == null ? "" : t.korean().strip();
                String en = t == null || t.english() == null ? "" : t.english().strip();
                if (ko.isEmpty() || ko.length() > MAX_LENGTH || en.length() > MAX_LENGTH) {
                    continue;
                }
                ins.setString(1, ko);
                ins.setString(2, en.isEmpty() ? null : en);
                ins.setString(3, t.explanation() == null || t.explanation().isBlank() ? null : t.explanation().strip());
                ins.executeUpdate();
                sel.setString(1, ko);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next() && !ids.contains(rs.getInt(1))) {
                        ids.add(rs.getInt(1));
                    }
                }
            }
        }
        return ids;
    }
}
