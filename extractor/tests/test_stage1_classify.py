"""1단계(페이지 유형 판별) 테스트. 테스트용 PDF는 코드로 만든다."""
import pymupdf
import pytest

from extractor.config import DEFAULT
from extractor.models import PageType
from extractor.stage1_classify import classify_page, count_text_chars, image_coverage

KO = "korea"
LONG_TEXT = "자료구조란 데이터를 효율적으로 저장하고 관리하는 방법이다. 스택과 큐가 대표적이다."
SHORT_TEXT = "1장 개요"   # 30자 미만 (제목, 쪽 번호 정도)


def png(w=200, h=200) -> bytes:
    """회색 사각형 PNG."""
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, w, h), False)
    pix.clear_with(200)
    return pix.tobytes("png")


def new_page(images=(), text=None, hidden_text=None):
    """images: 페이지 위 배치 좌표 (x0, y0, x1, y1) 목록. A4 = 595 x 842 pt."""
    doc = pymupdf.open()
    page = doc.new_page()
    for rect in images:
        # keep_proportion=False: 지정한 사각형을 꽉 채움 (기본값은 비율 유지라 덜 덮음)
        page.insert_image(pymupdf.Rect(rect), stream=png(), keep_proportion=False)
    if text:
        page.insert_textbox(pymupdf.Rect(72, 72, 520, 300), text, fontname=KO, fontsize=12)
    if hidden_text:
        # render_mode=3: 보이지 않는 글자 (OCR 된 스캔 PDF 가 이렇게 만들어짐)
        page.insert_textbox(pymupdf.Rect(72, 72, 520, 300), hidden_text,
                            fontname=KO, fontsize=12, render_mode=3)
    return doc, page


FULL = (0, 0, 595, 842)
SMALL = (400, 600, 500, 700)        # 페이지의 약 2%


@pytest.mark.parametrize("case, images, text, hidden, expected", [
    ("글자만 있는 일반 자료",       [],              LONG_TEXT,  None,      PageType.TEXT),
    ("글자 + 작은 사진",            [SMALL],         LONG_TEXT,  None,      PageType.TEXT),
    ("빈 페이지",                   [],              None,       None,      PageType.TEXT),
    ("작은 사진만 (글자 없음)",     [SMALL],         None,       None,      PageType.TEXT),
    ("스캔 페이지",                 [FULL],          None,       None,      PageType.SCANNED),
    ("스캔 + 짧은 제목만",          [FULL],          SHORT_TEXT, None,      PageType.SCANNED),
    ("스캔 위에 숨긴 글자",         [FULL],          None,       LONG_TEXT, PageType.SEARCHABLE_SCAN),
    ("큰 사진 + 보이는 긴 글자",    [FULL],          LONG_TEXT,  None,      PageType.SEARCHABLE_SCAN),
])
def test_classify(case, images, text, hidden, expected):
    doc, page = new_page(images, text, hidden)
    with doc:
        assert classify_page(page, DEFAULT) == expected, case


def test_coverage_values():
    doc, page = new_page([FULL])
    with doc:
        assert image_coverage(page) == pytest.approx(1.0)
    doc, page = new_page([(0, 0, 595, 421)])           # 위쪽 절반
    with doc:
        assert image_coverage(page) == pytest.approx(0.5, abs=0.01)
    doc, page = new_page()
    with doc:
        assert image_coverage(page) == 0.0


def test_coverage_uses_largest_image_not_sum():
    # 페이지의 40% 짜리 두 장 → 합치면 80% 지만 '큰 이미지' 는 아님
    doc, page = new_page([(0, 0, 595, 337), (0, 421, 595, 758)])
    with doc:
        assert image_coverage(page) == pytest.approx(0.4, abs=0.01)
        assert classify_page(page, DEFAULT) == PageType.TEXT


def test_coverage_clips_outside_page():
    # 페이지보다 큰 이미지도 비율은 1을 넘지 않음
    doc, page = new_page([(-100, -100, 700, 950)])
    with doc:
        assert image_coverage(page) == pytest.approx(1.0)


def test_count_ignores_spaces():
    doc, page = new_page(text="가 나\n다  라")
    with doc:
        assert count_text_chars(page) == 4


def test_threshold_boundary():
    # 기준값(30자) 바로 아래/위
    for n, expected in [(29, PageType.SCANNED), (30, PageType.SEARCHABLE_SCAN)]:
        doc, page = new_page([FULL], hidden_text="가" * n)
        with doc:
            assert classify_page(page, DEFAULT) == expected, n
