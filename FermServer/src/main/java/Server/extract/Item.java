package Server.extract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * extractor 가 돌려주는 추출 결과 한 항목 (문단 하나).
 * 필드는 extractor/extractor/models.py 의 Item 과 1:1 로 대응한다.
 *
 * @param sourceFile   원본 파일 이름 (예: lecture.pdf)
 * @param sourceFormat 원본 형식 (pdf, pptx, ppt, png, jpg)
 * @param page         페이지(슬라이드) 번호, 1부터
 * @param origin       추출 출처: 텍스트 레이어(TEXT) 또는 이미지 글자 인식(OCR)
 * @param content      추출된 문단
 * @param bbox         페이지 위 위치 [x0, y0, x1, y1] (단위: pt, 왼쪽 위가 원점)
 * @param confidence   OCR 신뢰도 0~1. origin 이 TEXT 이면 null
 * @param flags        표시 목록 (없으면 빈 목록). {@link #CODE} 코드 캡처 — 줄바꿈(\n)이 유지되고
 *                     번역하지 않아도 됨, {@link #TABLE} 그림 속 표 한 행 ("칸 | 칸"),
 *                     {@link #LOW_CONFIDENCE} OCR 신뢰도가 낮아 틀렸을 수 있음
 */
public record Item(
        @JsonProperty("source_file") String sourceFile,
        @JsonProperty("source_format") String sourceFormat,
        @JsonProperty("page") int page,
        @JsonProperty("origin") Origin origin,
        @JsonProperty("content") String content,
        @JsonProperty("bbox") List<Double> bbox,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("flags") List<String> flags) {

    public static final String CODE = "code";
    public static final String TABLE = "table";
    public static final String LOW_CONFIDENCE = "low_confidence";

    public Item {
        flags = flags == null ? List.of() : List.copyOf(flags);   // JSON 에 flags 가 없으면 빈 목록
    }

    /** flags 없는 항목 (텍스트 레이어 문단 등). */
    public Item(String sourceFile, String sourceFormat, int page, Origin origin, String content,
                List<Double> bbox, Double confidence) {
        this(sourceFile, sourceFormat, page, origin, content, bbox, confidence, List.of());
    }

    /** 예: {@code item.hasFlag(Item.CODE)} 이면 번역에서 건너뛴다. */
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public enum Origin {
        @JsonProperty("text") TEXT,
        @JsonProperty("ocr") OCR
    }
}
