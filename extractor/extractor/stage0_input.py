"""0단계. 입력 형식 판별 → PDF 변환.

- 파일 앞부분(시그니처)으로 진짜 형식을 확인한다. 확장자만 믿지 않는다.
- PPT/PPTX 는 LibreOffice 로 PDF 로 바꾼 뒤, 1~5단계를 PDF 와 똑같이 탄다.
  (슬라이드 1장 = PDF 1페이지 → page 번호가 슬라이드 번호와 같다)
- PNG/JPG 사진은 한 페이지짜리 PDF 로 바꾼다. 글자 층이 없어서 1단계에서 스캔 페이지로
  분류되고, 4단계에서 페이지 전체를 OCR 한다. (휴대폰 사진의 회전 정보도 반영)
"""
import os
import shutil
import subprocess
import tempfile
import zipfile
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

import pymupdf
from PIL import Image, ImageOps, UnidentifiedImageError

from .errors import (ConversionError, CorruptedFileError, EncryptedFileError,
                     InputNotFoundError, UnsupportedFormatError)

PDF_MAGIC = b"%PDF"
ZIP_MAGIC = b"PK\x03\x04"                      # pptx, docx
OLE_MAGIC = b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1"  # ppt, doc, 암호 걸린 pptx
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
JPEG_MAGIC = b"\xff\xd8\xff"
# 암호 걸린 pptx 는 OLE 파일 안에 이 이름(UTF-16)의 항목이 들어 있다
_ENCRYPTED_PACKAGE = "EncryptedPackage".encode("utf-16-le")

IMAGE_FORMATS = ("png", "jpg")
SUPPORTED = ("pdf", "pptx", "ppt", "png", "jpg", "jpeg")
UNSUPPORTED_MESSAGE = "PDF·PPT·이미지(PNG, JPG) 파일만 지원합니다"


def detect_format(path: Path) -> str:
    """'pdf' | 'pptx' | 'ppt' | 'png' | 'jpg' 반환.

    - 시그니처가 지원 형식이면 그 형식 (확장자가 틀려도 내용을 믿는다)
    - 확장자는 지원 형식인데 내용이 아니면 → 손상된 파일
    - 둘 다 아니면 → 지원하지 않는 형식
    """
    if not path.is_file():
        raise InputNotFoundError(f"파일이 없습니다: {path}")
    ext = path.suffix.lower().lstrip(".")
    with path.open("rb") as f:
        data = f.read(8)

    if data.startswith(PDF_MAGIC):
        return "pdf"
    if data.startswith(PNG_MAGIC):
        return "png"
    if data.startswith(JPEG_MAGIC):
        return "jpg"
    if data.startswith(ZIP_MAGIC):
        try:
            with zipfile.ZipFile(path) as z:
                names = set(z.namelist())
        except zipfile.BadZipFile as e:
            if ext == "pptx":
                raise CorruptedFileError(f"PPTX 파일이 손상되었습니다: {path.name}") from e
            raise UnsupportedFormatError(f"지원하지 않는 형식입니다: {path.name}") from e
        if "ppt/presentation.xml" in names:
            return "pptx"
        if ext == "pptx":
            raise CorruptedFileError(f"PPTX 파일이 손상되었습니다: {path.name}")
        raise UnsupportedFormatError(f"{UNSUPPORTED_MESSAGE}: {path.name}")
    if data.startswith(OLE_MAGIC):
        if _ENCRYPTED_PACKAGE in path.read_bytes():
            raise EncryptedFileError(f"암호가 걸린 파일입니다: {path.name}")
        if ext == "ppt":
            return "ppt"
        if ext == "pptx":    # 옛날 형식을 확장자만 바꾼 경우
            return "ppt"
        raise UnsupportedFormatError(f"{UNSUPPORTED_MESSAGE}: {path.name}")

    if ext in SUPPORTED:
        name = "JPG" if ext == "jpeg" else ext.upper()
        raise CorruptedFileError(f"{name} 파일이 손상되었습니다: {path.name}")
    raise UnsupportedFormatError(f"{UNSUPPORTED_MESSAGE}: {path.name}")


def find_soffice() -> str | None:
    """LibreOffice 실행 파일 경로. 환경변수 → PATH → 운영체제 기본 설치 위치 순."""
    if (env := os.environ.get("LIBREOFFICE_PATH")) and Path(env).is_file():
        return env
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


INSTALL_HINT = ("LibreOffice 가 없어 PPT 를 변환할 수 없습니다. "
                "설치: macOS `brew install --cask libreoffice`, "
                "Windows `winget install TheDocumentFoundation.LibreOffice` "
                "(다른 위치에 설치했다면 LIBREOFFICE_PATH 환경변수로 지정)")


def convert_to_pdf(src: Path, fmt: str, workdir: Path, timeout_sec: int) -> Path:
    """src 를 workdir 안에서 PDF 로 바꾸고 그 경로를 돌려준다."""
    soffice = find_soffice()
    if soffice is None:
        raise ConversionError(INSTALL_HINT)
    # 한글·공백 파일 이름 문제를 피하려고 영어 이름으로 복사해서 변환
    inp = workdir / f"input.{fmt}"
    shutil.copyfile(src, inp)
    # 변환마다 설정 폴더를 따로 써서, 동시에 여러 개 변환하거나 사용자가 LibreOffice 를
    # 열어 둔 상태에서도 서로 부딪히지 않게 한다
    profile = (workdir / "lo-profile").resolve().as_uri()
    cmd = [soffice, "--headless", "--norestore", "--nolockcheck",
           f"-env:UserInstallation={profile}",
           "--convert-to", "pdf", "--outdir", str(workdir), str(inp)]
    try:
        proc = subprocess.run(cmd, stdin=subprocess.DEVNULL, capture_output=True,
                              timeout=timeout_sec)
    except subprocess.TimeoutExpired as e:
        raise ConversionError(
            f"PPT 변환이 {timeout_sec}초 안에 끝나지 않아 중단했습니다: {src.name}") from e
    except OSError as e:
        raise ConversionError(f"LibreOffice 를 실행할 수 없습니다 ({e})") from e

    out = workdir / "input.pdf"
    if proc.returncode != 0 or not out.is_file() or out.stat().st_size == 0:
        detail = (proc.stderr or proc.stdout).decode("utf-8", "replace").strip().splitlines()
        hint = f" ({detail[-1]})" if detail else ""
        raise ConversionError(f"PPT 를 PDF 로 바꾸지 못했습니다: {src.name}{hint}")
    return out


def image_to_pdf(src: Path, fmt: str, workdir: Path, render_dpi: int) -> Path:
    """사진을 한 페이지짜리 PDF 로 바꾼다.

    페이지 크기는 render_dpi 로 그렸을 때 원본 픽셀 크기가 되도록 정한다
    (4단계가 render_dpi 로 다시 그려 OCR 하므로 사진이 늘거나 줄지 않음).
    """
    try:
        with Image.open(src) as im:
            im = ImageOps.exif_transpose(im)      # 휴대폰 사진의 회전 정보 반영
            if im.mode not in ("RGB", "L"):
                im = im.convert("RGB")
            out_png = workdir / "input.png"
            im.save(out_png, "PNG")
            width, height = im.size
    except (UnidentifiedImageError, OSError, SyntaxError) as e:
        raise CorruptedFileError(f"{fmt.upper()} 파일이 손상되었습니다: {src.name}") from e
    if width < 1 or height < 1:
        raise CorruptedFileError(f"{fmt.upper()} 파일이 손상되었습니다: {src.name}")

    scale = 72 / render_dpi
    doc = pymupdf.open()
    try:
        page = doc.new_page(width=width * scale, height=height * scale)
        page.insert_image(page.rect, filename=str(out_png))
        out = workdir / "input.pdf"
        doc.save(out)
    finally:
        doc.close()
    return out


@contextmanager
def as_pdf(path: Path, timeout_sec: int, render_dpi: int = 300) -> Iterator[tuple[Path, str]]:
    """(PDF 경로, 원래 형식) 을 넘긴다. PDF 면 그대로, PPT/PPTX·사진이면 임시 폴더에
    변환하고 with 블록이 끝나면 임시 폴더를 지운다."""
    fmt = detect_format(path)
    if fmt == "pdf":
        yield path, fmt
        return
    with tempfile.TemporaryDirectory(prefix="extractor-") as tmp:
        if fmt in IMAGE_FORMATS:
            yield image_to_pdf(path, fmt, Path(tmp), render_dpi), fmt
        else:
            yield convert_to_pdf(path, fmt, Path(tmp), timeout_sec), fmt
