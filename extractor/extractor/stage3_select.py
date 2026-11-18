"""3단계. OCR 대상 이미지 선정 + 사전 중복 검사."""
from dataclasses import dataclass

import pymupdf

from .config import Thresholds
from .models import BBox


@dataclass
class OcrTarget:
    xref: int     # PDF 내 이미지 참조 번호
    bbox: BBox    # 페이지 위 배치 좌표


def select_ocr_targets(page: "pymupdf.Page", text_blocks: list[tuple[str, BBox]],
                       th: Thresholds) -> list[OcrTarget]:
    """작은 이미지 제외, 텍스트가 이미 충분히 겹치는 이미지 제외. TODO"""
    raise NotImplementedError
