"""5단계. 결과 통합 + 최종 중복 제거 + 읽는 순서 정렬."""
from difflib import SequenceMatcher

from .config import Thresholds
from .models import BBox, Item

# 위→아래 정렬에서 위쪽 끝이 이 값(pt) 이내로 차이 나면 같은 높이로 보고 왼→오로 정렬
SAME_ROW_TOLERANCE = 3.0


def overlap(a: BBox, b: BBox) -> float:
    """작은 쪽 면적 대비 교집합 비율 (0~1)."""
    ix = max(0.0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0.0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    smaller = min((a[2] - a[0]) * (a[3] - a[1]), (b[2] - b[0]) * (b[3] - b[1]))
    return inter / smaller if smaller > 0 else 0.0


def similarity(a: str, b: str) -> float:
    """짧은 쪽 글자가 긴 쪽에 얼마나 들어 있는지 (0~1). 공백은 무시.

    OCR 문단이 텍스트 문단의 일부만 읽은 경우(예: 배경 그림 위 글자 한 줄)도
    같은 내용으로 보기 위해, 전체 유사도 대신 짧은 쪽 기준으로 잰다.
    """
    a, b = "".join(a.split()), "".join(b.split())
    if not a or not b:
        return 0.0
    short, long_ = (a, b) if len(a) <= len(b) else (b, a)
    matched = sum(m.size for m in SequenceMatcher(None, short, long_, autojunk=False).get_matching_blocks())
    return matched / len(short)


def is_duplicate(ocr: Item, text_items: list[Item], th: Thresholds) -> bool:
    """같은 페이지의 텍스트 문단과 위치도 겹치고 내용도 같으면 중복."""
    return any(
        t.page == ocr.page
        and overlap(t.bbox, ocr.bbox) >= th.overlap_ratio
        and similarity(t.content, ocr.content) >= th.text_similarity
        for t in text_items
    )


def reading_order(items: list[Item]) -> list[Item]:
    """페이지 → 위→아래 → 왼→오. 높이가 거의 같으면 같은 줄로 보고 왼쪽부터.

    TODO(8단계): 2단 편집은 왼쪽 칸을 다 읽고 오른쪽 칸을 읽어야 한다.
    """
    def key(i: Item):
        return (i.page, round(i.bbox[1] / SAME_ROW_TOLERANCE), i.bbox[0])
    return sorted(items, key=key)


def merge(text_items: list[Item], ocr_items: list[Item], th: Thresholds) -> list[Item]:
    """위치+내용이 겹치는 OCR 항목은 버리고(텍스트 우선), 읽는 순서로 정렬."""
    kept = [o for o in ocr_items if not is_duplicate(o, text_items, th)]
    return reading_order(text_items + kept)
