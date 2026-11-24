package com.termtranslator.extract;

import com.termtranslator.core.Models.Page;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 본문 추출: 강의자료 파일에서 페이지별 글자를 꺼낸다.
 *
 *   .pptx            슬라이드 XML 을 직접 읽음 (JDK 만 사용, 글자 그대로)          → PptxExtractor
 *   .pdf             글자 층이 있으면 그대로 읽고, 없는 페이지는 글자 인식(OCR)    → PdfExtractor
 *   .png .jpg 등     글자 인식(OCR). Windows 에 들어 있는 기능을 사용              → WindowsOcr
 *   .txt             그대로 읽음 (--- 만 있는 줄에서 페이지를 나눔)
 *   폴더             .txt 가 있으면 페이지별 텍스트 폴더, 아니면 그림 파일을 이름순으로 한 장씩
 *
 * 글자가 하나도 없는 페이지는 결과에서 빼고, 페이지 번호는 원래 번호를 유지한다.
 */
public final class TextExtractor {

    /** 본문에서 페이지를 나누는 줄: --- 만 있는 줄 */
    public static final Pattern PAGE_BREAK = Pattern.compile("(?m)^[ \\t]*-{3,}[ \\t]*$");
    private static final String PAGE_JOIN = "\n\n---\n\n";

    public static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "bmp", "gif", "tif", "tiff");
    private static final Set<String> IMAGES = Set.copyOf(IMAGE_EXTENSIONS);

    private TextExtractor() {}

    /** 파일 또는 폴더 하나에서 페이지를 꺼낸다. log 는 진행 상황을 한 줄씩 받는다. */
    public static List<Page> extract(Path input, Consumer<String> log) throws IOException, InterruptedException {
        if (Files.isDirectory(input)) return fromFolder(input, log);
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("파일이나 폴더가 없습니다: " + input.toAbsolutePath()
                    + "\n  VS Code 에서는 프로젝트 폴더(term-translator)를 열고 실행해야 합니다.");
        }
        String ext = extension(input);
        List<Page> pages;
        switch (ext) {
            case "txt" -> pages = splitPages(Files.readString(input, StandardCharsets.UTF_8));
            case "pptx" -> pages = number(PptxExtractor.extract(input, log));
            case "pdf" -> pages = number(PdfExtractor.extract(input, log));
            case "ppt" -> throw new IllegalArgumentException("옛 형식(.ppt)은 읽을 수 없습니다: " + input.getFileName()
                    + "\n  PowerPoint 에서 [다른 이름으로 저장]으로 .pptx 나 .pdf 로 바꾼 뒤 다시 불러오세요.");
            default -> {
                if (!IMAGES.contains(ext)) {
                    throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + input.getFileName()
                            + "\n  지원 형식: pdf, pptx, " + String.join(", ", IMAGE_EXTENSIONS) + ", txt");
                }
                pages = number(WindowsOcr.images(List.of(input), log));
            }
        }
        return dropBlank(pages, input.getFileName().toString(), log);
    }

    /** 파일 여러 개를 순서대로 이어 붙인다. 페이지 번호는 1 부터 다시 매긴다. */
    public static List<Page> extractAll(List<Path> inputs, Consumer<String> log) throws IOException, InterruptedException {
        List<Page> all = new ArrayList<>();
        for (Path input : inputs) {
            for (Page p : extract(input, log)) all.add(new Page(all.size() + 1, p.text()));
        }
        return all;
    }

    private static List<Page> fromFolder(Path dir, Consumer<String> log) throws IOException, InterruptedException {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(Files::isRegularFile).sorted(Comparator.comparing(p -> p.getFileName().toString(), TextExtractor::naturalCompare)).toList();
        }
        if (files.stream().anyMatch(f -> extension(f).equals("txt"))) return loadTextFolder(dir);
        List<Path> images = files.stream().filter(f -> IMAGES.contains(extension(f))).toList();
        if (images.isEmpty()) throw new IllegalArgumentException("폴더에 .txt 나 그림 파일이 없습니다: " + dir);
        return dropBlank(number(WindowsOcr.images(images, log)), dir.getFileName().toString(), log);
    }

    /** 페이지마다 .txt 하나인 폴더. 파일명의 숫자가 페이지 번호다 (page_01.txt ...). */
    public static List<Page> loadTextFolder(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("페이지 폴더가 없습니다: " + dir.toAbsolutePath()
                    + "\n  VS Code 에서는 프로젝트 폴더(term-translator)를 열고 실행해야 합니다.");
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        }
        if (files.isEmpty()) throw new IllegalArgumentException("페이지 폴더에 .txt 파일이 없습니다: " + dir);
        Pattern digits = Pattern.compile("(\\d+)");
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            Path f = files.get(i);
            Matcher m = digits.matcher(f.getFileName().toString());
            int number = m.find() ? Integer.parseInt(m.group(1)) : i + 1;
            pages.add(new Page(number, Files.readString(f, StandardCharsets.UTF_8)));
        }
        pages.sort((a, b) -> Integer.compare(a.number(), b.number()));
        return pages;
    }

    /** --- 만 있는 줄에서 본문을 페이지로 나눈다. 빈 조각은 버리고 1 부터 번호를 매긴다. */
    public static List<Page> splitPages(String text) {
        List<Page> pages = new ArrayList<>();
        for (String part : PAGE_BREAK.split(text == null ? "" : text.replace("\r\n", "\n"))) {
            if (!part.isBlank()) pages.add(new Page(pages.size() + 1, part.strip()));
        }
        return pages;
    }

    /** splitPages 의 반대: 페이지들을 --- 줄로 이어 붙인다 (화면의 본문 칸에 넣을 때 사용). */
    public static String joinPages(List<Page> pages) {
        List<String> texts = new ArrayList<>();
        // 본문 안에 우연히 --- 줄이 있으면 페이지가 잘못 나뉘므로 모양만 바꿔 둔다
        for (Page p : pages) texts.add(PAGE_BREAK.matcher(p.text().strip()).replaceAll("- - -"));
        return String.join(PAGE_JOIN, texts);
    }

    /** 추출 결과를 page_01.txt, page_02.txt ... 로 저장한다. 전에 추출해 둔 page_*.txt 는 지운다. */
    public static void writePages(Path dir, List<Page> pages) throws IOException {
        Files.createDirectories(dir);
        try (Stream<Path> s = Files.list(dir)) {
            for (Path old : s.filter(p -> p.getFileName().toString().matches("page_\\d+\\.txt")).toList()) Files.delete(old);
        }
        int width = Math.max(2, String.valueOf(pages.stream().mapToInt(Page::number).max().orElse(1)).length());
        for (Page p : pages) {
            Files.writeString(dir.resolve(String.format("page_%0" + width + "d.txt", p.number())), p.text() + "\n", StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ 도우미

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static List<Page> number(List<String> texts) {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) pages.add(new Page(i + 1, clean(texts.get(i))));
        return pages;
    }

    private static List<Page> dropBlank(List<Page> pages, String name, Consumer<String> log) {
        List<Page> kept = pages.stream().filter(p -> !p.text().isBlank()).toList();
        List<Integer> skipped = pages.stream().filter(p -> p.text().isBlank()).map(Page::number).toList();
        if (!skipped.isEmpty()) log.accept("글자가 없어 건너뛴 페이지: " + skipped);
        if (kept.isEmpty()) throw new IllegalArgumentException("글자를 찾지 못했습니다: " + name);
        log.accept(name + ": " + kept.size() + "페이지 추출");
        return kept;
    }

    /** 줄 끝 공백 제거, 줄바꿈 통일, 빈 줄이 3줄 이상 이어지면 1줄로 줄임 */
    static String clean(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ')
                .replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }

    /** 이름 속 숫자를 숫자로 비교: slide2 가 slide10 보다 앞에 온다. */
    static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=\\d)", ""), nb = b.substring(sj, j).replaceFirst("^0+(?=\\d)", "");
                int c = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (c != 0) return c;
            } else {
                int c = Character.compare(Character.toLowerCase(x), Character.toLowerCase(y));
                if (c != 0) return c;
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
