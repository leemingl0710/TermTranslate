from pathlib import Path

import pytest

SAMPLES = Path(__file__).parent / "samples"


@pytest.fixture(autouse=True)
def _no_result_cache(monkeypatch):
    # 테스트끼리 저장된 결과를 주고받지 않게 기본은 끈다 (test_result_cache 에서만 켬)
    monkeypatch.setenv("EXTRACTOR_CACHE", "0")


@pytest.fixture
def samples() -> Path:
    return SAMPLES
