package Server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 용어 입력 검사. 잘못된 입력은 DB 를 열기 전에 막는다 (DB 없이 돈다). */
class DefaultTermServiceTest {

    /** DB 를 열면 실패하는 서비스: 검사에서 걸러져야 하는 경우에 쓴다. */
    private final DefaultTermService noDb = new DefaultTermService(() -> {
        throw new AssertionError("잘못된 입력이면 DB 를 열면 안 됨");
    });

    @Test
    void cleansInput() throws Exception {
        DefaultTermService.Clean c = DefaultTermService.clean("  응력 ", " stress ", "  ");
        assertEquals("응력", c.korean());
        assertEquals("stress", c.english());
        assertNull(c.explanation(), "빈 설명은 null 로 저장");
        assertNull(DefaultTermService.clean("응력", "", null).english(), "빈 영어는 null 로 저장");
    }

    @Test
    void koreanIsRequired() {
        TermException e = assertThrows(TermException.class, () -> noDb.add("  ", "stress", null));
        assertEquals(TermException.BAD_REQUEST, e.getErrorType());
        assertThrows(TermException.class, () -> noDb.add(null, null, null));
        assertThrows(TermException.class, () -> noDb.update(1, "", "x", null));
    }

    @Test
    void lengthLimits() {
        String tooLong = "가".repeat(101);
        assertEquals(TermException.BAD_REQUEST,
                assertThrows(TermException.class, () -> noDb.add(tooLong, null, null)).getErrorType());
        assertThrows(TermException.class, () -> noDb.add("응력", "s".repeat(101), null));
        // 100자는 통과 (검사만 확인)
        assertEquals(100, assertDoesNotThrowClean("가".repeat(100)).korean().length());
    }

    private static DefaultTermService.Clean assertDoesNotThrowClean(String korean) {
        try {
            return DefaultTermService.clean(korean, null, null);
        } catch (TermException e) {
            throw new AssertionError(e);
        }
    }
}
