package Server.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void verifiesCorrectPassword() {
        String stored = PasswordHasher.hash("1234");
        assertTrue(stored.startsWith("pbkdf2_sha256$210000$"));
        assertTrue(stored.length() <= 255, "password_hash 칸(255자)에 들어가야 함");
        assertTrue(PasswordHasher.verify("1234", stored));
    }

    @Test
    void rejectsWrongPassword() {
        assertFalse(PasswordHasher.verify("12345", PasswordHasher.hash("1234")));
    }

    @Test
    void samePasswordDifferentHash() {
        assertNotEquals(PasswordHasher.hash("1234"), PasswordHasher.hash("1234"));
    }

    @Test
    void rejectsMalformed() {
        assertFalse(PasswordHasher.verify("1234", null));
        assertFalse(PasswordHasher.verify("1234", "1234"));
        assertFalse(PasswordHasher.verify("1234", "pbkdf2_sha256$x$y$z"));
    }
}
