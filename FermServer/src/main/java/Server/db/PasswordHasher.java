package Server.db;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 비밀번호 해시 (PBKDF2-HMAC-SHA256, 자바 기본 기능만 사용).
 *
 * <p>저장 형식 (users.password_hash 한 칸에 모두 들어감, 약 90자):
 * <pre>pbkdf2_sha256$반복횟수$솔트(Base64)$해시(Base64)</pre>
 * 같은 비밀번호도 솔트가 매번 달라 저장값이 달라진다. 원문 비밀번호는 저장하지 않는다.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "pbkdf2_sha256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return ALGORITHM + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    /** 비밀번호가 저장된 해시와 맞는지 확인한다. 형식이 잘못된 값이면 false. */
    public static boolean verify(String password, String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals(ALGORITHM)) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            // 비교 시간이 내용에 따라 달라지지 않게 MessageDigest.isEqual 사용
            return MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 를 사용할 수 없습니다", e);
        } finally {
            spec.clearPassword();
        }
    }
}
