"""0단계. 입력 형식 판별 → PDF 변환."""
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

PDF_MAGIC = b"%PDF"
ZIP_MAGIC = b"PK\x03\x04"                      # pptx, docx
OLE_MAGIC = b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1"  # ppt, doc


def detect_format(path: Path) -> str:
    """확장자로 1차 판별, 시그니처로 재확인. 'pdf' | 'pptx' | 'ppt' 반환.

    TODO: 지원하지 않거나 확장자와 시그니처가 다르면 UnsupportedFormatError.
    """
    raise NotImplementedError


def find_soffice() -> str | None:
    """LibreOffice 실행 파일 경로 (macOS 앱 번들 포함)."""
    import shutil
    for cand in ("soffice", "libreoffice"):
        if p := shutil.which(cand):
            return p
    for cand in (
        Path("/Applications/LibreOffice.app/Contents/MacOS/soffice"),      # macOS
        Path(r"C:\Program Files\LibreOffice\program\soffice.exe"),      # Windows
        Path(r"C:\Program Files (x86)\LibreOffice\program\soffice.exe"),
    ):
        if cand.exists():
            return str(cand)
    return None


@contextmanager
def as_pdf(path: Path, timeout_sec: int) -> Iterator[Path]:
    """PDF면 그대로, PPT/PPTX면 임시 폴더에 변환한 PDF를 넘기고 끝나면 삭제.

    TODO: soffice --headless --convert-to pdf --outdir <tmp> <file>, timeout 적용,
          실패 시 ConversionError.
    """
    raise NotImplementedError
    yield path
