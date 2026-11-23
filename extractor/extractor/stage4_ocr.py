"""4단계. 이미지 변환 → RapidOCR."""
from functools import lru_cache

import pymupdf

from .config import OCR_LANG, Thresholds
from .models import BBox
from .ocr_group import Fragment, group_paragraphs
from .stage3_select import OcrTarget

# OCR 문단 하나: 글자, 페이지 pt 좌표 bbox, 신뢰도(0~1)
OcrResult = Fragment


# 글자 위치 찾기에 쓰는 그림의 긴 변 상한(px). 슬라이드 한 장(780pt)이 1280px 이면
# 14pt 글자가 약 23px 높이라 위치 찾기에는 충분하다.
DET_MAX_SIDE = 1280


@lru_cache(maxsize=1)
def get_engine():
    """OCR 엔진은 프로세스당 한 번만 로드 (첫 실행 때 모델 파일을 내려받음)."""
    from rapidocr import LangRec, ModelType, OCRVersion, RapidOCR
    # 최신 PP-OCRv6 인식 모델엔 한국어가 없어 v5 한국어 모델을 지정
    # use_cls=False: 글자 방향 판별 끔. 강의 자료는 똑바로 놓여 있는데, 켜 두면
    #   멀쩡한 긴 줄을 거꾸로 뒤집어 깨진 글자("이위이난위융…")로 읽는 일이 있었다.
    # 9단계 속도 (2_파일입출력 그림 55개 기준 OCR 105초 → 54초, 글자 결과 98.8% 같음):
    #   Det.limit_*: 글자 위치를 찾을 때만 그림 긴 변을 1280px 이하로 줄여서 찾는다.
    #     글자 읽기는 원래 크기 그림에서 잘라 하므로 읽는 품질은 거의 같다. (54초 → 19초)
    #   Rec.rec_batch_num=1: 줄을 하나씩 읽는다. 여러 줄을 묶으면 가장 긴 줄 길이에 맞춰
    #     빈칸을 채우느라 오히려 느리다. (48초 → 33초)
    # 여러 프로세스로 나눠 돌리기는 CPU 를 이미 다 써서 효과가 없었다.
    return RapidOCR(params={
        "Global.use_cls": False,
        "Det.limit_type": "max",
        "Det.limit_side_len": DET_MAX_SIDE,
        "Rec.lang_type": LangRec(OCR_LANG),
        "Rec.ocr_version": OCRVersion.PPOCRV5,
        "Rec.model_type": ModelType.MOBILE,
        "Rec.rec_batch_num": 1,
    })


def run_ocr(img_bytes: bytes) -> list[tuple[str, BBox, float]]:
    """이미지(PNG 등 바이트) → [(글자, 픽셀 bbox, 신뢰도)]."""
    out = get_engine()(img_bytes)
    if out.txts is None:
        return []
    res = []
    for box, txt, score in zip(out.boxes, out.txts, out.scores):
        # 4꼭짓점 → 사각형. numpy 숫자는 JSON 으로 못 바꾸니 파이썬 float 로 바꾼다.
        xs, ys = [float(p[0]) for p in box], [float(p[1]) for p in box]
        res.append((txt, (min(xs), min(ys), max(xs), max(ys)), float(score)))
    return res


# 사진 OCR 해상도 상한. 원본이 아주 고해상도여도 이 이상은 느리기만 하고 정확도는 비슷하다.
MAX_IMAGE_DPI = 600


def _ocr_region(page: "pymupdf.Page", clip: pymupdf.Rect, dpi: float,
                th: Thresholds) -> list[OcrResult]:
    """페이지의 clip 영역을 dpi 그림으로 바꿔 OCR 하고 문단으로 묶는다.

    - 신뢰도가 min_ocr_confidence 미만인 줄은 버린다 (잘못 읽었을 가능성이 큼).
    - 좌표는 그림 픽셀 → 페이지 pt 로 환산해, 텍스트 레이어 결과와 같은 기준으로 맞춘다.
    """
    pix = page.get_pixmap(dpi=round(dpi), clip=clip, alpha=False)
    if pix.width == 0 or pix.height == 0:
        return []
    sx, sy = clip.width / pix.width, clip.height / pix.height
    lines = []
    for text, (x0, y0, x1, y1), score in run_ocr(pix.tobytes("png")):
        if score < th.min_ocr_confidence:
            continue
        bbox = (clip.x0 + x0 * sx, clip.y0 + y0 * sy, clip.x0 + x1 * sx, clip.y0 + y1 * sy)
        lines.append(Fragment(text, bbox, score))
    return group_paragraphs(lines)


def ocr_full_page(page: "pymupdf.Page", th: Thresholds) -> list[OcrResult]:
    """스캔 페이지: 페이지 전체를 render_dpi 그림으로 바꿔 OCR."""
    return _ocr_region(page, page.rect, th.render_dpi, th)


def ocr_image(page: "pymupdf.Page", target: "OcrTarget", th: Thresholds) -> list[OcrResult]:
    """글자 페이지 속 사진 하나: 사진이 놓인 영역만 잘라 OCR.

    원본 파일을 꺼내지 않고 페이지에서 그 영역을 그림으로 바꾼다. 그래야 회전·뒤집기·
    잘라내기·투명도가 화면에 보이는 그대로 반영되고, 좌표 환산도 간단하다.
    해상도는 render_dpi 이상, 원본이 더 선명하면 원본 해상도(최대 MAX_IMAGE_DPI)로 한다.
    (작게 줄여 넣은 코드 캡처처럼 글자가 작은 사진도 놓치지 않게)
    """
    dpi = max(th.render_dpi, min(target.native_dpi, MAX_IMAGE_DPI))
    return _ocr_region(page, pymupdf.Rect(target.bbox), dpi, th)
