package Server.extract;

import com.termtranslator.core.Models.Page;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 파이썬 extractor 대신 쓸 수 있는 자바 추출기 (term-translator 의 본문 추출 기능, com.termtranslator.extract).
 * .env 에 EXTRACTOR_ENGINE=java 로 고른다. 기본값은 파이썬 extractor ({@link ExtractorClient}).
 *
 * <p>Windows 11 스마트 앱 컨트롤처럼 서명이 없는 파이썬 파일(uv 가 받은 Python 등)을 막는 PC 에서
 * 파이썬 extractor 의 글자 인식(OCR)이 "애플리케이션 제어 정책에서 이 파일을 차단했습니다" 로 실패할 때 쓴다.
 *
 * <pre>
 * PDF  : 글자 층을 그대로 읽음 (pdftotext, Git for Windows 에 들어 있음)
 *        + 글자 층이 없는 페이지(스캔본, 그림 슬라이드)만 Windows 내장 OCR
 * PPTX : 슬라이드 XML 을 직접 읽음 (그림 속 글자는 읽지 않음 → PDF 로 저장해서 올리면 OCR 됨)
 * PPT  : 읽지 못함 (PPTX 나 PDF 로 저장해서 올릴 것)
 * </pre>
 * 결과는 페이지마다 빈 줄로 나눈 문단 하나가 Item 하나다. 위치(bbox)와 OCR 신뢰도는 없다.
 * Windows OCR 은 Windows 에서만 쓸 수 있다.
 */
public final class JavaExtractor implements TextExtractor {

    @Override
    public List<Item> extract(Path file) throws ExtractionException {
        String name = file.getFileName().toString();
        String tag = "[추출 " + name + "] ";
        List<Page> pages;
        try {
            pages = com.termtranslator.extract.TextExtractor.extract(file, line -> System.out.println(tag + line));
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.startsWith("글자를 찾지 못했습니다")) {
                return List.of();   // 파이썬 extractor 와 같이 빈 결과로 저장 (화면: "No text found in this file.")
            }
            String type = message.contains("파일이나 폴더가 없습니다") ? "InputNotFoundError" : "UnsupportedFormatError";
            throw new ExtractionException(type, message.replace("\n  ", " "), e);
        } catch (IOException e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            String type = message.startsWith("PPTX 파일을 열 수 없습니다") ? "CorruptedFileError" : "EXTRACTOR_FAILED";
            throw new ExtractionException(type, "텍스트 추출에 실패했습니다: " + message, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionException("TIMEOUT", "텍스트 추출이 취소되었습니다.", e);
        }

        String format = extension(name);
        List<Item> items = new ArrayList<>();
        for (Page page : pages) {
            for (String paragraph : page.text().split("\\n\\s*\\n")) {
                if (!paragraph.isBlank()) {
                    items.add(new Item(name, format, page.number(), Item.Origin.TEXT, paragraph.strip(), List.of(), null));
                }
            }
        }
        return items;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
