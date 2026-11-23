"""9단계. 같은 파일을 다시 넣으면 저장해 둔 결과를 바로 돌려준다.

- 열쇠: 파일 내용의 SHA-256 + extractor 코드 버전.
  파일 이름이 달라도 내용이 같으면 같은 파일로 본다.
  extractor 코드(.py)나 기준값을 고치면 버전이 바뀌어 예전 결과는 쓰지 않는다.
- 저장 위치: EXTRACTOR_CACHE_DIR 환경변수, 없으면 extractor/.cache/results
- 끄기: EXTRACTOR_CACHE=0
- 최근 MAX_ENTRIES 개만 남기고 오래된 것부터 지운다.
- 오류가 난 파일은 저장하지 않는다. 저장·읽기에 실패해도 추출은 그대로 진행한다.
"""
import hashlib
import json
import os
from functools import lru_cache
from pathlib import Path

PACKAGE_DIR = Path(__file__).resolve().parent
DEFAULT_DIR = PACKAGE_DIR.parent / ".cache" / "results"
MAX_ENTRIES = 100


def enabled() -> bool:
    return os.environ.get("EXTRACTOR_CACHE", "1") != "0"


def cache_dir() -> Path:
    env = os.environ.get("EXTRACTOR_CACHE_DIR")
    return Path(env) if env else DEFAULT_DIR


@lru_cache(maxsize=1)
def code_version() -> str:
    """extractor 패키지 .py 파일 내용 전체의 지문."""
    h = hashlib.sha256()
    for f in sorted(PACKAGE_DIR.glob("*.py")):
        h.update(f.name.encode())
        h.update(f.read_bytes())
    return h.hexdigest()[:16]


def _key(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return f"{h.hexdigest()}-{code_version()}"


def load(path: Path) -> list[dict] | None:
    """저장된 결과(항목 dict 목록). 없으면 None. source_file 은 지금 파일 이름으로 바꿔 준다."""
    if not enabled():
        return None
    try:
        f = cache_dir() / f"{_key(path)}.json"
        data = json.loads(f.read_text(encoding="utf-8"))
        os.utime(f)                     # 최근에 쓴 것으로 표시 (오래된 것부터 지우므로)
    except (OSError, ValueError):
        return None
    for d in data:
        d["source_file"] = path.name
    return data


def save(path: Path, items: list[dict]) -> None:
    if not enabled():
        return
    try:
        d = cache_dir()
        d.mkdir(parents=True, exist_ok=True)
        tmp = d / f"{_key(path)}.json.tmp{os.getpid()}"
        tmp.write_text(json.dumps(items, ensure_ascii=False), encoding="utf-8")
        tmp.replace(tmp.with_name(tmp.name.split(".tmp")[0]))    # 다 쓴 뒤 이름을 바꿔 반쯤 쓴 파일이 안 보이게
        old = sorted(d.glob("*.json"), key=lambda p: p.stat().st_mtime, reverse=True)[MAX_ENTRIES:]
        for p in old:
            p.unlink(missing_ok=True)
    except OSError:
        pass
