package com.termtranslator.extract;

import com.termtranslator.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * PDF 에서 페이지별 글자를 꺼낸다. 두 가지 방법을 섞어 쓴다.
 *
 *   1) 글자 층 읽기: PDF 안에 들어 있는 글자를 그대로 꺼낸다 (pdftotext 프로그램 사용).
 *      수식 기호나 영문 식별자까지 정확하다. pdftotext 는 Git for Windows 에 들어 있어서
 *      Git 이 깔려 있으면 자동으로 찾는다. 다른 위치면 .env 에 PDFTOTEXT_PATH 로 알려 준다.
 *   2) 글자 인식(OCR): 글자 층이 없거나(스캔본, 그림 슬라이드) 깨진 페이지만 그림으로 그려서 읽는다.
 *      pdftotext 가 없으면 모든 페이지를 이 방법으로 읽는다.
 *
 * .env 에 PDF_FORCE_OCR=true 를 넣으면 글자 층을 무시하고 전부 OCR 로 읽는다
 * (글자 층이 깨져서 이상한 글자가 나오는 PDF 용).
 */
final class PdfExtractor {

    private PdfExtractor() {}

    static List<String> extract(Path pdf, Consumer<String> log) throws IOException, InterruptedException {
        boolean forceOcr = "true".equalsIgnoreCase(Config.get("PDF_FORCE_OCR"));
        List<String> texts = forceOcr ? null : textLayer(pdf, log);

        if (texts == null) {
            if (!WindowsOcr.available()) {
                throw new IOException("PDF 글자를 읽을 방법이 없습니다. pdftotext(xpdf 또는 poppler)를 설치하고 "
                        + ".env 의 PDFTOTEXT_PATH 에 경로를 적거나, Windows 에서 실행하세요.");
            }
            log.accept(forceOcr ? "PDF: 모든 페이지를 글자 인식(OCR)으로 읽습니다 (PDF_FORCE_OCR)"
                    : "PDF: pdftotext 를 찾지 못해 모든 페이지를 글자 인식(OCR)으로 읽습니다");
            Map<Integer, String> ocr = WindowsOcr.pdf(pdf, List.of(), log);
            int count = ocr.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= count; i++) out.add(ocr.getOrDefault(i, ""));
            return out;
        }

        List<Integer> poor = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (isPoor(texts.get(i))) poor.add(i + 1);
        }
        if (poor.isEmpty()) {
            log.accept("PDF " + texts.size() + "페이지: 모두 글자 층에서 읽음");
            return texts;
        }
        if (!WindowsOcr.available()) {
            log.accept("[경고] 글자 층이 없는 페이지 " + poor + " 는 읽지 못했습니다 (글자 인식은 Windows 에서만 지원)");
            return texts;
        }
        log.accept("PDF " + texts.size() + "페이지: 글자 층 " + (texts.size() - poor.size()) + "쪽, 글자 인식(OCR) " + poor.size() + "쪽 " + poor);
        Map<Integer, String> ocr = WindowsOcr.pdf(pdf, poor, log);
        for (int number : poor) {
            String recognized = ocr.getOrDefault(number, "");
            if (!recognized.isBlank()) texts.set(number - 1, recognized);
        }
        return texts;
    }

    /** 글자가 거의 없거나 깨진 글자가 많으면 글자 층을 못 쓰는 페이지로 본다. */
    static boolean isPoor(String text) {
        long letters = text.codePoints().filter(Character::isLetterOrDigit).count();
        long broken = text.codePoints().filter(c -> c == 0xFFFD || (c >= 0xE000 && c <= 0xF8FF)
                || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')).count();
        return letters < 8 || broken * 10 > letters;
    }

    /** pdftotext 로 페이지별 글자를 읽는다. 프로그램이 없거나 실패하면 null. */
    private static List<String> textLayer(Path pdf, Consumer<String> log) throws IOException, InterruptedException {
        String exe = findPdftotext();
        if (exe == null) return null;

        // pdftotext 는 한글 등이 섞인 경로를 못 여는 경우가 있어서, 그런 경로면 임시 파일로 복사해서 넘긴다
        Path input = pdf.toAbsolutePath().normalize();
        Path copy = null;
        if (!input.toString().chars().allMatch(c -> c < 128)) {
            copy = Files.createTempFile("tt-pdf", ".pdf");
            Files.copy(input, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            input = copy;
        }
        Path out = Files.createTempFile("tt-pdf", ".txt");
        try {
            // -layout: 화면에 보이는 배치대로 읽는다. 표의 한 행이 한 줄로 나오고 글머리 기호 항목이 섞이지 않는다
            Process process = new ProcessBuilder(exe, "-enc", "UTF-8", "-q", "-layout", input.toString(), out.toString())
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            try {
                if (!process.waitFor(180, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    log.accept("pdftotext 가 너무 오래 걸려 글자 인식(OCR)으로 바꿉니다");
                    return null;
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                throw e;
            }
            if (process.exitValue() != 0) {
                log.accept("pdftotext 로 읽지 못해 글자 인식(OCR)으로 바꿉니다 (종료 코드 " + process.exitValue() + ")");
                return null;
            }
            // 페이지 끝마다 폼피드(\f) 문자가 들어 있다
            List<String> pages = new ArrayList<>();
            for (String part : Files.readString(out, StandardCharsets.UTF_8).split("\f", -1)) pages.add(tidyLayout(part));
            if (!pages.isEmpty() && pages.get(pages.size() - 1).isBlank()) pages.remove(pages.size() - 1);
            return pages.isEmpty() ? null : pages;
        } catch (IOException e) {
            log.accept("pdftotext 실행 실패, 글자 인식(OCR)으로 바꿉니다: " + e.getMessage());
            return null;
        } finally {
            Files.deleteIfExists(out);
            if (copy != null) Files.deleteIfExists(copy);
        }
    }

    /**
     * -layout 출력은 배치를 공백으로 맞춰 놓는다. 줄 앞뒤 공백은 지우고,
     * 넓게 벌어진 칸(공백 3칸 이상: 표의 열)은 PPTX 추출과 같은 " | " 로 바꾼다.
     */
    static String tidyLayout(String page) {
        StringBuilder sb = new StringBuilder();
        for (String line : page.replace("\r", "").split("\n", -1)) {
            sb.append(line.strip().replaceAll("[ \\t]{3,}", " | ").replaceAll("[ \\t]{2}", " ")).append('\n');
        }
        return sb.toString();
    }

    /** 찾는 순서: .env 의 PDFTOTEXT_PATH → PATH → Git for Windows 설치 폴더 */
    static String findPdftotext() {
        String configured = Config.get("PDFTOTEXT_PATH");
        if (configured != null && Files.isRegularFile(Path.of(configured))) return configured;

        boolean windows = WindowsOcr.available();
        String name = windows ? "pdftotext.exe" : "pdftotext";
        List<Path> candidates = new ArrayList<>();
        String pathVar = System.getenv("PATH");
        if (pathVar != null) {
            for (String dir : pathVar.split(java.io.File.pathSeparator)) {
                if (dir.isBlank()) continue;
                try {
                    Path d = Path.of(dir.trim());
                    candidates.add(d.resolve(name));
                    // Git 은 보통 ...\Git\cmd 만 PATH 에 있고, pdftotext 는 ...\Git\mingw64\bin 에 있다
                    if (windows && Files.isRegularFile(d.resolve("git.exe")) && d.getParent() != null) {
                        candidates.add(d.getParent().resolve("mingw64").resolve("bin").resolve(name));
                    }
                } catch (java.nio.file.InvalidPathException e) {
                    // PATH 에 잘못된 항목이 있으면 건너뛴다
                }
            }
        }
        if (windows) {
            for (String var : new String[] {"ProgramFiles", "ProgramW6432", "LOCALAPPDATA"}) {
                String base = System.getenv(var);
                if (base == null) continue;
                candidates.add(Path.of(base, "Git", "mingw64", "bin", name));
                candidates.add(Path.of(base, "Programs", "Git", "mingw64", "bin", name));
            }
        }
        for (Path c : candidates) {
            if (Files.isRegularFile(c)) return c.toString();
        }
        return null;
    }
}
