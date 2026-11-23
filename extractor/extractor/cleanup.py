"""8-1. 번역할 필요 없는 잡음 지우기 (5단계 정리가 끝난 결과에 적용).

강의 PDF 7개를 조사해서 정한 규칙:
1) 페이지 번호: 위·아래 띠 안에 숫자만 있는 칸 (예: "9", "- 9 -", "9 / 47") → 지움
2) 아래 띠에서 3페이지 이상 반복되는 글자 (예: "소스코드 다운로드", "동영상 설명") → 모두 지움
3) 위 띠에서 3페이지 이상 반복되는 글자 (예: 장 제목 "2 파일과 디렉터리") → 처음 한 번만 남김
   어느 장인지 알려 주는 글자라 하나는 남긴다.

띠 기준: 글자 아래 끝이 페이지 위쪽 12% 안이면 위 띠, 글자 위 끝이 아래쪽 12% 안이면 아래 띠.
슬라이드 제목은 보통 이보다 조금 아래(10~11% 에서 시작)에 있어서 위 띠에 안 걸린다.
페이지 가운데 있는 글자는 반복돼도 건드리지 않는다 (장 표지, 그림 설명 등).
"""
import re
from collections import defaultdict

from .models import Item

EDGE_RATIO = 0.12          # 위·아래 띠 두께 (페이지 높이 대비)
MIN_REPEAT_PAGES = 3       # 이 페이지 수 이상 나오면 반복 글자

_PAGE_NUMBER = re.compile(r"[-–(\[]?\s*(?:p\.?\s*)?\d{1,4}\s*(?:/\s*\d{1,4})?\s*[-–)\]]?", re.IGNORECASE)
# 비교할 때 앞뒤 숫자(페이지 번호·장 번호)는 무시: "11 소스코드 다운로드" == "소스코드 다운로드"
_EDGE_NUMBERS = re.compile(r"^\d+\s+|\s+\d+$")


def _key(text: str) -> str:
    return "".join(_EDGE_NUMBERS.sub("", text).split())


def _band(item: Item, page_height: float) -> str | None:
    _x0, y0, _x1, y1 = item.bbox
    if y1 <= page_height * EDGE_RATIO:
        return "top"
    if y0 >= page_height * (1 - EDGE_RATIO):
        return "bottom"
    return None


def remove_noise(items: list[Item], page_heights: dict[int, float]) -> list[Item]:
    """items: 읽는 순서로 정렬된 항목. page_heights: 페이지 번호 → 높이(pt). 순서는 유지한다."""
    bands = [_band(i, page_heights[i.page]) for i in items]

    # 띠 안 글자가 몇 페이지에 나오는지
    pages_of: dict[tuple[str, str], set[int]] = defaultdict(set)
    for item, band in zip(items, bands):
        if band and (k := _key(item.content)):
            pages_of[(band, k)].add(item.page)

    kept, seen_top = [], set()
    for item, band in zip(items, bands):
        if band is None:
            kept.append(item)
            continue
        if _PAGE_NUMBER.fullmatch(item.content.strip()):
            continue
        k = _key(item.content)
        if len(pages_of[(band, k)]) >= MIN_REPEAT_PAGES:
            if band == "bottom" or k in seen_top:
                continue
            seen_top.add(k)
        kept.append(item)
    return kept
