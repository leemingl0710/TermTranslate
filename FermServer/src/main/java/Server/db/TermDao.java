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

    /** 번역에 쓸 사전: 영어 번역이 적힌 용어 전체. */
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
     * 용어들의 term_id 를 돌려준다. 사전에 없는 한국어 용어는 새로 넣고,
     * 이미 있으면 기존 값(영어·설명)을 그대로 둔다. 길이가 맞지 않는 용어는 건너뛴다.
     */
    public static List<Integer> ensureIds(Connection conn, List<Term> terms) throws SQLException {
        String insert = "INSERT INTO terms (korean_term, english_term, explanation) VALUES (?, ?, ?) "
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
