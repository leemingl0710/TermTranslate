"""출력 데이터 형식. 필드 이름은 초안 — 번역 모듈과 맞춰 확정."""
from dataclasses import dataclass, asdict, field
from enum import Enum
from typing import Literal, Optional

BBox = tuple[float, float, float, float]  # (x0, y0, x1, y1), PDF pt 좌표


class PageType(str, Enum):
    TEXT = "text"                  # 텍스트 추출 + 끼워진 사진만 OCR
    SCANNED = "scanned"            # 페이지 전체 OCR
    SEARCHABLE_SCAN = "searchable"  # 텍스트 레이어만 사용, OCR 생략


@dataclass
class Item:
    source_file: str
    source_format: str             # pdf, pptx, ppt
    page: int                      # 1부터 시작 (슬라이드 번호와 동일)
    origin: Literal["text", "ocr"]
    content: str
    bbox: BBox
    confidence: Optional[float] = None  # text 이면 None
    # 표시 (없으면 JSON 에서 빠짐): "code" 코드 캡처(줄바꿈 유지, 번역 안 해도 됨),
    # "table" 그림 속 표 한 행("칸 | 칸"), "low_confidence" OCR 신뢰도 낮음
    flags: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        d = asdict(self)
        d["bbox"] = [round(float(v), 2) for v in self.bbox]
        if self.confidence is not None:
            d["confidence"] = round(float(self.confidence), 3)
        if not d["flags"]:
            del d["flags"]
        return d
