"""전체 흐름 조립.

for 페이지 in PDF:
    종류 = classify_page                          (1단계)
    글자 문단 = extract_text_blocks               (2단계)
    SCANNED    → ocr_full_page                    (4단계, 페이지 전체 OCR)
    TEXT       → select_ocr_targets → ocr_image   (3·4단계, 사진만 OCR)
    SEARCHABLE → OCR 생략 (이미 글자가 있음)
문서 본문 단어로 OCR 글자 고치기 (ocr_fix)
merge: 중복 제거 + 읽는 순서 정렬               (5단계)

TODO(0단계): PPT/PPTX → PDF 변환.
"""
from pathlib import Path

import pymupdf

from .config import DEFAULT, Thresholds
from .errors import (CorruptedFileError, EncryptedFileError, InputNotFoundError,
                     UnsupportedFormatError)
from .models import Item, PageType
from .ocr_fix import OcrFixer
from .stage1_classify import classify_page
from .stage2_text import extract_text_blocks
from .stage3_select import OcrTarget, select_ocr_targets
from .stage4_ocr import OcrResult, ocr_full_page, ocr_image
from .stage5_merge import merge


def open_pdf(path: Path) -> "pymupdf.Document":
    """PDF를 열고, 손상·암호 파일이면 정해진 오류를 낸다.

    TODO(0단계): 시그니처 판별 + PPT/PPTX → PDF 변환으로 교체.
    """
    if not path.is_file():
        raise InputNotFoundError(f"파일이 없습니다: {path}")
    if path.suffix.lower() != ".pdf":
        raise UnsupportedFormatError(f"아직 PDF만 지원합니다: {path.name}")
    try:
        doc = pymupdf.open(path, filetype="pdf")
    except (pymupdf.FileDataError, pymupdf.EmptyFileError) as e:
        raise CorruptedFileError(f"PDF 파일이 손상되었습니다: {path.name}") from e
    if doc.needs_pass:
        doc.close()
        raise EncryptedFileError(f"암호가 걸린 PDF입니다: {path.name}")
    return doc


class _ImageOcrCache:
    """같은 그림이 같은 크기로 여러 페이지에 있으면 한 번만 OCR 한다 (예: 반복되는 도표).

    결과는 그림 왼쪽 위 기준 상대 좌표로 저장했다가, 페이지마다 놓인 위치로 옮겨 쓴다.
    """

    def __init__(self):
        self._store: dict[tuple, list[OcrResult]] = {}

    def get(self, page: "pymupdf.Page", target: OcrTarget, th: Thresholds) -> list[OcrResult]:
        x0, y0, x1, y1 = target.bbox
        if target.xref == 0:                       # 페이지에 직접 박힌 그림은 구별할 수 없음
            return ocr_image(page, target, th)
        key = (target.xref, round(x1 - x0, 1), round(y1 - y0, 1))
        if key not in self._store:
            self._store[key] = [
                OcrResult(r.text, (r.bbox[0] - x0, r.bbox[1] - y0, r.bbox[2] - x0, r.bbox[3] - y0),
                          r.confidence)
                for r in ocr_image(page, target, th)
            ]
        return [
            OcrResult(r.text, (r.bbox[0] + x0, r.bbox[1] + y0, r.bbox[2] + x0, r.bbox[3] + y0),
                      r.confidence)
            for r in self._store[key]
        ]


def page_ocr(page: "pymupdf.Page", text_blocks, th: Thresholds, cache: _ImageOcrCache) -> list[OcrResult]:
    """페이지 종류에 따라 필요한 OCR 만 한다."""
    kind = classify_page(page, th)
    if kind == PageType.SCANNED:
        return ocr_full_page(page, th)
    if kind == PageType.SEARCHABLE_SCAN:
        return []
    results = []
    for target in select_ocr_targets(page, text_blocks, th):
        results.extend(cache.get(page, target, th))
    return results


def extract(path: str | Path, th: Thresholds = DEFAULT) -> list[Item]:
    """파일 하나를 받아 항목 목록을 돌려준다 (페이지 순, 페이지 안에서는 위→아래)."""
    path = Path(path)
    text_items: list[Item] = []
    ocr_items: list[Item] = []
    cache = _ImageOcrCache()

    def item(page_no, origin, content, bbox, confidence=None):
        return Item(source_file=path.name, source_format="pdf", page=page_no,
                    origin=origin, content=content, bbox=bbox, confidence=confidence)

    with open_pdf(path) as doc:
        for page_no, page in enumerate(doc, start=1):
            blocks = extract_text_blocks(page)
            text_items += [item(page_no, "text", text, bbox) for text, bbox in blocks]
            ocr_items += [item(page_no, "ocr", r.text, r.bbox, r.confidence)
                          for r in page_ocr(page, blocks, th, cache)]

    # 문서 전체 본문을 다 모은 뒤에 고친다 (다른 페이지 본문에 나온 단어도 쓰려고)
    fixer = OcrFixer(i.content for i in text_items)
    for o in ocr_items:
        o.content = fixer.fix(o.content)
    return merge(text_items, ocr_items, th)
