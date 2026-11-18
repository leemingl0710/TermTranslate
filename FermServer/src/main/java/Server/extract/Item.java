package Server.extract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * extractor 가 돌려주는 추출 결과 한 항목 (문단 하나).
 * 필드는 extractor/extractor/models.py 의 Item 과 1:1 로 대응한다.
 *
 * @param sourceFile   원본 파일 이름 (예: lecture.pdf)
 * @param sourceFormat 원본 형식 (pdf, pptx, ppt)
 * @param page         페이지(슬라이드) 번호, 1부터
 * @param origin       추출 출처: 텍스트 레이어(TEXT) 또는 이미지 글자 인식(OCR)
 * @param content      추출된 문단
 * @param bbox         페이지 위 위치 [x0, y0, x1, y1] (단위: pt, 왼쪽 위가 원점)
 * @param confidence   OCR 신뢰도 0~1. origin 이 TEXT 이면 null
 */
public record Item(
        @JsonProperty("source_file") String sourceFile,
        @JsonProperty("source_format") String sourceFormat,
        @JsonProperty("page") int page,
        @JsonProperty("origin") Origin origin,
        @JsonProperty("content") String content,
        @JsonProperty("bbox") List<Double> bbox,
        @JsonProperty("confidence") Double confidence) {

    public enum Origin {
        @JsonProperty("text") TEXT,
        @JsonProperty("ocr") OCR
    }
}
