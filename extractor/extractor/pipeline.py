"""전체 흐름 조립 (0~6단계).

현재 연결된 단계: 2 (텍스트 레이어 추출).
나머지 단계는 구현되는 대로 extract() 안에 끼워 넣는다.
"""
from pathlib import Path

import pymupdf

from .config import DEFAULT, Thresholds
from .errors import (CorruptedFileError, EncryptedFileError, InputNotFoundError,
                     UnsupportedFormatError)
from .models import Item
from .stage2_text import extract_text_blocks


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


def extract(path: str | Path, th: Thresholds = DEFAULT) -> list[Item]:
    """파일 하나를 받아 항목 목록을 돌려준다.

    최종 흐름 (예정):
    for page in pdf:
        type = classify_page            (1)
        blocks = extract_text_blocks    (2)  ← 현재 여기까지
        SCANNED    → ocr_full_page      (4)
        TEXT       → select_ocr_targets (3) → ocr_image (4)
        SEARCHABLE → OCR 생략
        merge                           (5)
    """
    path = Path(path)
    items: list[Item] = []
    with open_pdf(path) as doc:
        for page_no, page in enumerate(doc, start=1):
            for text, bbox in extract_text_blocks(page):
                items.append(Item(
                    source_file=path.name,
                    source_format="pdf",
                    page=page_no,
                    origin="text",
                    content=text,
                    bbox=bbox,
                ))
    return items
