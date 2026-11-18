"""4단계. 이미지 변환 → RapidOCR."""
from dataclasses import dataclass
from functools import lru_cache

import pymupdf

from .config import OCR_LANG, Thresholds
from .models import BBox


@dataclass
class OcrResult:
    text: str
    bbox: BBox          # 페이지 pt 좌표로 환산된 값
    confidence: float


@lru_cache(maxsize=1)
def get_engine():
    """OCR 엔진은 프로세스당 한 번만 로드 (첫 실행 때 모델 파일을 내려받음)."""
    from rapidocr import LangRec, ModelType, OCRVersion, RapidOCR
    # 최신 PP-OCRv6 인식 모델엔 한국어가 없어 v5 한국어 모델을 지정
    return RapidOCR(params={
        "Rec.lang_type": LangRec(OCR_LANG),
        "Rec.ocr_version": OCRVersion.PPOCRV5,
        "Rec.model_type": ModelType.MOBILE,
    })


def run_ocr(img_bytes: bytes) -> list[tuple[str, BBox, float]]:
    """이미지(PNG 등 바이트) → [(글자, 픽셀 bbox, 신뢰도)]."""
    out = get_engine()(img_bytes)
    if out.txts is None:
        return []
    res = []
    for box, txt, score in zip(out.boxes, out.txts, out.scores):
        xs, ys = [p[0] for p in box], [p[1] for p in box]   # 4꼭짓점 → 사각형
        res.append((txt, (min(xs), min(ys), max(xs), max(ys)), float(score)))
    return res


def ocr_full_page(page: "pymupdf.Page", th: Thresholds) -> list[OcrResult]:
    """페이지 전체를 render_dpi 로 렌더링해 OCR. 픽셀→pt 좌표 환산. TODO"""
    raise NotImplementedError


def ocr_image(page: "pymupdf.Page", xref: int, place: BBox, th: Thresholds) -> list[OcrResult]:
    """페이지 속 이미지 하나만 꺼내 OCR. 이미지 좌표→페이지 좌표 환산. TODO"""
    raise NotImplementedError
