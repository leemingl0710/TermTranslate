"""2단계(텍스트 레이어 추출) 테스트. 테스트용 PDF는 코드로 만든다."""
import json

import pymupdf
import pytest

from extractor.__main__ import main
from extractor.errors import CorruptedFileError, EncryptedFileError, UnsupportedFormatError
from extractor.pipeline import extract
from extractor.stage2_text import clean_text

KO = "korea"  # PyMuPDF 내장 한글 글꼴 (맥/윈도우 공통)


def make_pdf(path, pages):
    """pages: 페이지별 [(x, y, 글자)] 목록."""
    doc = pymupdf.open()
    for blocks in pages:
        page = doc.new_page()
        for x, y, text in blocks:
            page.insert_text((x, y), text, fontname=KO, fontsize=12)
    doc.save(path)
    return path


# ---- 글자 정리 ----

@pytest.mark.parametrize("raw, expected", [
    ("자료구조란\n데이터를 저장하는\n방법이다.\n", "자료구조란 데이터를 저장하는 방법이다."),
    ("an efficient algo-\nrithm", "an efficient algorithm"),
    ("well-known\nmethod", "well-known method"),   # 줄 중간 하이픈은 유지
    ("a   b\t\tc", "a b c"),
    ("  \n \n", ""),
])
def test_clean_text(raw, expected):
    assert clean_text(raw) == expected


# ---- 추출 ----

def test_korean_and_english_blocks(tmp_path):
    pdf = make_pdf(tmp_path / "a.pdf", [[
        (72, 100, "자료구조란\n데이터를 저장하는 방법이다."),
        (72, 300, "A stack is a LIFO structure."),
    ]])
    items = extract(pdf)
    assert [i.content for i in items] == [
        "자료구조란 데이터를 저장하는 방법이다.",
        "A stack is a LIFO structure.",
    ]
    first = items[0]
    assert (first.source_file, first.source_format, first.page) == ("a.pdf", "pdf", 1)
    assert first.origin == "text" and first.confidence is None
    x0, y0, x1, y1 = first.bbox
    assert x0 < x1 and y0 < y1 and y1 < 300   # 첫 문단은 두 번째보다 위


def test_page_numbers(tmp_path):
    pdf = make_pdf(tmp_path / "b.pdf", [[(72, 100, "첫 페이지")], [(72, 100, "둘째 페이지")]])
    assert [(i.page, i.content) for i in extract(pdf)] == [(1, "첫 페이지"), (2, "둘째 페이지")]


def test_empty_page(tmp_path):
    pdf = make_pdf(tmp_path / "c.pdf", [[]])
    assert extract(pdf) == []


def test_image_block_ignored(tmp_path):
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text((72, 100), "본문", fontname=KO)
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, 20, 20), False)
    pix.clear_with(200)
    page.insert_image(pymupdf.Rect(72, 200, 172, 300), pixmap=pix)
    doc.save(tmp_path / "d.pdf")
    assert [i.content for i in extract(tmp_path / "d.pdf")] == ["본문"]


# ---- 오류 ----

def test_corrupted(tmp_path):
    bad = tmp_path / "bad.pdf"
    bad.write_bytes(b"%PDF-1.7 garbage")
    with pytest.raises(CorruptedFileError):
        extract(bad)


def test_encrypted(tmp_path):
    doc = pymupdf.open()
    doc.new_page().insert_text((72, 72), "secret")
    p = tmp_path / "enc.pdf"
    doc.save(p, encryption=pymupdf.PDF_ENCRYPT_AES_256, user_pw="u", owner_pw="o")
    with pytest.raises(EncryptedFileError):
        extract(p)


def test_unsupported(tmp_path):
    f = tmp_path / "a.txt"
    f.write_text("hi")
    with pytest.raises(UnsupportedFormatError):
        extract(f)


# ---- CLI ----

def test_cli_writes_json(tmp_path):
    pdf = make_pdf(tmp_path / "e.pdf", [[(72, 100, "번역할 문단")]])
    out = tmp_path / "out.json"
    assert main([str(pdf), "-o", str(out)]) == 0
    data = json.loads(out.read_text(encoding="utf-8"))
    assert data[0]["content"] == "번역할 문단" and len(data[0]["bbox"]) == 4


def test_cli_error_code(tmp_path, capsys):
    assert main([str(tmp_path / "없는파일.pdf")]) == 2
    err = json.loads(capsys.readouterr().err)
    assert err["error"] == "InputNotFoundError"
