package Server.db;

import Server.db.AnalysisResultDao.Term;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 용어를 DB 에 넣는 순서: 동시 저장에서 교착 상태가 생기지 않게 항상 가나다순 (DB 없이 돈다). */
class TermDaoOrderTest {

    private static List<String> korean(List<Term> terms) {
        return terms.stream().map(Term::korean).toList();
    }

    @Test
    void sameTermsGiveSameOrderWhateverTheInputOrder() {
        List<Term> a = List.of(new Term("스트림", "stream", null), new Term("패키지", "package", null),
                new Term("직렬화", "serialization", null));
        List<Term> b = List.of(new Term("패키지", "package", null), new Term("직렬화", "serialization", null),
                new Term("스트림", "stream", null));
        assertEquals(List.of("스트림", "직렬화", "패키지"), korean(TermDao.lockOrder(a)));
        assertEquals(korean(TermDao.lockOrder(a)), korean(TermDao.lockOrder(b)), "두 업로드가 같은 순서로 넣음");
    }

    @Test
    void duplicatesAndInvalidTermsAreDropped() {
        List<Term> in = Arrays.asList(
                new Term("스트림", "stream", "처음 설명"),
                null,
                new Term("  ", "blank", null),
                new Term("스트림 ", "stream", "두 번째 설명"),
                new Term("가".repeat(TermDao.MAX_LENGTH + 1), "too long", null),
                new Term("버퍼", "b".repeat(TermDao.MAX_LENGTH + 1), null));
        List<Term> out = TermDao.lockOrder(in);
        assertEquals(List.of("스트림"), korean(out));
        assertEquals("처음 설명", out.get(0).explanation(), "같은 용어는 처음 것을 넣음 (예전과 같음)");
        assertEquals(List.of(), TermDao.lockOrder(null));
    }
}
