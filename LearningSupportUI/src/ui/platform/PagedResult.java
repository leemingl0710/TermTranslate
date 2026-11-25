package ui.platform;

import ui.ApiClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 서버 분석 결과 하나를 결과 화면용 "페이지 목록"으로 바꾼다 (Swing 없음, 순수 로직).
 *
 * <p>서버 번역기(LlmTranslator.toTranslation)가 만드는 글 형식:
 * <ul>
 *   <li>translatedText: "[Page N]\n번역" 블록을 빈 줄로 이음. 번역 안에도 빈 줄이 있을 수 있어
 *       빈 줄이 아니라 "[Page N]" 머리줄로 나눈다.</li>
 *   <li>summary: 단원 요약 → "Page summaries" 아래 "[Page N]\n- 항목" 블록들.</li>
 *   <li>explanation: "Key concepts: ..." → "Concept explanation" → "How the pages connect" 아래 "Page N: ..." 줄.</li>
 * </ul>
 * 어느 값이든 null 일 수 있다 (LLM 미설정). 페이지 번호는 원문(items)과 번역 쪽의 합집합을 정렬해 쓴다.
 */
final class PagedResult {

    static final String NOT_AVAILABLE = "Not available yet.";
    static final String PAGE_SUMMARIES_HEADER = "Page summaries";
    static final String CONCEPT_HEADER = "Concept explanation";
    static final String CONNECT_HEADER = "How the pages connect";

    private static final Pattern PAGE_HEADER = Pattern.compile("^\\[Page\\s+(\\d+)\\]\\s*$");
    private static final Pattern CONNECT_LINE = Pattern.compile("^Page\\s+(\\d+)\\s*:\\s*(.*)$");

    /** 한 페이지 분량. translation / connection 은 없으면 null, summaryBullets 는 없으면 빈 목록. */
    record PageView(int number, String original, String translation,
                    List<String> summaryBullets, String connection) {

        PageView {
            original = original == null ? "" : original;
            summaryBullets = summaryBullets == null ? List.of() : List.copyOf(summaryBullets);
        }

        /** "Page Explanation" 탭 내용: 요약 항목 + 앞 페이지와의 연결. 둘 다 없으면 null. */
        String explanationText() {
            StringBuilder sb = new StringBuilder();
            for (String bullet : summaryBullets) {
                sb.append("- ").append(bullet).append('\n');
            }
            if (connection != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("Connection to previous pages: ").append(connection);
            }
            String text = sb.toString().strip();
            return text.isEmpty() ? null : text;
        }
    }

    private final String fileName;
    private final List<PageView> pages;
    private final String unitSummary;
    private final String explanation;
    private final String conceptText;
    private final String termsText;

    private PagedResult(String fileName, List<PageView> pages, String unitSummary,
                        String explanation, String conceptText, String termsText) {
        this.fileName = fileName;
        this.pages = pages;
        this.unitSummary = unitSummary;
        this.explanation = explanation;
        this.conceptText = conceptText;
        this.termsText = termsText;
    }

    static PagedResult of(ApiClient.AnalysisResult result) {
        if (result == null) {
            return new PagedResult(null, List.of(), null, null, null, null);
        }
        Map<Integer, String> originals = originalsByPage(result.items());
        Map<Integer, String> translations = splitByPageHeader(normalize(result.translatedText()));

        String summary = normalize(result.summary());
        String[] summaryParts = splitAtLastHeader(summary, PAGE_SUMMARIES_HEADER);
        Map<Integer, List<String>> bullets = new LinkedHashMap<>();
        splitByPageHeader(summaryParts[1]).forEach((page, body) -> bullets.put(page, bulletLines(body)));

        String explanation = normalize(result.explanation());
        String[] explanationParts = splitAtLastHeader(explanation, CONNECT_HEADER);
        Map<Integer, String> connections = connectionLines(explanationParts[1]);

        TreeSet<Integer> numbers = new TreeSet<>();
        numbers.addAll(originals.keySet());
        numbers.addAll(translations.keySet());
        numbers.addAll(bullets.keySet());

        List<PageView> pages = new ArrayList<>();
        for (int n : numbers) {
            pages.add(new PageView(n, originals.get(n), translations.get(n),
                    bullets.get(n), connections.get(n)));
        }
        return new PagedResult(result.fileName(), Collections.unmodifiableList(pages),
                blankToNull(summaryParts[0]), blankToNull(explanation),
                blankToNull(explanationParts[0]), termsText(result.terms()));
    }

    // ---------------- 꺼내 쓰기 ----------------

    String fileName() {
        return fileName;
    }

    List<PageView> pages() {
        return pages;
    }

    int pageCount() {
        return pages.size();
    }

    /** "Page summaries" 앞부분 (단원 요약). 없으면 null. */
    String unitSummary() {
        return unitSummary;
    }

    /** 설명 전체 (가공 전). 없으면 null. */
    String explanation() {
        return explanation;
    }

    /** 설명에서 "How the pages connect" 앞부분 (핵심 개념 + 개념 설명). 없으면 null. */
    String conceptText() {
        return conceptText;
    }

    /** 용어 목록: 줄마다 "한국어 — english: explanation". 없으면 null. */
    String termsText() {
        return termsText;
    }

    // ---------------- 해석 ----------------

    /** 페이지별 원문: 같은 페이지 문단을 빈 줄로 잇는다. 코드 문단은 줄바꿈·들여쓰기를 그대로 둔다. */
    static Map<Integer, String> originalsByPage(List<ApiClient.Item> items) {
        Map<Integer, StringBuilder> byPage = new TreeMap<>();
        if (items != null) {
            for (ApiClient.Item item : items) {
                if (item == null || item.content() == null || item.content().isBlank()) {
                    continue;
                }
                String content = normalize(item.content());
                boolean code = item.flags() != null && item.flags().contains("code");
                content = code ? stripBlankEdgeLines(content) : content.strip();
                StringBuilder sb = byPage.computeIfAbsent(item.page(), k -> new StringBuilder());
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(content);
            }
        }
        Map<Integer, String> out = new TreeMap<>();
        byPage.forEach((page, sb) -> out.put(page, sb.toString()));
        return out;
    }

    /**
     * "[Page N]" 머리줄로 나눈다. 머리줄은 글 맨 앞이거나 빈 줄 바로 다음일 때만 인정한다
     * (번역 문장 중간에 우연히 같은 글이 있어도 잘리지 않게). 첫 머리줄 앞의 글은 버린다.
     * 같은 번호가 두 번 나오면 처음 것을 쓴다.
     */
    static Map<Integer, String> splitByPageHeader(String text) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String[] lines = text.split("\n", -1);
        Integer current = null;
        StringBuilder body = new StringBuilder();
        boolean previousBlank = true;
        for (String line : lines) {
            Matcher m = PAGE_HEADER.matcher(line.strip());
            if (previousBlank && m.matches()) {
                putBlock(out, current, body);
                current = Integer.parseInt(m.group(1));
                body.setLength(0);
                previousBlank = false;
                continue;
            }
            if (current != null) {
                body.append(line).append('\n');
            }
            previousBlank = line.isBlank();
        }
        putBlock(out, current, body);
        return out;
    }

    private static void putBlock(Map<Integer, String> out, Integer page, StringBuilder body) {
        if (page == null) {
            return;
        }
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            out.putIfAbsent(page, text);
        }
    }

    /**
     * header 와 똑같은 줄 중 마지막 것을 기준으로 [앞부분, 뒷부분] 을 돌려준다.
     * 머리줄이 없으면 [전체, null].
     */
    static String[] splitAtLastHeader(String text, String header) {
        if (text == null) {
            return new String[]{null, null};
        }
        String[] lines = text.split("\n", -1);
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].strip().equalsIgnoreCase(header)) {
                String before = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, i));
                String after = String.join("\n", java.util.Arrays.copyOfRange(lines, i + 1, lines.length));
                return new String[]{before, after};
            }
        }
        return new String[]{text, null};
    }

    private static List<String> bulletLines(String body) {
        List<String> out = new ArrayList<>();
        for (String line : body.split("\n")) {
            String s = line.strip();
            if (s.startsWith("- ") || s.startsWith("• ") || s.startsWith("* ")) {
                s = s.substring(2).strip();
            } else if (s.equals("-")) {
                s = "";
            }
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static Map<Integer, String> connectionLines(String section) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (section == null) {
            return out;
        }
        for (String line : section.split("\n")) {
            Matcher m = CONNECT_LINE.matcher(line.strip());
            if (m.matches() && !m.group(2).isBlank()) {
                out.putIfAbsent(Integer.parseInt(m.group(1)), m.group(2).strip());
            }
        }
        return out;
    }

    static String termsText(List<ApiClient.Term> terms) {
        if (terms == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ApiClient.Term t : terms) {
            if (t == null || t.korean() == null || t.korean().isBlank()) {
                continue;
            }
            sb.append(t.korean().strip());
            if (t.english() != null && !t.english().isBlank()) {
                sb.append(" — ").append(t.english().strip());
            }
            if (t.explanation() != null && !t.explanation().isBlank()) {
                sb.append(": ").append(normalize(t.explanation()).strip().replace('\n', ' '));
            }
            sb.append('\n');
        }
        return blankToNull(sb.toString());
    }

    // ---------------- 작은 도우미 ----------------

    /** \r\n, \r → \n. null 은 그대로. */
    static String normalize(String s) {
        return s == null ? null : s.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 코드 문단: 앞뒤 빈 줄과 줄 끝 공백만 없애고 들여쓰기는 남긴다. */
    private static String stripBlankEdgeLines(String s) {
        String[] lines = s.split("\n", -1);
        int start = 0;
        int end = lines.length;
        while (start < end && lines[start].isBlank()) {
            start++;
        }
        while (end > start && lines[end - 1].isBlank()) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                sb.append('\n');
            }
            sb.append(lines[i].stripTrailing());
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
