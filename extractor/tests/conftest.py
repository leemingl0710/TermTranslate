from pathlib import Path

import pytest

SAMPLES = Path(__file__).parent / "samples"


@pytest.fixture
def samples() -> Path:
    return SAMPLES
