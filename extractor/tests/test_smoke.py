"""환경/뼈대 확인용. 각 단계 구현하면서 테스트를 추가한다."""
import pymupdf

from extractor.models import Item
from extractor.stage5_merge import overlap, similarity


def test_pymupdf_reads_generated_pdf(tmp_path):
    doc = pymupdf.open()
    doc.new_page().insert_text((72, 72), "Hello extractor")
    p = tmp_path / "t.pdf"
    doc.save(p)
    assert "Hello extractor" in pymupdf.open(p)[0].get_text()


def test_overlap_and_similarity():
    assert overlap((0, 0, 10, 10), (5, 5, 15, 15)) == 0.25
    assert overlap((0, 0, 10, 10), (20, 20, 30, 30)) == 0.0
    assert similarity("번역 모듈", "번역 모듈") == 1.0


def test_item_to_dict():
    d = Item("a.pdf", "pdf", 1, "text", "hi", (1.234, 2, 3, 4)).to_dict()
    assert d["bbox"] == [1.23, 2, 3, 4] and d["confidence"] is None and "flags" not in d
