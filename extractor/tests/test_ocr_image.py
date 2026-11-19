"""3단계(사진 고르기 + 사진 OCR) 테스트. 테스트용 PDF 는 코드로 만든다."""
from difflib import SequenceMatcher

import pymupdf
import pytest

from extractor.config import DEFAULT
from extractor.ocr_group import Fragment, group_paragraphs
from extractor.stage2_text import extract_text_blocks
from extractor.stage3_select import select_ocr_targets, text_coverage
from extractor.stage4_ocr import ocr_image

KO = "korea"
PHOTO_TEXT = "사진 속 글자: 큐는 선입선출 구조이다"


def text_png(text: str, w=420, h=90, dpi=150) -> bytes:
    """글자가 그려진 그림(PNG). 글자 PDF 한 장을 그림으로 바꿔 만든다."""
    with pymupdf.open() as doc:
        page = doc.new_page(width=w, height=h)
        page.insert_textbox(pymupdf.Rect(10, 25, w - 10, h - 10), text, fontname=KO, fontsize=16)
        return page.get_pixmap(dpi=dpi).tobytes("png")


def blank_png(w=100, h=100) -> bytes:
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, w, h), False)
    pix.clear_with(180)
    return pix.tobytes("png")


PHOTO = pymupdf.Rect(72, 300, 492, 390)      # 글자 든 사진 (420 x 90 pt)
ICON = pymupdf.Rect(500, 40, 540, 80)        # 40 x 40 pt 아이콘 → 제외
BEHIND = pymupdf.Rect(72, 80, 520, 125)      # 글자 뒤 배경 그림 (글자가 대부분 덮음) → 제외


@pytest.fixture()
def slide():
    """글자 + 글자 든 사진 + 작은 아이콘 + 글자 뒤 배경 그림이 있는 슬라이드."""
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_image(BEHIND, stream=blank_png(), keep_proportion=False)
    page.insert_textbox(BEHIND, "스택과 큐는 대표적인 자료구조이다. 스택은 후입선출, "
                        "큐는 선입선출 방식으로 데이터를 다룬다.", fontname=KO, fontsize=14)
    page.insert_image(ICON, stream=blank_png(), keep_proportion=False)
    page.insert_image(PHOTO, stream=text_png(PHOTO_TEXT), keep_proportion=False)
    yield page
    doc.close()


def test_selects_only_the_photo(slide):
    targets = select_ocr_targets(slide, extract_text_blocks(slide), DEFAULT)
    assert len(targets) == 1
    assert targets[0].bbox == pytest.approx(tuple(PHOTO), abs=0.01)
    assert targets[0].native_dpi == pytest.approx(150, rel=0.02)   # 150dpi 로 만든 그림


def test_text_coverage():
    img = pymupdf.Rect(0, 0, 100, 100)
    assert text_coverage(img, []) == 0
    assert text_coverage(img, [("가", (0, 0, 100, 50))]) == pytest.approx(0.5)
    assert text_coverage(img, [("가", (0, 0, 100, 80)), ("나", (0, 50, 100, 100))]) == 1.0  # 1 을 넘지 않음
    assert text_coverage(img, [("가", (200, 200, 300, 300))]) == 0


def test_image_partly_off_page_is_clipped():
    with pymupdf.open() as doc:
        page = doc.new_page()
        page.insert_image(pymupdf.Rect(400, 700, 700, 900), stream=blank_png(), keep_proportion=False)
        [t] = select_ocr_targets(page, [], DEFAULT)
        assert t.bbox == pytest.approx((400, 700, page.rect.x1, page.rect.y1), abs=0.01)


def test_no_images():
    with pymupdf.open() as doc:
        page = doc.new_page()
        assert select_ocr_targets(page, [], DEFAULT) == []


def test_ocr_reads_photo_text_in_page_coordinates(slide):
    [target] = select_ocr_targets(slide, extract_text_blocks(slide), DEFAULT)
    results = ocr_image(slide, target, DEFAULT)
    text = " ".join(r.text for r in results)
    ratio = SequenceMatcher(None, PHOTO_TEXT.replace(" ", ""), text.replace(" ", "")).ratio()
    assert ratio >= 0.9, text
    for r in results:
        # 좌표는 사진 안쪽, 페이지 pt 기준 (사진 속 픽셀 좌표가 아님)
        x0, y0, x1, y1 = r.bbox
        assert PHOTO.x0 - 2 <= x0 < x1 <= PHOTO.x1 + 2
        assert PHOTO.y0 - 2 <= y0 < y1 <= PHOTO.y1 + 2
        assert type(x0) is float and type(r.confidence) is float


# ---- 줄 번호·글머리 기호 붙이기 ----

def test_line_number_joins_indented_code():
    paras = group_paragraphs([
        Fragment("07:", (50, 100, 70, 110), 0.9),
        Fragment("System.out.println(f1);", (120, 100, 300, 110), 0.9),   # 줄 높이 5배 떨어짐
    ])
    assert [p.text for p in paras] == ["07: System.out.println(f1);"]


def test_long_pieces_far_apart_stay_separate():
    paras = group_paragraphs([
        Fragment("왼쪽 칸의 긴 문장", (50, 100, 200, 110), 0.9),
        Fragment("오른쪽 칸의 긴 문장", (250, 100, 400, 110), 0.9),    # 줄 높이 5배 떨어짐
    ])
    assert len(paras) == 2
