"""2단계(스캔 페이지 전체 OCR) 테스트.

- 문단 묶기는 가짜 OCR 조각으로 빠르게 확인한다.
- 실제 OCR 은 글자 PDF 를 그림으로 바꾼 '가짜 스캔 PDF' 로 확인한다 (페이지당 몇 초).
"""
from difflib import SequenceMatcher

import pymupdf
import pytest

from extractor import stage4_ocr
from extractor.config import DEFAULT
from extractor.models import PageType
from extractor.ocr_group import Fragment, group_paragraphs
from extractor.stage1_classify import classify_page
from extractor.stage2_text import extract_text_blocks
from extractor.stage4_ocr import ocr_full_page

KO = "korea"


def frag(text, x0, y0, x1, y1, conf=0.9):
    return Fragment(text, (x0, y0, x1, y1), conf)


# ---- 문단 묶기 ----

def test_lines_of_one_paragraph_are_joined():
    paras = group_paragraphs([
        frag("자료구조란 데이터를", 72, 100, 300, 115),
        frag("저장하는 방법이다.", 72, 118, 260, 133),
    ])
    assert [p.text for p in paras] == ["자료구조란 데이터를 저장하는 방법이다."]
    assert paras[0].bbox == (72, 100, 300, 133)


def test_far_apart_lines_are_separate():
    paras = group_paragraphs([
        frag("첫 문단", 72, 100, 200, 115),
        frag("둘째 문단", 72, 160, 200, 175),     # 줄 높이의 4배 아래
    ])
    assert [p.text for p in paras] == ["첫 문단", "둘째 문단"]


def test_title_with_bigger_font_is_separate():
    paras = group_paragraphs([
        frag("1장 개요", 72, 70, 250, 100),        # 높이 30 (제목)
        frag("본문 첫 줄", 72, 104, 250, 116),     # 높이 12
    ])
    assert len(paras) == 2


def test_two_columns_are_separate():
    paras = group_paragraphs([
        frag("왼쪽 칸 첫 줄", 50, 100, 250, 115),
        frag("오른쪽 칸 첫 줄", 330, 100, 530, 115),
        frag("왼쪽 칸 둘째 줄", 50, 118, 250, 133),
        frag("오른쪽 칸 둘째 줄", 330, 118, 530, 133),
    ])
    assert [p.text for p in paras] == ["왼쪽 칸 첫 줄 왼쪽 칸 둘째 줄", "오른쪽 칸 첫 줄 오른쪽 칸 둘째 줄"]


def test_pieces_on_same_row_become_one_line():
    paras = group_paragraphs([
        frag("본문입니다", 90, 100, 200, 115),
        frag("•", 72, 101, 80, 114),               # 글머리 기호가 따로 인식된 경우
    ])
    # 한 문단으로 합쳐지고, 맨 앞 글머리 기호는 clean_text 가 지운다 (8-1)
    assert [p.text for p in paras] == ["본문입니다"]
    assert paras[0].bbox[0] == 72


def test_english_hyphen_break_and_weighted_confidence():
    paras = group_paragraphs([
        frag("an efficient algo-", 72, 100, 250, 115, conf=1.0),
        frag("rithm", 72, 118, 130, 133, conf=0.5),
    ])
    assert paras[0].text == "an efficient algorithm"
    # 글자 수 가중 평균: (18*1.0 + 5*0.5) / 23
    assert paras[0].confidence == pytest.approx((18 * 1.0 + 5 * 0.5) / 23)


def test_empty_pieces_are_dropped():
    assert group_paragraphs([frag("  ", 72, 100, 100, 115)]) == []


def test_low_confidence_lines_are_dropped(monkeypatch):
    monkeypatch.setattr(stage4_ocr, "run_ocr", lambda img: [
        ("잘 읽힌 줄", (100, 100, 900, 160), 0.95),
        ("깨진글자", (100, 400, 900, 460), 0.2),     # 기준(0.4) 미만
    ])
    with pymupdf.open() as doc:
        page = doc.new_page()
        assert [r.text for r in ocr_full_page(page, DEFAULT)] == ["잘 읽힌 줄"]


# ---- 실제 OCR (가짜 스캔 PDF) ----

BLOCKS = [
    (pymupdf.Rect(72, 80, 520, 120), "자료구조와 알고리즘", 22),
    (pymupdf.Rect(72, 160, 520, 260), "스택은 나중에 넣은 데이터가 먼저 나오는 구조이다. "
                                      "큐는 먼저 넣은 데이터가 먼저 나오는 구조이다.", 14),
    (pymupdf.Rect(72, 320, 520, 360), "A queue is a FIFO structure.", 14),
]


@pytest.fixture(scope="module")
def scanned():
    """글자 PDF 를 만들고, 같은 내용을 그림으로만 담은 스캔 PDF 를 만든다."""
    src = pymupdf.open()
    page = src.new_page()
    for rect, text, size in BLOCKS:
        page.insert_textbox(rect, text, fontname=KO, fontsize=size)
    truth = extract_text_blocks(page)

    scan = pymupdf.open()
    spage = scan.new_page(width=page.rect.width, height=page.rect.height)
    spage.insert_image(spage.rect, pixmap=page.get_pixmap(dpi=150))
    yield spage, truth
    scan.close()
    src.close()


def test_fake_scan_is_classified_as_scanned(scanned):
    spage, _ = scanned
    assert spage.get_text().strip() == ""
    assert classify_page(spage, DEFAULT) == PageType.SCANNED


def test_ocr_reads_text_and_positions(scanned):
    spage, truth = scanned
    results = ocr_full_page(spage, DEFAULT)
    assert len(results) == len(truth), [r.text for r in results]

    for (text, bbox), r in zip(truth, results):
        a, b = text.replace(" ", ""), r.text.replace(" ", "")
        assert SequenceMatcher(None, a, b).ratio() >= 0.9, (text, r.text)
        assert DEFAULT.min_ocr_confidence <= r.confidence <= 1.0
        # 좌표가 원래 글자 위치와 비슷해야 함 (픽셀→pt 환산 확인).
        # OCR 상자는 글자 둘레 여백 때문에 몇 pt 차이가 난다.
        for got, want in zip(r.bbox, bbox):
            assert type(got) is float
            assert abs(got - want) < 12, (text, r.bbox, bbox)
