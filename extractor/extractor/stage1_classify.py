"""1단계. 페이지 유형 판별 (텍스트 양 + 큰 이미지 덮는 비율).

| 글자 수         | 큰 이미지 | 결과             | 이후 처리                      |
|-----------------|-----------|------------------|--------------------------------|
| min_text_chars 미만 | 있음  | SCANNED          | 페이지 전체 OCR                |
| min_text_chars 이상 | 있음  | SEARCHABLE_SCAN  | 텍스트 레이어만 사용, OCR 생략 |
| 그 외               |       | TEXT             | 텍스트 추출 + 작은 사진만 OCR  |
"""
import pymupdf

from .config import Thresholds
from .models import PageType


def count_text_chars(page: "pymupdf.Page") -> int:
    """공백을 뺀 글자 수. 화면에 안 보이는 글자(스캔 위에 숨긴 OCR 레이어)도 센다."""
    return sum(1 for ch in page.get_text("text") if not ch.isspace())


def image_coverage(page: "pymupdf.Page") -> float:
    """페이지 면적 대비 가장 큰 이미지가 덮는 비율 (0~1).

    페이지 밖으로 나간 부분은 잘라서 계산한다. 이미지가 없으면 0.
    """
    page_rect = page.rect
    page_area = page_rect.width * page_rect.height
    if page_area <= 0:
        return 0.0
    best = 0.0
    for info in page.get_image_info():
        visible = pymupdf.Rect(info["bbox"]) & page_rect   # 페이지 안쪽만
        if visible.is_empty:
            continue
        best = max(best, visible.width * visible.height / page_area)
    return min(best, 1.0)


def classify_page(page: "pymupdf.Page", th: Thresholds) -> PageType:
    """텍스트 있음/없음 × 큰 이미지 있음/없음 → PageType."""
    if image_coverage(page) < th.large_image_ratio:
        return PageType.TEXT
    if count_text_chars(page) < th.min_text_chars:
        return PageType.SCANNED
    return PageType.SEARCHABLE_SCAN
