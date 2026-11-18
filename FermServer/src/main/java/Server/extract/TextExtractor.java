package Server.extract;

import java.nio.file.Path;
import java.util.List;

/** 파일 → 추출 항목. 실제 구현은 {@link ExtractorClient}, 테스트에서는 가짜로 바꿔 끼운다. */
@FunctionalInterface
public interface TextExtractor {
    List<Item> extract(Path file) throws ExtractionException;
}
