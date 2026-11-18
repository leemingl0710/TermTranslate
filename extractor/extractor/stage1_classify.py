"""1단계. 페이지 유형 판별 (텍스트 양 + 큰 이미지 덮는 비율)."""
import pymupdf

from .config import Thresholds
from .models import PageType


def image_coverage(page: "pymupdf.Page") -> float:
    """페이지 면적 대비 가장 큰 이미지가 덮는 비율. TODO"""
    raise NotImplementedError


def classify_page(page: "pymupdf.Page", th: Thresholds) -> PageType:
    """텍스트 있음/없음 × 큰 이미지 있음/없음 → PageType. TODO"""
    raise NotImplementedError
