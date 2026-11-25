
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;

public class AuthService {

    private static final int ITERATIONS = 600000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    // 회원가입: 비밀번호를 해시로 변환
    public static String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);

            byte[] hash = pbkdf2(
                password.toCharArray(), salt,
                ITERATIONS, KEY_LENGTH);

            return "pbkdf2_sha256$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);

        } catch (Exception e) {
            throw new IllegalStateException(
                "비밀번호 암호화 처리 실패", e);
        }
    }

    // 로그인: 입력 비밀번호와 저장된 해시 비교
    public static boolean verifyPassword(
            String password, String savedHash) {

        if (password == null || savedHash == null) {
            return false;
        }

        try {
            String[] parts = savedHash.split("\\$");

            if (parts.length != 4 ||
                    !parts[0].equals("pbkdf2_sha256")) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);

            if (iterations < 10000 || iterations > 1000000) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);

            if (salt.length != 16 || expected.length != 32) {
                return false;
            }

            byte[] actual = pbkdf2(
                password.toCharArray(), salt,
                iterations, expected.length * 8);

            return MessageDigest.isEqual(expected, actual);

        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pbkdf2(
            char[] password, byte[] salt,
            int iterations, int keyLength)
            throws Exception {

        PBEKeySpec spec = new PBEKeySpec(
            password, salt, iterations, keyLength);

        try {
            return SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
