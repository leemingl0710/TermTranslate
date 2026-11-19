"""3단계. OCR 대상 이미지 선정 + 사전 중복 검사.

글자 페이지(PageType.TEXT) 안의 사진 중 OCR 할 것만 고른다.
- 작은 그림 제외: 가로·세로 중 하나라도 min_image_side_pt 미만 (아이콘, 로고, 버튼)
- 이미 글자가 덮은 그림 제외: 텍스트 레이어 문단이 그림 면적의 overlap_ratio 이상을
  덮으면, 그 글자는 이미 읽혔으므로 OCR 하지 않는다 (같은 글자를 두 번 읽지 않게).
"""
from dataclasses import dataclass

import pymupdf

from .config import Thresholds
from .models import BBox


@dataclass
class OcrTarget:
    xref: int          # PDF 내 이미지 참조 번호 (페이지에 직접 박힌 그림은 0)
    bbox: BBox         # 페이지 위 배치 좌표 (pt, 페이지 밖은 잘라냄)
    native_dpi: float  # 원본 그림 해상도 (배치 크기 기준). OCR 렌더링 해상도를 정할 때 사용


def text_coverage(img: pymupdf.Rect, text_blocks: list[tuple[str, BBox]]) -> float:
    """그림 면적 중 텍스트 문단이 덮는 비율 (0~1). 문단끼리는 거의 안 겹치므로 합으로 계산."""
    area = img.width * img.height
    if area <= 0:
        return 0.0
    covered = 0.0
    for _text, bbox in text_blocks:
        inter = img & pymupdf.Rect(bbox)
        if not inter.is_empty:
            covered += inter.width * inter.height
    return min(covered / area, 1.0)


def select_ocr_targets(page: "pymupdf.Page", text_blocks: list[tuple[str, BBox]],
                       th: Thresholds) -> list[OcrTarget]:
    """작은 이미지 제외, 텍스트가 이미 충분히 겹치는 이미지 제외. 위→아래 순서."""
    targets = []
    seen = set()
    for info in page.get_image_info(xrefs=True):
        rect = pymupdf.Rect(info["bbox"]) & page.rect
        if rect.is_empty:
            continue
        if min(rect.width, rect.height) < th.min_image_side_pt:
            continue
        if text_coverage(rect, text_blocks) >= th.overlap_ratio:
            continue
        key = (info["xref"], tuple(round(v, 1) for v in rect))
        if key in seen:     # 같은 그림이 같은 자리에 두 번 그려진 경우
            continue
        seen.add(key)
        full = pymupdf.Rect(info["bbox"])   # 잘리기 전 크기로 원본 해상도 계산
        native_dpi = info["width"] / (full.width / 72) if full.width > 0 else 0.0
        targets.append(OcrTarget(info["xref"], tuple(rect), native_dpi))
    targets.sort(key=lambda t: (t.bbox[1], t.bbox[0]))
    return targets
