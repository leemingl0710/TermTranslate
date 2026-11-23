"""9단계: 같은 파일 결과 다시 쓰기 테스트."""
import json

import pymupdf
import pytest

from extractor import pipeline, result_cache
from extractor.__main__ import main


@pytest.fixture
def cache(tmp_path, monkeypatch):
    d = tmp_path / "cache"
    monkeypatch.setenv("EXTRACTOR_CACHE", "1")
    monkeypatch.setenv("EXTRACTOR_CACHE_DIR", str(d))
    return d


@pytest.fixture
def calls(monkeypatch):
    """extract 가 몇 번 불렸는지 센다."""
    n = []
    real = pipeline.extract

    def counting(path, *a, **k):
        n.append(path)
        return real(path, *a, **k)

    monkeypatch.setattr("extractor.__main__.extract", counting)
    return n


def make_pdf(path, text="파일 입출력 본문"):
    doc = pymupdf.open()
    doc.new_page().insert_text((72, 100), text, fontname="korea", fontsize=12)
    doc.save(path)
    return path


def run(path, out, *extra):
    assert main([str(path), "-o", str(out), *extra]) == 0
    return json.loads(out.read_text(encoding="utf-8"))


def test_second_run_uses_saved_result(tmp_path, cache, calls):
    pdf = make_pdf(tmp_path / "a.pdf")
    first = run(pdf, tmp_path / "1.json")
    second = run(pdf, tmp_path / "2.json")
    assert first == second
    assert len(calls) == 1
    assert len(list(cache.glob("*.json"))) == 1


def test_same_content_other_name(tmp_path, cache, calls):
    pdf = make_pdf(tmp_path / "a.pdf")
    run(pdf, tmp_path / "1.json")
    copy = tmp_path / "강의 복사본.pdf"
    copy.write_bytes(pdf.read_bytes())
    data = run(copy, tmp_path / "2.json")
    assert len(calls) == 1
    assert {d["source_file"] for d in data} == {"강의 복사본.pdf"}   # 이름은 지금 파일 것으로


def test_different_content_is_extracted_again(tmp_path, cache, calls):
    run(make_pdf(tmp_path / "a.pdf", "첫 번째"), tmp_path / "1.json")
    data = run(make_pdf(tmp_path / "a.pdf", "두 번째"), tmp_path / "2.json")
    assert len(calls) == 2
    assert data[0]["content"] == "두 번째"


def test_code_change_invalidates(tmp_path, cache, calls, monkeypatch):
    pdf = make_pdf(tmp_path / "a.pdf")
    run(pdf, tmp_path / "1.json")
    monkeypatch.setattr(result_cache, "code_version", lambda: "new-version")
    run(pdf, tmp_path / "2.json")
    assert len(calls) == 2


def test_no_cache_option_and_env(tmp_path, cache, calls, monkeypatch):
    pdf = make_pdf(tmp_path / "a.pdf")
    run(pdf, tmp_path / "1.json")
    run(pdf, tmp_path / "2.json", "--no-cache")
    monkeypatch.setenv("EXTRACTOR_CACHE", "0")
    run(pdf, tmp_path / "3.json")
    assert len(calls) == 3


def test_errors_are_not_saved(tmp_path, cache):
    bad = tmp_path / "bad.pdf"
    bad.write_bytes(b"hello")
    assert main([str(bad), "-o", str(tmp_path / "o.json")]) == 2
    assert not cache.exists() or not list(cache.glob("*.json"))


def test_broken_cache_file_is_ignored(tmp_path, cache, calls):
    pdf = make_pdf(tmp_path / "a.pdf")
    run(pdf, tmp_path / "1.json")
    for f in cache.glob("*.json"):
        f.write_text("{깨짐", encoding="utf-8")
    assert run(pdf, tmp_path / "2.json")[0]["content"] == "파일 입출력 본문"
    assert len(calls) == 2


def test_old_entries_are_pruned(tmp_path, cache, monkeypatch):
    monkeypatch.setattr(result_cache, "MAX_ENTRIES", 2)
    for n in range(4):
        run(make_pdf(tmp_path / f"{n}.pdf", f"문서 {n}"), tmp_path / f"{n}.json")
    assert len(list(cache.glob("*.json"))) == 2
