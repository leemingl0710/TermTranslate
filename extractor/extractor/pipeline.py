"""전체 흐름 조립.

PPT/PPTX 면 PDF 로 변환                       (0단계)
for 페이지 in PDF:
    종류 = classify_page                          (1단계)
    글자 문단 = extract_text_blocks               (2단계)
    SCANNED    → ocr_full_page                    (4단계, 페이지 전체 OCR)
    TEXT       → select_ocr_targets → ocr_image   (3·4단계, 사진만 OCR)
    SEARCHABLE → OCR 생략 (이미 글자가 있음)
문서 본문 단어로 OCR 글자 고치기 (ocr_fix), 코드·표는 빈 괄호도 고침
merge: 중복 제거 + 읽는 순서 정렬               (5단계)
remove_noise: 페이지 번호, 반복 머리말·꼬리말 지우기 (8-1)
"""
from pathlib import Path

import pymupdf

from .cleanup import remove_noise
from .config import DEFAULT, Thresholds
from .errors import CorruptedFileError, EncryptedFileError
from .models import Item, PageType
from .ocr_fix import OcrFixer, fix_parens
from .stage0_input import as_pdf
from .stage1_classify import classify_page
from .stage2_text import extract_text_blocks
from .stage3_select import OcrTarget, select_ocr_targets
from .stage4_ocr import OcrResult, ocr_full_page, ocr_image
from .stage5_merge import merge


def open_pdf(path: Path, display_name: str | None = None) -> "pymupdf.Document":
    """PDF를 열고, 손상·암호 파일이면 정해진 오류를 낸다.

    display_name: 오류 메시지에 쓸 이름 (PPT 를 변환한 임시 PDF 면 원래 파일 이름).
    """
    name = display_name or path.name
    try:
        doc = pymupdf.open(path, filetype="pdf")
    except (pymupdf.FileDataError, pymupdf.EmptyFileError) as e:
        raise CorruptedFileError(f"파일이 손상되었습니다: {name}") from e
    if doc.needs_pass:
        doc.close()
        raise EncryptedFileError(f"암호가 걸린 파일입니다: {name}")
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
                          r.confidence, r.kind)
                for r in ocr_image(page, target, th)
            ]
        return [
            OcrResult(r.text, (r.bbox[0] + x0, r.bbox[1] + y0, r.bbox[2] + x0, r.bbox[3] + y0),
                      r.confidence, r.kind)
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


def ocr_flags(r: OcrResult, th: Thresholds) -> list[str]:
    """OCR 결과에 붙일 표시: code(코드 캡처) / table(표 행) / low_confidence(신뢰도 낮음)."""
    flags = [r.kind] if r.kind in ("code", "table") else []
    if r.confidence < th.low_ocr_confidence:
        flags.append("low_confidence")
    return flags


def extract(path: str | Path, th: Thresholds = DEFAULT) -> list[Item]:
    """파일 하나를 받아 항목 목록을 돌려준다 (페이지 순, 페이지 안에서는 위→아래)."""
    path = Path(path)
    text_items: list[Item] = []
    ocr_items: list[Item] = []
    page_heights: dict[int, float] = {}
    cache = _ImageOcrCache()

    with as_pdf(path, th.convert_timeout_sec) as (pdf_path, fmt):

        def item(page_no, origin, content, bbox, confidence=None, flags=()):
            return Item(source_file=path.name, source_format=fmt, page=page_no,
                        origin=origin, content=content, bbox=bbox, confidence=confidence,
                        flags=list(flags))

        with open_pdf(pdf_path, path.name) as doc:
            for page_no, page in enumerate(doc, start=1):
                page_heights[page_no] = page.rect.height
                blocks = extract_text_blocks(page)
                text_items += [item(page_no, "text", text, bbox) for text, bbox in blocks]
                ocr_items += [item(page_no, "ocr", r.text, r.bbox, r.confidence, ocr_flags(r, th))
                              for r in page_ocr(page, blocks, th, cache)]

    # 문서 전체 본문을 다 모은 뒤에 고친다 (다른 페이지 본문에 나온 단어도 쓰려고)
    fixer = OcrFixer(i.content for i in text_items)
    for o in ocr_items:
        o.content = fixer.fix(o.content)
        if "code" in o.flags or "table" in o.flags:
            o.content = fix_parens(o.content)
    return remove_noise(merge(text_items, ocr_items, th), page_heights)
