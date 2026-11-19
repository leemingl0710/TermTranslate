"""4단계: OCR 글자 고치기, 중복 제거, 정렬, 전체 흐름 연결 테스트."""
import json

import pymupdf
import pytest

from extractor import pipeline, stage4_ocr
from extractor.__main__ import main
from extractor.config import DEFAULT
from extractor.models import Item
from extractor.ocr_fix import OcrFixer
from extractor.pipeline import extract
from extractor.stage5_merge import merge, reading_order, similarity

KO = "korea"


# ---- OCR 글자 고치기 ----

@pytest.fixture()
def fixer():
    return OcrFixer(["FileInputStream 클래스와 FileOutputStream 클래스", "DataInputStream 사용"],
                    extra_words=["IOException"])


@pytest.mark.parametrize("ocr, expected", [
    ("FilelnputStream", "FileInputStream"),       # 대문자 I → 소문자 l
    ("File0utputStream", "FileOutputStream"),     # 대문자 O → 숫자 0
    ("FileoutputStream", "FileOutputStream"),     # 대문자 O → 소문자 o
    ("DatalnputStream", "DataInputStream"),
    ("I0Exception", "IOException"),               # 기본 목록 단어
    ("dir0bj", "dirObj"),                          # 목록에 없어도 글자 사이 0 → O
    ("FileInputStream", "FileInputStream"),       # 맞는 단어는 그대로
    ("Test10", "Test10"),                          # 숫자 뒤 0 은 그대로
    ("available0", "available0"),                 # 단어 끝 0 은 그대로 (() 인지 알 수 없음)
    ("Hello", "Hello"),                            # 모르는 단어는 그대로
    ("FilelnputStreamTest", "FileInputStreamTest"),  # 아는 단어 + 뒤에 붙은 말
    ("DatalnputStreamTest1", "DataInputStreamTest1"),
    ("HelloWorld", "HelloWorld"),                  # 앞부분이 모르는 단어면 그대로
])
def test_fix_word(fixer, ocr, expected):
    assert fixer.fix_word(ocr) == expected


def test_fix_keeps_korean_and_spacing(fixer):
    assert fixer.fix("new FilelnputStream(\"a.txt\") 로 파일을 연다") == \
        "new FileInputStream(\"a.txt\") 로 파일을 연다"


def test_ambiguous_word_is_not_changed():
    # 'Iolt' 은 헷갈리는 글자만 바꾸면 'lolt' 와 'IOlt' 둘 다 될 수 있음 → 어느 쪽인지 모르니 그대로
    f = OcrFixer(["lolt IOlt"], extra_words=[])
    assert f.fix_word("Iolt") == "Iolt"
    # 하나뿐이면 고침
    assert OcrFixer(["line"], extra_words=[]).fix_word("Iine") == "line"


# ---- 중복 제거, 정렬 ----

def it(content, bbox, page=1, origin="text"):
    return Item("a.pdf", "pdf", page, origin, content, bbox, 0.9 if origin == "ocr" else None)


def test_similarity_short_inside_long():
    assert similarity("스택은 후입선출", "스택은 후입선출 구조이고 큐는 선입선출 구조이다") == 1.0
    assert similarity("전혀 다른 글자", "스택은 후입선출") < 0.5
    assert similarity("", "가") == 0.0


def test_ocr_duplicate_of_text_is_dropped():
    text = [it("스택은 후입선출 구조이다", (72, 100, 300, 120))]
    ocr = [
        it("스택은 후입선출 구조이다", (70, 98, 302, 122), origin="ocr"),     # 같은 자리, 같은 글자 → 버림
        it("스택은 후입선출 구조이다", (72, 500, 300, 520), origin="ocr"),    # 다른 자리 → 남김
        it("그림 속 다른 글자", (72, 102, 300, 118), origin="ocr"),           # 같은 자리, 다른 글자 → 남김
        it("스택은 후입선출 구조이다", (72, 100, 300, 120), page=2, origin="ocr"),  # 다른 페이지 → 남김
    ]
    out = merge(text, ocr, DEFAULT)
    assert len(out) == 4
    assert [(o.page, o.origin, o.bbox[1]) for o in out if o.origin == "ocr"] == \
        [(1, "ocr", 102), (1, "ocr", 500), (2, "ocr", 100)]


def test_reading_order():
    items = [
        it("2쪽", (72, 50, 200, 60), page=2),
        it("아래", (72, 400, 200, 410)),
        it("위 오른쪽", (300, 101, 400, 111)),
        it("위 왼쪽", (72, 100, 200, 110)),       # 높이가 1pt 차이 → 같은 줄, 왼쪽 먼저
    ]
    assert [i.content for i in reading_order(items)] == ["위 왼쪽", "위 오른쪽", "아래", "2쪽"]


# ---- 전체 흐름 (OCR 은 가짜로 바꿔 빠르게) ----

def png(w=300, h=200) -> bytes:
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, w, h), False)
    pix.clear_with(200)
    return pix.tobytes("png")


@pytest.fixture()
def fake_ocr(monkeypatch):
    """run_ocr 를 가짜로 바꾼다. 그림 픽셀 좌표 (10,10)-(200,40) 에 정해진 글자를 돌려준다."""
    calls = []

    def run(img_bytes):
        calls.append(len(img_bytes))
        return [("new FilelnputStream(f)", (10.0, 10.0, 200.0, 40.0), 0.95)]
    monkeypatch.setattr(stage4_ocr, "run_ocr", run)
    return calls


def test_text_page_with_photo(tmp_path, fake_ocr):
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text((72, 80), "FileInputStream 으로 파일을 읽는다", fontname=KO, fontsize=14)
    page.insert_image(pymupdf.Rect(72, 300, 372, 500), stream=png(), keep_proportion=False)
    doc.save(tmp_path / "s.pdf")

    items = extract(tmp_path / "s.pdf")
    assert [(i.origin, i.content) for i in items] == [
        ("text", "FileInputStream 으로 파일을 읽는다"),
        ("ocr", "new FileInputStream(f)"),        # 본문 단어로 I/l 고침
    ]
    ocr = items[1]
    assert ocr.confidence == pytest.approx(0.95)
    assert 72 <= ocr.bbox[0] < ocr.bbox[2] <= 372 and 300 <= ocr.bbox[1] < ocr.bbox[3] <= 500


def test_scanned_page_uses_full_page_ocr(tmp_path, fake_ocr):
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_image(page.rect, stream=png(), keep_proportion=False)
    doc.save(tmp_path / "scan.pdf")

    items = extract(tmp_path / "scan.pdf")
    assert len(fake_ocr) == 1
    assert [(i.origin, i.page) for i in items] == [("ocr", 1)]


def test_searchable_scan_skips_ocr(tmp_path, monkeypatch):
    monkeypatch.setattr(stage4_ocr, "run_ocr", lambda b: pytest.fail("OCR 하면 안 됨"))
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_image(page.rect, stream=png(), keep_proportion=False)
    page.insert_textbox(pymupdf.Rect(72, 72, 520, 300), "스캔 위에 숨겨 넣은 글자입니다. " * 3,
                        fontname=KO, fontsize=12, render_mode=3)
    doc.save(tmp_path / "searchable.pdf")
    items = extract(tmp_path / "searchable.pdf")
    assert items and all(i.origin == "text" for i in items)


def test_same_image_on_many_pages_is_read_once(tmp_path, fake_ocr):
    doc = pymupdf.open()
    xref = 0
    for n in range(3):
        page = doc.new_page()
        page.insert_text((72, 80), f"{n + 1}쪽 본문", fontname=KO, fontsize=14)
        rect = pymupdf.Rect(72, 300 + n * 50, 372, 500 + n * 50)    # 페이지마다 위치는 다름
        if xref == 0:
            xref = page.insert_image(rect, stream=png(), keep_proportion=False)
        else:
            page.insert_image(rect, xref=xref, keep_proportion=False)
    doc.save(tmp_path / "rep.pdf")

    items = extract(tmp_path / "rep.pdf")
    assert len(fake_ocr) == 1                          # OCR 은 한 번만
    ocr = [i for i in items if i.origin == "ocr"]
    assert [i.page for i in ocr] == [1, 2, 3]
    # 페이지마다 놓인 위치로 옮겨졌는지
    assert [round(i.bbox[1] - 300 - (i.page - 1) * 50) for i in ocr] == [round(ocr[0].bbox[1] - 300)] * 3


def test_cli_json_has_ocr_fields(tmp_path, fake_ocr):
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_image(page.rect, stream=png(), keep_proportion=False)
    doc.save(tmp_path / "scan.pdf")
    out = tmp_path / "out.json"
    assert main([str(tmp_path / "scan.pdf"), "-o", str(out)]) == 0
    [d] = json.loads(out.read_text(encoding="utf-8"))
    assert d["origin"] == "ocr" and d["confidence"] == 0.95
    assert all(isinstance(v, float) for v in d["bbox"])
