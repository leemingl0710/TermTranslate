package Server.extract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JSON 읽기 테스트. uv/파이썬 없이 돈다. */
class ExtractorClientParseTest {

    @Test
    void parsesItems() throws Exception {
        String json = """
                [
                  {"source_file": "a.pdf", "source_format": "pdf", "page": 1, "origin": "text",
                   "content": "자료구조란 데이터를 저장하는 방법이다.",
                   "bbox": [72.0, 140.5, 520.3, 168.2], "confidence": null},
                  {"source_file": "a.pdf", "source_format": "pdf", "page": 2, "origin": "ocr",
                   "content": "그림 속 글자", "bbox": [1, 2, 3, 4], "confidence": 0.93,
                   "flags": ["low_confidence"], "future_field": 123}
                ]""";
        List<Item> items = ExtractorClient.parseItems(json);

        assertEquals(2, items.size());
        Item text = items.get(0);
        assertEquals("a.pdf", text.sourceFile());
        assertEquals(1, text.page());
        assertEquals(Item.Origin.TEXT, text.origin());
        assertEquals("자료구조란 데이터를 저장하는 방법이다.", text.content());
        assertEquals(List.of(72.0, 140.5, 520.3, 168.2), text.bbox());
        assertNull(text.confidence());
        assertEquals(List.of(), text.flags());            // flags 가 없으면 빈 목록

        Item ocr = items.get(1);   // 모르는 필드(future_field)가 있어도 읽힌다
        assertEquals(Item.Origin.OCR, ocr.origin());
        assertEquals(0.93, ocr.confidence());
        assertEquals(List.of("low_confidence"), ocr.flags());
        assertTrue(ocr.hasFlag(Item.LOW_CONFIDENCE));
    }

    @Test
    void parsesCodeItem() throws Exception {
        String json = """
                [{"source_file": "a.pdf", "source_format": "pdf", "page": 3, "origin": "ocr",
                  "content": "01: import java.io.File;\\n02: public class A {",
                  "bbox": [1, 2, 3, 4], "confidence": 0.98, "flags": ["code"]}]""";
        Item code = ExtractorClient.parseItems(json).get(0);
        assertTrue(code.hasFlag(Item.CODE));
        assertEquals(List.of("01: import java.io.File;", "02: public class A {"),
                code.content().lines().toList());   // 줄바꿈이 유지된다
    }

    @Test
    void parsesEmptyList() throws Exception {
        assertTrue(ExtractorClient.parseItems("[]").isEmpty());
    }

    @Test
    void parsesErrorLineAmongLogs() {
        String stderr = """
                [INFO] some log line
                {"error": "EncryptedFileError", "message": "암호가 걸린 PDF입니다: a.pdf"}
                """;
        ExtractionException e = ExtractorClient.parseError(stderr);
        assertEquals("EncryptedFileError", e.getErrorType());
        assertEquals("암호가 걸린 PDF입니다: a.pdf", e.getMessage());
    }

    @Test
    void unknownStderrBecomesExtractorFailed() {
        ExtractionException e = ExtractorClient.parseError("Traceback ...\nValueError: boom");
        assertEquals("EXTRACTOR_FAILED", e.getErrorType());
        assertTrue(e.getMessage().contains("ValueError: boom"));
    }
}
