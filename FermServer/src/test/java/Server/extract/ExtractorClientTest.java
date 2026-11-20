package Server.extract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제로 extractor 를 실행하는 테스트.
 * uv 와 extractor 환경(extractor/setup 스크립트)이 없으면 건너뛴다.
 */
class ExtractorClientTest {

    private static ExtractorClient client;

    @BeforeAll
    static void setUp() throws Exception {
        client = ExtractorClient.fromEnv();
        boolean ready;
        try {
            Process p = new ProcessBuilder(client.uvPath(), "--version").start();
            ready = p.waitFor() == 0;
        } catch (Exception e) {
            ready = false;
        }
        assumeTrue(ready, "uv 가 없어 extractor 연동 테스트를 건너뜁니다. extractor/setup 스크립트를 실행하세요.");
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(ExtractorClientTest.class.getResource("/extract/" + name).toURI());
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        List<Item> items = client.extract(resource("sample.pdf"));

        assertEquals(3, items.size());
        Item title = items.get(0);
        assertEquals("sample.pdf", title.sourceFile());
        assertEquals("pdf", title.sourceFormat());
        assertEquals(1, title.page());
        assertEquals(Item.Origin.TEXT, title.origin());
        assertEquals("1장. 자료구조 개요", title.content());
        assertEquals(4, title.bbox().size());
        assertNull(title.confidence());

        assertEquals("자료구조는 데이터를 효율적으로 저장하고 관리하기 위한 방법이다.", items.get(1).content());
        assertEquals(2, items.get(2).page());
        assertEquals("A stack is a LIFO structure.", items.get(2).content());
    }

    @Test
    void extractsTextFromPptx() throws Exception {
        List<Item> items;
        try {
            items = client.extract(resource("sample.pptx"));
        } catch (ExtractionException e) {
            // PPT 변환에는 LibreOffice 가 필요하다. 없는 PC 에서는 건너뛴다.
            assumeFalse(e.getMessage().contains("LibreOffice 가 없어"), e.getMessage());
            throw e;
        }

        assertEquals(4, items.size());
        assertEquals("sample.pptx", items.get(0).sourceFile());
        assertEquals("pptx", items.get(0).sourceFormat());
        assertEquals(List.of(1, 1, 2, 2), items.stream().map(Item::page).toList());   // 슬라이드 번호
        assertEquals(List.of("1장. 자료구조 개요", "스택은 후입선출 구조이다", "2장. 큐", "큐는 선입선출 구조이다"),
                items.stream().map(Item::content).toList());
    }

    @Test
    void encryptedPdf() {
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> client.extract(resource("encrypted.pdf")));
        assertEquals("EncryptedFileError", e.getErrorType());
        assertTrue(e.getMessage().contains("암호"), e.getMessage());
    }

    @Test
    void corruptedPdf() {
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> client.extract(resource("corrupted.pdf")));
        assertEquals("CorruptedFileError", e.getErrorType());
    }

    @Test
    void missingFile(@TempDir Path tmp) {
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> client.extract(tmp.resolve("없는파일.pdf")));
        assertEquals("InputNotFoundError", e.getErrorType());
    }

    @Test
    void wrongExtractorDir(@TempDir Path tmp) {
        ExtractorClient bad = new ExtractorClient(tmp, "uv", Duration.ofSeconds(10));
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> bad.extract(Path.of("a.pdf")));
        assertEquals("EXTRACTOR_NOT_FOUND", e.getErrorType());
    }

    @Test
    void uvNotFound() throws Exception {
        ExtractorClient bad = new ExtractorClient(Path.of("../extractor"), "/no/such/uv", Duration.ofSeconds(10));
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> bad.extract(resource("sample.pdf")));
        assertEquals("UV_NOT_FOUND", e.getErrorType());
        assertFalse(e.getMessage().isBlank());
    }
}
