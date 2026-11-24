package com.termtranslator.extract;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * PowerPoint(.pptx) 에서 슬라이드별 글자를 꺼낸다.
 * .pptx 는 XML 파일들을 묶은 zip 이라서 JDK 만으로 읽을 수 있다.
 *
 *   ppt/presentation.xml            슬라이드 순서 (r:id 목록)
 *   ppt/_rels/presentation.xml.rels r:id → 슬라이드 파일
 *   ppt/slides/slideN.xml           슬라이드 내용 (도형 안의 문단 a:p, 표 a:tbl)
 *
 * 제목을 맨 앞에 두고, 표는 한 행을 " | " 로 이은 한 줄로 만든다.
 * 슬라이드 번호·바닥글·날짜, 숨긴 슬라이드, 발표자 노트는 뺀다. 그림 속 글자는 읽지 않는다.
 */
final class PptxExtractor {

    private static final String DRAWING_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final Set<String> SKIP_PLACEHOLDERS = Set.of("sldNum", "ftr", "dt", "hdr");
    private static final Set<String> TITLE_PLACEHOLDERS = Set.of("title", "ctrTitle");
    private static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;

    private PptxExtractor() {}

    /** 슬라이드 순서대로 글자를 돌려준다. 글자가 없는 슬라이드는 빈 문자열. */
    static List<String> extract(Path file, Consumer<String> log) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            List<String> slides = slideOrder(zip);
            if (slides.isEmpty()) throw new IOException("슬라이드를 찾지 못했습니다: " + file.getFileName());
            List<String> texts = new ArrayList<>();
            List<Integer> hidden = new ArrayList<>();
            for (int i = 0; i < slides.size(); i++) {
                Element root = parse(zip, slides.get(i)).getDocumentElement();
                if ("0".equals(root.getAttribute("show"))) {
                    hidden.add(i + 1);
                    texts.add("");
                    continue;
                }
                texts.add(slideText(root));
            }
            if (!hidden.isEmpty()) log.accept("숨긴 슬라이드는 제외: " + hidden);
            return texts;
        } catch (java.util.zip.ZipException e) {
            throw new IOException("PPTX 파일을 열 수 없습니다: " + file.getFileName()
                    + " (암호가 걸려 있거나 손상된 파일일 수 있습니다)", e);
        }
    }

    /** 발표 순서대로 슬라이드 파일 경로. 순서 정보를 못 읽으면 파일 번호순. */
    private static List<String> slideOrder(ZipFile zip) throws IOException {
        List<String> ordered = new ArrayList<>();
        if (zip.getEntry("ppt/presentation.xml") != null && zip.getEntry("ppt/_rels/presentation.xml.rels") != null) {
            Map<String, String> targets = new HashMap<>();
            NodeList rels = parse(zip, "ppt/_rels/presentation.xml.rels").getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < rels.getLength(); i++) {
                Element r = (Element) rels.item(i);
                String target = r.getAttribute("Target");
                targets.put(r.getAttribute("Id"), target.startsWith("/") ? target.substring(1) : "ppt/" + target);
            }
            NodeList ids = parse(zip, "ppt/presentation.xml").getElementsByTagNameNS("*", "sldId");
            for (int i = 0; i < ids.getLength(); i++) {
                String path = targets.get(((Element) ids.item(i)).getAttributeNS(REL_NS, "id"));
                if (path != null && zip.getEntry(path) != null) ordered.add(path);
            }
        }
        if (!ordered.isEmpty()) return ordered;

        Pattern slide = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");
        Map<Integer, String> byNumber = new java.util.TreeMap<>();
        zip.stream().forEach(e -> {
            Matcher m = slide.matcher(e.getName());
            if (m.matches()) byNumber.put(Integer.parseInt(m.group(1)), e.getName());
        });
        return new ArrayList<>(byNumber.values());
    }

    private static Document parse(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("PPTX 안에 " + name + " 이(가) 없습니다.");
        if (entry.getSize() > MAX_ENTRY_BYTES) throw new IOException("PPTX 안의 " + name + " 이(가) 너무 큽니다.");
        try (InputStream in = zip.getInputStream(entry)) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // 받은 파일을 그대로 읽으므로 외부 파일을 끌어오는 XML 기능은 꺼 둔다
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(in);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("PPTX 안의 " + name + " 을(를) 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    private static String slideText(Element slide) {
        NodeList trees = slide.getElementsByTagNameNS("*", "spTree");
        if (trees.getLength() == 0) return "";
        List<String> titles = new ArrayList<>(), body = new ArrayList<>();
        for (Element shape : children((Element) trees.item(0))) {
            String placeholder = placeholderType(shape);
            if (SKIP_PLACEHOLDERS.contains(placeholder)) continue;
            walk(shape, TITLE_PLACEHOLDERS.contains(placeholder) ? titles : body);
        }
        titles.addAll(body);
        return String.join("\n", titles);
    }

    /** 도형의 자리표시자 종류 (title, body, sldNum ...). 자리표시자가 아니면 빈 문자열. */
    private static String placeholderType(Element shape) {
        if (!"sp".equals(shape.getLocalName())) return "";
        NodeList ph = shape.getElementsByTagNameNS("*", "ph");
        return ph.getLength() == 0 ? "" : ((Element) ph.item(0)).getAttribute("type");
    }

    private static void walk(Element e, List<String> lines) {
        String name = e.getLocalName();
        if ("AlternateContent".equals(name)) {
            // 같은 내용이 두 가지 표현(Choice / Fallback)으로 들어 있으므로 첫 번째만 읽는다
            List<Element> kids = children(e);
            if (!kids.isEmpty()) walk(kids.get(0), lines);
            return;
        }
        if ("tbl".equals(name) && DRAWING_NS.equals(e.getNamespaceURI())) {
            for (Element row : children(e)) {
                if (!"tr".equals(row.getLocalName())) continue;
                List<String> cells = new ArrayList<>();
                for (Element cell : children(row)) {
                    if (!"tc".equals(cell.getLocalName())) continue;
                    List<String> cellLines = new ArrayList<>();
                    walk(cell, cellLines);
                    cells.add(String.join(" ", cellLines));
                }
                if (cells.stream().anyMatch(c -> !c.isBlank())) lines.add(String.join(" | ", cells));
            }
            return;
        }
        if ("p".equals(name) && DRAWING_NS.equals(e.getNamespaceURI())) {
            StringBuilder sb = new StringBuilder();
            paragraph(e, sb);
            if (!sb.toString().isBlank()) lines.add(sb.toString().strip());
            return;
        }
        for (Element kid : children(e)) walk(kid, lines);
    }

    /** 문단 안의 글자 조각(a:t, 수식의 m:t)을 잇는다. a:br 은 줄바꿈. */
    private static void paragraph(Element e, StringBuilder sb) {
        for (Element kid : children(e)) {
            String name = kid.getLocalName();
            if ("t".equals(name)) {
                sb.append(kid.getTextContent());
            } else if ("br".equals(name)) {
                sb.append('\n');
            } else if ("AlternateContent".equals(name)) {
                List<Element> options = children(kid);
                if (!options.isEmpty()) paragraph(options.get(0), sb);
            } else {
                paragraph(kid, sb);
            }
        }
    }

    private static List<Element> children(Element e) {
        List<Element> out = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el) out.add(el);
        }
        return out;
    }
}
