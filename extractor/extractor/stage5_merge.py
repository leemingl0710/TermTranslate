"""5단계. 결과 통합 + 최종 중복 제거 + 읽는 순서 정렬."""
from difflib import SequenceMatcher

from .config import Thresholds
from .models import BBox, Item


def overlap(a: BBox, b: BBox) -> float:
    """작은 쪽 면적 대비 교집합 비율 (0~1)."""
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    smaller = min((a[2] - a[0]) * (a[3] - a[1]), (b[2] - b[0]) * (b[3] - b[1]))
    return inter / smaller if smaller > 0 else 0.0


def similarity(a: str, b: str) -> float:
    """OCR 오타를 감안한 유사도. 필요하면 rapidfuzz 로 교체."""
    return SequenceMatcher(None, a, b).ratio()


def merge(text_items: list[Item], ocr_items: list[Item], th: Thresholds) -> list[Item]:
    """위치+내용이 겹치는 OCR 항목은 버리고(텍스트 우선), 위→아래·왼→오 정렬. TODO"""
    raise NotImplementedError
