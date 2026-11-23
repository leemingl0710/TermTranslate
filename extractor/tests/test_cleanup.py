"""8-1: 페이지 번호, 반복 머리말·꼬리말 지우기 테스트.

예시는 실제 강의 PDF(780x540 슬라이드)에서 본 모양을 본떴다.
"""
import pymupdf
import pytest

from extractor.cleanup import remove_noise
from extractor.models import Item
from extractor.pipeline import extract

H = 540.0     # 슬라이드 높이
KO = "korea"


def it(page, content, y0, y1=None, origin="text"):
    return Item("a.pdf", "pdf", page, origin, content, (30, y0, 400, y1 or y0 + 18))


def run(items):
    pages = {i.page for i in items}
    return [(i.page, i.content) for i in remove_noise(items, {p: H for p in pages})]


@pytest.mark.parametrize("number", ["9", "10", "- 9 -", "9 / 47", "p. 9", "(9)"])
def test_page_number_in_edge_is_removed(number):
    assert run([it(1, "본문", 200), it(1, number, 520)]) == [(1, "본문")]
    assert run([it(1, number, 10), it(1, "본문", 200)]) == [(1, "본문")]


def test_number_in_middle_is_kept():
    # 그림 설명·표 안 숫자일 수 있어서 가운데 숫자는 그대로
    assert run([it(1, "42", 250)]) == [(1, "42")]


def test_repeated_footer_removed_everywhere():
    items = []
    for p in range(1, 5):
        items += [it(p, f"{p}쪽 본문", 200), it(p, "소스코드 다운로드", 510), it(p, "동영상 설명", 522)]
    # 페이지 번호가 붙어 한 칸이 된 경우도 같은 글자로 본다
    items.append(it(5, "11 소스코드 다운로드", 509))
    items.append(it(5, "5쪽 본문", 200))
    assert run(items) == [(p, f"{p}쪽 본문") for p in range(1, 6)]


def test_repeated_header_kept_once():
    items = []
    for p in range(1, 5):
        items += [it(p, "2 파일과 디렉터리", 36, 54),          # 장 제목 (위 띠)
                  it(p, "2-1 File 클래스", 58, 76),            # 슬라이드 제목 (띠 아래) → 반복돼도 그대로
                  it(p, f"{p}쪽 본문", 200)]
    out = run(items)
    assert out.count((1, "2 파일과 디렉터리")) == 1
    assert sum(c == "2 파일과 디렉터리" for _, c in out) == 1
    assert sum(c == "2-1 File 클래스" for _, c in out) == 4


def test_two_repeats_are_not_enough():
    items = [it(p, "동영상 설명", 522) for p in (1, 2)]
    assert run(items) == [(1, "동영상 설명"), (2, "동영상 설명")]


def test_repeated_text_in_middle_is_kept():
    # 장 표지처럼 페이지 가운데 반복되는 글자는 지우지 않는다
    items = [it(p, "바이트 스트림", 272) for p in range(1, 5)]
    assert len(run(items)) == 4


def test_ocr_items_are_cleaned_too():
    items = [it(p, "동영상 설명", 522, origin="ocr") for p in range(1, 4)]
    assert run(items) == []


def test_pipeline_removes_noise(tmp_path):
    doc = pymupdf.open()
    for n in range(1, 4):
        page = doc.new_page(width=780, height=540)
        page.insert_text((193, 50), "2 파일과 디렉터리", fontname=KO, fontsize=14)
        page.insert_text((60, 200), f"• {n}번째 슬라이드 본문", fontname=KO, fontsize=14)
        page.insert_text((677, 520), "소스코드 다운로드", fontname=KO, fontsize=10)
        page.insert_text((736, 532), str(n), fontname=KO, fontsize=10)
    doc.save(tmp_path / "lec.pdf")
    assert [(i.page, i.content) for i in extract(tmp_path / "lec.pdf")] == [
        (1, "2 파일과 디렉터리"),
        (1, "1번째 슬라이드 본문"),
        (2, "2번째 슬라이드 본문"),
        (3, "3번째 슬라이드 본문"),
    ]
