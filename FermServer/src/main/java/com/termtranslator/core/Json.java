package com.termtranslator.core;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부 라이브러리 없이 쓰는 작은 JSON 파서/작성기.
 * 정규식이 아니라 한 글자씩 읽는 방식이라, 문자열 안에 { } [ ] " 가 있어도 깨지지 않는다.
 * LLM 이 자주 내는 실수인 "마지막 원소 뒤 쉼표" 는 허용한다.
 *
 * parse 결과: 객체 → Map(LinkedHashMap), 배열 → List, 문자열 → String,
 *             정수 → Long, 실수 → Double, true/false → Boolean, null → null
 */
public final class Json {
    private Json() {}

    // ------------------------------------------------------------------ 파싱

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < text.length()) throw p.error("JSON 뒤에 불필요한 내용이 있음");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) throw new IllegalArgumentException("JSON 객체가 아님");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int i;

        Parser(String s) { this.s = s; }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException(msg + " (위치 " + i + ")");
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            if (i >= s.length()) throw error("JSON 이 중간에 끝남");
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw error("예상하지 못한 문자 '" + c + "'");
            }
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (peek('}')) { i++; return m; }
            while (true) {
                ws();
                if (peek('}')) { i++; return m; } // 끝 쉼표 허용
                if (!peek('"')) throw error("객체 키는 \" 로 시작해야 함");
                String key = string();
                ws();
                expect(':');
                ws();
                m.put(key, value());
                ws();
                if (peek(',')) { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++; // [
            ws();
            if (peek(']')) { i++; return list; }
            while (true) {
                ws();
                if (peek(']')) { i++; return list; } // 끝 쉼표 허용
                list.add(value());
                ws();
                if (peek(',')) { i++; continue; }
                expect(']');
                return list;
            }
        }

        String string() {
            i++; // "
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error("잘못된 \\u 이스케이프");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e);
                }
            }
            throw error("문자열이 닫히지 않음");
        }

        Object number() {
            int start = i;
            if (peek('-')) i++;
            boolean fractional = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') { i++; }
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { fractional = true; i++; }
                else break;
            }
            String n = s.substring(start, i);
            try {
                return fractional ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
            } catch (NumberFormatException ex) {
                throw error("잘못된 숫자 " + n);
            }
        }

        Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) throw error("예상하지 못한 값");
            i += word.length();
            return v;
        }

        boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

        void expect(char c) {
            if (!peek(c)) throw error("'" + c + "' 가 필요함");
            i++;
        }
    }

    // ------------------------------------------------------------------ 값 꺼내기 도우미 (LLM 응답 형식이 조금 달라도 견디도록)

    public static String str(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        if (v == null) return "";
        if (v instanceof List<?> l) return String.join(" ", l.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }

    public static int integer(Map<String, Object> m, String key, int fallback) {
        Object v = m == null ? null : m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            String digits = s.replaceAll("\\D", "");
            if (!digits.isEmpty()) return Integer.parseInt(digits);
        }
        return fallback;
    }

    /** 문자열 배열. 모델이 배열 대신 줄바꿈 문자열을 주면 줄 단위로 나눈다. */
    public static List<String> strList(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) if (o != null) out.add(String.valueOf(o));
        } else if (v instanceof String s && !s.isBlank()) {
            for (String line : s.split("\\R")) if (!line.isBlank()) out.add(line.trim());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objList(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        List<Map<String, Object>> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) if (o instanceof Map) out.add((Map<String, Object>) o);
        }
        return out;
    }

    // ------------------------------------------------------------------ 작성

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, -1, 0);
        return sb.toString();
    }

    public static String writePretty(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, 2, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            quote(sb, s);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            writeEntries(sb, m, indent, depth);
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            int k = 0;
            for (Object o : c) {
                if (k++ > 0) sb.append(',');
                newline(sb, indent, depth + 1);
                write(sb, o, indent, depth + 1);
            }
            if (k > 0) newline(sb, indent, depth);
            sb.append(']');
        } else if (v.getClass().isRecord()) {
            writeRecord(sb, v, indent, depth);
        } else {
            quote(sb, v.toString());
        }
    }

    private static void writeRecord(StringBuilder sb, Object v, int indent, int depth) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (RecordComponent rc : v.getClass().getRecordComponents()) {
            try {
                m.put(rc.getName(), rc.getAccessor().invoke(v));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        write(sb, m, indent, depth);
    }

    private static void writeEntries(StringBuilder sb, Map<?, ?> entries, int indent, int depth) {
        sb.append('{');
        int k = 0;
        for (Map.Entry<?, ?> e : entries.entrySet()) {
            if (k++ > 0) sb.append(',');
            newline(sb, indent, depth + 1);
            quote(sb, String.valueOf(e.getKey()));
            sb.append(indent < 0 ? ":" : ": ");
            write(sb, e.getValue(), indent, depth + 1);
        }
        if (k > 0) newline(sb, indent, depth);
        sb.append('}');
    }

    private static void newline(StringBuilder sb, int indent, int depth) {
        if (indent < 0) return;
        sb.append('\n');
        sb.append(" ".repeat(indent * depth));
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
