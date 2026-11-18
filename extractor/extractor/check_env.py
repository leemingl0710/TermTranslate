"""개발 환경 점검: uv run python -m extractor.check_env"""
import sys


def main() -> int:
    ok = True
    print(f"python   {sys.version.split()[0]}")
    try:
        import pymupdf
        print(f"pymupdf  {pymupdf.VersionBind}")
    except ImportError as e:
        ok = False; print(f"pymupdf  없음 ({e})")
    try:
        from importlib.metadata import version
        import pymupdf
        from .stage4_ocr import run_ocr
        # 실제로 한 번 인식해 봐야 모델 다운로드·한국어 지원까지 확인된다
        doc = pymupdf.open(); pg = doc.new_page(width=300, height=80)
        pg.insert_text((10, 50), "OCR test 2026", fontsize=24)
        txt = " ".join(t for t, _, _ in run_ocr(pg.get_pixmap(dpi=150).tobytes("png")))
        if "2026" not in txt:
            raise RuntimeError(f"인식 결과 이상: {txt!r}")
        print(f"rapidocr {version('rapidocr')} (한국어 모델, 시험 인식 성공)")
    except Exception as e:
        ok = False; print(f"rapidocr 문제 ({e})")
    from .stage0_input import find_soffice
    s = find_soffice()
    print(f"soffice  {s or '없음 → LibreOffice 설치 필요 (PPT 변환에만 사용, PDF는 없어도 됨)'}")
    print("준비 완료" if ok else "설치가 덜 됐습니다")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
