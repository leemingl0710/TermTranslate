"""OCR 결과(한 줄 조각들)를 문단으로 묶는다.

RapidOCR 는 글자를 '한 줄 조각' 단위로 돌려준다. 텍스트 레이어 추출(2단계)이
문단 단위이므로 OCR 결과도 같은 단위로 맞춘다.

1) 같은 줄 조각 합치기: 세로로 많이 겹치고 가로로 가까우면 한 줄
   (예: 글머리 기호 '•' 와 본문, 코드 캡처의 줄 번호 '07:' 와 코드가 따로 인식된 경우)
2) 줄 → 문단: 바로 아래 줄이고, 글자 크기가 비슷하고, 가로 범위가 겹치면 같은 문단
   (제목처럼 글자 크기가 다르면 다른 문단, 2단 편집처럼 가로로 떨어져 있으면 다른 문단)
"""
from dataclasses import dataclass

from .models import BBox
from .stage2_text import clean_text

# 같은 줄: 세로 겹침이 작은 쪽 높이의 이 비율 이상
SAME_ROW_OVERLAP = 0.5
# 같은 줄: 조각 사이 가로 간격이 줄 높이의 이 배수 이하
SAME_ROW_GAP = 3.0
# 같은 줄: 한쪽이 줄 번호·글머리 기호처럼 짧은 조각(이 글자 수 이하)이면 더 멀어도 붙인다
SHORT_PIECE_CHARS = 4
SHORT_PIECE_GAP = 8.0
# 같은 문단: 윗줄과 아랫줄 사이 세로 간격이 줄 높이의 이 배수 이하
PARA_LINE_GAP = 0.5
# 같은 문단: 두 줄 높이 비율이 이 범위 안 (글자 크기가 비슷)
PARA_HEIGHT_RATIO = (0.7, 1.4)


@dataclass
class Fragment:
    text: str
    bbox: BBox
    confidence: float


def _height(b: BBox) -> float:
    return b[3] - b[1]


def _union(a: BBox, b: BBox) -> BBox:
    return (min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3]))


def _weighted_conf(frags: list[Fragment]) -> float:
    """글자 수로 가중 평균한 신뢰도 (긴 줄의 신뢰도가 더 크게 반영)."""
    total = sum(len(f.text) for f in frags)
    if total == 0:
        return 0.0
    return sum(f.confidence * len(f.text) for f in frags) / total


def _merge_rows(frags: list[Fragment]) -> list[Fragment]:
    """같은 줄에 있는 조각을 왼쪽→오른쪽으로 이어 붙인다."""
    rows: list[list[Fragment]] = []
    for f in sorted(frags, key=lambda f: (f.bbox[1], f.bbox[0])):
        for row in rows:
            left = min(r.bbox[0] for r in row)
            right = max(r.bbox[2] for r in row)
            top = min(r.bbox[1] for r in row)
            bottom = max(r.bbox[3] for r in row)
            v_overlap = min(bottom, f.bbox[3]) - max(top, f.bbox[1])
            h = min(bottom - top, _height(f.bbox))
            # 줄의 왼쪽이든 오른쪽이든 가까우면 같은 줄 (겹치면 gap 은 음수)
            gap = max(f.bbox[0] - right, left - f.bbox[2])
            # 코드 캡처의 "07:" 와 들여쓴 코드처럼, 짧은 조각은 멀리 떨어져 있어도 같은 줄
            short = (len(f.text.strip()) <= SHORT_PIECE_CHARS
                     or sum(len(r.text.strip()) for r in row) <= SHORT_PIECE_CHARS)
            limit = (SHORT_PIECE_GAP if short else SAME_ROW_GAP) * h
            if h > 0 and v_overlap >= SAME_ROW_OVERLAP * h and gap <= limit:
                row.append(f)
                break
        else:
            rows.append([f])

    lines = []
    for row in rows:
        row.sort(key=lambda f: f.bbox[0])
        bbox = row[0].bbox
        for f in row[1:]:
            bbox = _union(bbox, f.bbox)
        lines.append(Fragment(" ".join(f.text for f in row), bbox, _weighted_conf(row)))
    return lines


def _belongs(para: list[Fragment], line: Fragment) -> bool:
    last = para[-1]
    h_last, h_line = _height(last.bbox), _height(line.bbox)
    if h_last <= 0 or h_line <= 0:
        return False
    lo, hi = PARA_HEIGHT_RATIO
    if not lo <= h_line / h_last <= hi:
        return False
    gap = line.bbox[1] - last.bbox[3]
    if not -0.3 * h_last <= gap <= PARA_LINE_GAP * h_last:
        return False
    # 가로 범위가 겹쳐야 함 (2단 편집의 옆 칸은 제외)
    left = min(f.bbox[0] for f in para)
    right = max(f.bbox[2] for f in para)
    return min(right, line.bbox[2]) - max(left, line.bbox[0]) > 0


def group_paragraphs(frags: list[Fragment]) -> list[Fragment]:
    """한 줄 조각 → 문단. 결과는 위→아래, 왼→오 순서."""
    frags = [f for f in frags if f.text.strip()]
    paras: list[list[Fragment]] = []
    for line in sorted(_merge_rows(frags), key=lambda f: (f.bbox[1], f.bbox[0])):
        # 가장 최근에 끝난 문단부터 확인 (가까운 문단에 붙도록)
        for para in sorted(paras, key=lambda p: -p[-1].bbox[3]):
            if _belongs(para, line):
                para.append(line)
                break
        else:
            paras.append([line])

    out = []
    for para in paras:
        bbox = para[0].bbox
        for f in para[1:]:
            bbox = _union(bbox, f.bbox)
        text = clean_text("\n".join(f.text for f in para))   # 영어 하이픈 줄바꿈 처리 재사용
        out.append(Fragment(text, bbox, _weighted_conf(para)))
    out.sort(key=lambda f: (f.bbox[1], f.bbox[0]))
    return out
