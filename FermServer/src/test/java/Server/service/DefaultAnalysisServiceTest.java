package Server.service;

import Server.extract.ExtractionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 실패했을 때 DB·파일에 아무것도 남지 않는지 확인 (DB 없이 돈다). */
class DefaultAnalysisServiceTest {

    @Test
    void extractionFailureLeavesNothing(@TempDir Path tmp) throws Exception {
        Path uploads = tmp.resolve("uploads");
        AtomicBoolean dbTouched = new AtomicBoolean(false);
        Path[] seen = new Path[1];
        DefaultAnalysisService service = new DefaultAnalysisService(
                file -> {
                    seen[0] = file;
                    throw new ExtractionException("EncryptedFileError", "암호가 걸린 PDF입니다: " + file.getFileName());
                },
                () -> {
                    dbTouched.set(true);
                    throw new AssertionError("추출이 실패하면 DB 를 열면 안 됨");
                },
                uploads, "admin");

        Path upload = Files.writeString(tmp.resolve("part"), "%PDF");
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> service.analyze("강의 1장.pdf", upload));

        assertEquals("강의 1장.pdf", seen[0].getFileName().toString(), "원래 이름으로 저장해 추출");
        assertTrue(e.getMessage().endsWith("강의 1장.pdf"));
        assertFalse(dbTouched.get());
        try (Stream<Path> left = Files.list(uploads)) {
            assertEquals(0, left.count(), "저장했던 파일·폴더가 지워져야 함");
        }
    }

    @Test
    void pathCharactersStayInsideUploadDir(@TempDir Path tmp) throws Exception {
        Path uploads = tmp.resolve("uploads");
        Path[] seen = new Path[1];
        DefaultAnalysisService service = new DefaultAnalysisService(
                file -> {
                    seen[0] = file;
                    throw new ExtractionException("CorruptedFileError", "x");
                },
                () -> null, uploads, "admin");

        Path upload = Files.writeString(tmp.resolve("part"), "%PDF");
        assertThrows(ExtractionException.class, () -> service.analyze("../../etc/evil.pdf", upload));
        assertTrue(seen[0].toAbsolutePath().normalize().startsWith(uploads.toAbsolutePath().normalize()));
        assertEquals("upload.._.._etc_evil.pdf", seen[0].getFileName().toString());
    }
}
