package Server.extract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 자바 추출기 (EXTRACTOR_ENGINE=java). PDF 는 pdftotext/Windows OCR 을 쓰므로 Windows 에서만 돈다. */
class JavaExtractorTest {

    private static final Path SAMPLES = Path.of("src/test/resources/extract");

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    @Test
    void pdfTextLayer() throws Exception {
        assumeTrue(windows(), "PDF 추출은 Windows 에서만 확인");
        List<Item> items = new JavaExtractor().extract(SAMPLES.resolve("sample.pdf"));
        assertFalse(items.isEmpty());
        assertEquals(1, items.get(0).page());
        assertEquals("pdf", items.get(0).sourceFormat());
        assertTrue(items.stream().anyMatch(i -> i.content().contains("stack")), "글자 층의 문장을 읽는다");
    }

    @Test
    void pptxSlides() throws Exception {
        List<Item> items = new JavaExtractor().extract(SAMPLES.resolve("sample.pptx"));
        assertFalse(items.isEmpty());
        assertTrue(items.stream().allMatch(i -> i.page() >= 1 && !i.content().isBlank()));
        assertEquals("pptx", items.get(0).sourceFormat());
    }

    @Test
    void oldPptIsUnsupported(@TempDir Path tmp) throws Exception {
        Path ppt = Files.writeString(tmp.resolve("lecture.ppt"), "old");
        ExtractionException e = assertThrows(ExtractionException.class, () -> new JavaExtractor().extract(ppt));
        assertEquals("UnsupportedFormatError", e.getErrorType(), "사용자 파일 문제 → 400");
    }

    @Test
    void brokenPptx(@TempDir Path tmp) throws Exception {
        Path pptx = Files.writeString(tmp.resolve("broken.pptx"), "not a zip");
        ExtractionException e = assertThrows(ExtractionException.class, () -> new JavaExtractor().extract(pptx));
        assertEquals("CorruptedFileError", e.getErrorType());
    }
}
