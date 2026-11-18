"""기준값 모음. 처음엔 임의값이고, tests/samples 의 테스트 파일로 조정한다."""
from dataclasses import dataclass


@dataclass(frozen=True)
class Thresholds:
    # 1단계: 이 글자 수 미만이면 스캔 페이지 후보
    min_text_chars: int = 30
    # 1단계: 페이지 면적의 이 비율 이상을 덮으면 '큰 이미지'
    large_image_ratio: float = 0.6
    # 3단계: 가로/세로 중 하나라도 이보다 작으면(pt) OCR 제외 (아이콘·로고)
    min_image_side_pt: float = 50.0
    # 3·5단계: 이 비율 이상 겹치면 중복 후보
    overlap_ratio: float = 0.5
    # 5단계: 이 값 이상이면 같은 문장
    text_similarity: float = 0.8
    # 4단계: 이 값 미만 OCR 결과는 버리거나 표시
    min_ocr_confidence: float = 0.4
    # 4단계: 스캔 페이지 렌더링 해상도
    render_dpi: int = 300
    # 0단계: LibreOffice 변환 최대 시간(초)
    convert_timeout_sec: int = 120


DEFAULT = Thresholds()
# RapidOCR 한국어 인식 모델 (영문·숫자도 함께 인식)
OCR_LANG = "korean"
