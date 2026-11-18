"""6단계. CLI: python -m extractor <파일> [-o out.json]

Java 연동 방식(FastAPI 서버 / 스크립트 실행)이 정해지면 이 진입점을 감싼다.
"""
import argparse
import json
import sys
from pathlib import Path

from .errors import ExtractionError
from .pipeline import extract


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="extractor")
    ap.add_argument("file", type=Path)
    ap.add_argument("-o", "--output", type=Path, help="생략하면 stdout")
    args = ap.parse_args(argv)
    try:
        items = extract(args.file)
    except ExtractionError as e:
        print(json.dumps({"error": type(e).__name__, "message": str(e)}, ensure_ascii=False),
              file=sys.stderr)
        return 2
    out = json.dumps([i.to_dict() for i in items], ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(out, encoding="utf-8")
    else:
        print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
