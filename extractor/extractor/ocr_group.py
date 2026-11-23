"""OCR 결과(한 줄 조각들)를 문단으로 묶는다.

RapidOCR 는 글자를 '한 줄 조각' 단위로 돌려준다. 텍스트 레이어 추출(2단계)이
문단 단위이므로 OCR 결과도 같은 단위로 맞춘다.

1) 같은 줄 조각 합치기: 세로로 많이 겹치고 가로로 가까우면 한 줄
   (예: 글머리 기호 '•' 와 본문, 코드 캡처의 줄 번호 '07:' 와 코드가 따로 인식된 경우)
2) 줄 → 문단: 바로 아래 줄이고, 글자 크기가 비슷하고, 가로 범위가 겹치면 같은 문단
   (제목처럼 글자 크기가 다르면 다른 문단, 2단 편집처럼 가로로 떨어져 있으면 다른 문단)

8단계에서 추가 (강의 PDF 의 코드 캡처·표 그림을 보고 정한 규칙):
3) 코드 (kind="code"): 줄 번호 "01:" 나 ; { } · 자바 예약어가 있는 조각을 코드로 보고,
   코드 줄끼리만 모아 줄바꿈(\n)을 지킨 한 덩어리로 만든다.
   코드 옆 한글 설명 말풍선은 코드에 끼우지 않고 따로 일반 문단으로 둔다.
4) 표 (kind="table"): 왼쪽 끝이 같은 세로줄이 2개 이상이고 서로 같은 높이에 여러 번
   나란히 있으면 표로 본다. 칸끼리는 합치지 않고 행마다 "칸 | 칸" 한 항목으로 만든다.
"""
import re
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


# 코드: 줄 번호 "01:" 로 시작
_LINE_NO = re.compile(r"^\d{1,3}:(\s|$)")
# 줄 번호만 있는 조각 (코드는 따로 인식된 경우)
_BARE_LINE_NO = re.compile(r"^\d{1,3}:\s*$")
# 코드: ; { } 가 있거나 자바 예약어·System. 으로 시작 (괄호만으로는 판단 안 함: 표의 메소드 이름 칸과 구별)
_CODE_SIGNAL = re.compile(
    r"[;{}]|^\s*(import|package|public|private|protected|class|if|else|for|while|switch|"
    r"case|return|try|catch|finally|throw|new)\b|^\s*System\.")
# 코드와 말풍선이 한 조각으로 인식된 경우의 화살표: 'new File("/");← 파일의 경로'
_ARROW = re.compile(r"\s*[←→↑↓]+[\s—–-]*")
_HANGUL = re.compile(r"[가-힣]")
# 표: 왼쪽 끝이 이 거리(pt) 안이면 같은 세로줄
TABLE_COLUMN_TOLERANCE = 6.0
# 표: 세로줄 하나에 조각이 이 개수 이상, 두 세로줄이 같은 높이에 나란히 이 횟수 이상
TABLE_MIN_ROWS = 3


@dataclass
class Fragment:
    text: str
    bbox: BBox
    confidence: float
    kind: str = "text"        # text | code | table


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


def _paragraphs(frags: list[Fragment]) -> list[Fragment]:
    """일반 글자: 한 줄 조각 → 문단."""
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
        text = clean_text("\n".join(f.text for f in para))   # 영어 하이픈 줄바꿈 처리 재사용
        out.append(Fragment(text, _union_all(para), _weighted_conf(para)))
    return out


def _union_all(frags: list[Fragment]) -> BBox:
    bbox = frags[0].bbox
    for f in frags[1:]:
        bbox = _union(bbox, f.bbox)
    return bbox


def _v_overlap_ratio(a: BBox, b: BBox) -> float:
    h = min(_height(a), _height(b))
    return (min(a[3], b[3]) - max(a[1], b[1])) / h if h > 0 else 0.0


# ---------------- 코드 ----------------

def _split_arrows(frags: list[Fragment]) -> list[Fragment]:
    """코드 + 화살표 + 한글 설명이 한 조각이면 둘로 나눈다. 좌표는 글자 수 비율로 나눈다."""
    out = []
    for f in frags:
        m = _ARROW.search(f.text)
        head, tail = (f.text[:m.start()], f.text[m.end():]) if m else (f.text, "")
        if not (m and _CODE_SIGNAL.search(_LINE_NO.sub("", head)) and _HANGUL.search(tail)):
            out.append(f)
            continue
        x0, y0, x1, y1 = f.bbox
        cut = x0 + (x1 - x0) * m.start() / max(len(f.text), 1)
        out.append(Fragment(head, (x0, y0, cut, y1), f.confidence))
        out.append(Fragment(tail, (cut, y0, x1, y1), f.confidence))
    return out


def _is_code(f: Fragment) -> bool:
    return bool(_LINE_NO.match(f.text) or _CODE_SIGNAL.search(f.text))


def _pick_code(frags: list[Fragment]) -> tuple[list[Fragment], list[Fragment]]:
    """코드 조각과 나머지로 나눈다.

    예약어도 기호도 없는 코드 줄(예: 혼자 있는 'else')은, 줄 번호만 있는 조각 바로 오른쪽 가까이
    있으면 코드로 본다. 멀리 떨어진 것은 빈 코드 줄 옆의 설명 말풍선일 수 있어서 제외.
    """
    code = {id(f) for f in frags if _is_code(f)}
    for num in (f for f in frags if _BARE_LINE_NO.match(f.text)):
        h = _height(num.bbox)
        right = [f for f in frags if f is not num and f.bbox[0] >= num.bbox[2] - 1
                 and _v_overlap_ratio(f.bbox, num.bbox) >= SAME_ROW_OVERLAP
                 and f.bbox[0] - num.bbox[2] <= SHORT_PIECE_GAP * h]
        if right:
            code.add(id(min(right, key=lambda f: f.bbox[0])))
    return [f for f in frags if id(f) in code], [f for f in frags if id(f) not in code]


def _code_rows(frags: list[Fragment]) -> list[Fragment]:
    """코드 조각 → 코드 줄. 줄 번호만 있는 조각이 있으면 같은 높이의 오른쪽 조각을 거리와 상관없이
    그 번호 줄에 붙인다 (깊게 들여쓴 줄도 자기 번호와 함께). 번호 없는 조각은 일반 규칙으로 줄을 만든다."""
    nums = [f for f in frags if _BARE_LINE_NO.match(f.text)]
    attached: dict[int, list[Fragment]] = {id(n): [] for n in nums}
    loose = []
    for f in frags:
        if _BARE_LINE_NO.match(f.text):
            continue
        cands = [n for n in nums if n.bbox[2] <= f.bbox[0] + 1
                 and _v_overlap_ratio(n.bbox, f.bbox) >= SAME_ROW_OVERLAP]
        if cands:
            attached[id(max(cands, key=lambda n: _v_overlap_ratio(n.bbox, f.bbox)))].append(f)
        else:
            loose.append(f)
    rows = []
    for n in nums:
        parts = [n] + sorted(attached[id(n)], key=lambda f: f.bbox[0])
        rows.append(Fragment(" ".join(p.text.strip() for p in parts), _union_all(parts),
                             _weighted_conf(parts)))
    return rows + _merge_rows(loose)


def _code_blocks(frags: list[Fragment]) -> tuple[list[Fragment], list[Fragment]]:
    """코드 줄 → 코드 덩어리 (줄바꿈 유지). 한 줄짜리이고 줄 번호도 없으면 코드로 보지 않고 돌려준다."""
    blocks: list[list[Fragment]] = []
    for row in sorted(_code_rows(frags), key=lambda f: f.bbox[1]):
        if blocks:
            last = blocks[-1][-1]
            near = row.bbox[1] - last.bbox[3] <= 1.5 * _height(last.bbox)
            if near:
                blocks[-1].append(row)
                continue
        blocks.append([row])

    out, back = [], []
    for rows in blocks:
        if len(rows) == 1 and not _LINE_NO.match(rows[0].text):
            back.extend(f for f in frags if _inside(f.bbox, rows[0].bbox))
            continue
        out.append(Fragment("\n".join(r.text.strip() for r in rows), _union_all(rows),
                            _weighted_conf(rows), "code"))
    return out, back


def _inside(a: BBox, b: BBox) -> bool:
    return a[0] >= b[0] - 0.01 and a[1] >= b[1] - 0.01 and a[2] <= b[2] + 0.01 and a[3] <= b[3] + 0.01


# ---------------- 표 ----------------

def _columns(frags: list[Fragment]) -> list[list[Fragment]]:
    """왼쪽 끝이 같은 조각끼리 모은 세로줄 (TABLE_MIN_ROWS 개 이상인 것만), 왼쪽부터."""
    cols: list[list[Fragment]] = []
    for f in sorted(frags, key=lambda f: f.bbox[0]):
        if cols and abs(f.bbox[0] - cols[-1][0].bbox[0]) <= TABLE_COLUMN_TOLERANCE:
            cols[-1].append(f)
        else:
            cols.append([f])
    return [c for c in cols if len(c) >= TABLE_MIN_ROWS]


def _side_by_side(a: list[Fragment], b: list[Fragment]) -> int:
    """두 세로줄의 조각이 같은 높이에 나란히 있는 횟수."""
    return sum(any(_v_overlap_ratio(x.bbox, y.bbox) > 0 for y in b) for x in a)


def _pick_table(frags: list[Fragment]) -> tuple[list[Fragment], list[Fragment]]:
    """표가 있으면 (표 행 목록, 나머지 조각), 없으면 ([], frags)."""
    cols = _columns(frags)
    # 왼쪽 세로줄과 나란한 세로줄만 표의 칸으로 본다
    table_cols = []
    for c in cols:
        if not table_cols:
            table_cols.append(c)
        elif c[0].bbox[0] > table_cols[-1][0].bbox[0] and \
                _side_by_side(table_cols[0], c) >= TABLE_MIN_ROWS:
            table_cols.append(c)
    if len(table_cols) < 2:
        # 첫 세로줄이 표가 아닐 수도 있으니 두 번째부터 다시
        return ([], frags) if len(cols) < 3 else _retry_table(frags, cols)
    return _build_table(frags, table_cols)


def _retry_table(frags, cols):
    for i in range(1, len(cols) - 1):
        rest = cols[i:]
        if _side_by_side(rest[0], rest[1]) >= TABLE_MIN_ROWS:
            return _build_table(frags, [c for c in rest
                                        if c is rest[0] or _side_by_side(rest[0], c) >= TABLE_MIN_ROWS])
    return [], frags


def _build_table(frags: list[Fragment], table_cols: list[list[Fragment]]):
    # 옆 칸에 같은 높이 짝이 있는 조각만 표 범위를 정하는 데 쓴다
    # (표 왼쪽 끝과 줄이 맞는 표 제목·설명 글자가 표에 끼지 않게)
    members = [f for i, c in enumerate(table_cols) for f in c
               if any(_v_overlap_ratio(f.bbox, g.bbox) > 0
                      for j, d in enumerate(table_cols) if j != i for g in d)]
    paired = {id(f) for f in members}
    ranges = [(min(f.bbox[0] for f in c if id(f) in paired), max(f.bbox[2] for f in c if id(f) in paired))
              for c in table_cols]
    top = min(f.bbox[1] for f in members)
    bottom = max(f.bbox[3] for f in members)
    h = sorted(_height(f.bbox) for f in members)[len(members) // 2]

    def col_of(f: Fragment) -> int | None:
        best, best_ov = None, 0.0
        for i, (l, r) in enumerate(ranges):
            ov = min(r, f.bbox[2]) - max(l, f.bbox[0])
            if ov > best_ov:
                best, best_ov = i, ov
        return best

    def spans_columns(f: Fragment) -> bool:
        """여러 칸에 걸친 조각 (표 제목 등)."""
        return sum(min(r, f.bbox[2]) - max(l, f.bbox[0]) > 0.3 * (r - l) for l, r in ranges) >= 2

    cells = [f for f in frags
             if top <= (f.bbox[1] + f.bbox[3]) / 2 <= bottom and col_of(f) is not None]
    taken = {id(f) for f in cells}
    def has_partner(f: Fragment) -> bool:
        return any(g is not f and col_of(g) not in (None, col_of(f))
                   and _v_overlap_ratio(f.bbox, g.bbox) > 0 for g in frags)

    # 위·아래로 바로 붙은 조각도 한 칸 안에 들어가면 표에 넣는다
    # (OCR 이 칸 첫 글자를 빠뜨려 세로줄이 어긋난 행, "메소드 이름 | 설명" 같은 머리글 행)
    # 위쪽은 옆 칸에 짝이 있는 줄만 (한 칸짜리 표 제목은 제외), 아래쪽은 한 칸만 있어도 (감긴 줄)
    grew = True
    while grew:
        grew = False
        for f in frags:
            if id(f) in taken or col_of(f) is None or spans_columns(f):
                continue
            above = f.bbox[1] < top
            if top - 1.5 * h <= f.bbox[3] and f.bbox[1] <= bottom + 1.5 * h \
                    and (not above or has_partner(f)):
                cells.append(f)
                taken.add(id(f))
                top, bottom = min(top, f.bbox[1]), max(bottom, f.bbox[3])
                grew = True

    # 행 나누기: 세로로 겹치거나 맞닿은 조각끼리 한 행 (여러 줄로 감긴 칸도 한 행)
    rows: list[list[Fragment]] = []
    row_bottom = None
    for f in sorted(cells, key=lambda f: f.bbox[1]):
        if rows and f.bbox[1] <= row_bottom + 1:
            rows[-1].append(f)
            row_bottom = max(row_bottom, f.bbox[3])
        else:
            rows.append([f])
            row_bottom = f.bbox[3]
    # 첫 칸이 빈 행, 또는 한 칸만 있고 윗행에 바로 붙은 행은 윗행이 이어진 것으로 본다
    merged: list[list[Fragment]] = []
    for r in rows:
        one_col = len({col_of(f) for f in r}) == 1
        touching = merged and min(f.bbox[1] for f in r) - max(f.bbox[3] for f in merged[-1]) <= 0.5 * h
        if merged and (all(col_of(f) != 0 for f in r) or (one_col and touching)):
            merged[-1].extend(r)
        else:
            merged.append(r)

    out = []
    for r in merged:
        texts = []
        for i in range(len(ranges)):
            parts = sorted((f for f in r if col_of(f) == i), key=lambda f: (f.bbox[1], f.bbox[0]))
            if parts:
                texts.append(clean_text(" ".join(f.text for f in parts)))
        out.append(Fragment(" | ".join(texts), _union_all(r), _weighted_conf(r), "table"))
    return out, [f for f in frags if id(f) not in taken]


# ---------------- 전체 ----------------

def group_paragraphs(frags: list[Fragment]) -> list[Fragment]:
    """한 줄 조각 → 코드 덩어리 / 표 행 / 문단. 결과는 위→아래, 왼→오 순서."""
    frags = _split_arrows([f for f in frags if f.text.strip()])
    code, rest = _pick_code(frags)
    code_blocks, back = _code_blocks(code)
    table_rows, rest = _pick_table(rest + back)
    out = code_blocks + table_rows + _paragraphs(rest)
    out.sort(key=lambda f: (f.bbox[1], f.bbox[0]))
    return out
