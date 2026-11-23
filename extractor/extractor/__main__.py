"""6단계. CLI: python -m extractor <파일> [-o out.json]

Java 연동 방식(FastAPI 서버 / 스크립트 실행)이 정해지면 이 진입점을 감싼다.
"""
import argparse
import json
import sys
from pathlib import Path

from . import result_cache
from .errors import ExtractionError
from .pipeline import extract


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="extractor")
    ap.add_argument("file", type=Path)
    ap.add_argument("-o", "--output", type=Path, help="생략하면 stdout")
    ap.add_argument("--no-cache", action="store_true", help="저장된 결과를 쓰지 않고 새로 추출")
    args = ap.parse_args(argv)
    data = None if args.no_cache or not args.file.is_file() else result_cache.load(args.file)
    if data is None:
        try:
            data = [i.to_dict() for i in extract(args.file)]
        except ExtractionError as e:
            print(json.dumps({"error": type(e).__name__, "message": str(e)}, ensure_ascii=False),
                  file=sys.stderr)
            return 2
        result_cache.save(args.file, data)
    out = json.dumps(data, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(out, encoding="utf-8")
    else:
        print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
