"""0단계: 입력 형식 판별, PPT → PDF 변환 테스트.

진짜 변환 테스트는 LibreOffice 가 있을 때만 돌고, 없으면 건너뛴다.
"""
import json
import os
import stat
import sys
import zipfile

import pymupdf
import pytest
from pptx import Presentation
from pptx.util import Inches, Pt

from extractor import stage0_input, stage4_ocr
from extractor.__main__ import main
from extractor.errors import (ConversionError, CorruptedFileError, EncryptedFileError,
                              InputNotFoundError, UnsupportedFormatError)
from extractor.pipeline import extract
from extractor.stage0_input import OLE_MAGIC, as_pdf, detect_format, find_soffice

needs_soffice = pytest.mark.skipif(find_soffice() is None, reason="LibreOffice 없음")


def make_pptx(path, slides, picture: bytes | None = None):
    """slides: 슬라이드마다 (제목, 본문). picture 를 주면 첫 슬라이드 아래쪽에 그림을 넣는다."""
    prs = Presentation()
    for title, body in slides:
        s = prs.slides.add_slide(prs.slide_layouts[1])      # 제목 + 내용
        s.shapes.title.text = title
        s.placeholders[1].text = body
    if picture:
        import io
        prs.slides[0].shapes.add_picture(io.BytesIO(picture), Inches(1), Inches(5.5),
                                         Inches(4), Inches(1.5))
    prs.save(path)
    return path


def png(w=400, h=150) -> bytes:
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, w, h), False)
    pix.clear_with(220)
    return pix.tobytes("png")


# ---- 형식 판별 ----

def test_detect_pdf(tmp_path):
    p = tmp_path / "a.pdf"
    p.write_bytes(b"%PDF-1.7 ...")
    assert detect_format(p) == "pdf"


def test_detect_pptx(tmp_path):
    assert detect_format(make_pptx(tmp_path / "a.pptx", [("t", "b")])) == "pptx"


def test_content_wins_over_extension(tmp_path):
    # 확장자가 틀려도 내용이 PDF 면 PDF
    p = tmp_path / "a.pptx"
    p.write_bytes(b"%PDF-1.7 ...")
    assert detect_format(p) == "pdf"
    q = make_pptx(tmp_path / "b.pptx", [("t", "b")])
    assert detect_format(q.rename(tmp_path / "b.pdf")) == "pptx"


def test_detect_ppt(tmp_path):
    p = tmp_path / "old.ppt"
    p.write_bytes(OLE_MAGIC + b"\0" * 100)
    assert detect_format(p) == "ppt"


def test_word_file_is_unsupported(tmp_path):
    docx = tmp_path / "a.docx"
    with zipfile.ZipFile(docx, "w") as z:
        z.writestr("word/document.xml", "<w/>")
    with pytest.raises(UnsupportedFormatError):
        detect_format(docx)
    doc = tmp_path / "a.doc"
    doc.write_bytes(OLE_MAGIC + b"\0" * 100)
    with pytest.raises(UnsupportedFormatError):
        detect_format(doc)


@pytest.mark.parametrize("name, data", [
    ("bad.pptx", b"PK\x03\x04 broken zip"),       # zip 이 깨짐
    ("bad.pptx", b"hello"),                         # 확장자만 pptx
    ("bad.ppt", b"hello"),
    ("bad.pdf", b"hello"),
    ("empty.pdf", b""),
])
def test_corrupted(tmp_path, name, data):
    p = tmp_path / name
    p.write_bytes(data)
    with pytest.raises(CorruptedFileError):
        detect_format(p)


def test_encrypted_pptx(tmp_path):
    # 암호 걸린 pptx 는 OLE 파일 안에 EncryptedPackage 항목이 있다
    p = tmp_path / "secret.pptx"
    p.write_bytes(OLE_MAGIC + b"\0" * 50 + "EncryptedPackage".encode("utf-16-le") + b"\0" * 50)
    with pytest.raises(EncryptedFileError):
        detect_format(p)


def test_missing_and_unsupported(tmp_path):
    with pytest.raises(InputNotFoundError):
        detect_format(tmp_path / "없음.pptx")
    t = tmp_path / "a.txt"
    t.write_text("hi")
    with pytest.raises(UnsupportedFormatError):
        detect_format(t)


# ---- LibreOffice 찾기 / 변환 실패 (가짜 soffice) ----

def fake_soffice(tmp_path, script: str):
    """가짜 soffice 스크립트를 만들어 LIBREOFFICE_PATH 로 쓰게 한다."""
    if sys.platform == "win32":
        pytest.skip("가짜 soffice 는 sh 스크립트")
    f = tmp_path / "fake-soffice"
    f.write_text("#!/bin/sh\n" + script)
    f.chmod(f.stat().st_mode | stat.S_IEXEC)
    return str(f)


def test_env_path_is_used_first(tmp_path, monkeypatch):
    path = fake_soffice(tmp_path, "exit 0\n")
    monkeypatch.setenv("LIBREOFFICE_PATH", path)
    assert find_soffice() == path


def test_no_soffice(tmp_path, monkeypatch):
    monkeypatch.setattr(stage0_input, "find_soffice", lambda: None)
    pptx = make_pptx(tmp_path / "a.pptx", [("t", "b")])
    with pytest.raises(ConversionError, match="LibreOffice"):
        with as_pdf(pptx, 10):
            pass


def test_timeout(tmp_path, monkeypatch):
    monkeypatch.setenv("LIBREOFFICE_PATH", fake_soffice(tmp_path, "sleep 5\n"))
    pptx = make_pptx(tmp_path / "a.pptx", [("t", "b")])
    with pytest.raises(ConversionError, match="1초"):
        with as_pdf(pptx, 1):
            pass


def test_no_output(tmp_path, monkeypatch):
    monkeypatch.setenv("LIBREOFFICE_PATH", fake_soffice(tmp_path, "echo 'Error: source file could not be loaded' >&2\nexit 0\n"))
    pptx = make_pptx(tmp_path / "a.pptx", [("t", "b")])
    with pytest.raises(ConversionError, match="could not be loaded"):
        with as_pdf(pptx, 10):
            pass


def test_temp_folder_is_removed(tmp_path, monkeypatch):
    # 가짜 soffice 가 --outdir 에 input.pdf 를 만든다
    pdf = tmp_path / "src.pdf"
    d = pymupdf.open(); d.new_page(); d.save(pdf)
    script = f'while [ "$1" != "--outdir" ]; do shift; done\ncp "{pdf}" "$2/input.pdf"\n'
    monkeypatch.setenv("LIBREOFFICE_PATH", fake_soffice(tmp_path, script))
    pptx = make_pptx(tmp_path / "a.pptx", [("t", "b")])
    with as_pdf(pptx, 10) as (out, fmt):
        assert fmt == "pptx" and out.is_file() and out.parent != tmp_path
        work = out.parent
    assert not work.exists()


# ---- 진짜 변환 (LibreOffice 필요) ----

@needs_soffice
def test_pptx_end_to_end(tmp_path, monkeypatch):
    monkeypatch.delenv("LIBREOFFICE_PATH", raising=False)
    monkeypatch.setattr(stage4_ocr, "run_ocr",
                        lambda b: [("new FilelnputStream(f)", (10.0, 10.0, 300.0, 50.0), 0.95)])
    src = make_pptx(tmp_path / "강의 자료 1장.pptx", [
        ("파일 입출력", "FileInputStream 으로 파일을 읽는다"),
        ("두 번째 슬라이드", "스트림은 바이트 단위로 읽는다"),
    ], picture=png())
    items = extract(src)

    assert {i.source_file for i in items} == {"강의 자료 1장.pptx"}
    assert {i.source_format for i in items} == {"pptx"}
    by_page = {}
    for i in items:
        # 본문 칸의 글머리 기호(•)도 글자로 나온다 (PDF 와 같음, 지우기는 8단계에서)
        by_page.setdefault(i.page, []).append((i.origin, i.content.removeprefix("• ")))
    assert sorted(by_page) == [1, 2]                         # 슬라이드 번호 = page
    assert ("text", "파일 입출력") in by_page[1]
    assert ("text", "FileInputStream 으로 파일을 읽는다") in by_page[1]
    assert ("ocr", "new FileInputStream(f)") in by_page[1]   # 슬라이드 속 그림도 OCR
    assert ("text", "스트림은 바이트 단위로 읽는다") in by_page[2]
    assert all(origin == "text" for origin, _ in by_page[2])


@needs_soffice
def test_old_ppt_and_cli(tmp_path, monkeypatch):
    """옛날 .ppt 는 LibreOffice 로 만들어서 확인한다."""
    import subprocess
    monkeypatch.delenv("LIBREOFFICE_PATH", raising=False)
    pptx = make_pptx(tmp_path / "old.pptx", [("옛날 형식", "PPT 97 파일도 읽는다")])
    subprocess.run([find_soffice(), "--headless",
                    f"-env:UserInstallation={(tmp_path / 'prof').as_uri()}",
                    "--convert-to", "ppt", "--outdir", str(tmp_path / "ppt"), str(pptx)],
                   check=True, capture_output=True, timeout=120)
    ppt = tmp_path / "ppt" / "old.ppt"
    assert detect_format(ppt) == "ppt"

    out = tmp_path / "out.json"
    assert main([str(ppt), "-o", str(out)]) == 0
    data = json.loads(out.read_text(encoding="utf-8"))
    assert {d["source_format"] for d in data} == {"ppt"}
    assert [d["content"].removeprefix("• ") for d in data] == ["옛날 형식", "PPT 97 파일도 읽는다"]
