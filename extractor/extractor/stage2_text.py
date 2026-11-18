"""2단계. 텍스트 레이어를 문단(블록) 단위로 좌표와 함께 추출."""
import re

import pymupdf

from .models import BBox

_BLOCK_TEXT = 0  # get_text("blocks") 의 마지막 값: 0=글자, 1=이미지

# 줄 끝 하이픈으로 끊긴 영어 단어: "algo-\nrithm" → "algorithm"
_HYPHEN_BREAK = re.compile(r"([A-Za-z])-\n([a-z])")
_SPACES = re.compile(r"\s+")


def clean_text(raw: str) -> str:
    """문단 안 줄바꿈을 이어 붙이고 공백을 정리한다."""
    text = _HYPHEN_BREAK.sub(r"\1\2", raw)
    return _SPACES.sub(" ", text).strip()  # 줄바꿈·탭·연속 공백 → 공백 하나


def extract_text_blocks(page: "pymupdf.Page") -> list[tuple[str, BBox]]:
    """페이지의 글자 문단을 [(문단 글자, (x0, y0, x1, y1))] 로 돌려준다.

    - 이미지 칸은 버린다 (사진 속 글자는 3·4단계에서 OCR).
    - 순서는 PDF에 저장된 순서. 위→아래 정렬은 5단계에서 한다.
    TODO(8단계): 머리말/꼬리말/페이지 번호 제거, 2단 편집·표 읽는 순서.
    """
    result = []
    for x0, y0, x1, y1, raw, _no, kind in page.get_text("blocks"):
        if kind != _BLOCK_TEXT:
            continue
        text = clean_text(raw)
        if text:
            result.append((text, (x0, y0, x1, y1)))
    return result
