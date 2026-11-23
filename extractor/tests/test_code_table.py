"""8-2·8-3·8-4: 그림 속 코드·표 정리, 빈 괄호 고치기, flags 테스트.

조각 좌표는 실제 강의 PDF(2_파일입출력 9·10·11쪽)의 OCR 결과를 줄여서 옮겼다.
"""
import json

import pymupdf
import pytest

from extractor import stage4_ocr
from extractor.__main__ import main
from extractor.ocr_fix import fix_parens
from extractor.ocr_group import Fragment, group_paragraphs
from extractor.pipeline import extract


def F(text, x0, y0, x1, y1, conf=0.95):
    return Fragment(text, (x0, y0, x1, y1), conf)


def kinds(paras):
    return [(p.kind, p.text) for p in paras]


# ---- 코드 (8-3) ----

def _code_page():
    """11쪽: 줄 번호와 코드가 따로 인식되고, 오른쪽에 한글 말풍선. 줄 간격 19pt."""
    rows = [  # (번호, 코드 x0, 코드, 말풍선)
        ("05:", 259.9, "File f1 = new File(directory);", "C:WAWindows 디렉터리로 File 객체 생성"),
        ("06:", 260.2, "if (f1.isDirectory()) {", None),
        ("07:", 306.2, "if (f.isDirectory())", None),
        ("08:", 327.8, 'System.out.println(s[i] +": 디렉터리");', None),   # 깊게 들여씀
        ("09:", 305.4, "else", None),                                       # 기호 없는 코드
        ("10:", None, None, None),                                          # 빈 줄
        ("11:", None, None, "디렉터리인지 파일인지를 판별하여 출력"),          # 빈 줄 옆 말풍선
    ]
    frags = []
    for n, (num, x0, code, callout) in enumerate(rows):
        y = 195 + n * 19
        frags.append(F(num, 192, y + 1, 211.5, y + 13))
        if code:
            frags.append(F(code, x0, y - 1, x0 + 6.2 * len(code), y + 14))
        if callout:
            frags.append(F(callout, 519, y + 1, 662, y + 12.5))
    return frags


CODE_PAGE = _code_page()


def test_code_block_keeps_lines_and_separates_callouts():
    out = group_paragraphs(CODE_PAGE)
    code = [p for p in out if p.kind == "code"]
    assert len(code) == 1
    assert code[0].text.split("\n") == [
        "05: File f1 = new File(directory);",
        "06: if (f1.isDirectory()) {",
        "07: if (f.isDirectory())",
        '08: System.out.println(s[i] +": 디렉터리");',
        "09: else",
        "10:",
        "11:",
    ]
    callouts = [p.text for p in out if p.kind == "text"]
    assert callouts == ["C:WAWindows 디렉터리로 File 객체 생성", "디렉터리인지 파일인지를 판별하여 출력"]


def test_code_and_callout_in_one_fragment_are_split():
    out = group_paragraphs([
        F('01: File f1 = new File("/");', 79.5, 192.8, 270.5, 211.5),
        F('02: File f2 = new File("/","a.bat");← 파일의 경로와 이름을 가진 객체', 79.5, 217.5, 561.2, 237.1),
    ])
    assert kinds(out) == [
        ("code", '01: File f1 = new File("/");\n02: File f2 = new File("/","a.bat");'),
        ("text", "파일의 경로와 이름을 가진 객체"),
    ]


def test_code_without_line_numbers():
    out = group_paragraphs([
        F("int sum = 0;", 50, 100, 150, 112),
        F("for (int i = 0; i < 10; i++) {", 50, 116, 260, 128),
        F("sum += i;", 70, 132, 150, 144),
        F("}", 50, 148, 56, 160),
    ])
    assert kinds(out) == [("code", "int sum = 0;\nfor (int i = 0; i < 10; i++) {\nsum += i;\n}")]


def test_single_line_with_semicolon_is_not_code():
    # 일반 문장에 ; 가 하나 있는 정도로는 코드로 보지 않는다
    out = group_paragraphs([F("스택; 후입선출 구조", 50, 100, 200, 112)])
    assert kinds(out) == [("text", "스택; 후입선출 구조")]


# ---- 표 (8-2) ----

TABLE_PAGE = [   # 10쪽: 메소드 이름 | 설명, 설명이 두 줄로 감긴 행 포함
    F("표13-2 File 클래스의 메소드", 187.0, 95.0, 420.0, 110.0),
    F("설명", 443.6, 122.1, 464.2, 136.2),
    F("메소드 이름", 222.5, 122.3, 269.7, 136.0),
    F("현재의 객체와 ob로 지정된 객체가 같은 파일을 가지고 있으면 true,아니", 314.8, 141.7, 590.7, 154.7),
    F("boolean equals(Object obj)", 188.2, 149.2, 299.2, 161.9),
    F("면false를 반환", 314.8, 155.4, 373.6, 167.7),
    F("String getAbsolutePath0", 187.4, 174.6, 287.7, 189.2),
    F("파일에 대한 절대 경로를 반환", 314.6, 174.6, 428.3, 186.8),
    F("String getParent)", 187.4, 215.6, 260.4, 229.8),
    F("부모 디렉터리 이름을 반환", 315.3, 216.1, 416.5, 227.4),
    F("파일의이름을 반환", 314.6, 236.3, 389.6, 248.3),
    F("String getName0", 187.9, 237.0, 258.0, 249.9),
    F("boolean renameTo(File", 188.2, 478.3, 305.0, 490.3),
    F("파일이나 디렉터리의 이름을 변경한 다음 true를 반환.이", 313.8, 476.9, 590.4, 489.4),
    F("름을 변경하지 못하면 false를 반환", 315.3, 490.1, 445.1, 502.1),
    F("newName)", 187.4, 490.6, 235.2, 503.5),
]


def test_table_rows_keep_cells_apart():
    out = group_paragraphs(TABLE_PAGE)
    assert kinds(out) == [
        ("text", "표13-2 File 클래스의 메소드"),                      # 표 제목은 일반 글자
        ("table", "메소드 이름 | 설명"),
        ("table", "boolean equals(Object obj) | 현재의 객체와 ob로 지정된 객체가 같은 파일을 "
                  "가지고 있으면 true,아니 면false를 반환"),
        ("table", "String getAbsolutePath0 | 파일에 대한 절대 경로를 반환"),
        ("table", "String getParent) | 부모 디렉터리 이름을 반환"),
        ("table", "String getName0 | 파일의이름을 반환"),
        ("table", "boolean renameTo(File newName) | 파일이나 디렉터리의 이름을 변경한 다음 "
                  "true를 반환.이 름을 변경하지 못하면 false를 반환"),
    ]


def test_left_aligned_paragraphs_are_not_a_table():
    # 왼쪽 끝이 같은 문장들만 있으면 표가 아니다
    out = group_paragraphs([F(f"{n}번째 문장입니다", 50, 100 + n * 30, 250, 112 + n * 30) for n in range(5)])
    assert all(p.kind == "text" for p in out)


# ---- 빈 괄호 고치기 ----

@pytest.mark.parametrize("ocr, expected", [
    ("String getName0 | 파일의이름을 반환", "String getName() | 파일의이름을 반환"),
    ("boolean mkdir) | 디렉터리를 생성", "boolean mkdir() | 디렉터리를 생성"),
    ("String getParent)", "String getParent()"),
    ("boolean isFile0", "boolean isFile()"),
    ("boolean renameTo(File newName)", "boolean renameTo(File newName)"),   # 짝 맞으면 그대로
    ("if (f.isDirectory())", "if (f.isDirectory())"),
    ("int x = 10;", "int x = 10;"),                                         # 진짜 숫자
    ("Test10 | 1970년", "Test10 | 1970년"),
    ("01: a.getName0;\n02: b.close)", "01: a.getName();\n02: b.close()"),
])
def test_fix_parens(ocr, expected):
    assert fix_parens(ocr) == expected


# ---- 전체 흐름: flags (8-4) ----

def png(w=600, h=400) -> bytes:
    pix = pymupdf.Pixmap(pymupdf.csRGB, pymupdf.IRect(0, 0, w, h), False)
    pix.clear_with(230)
    return pix.tobytes("png")


def test_pipeline_sets_flags(tmp_path, monkeypatch):
    # 그림 픽셀 좌표 = 페이지 pt 와 같게 (그림을 (0,0)~(600,400) 에 원본 크기로 넣고, dpi 72 기준이 아니므로
    # 좌표는 비율만 맞으면 된다)
    def fake(img_bytes):
        return [
            ("01:", (10, 10, 30, 22), 0.95), ("String s = f.getName0;", (40, 10, 300, 22), 0.95),
            ("02:", (10, 26, 30, 38), 0.95), ("if (s == null) {", (40, 26, 300, 38), 0.95),
            ("흐릿한 글자", (10, 200, 200, 215), 0.5),
        ]
    monkeypatch.setattr(stage4_ocr, "run_ocr", fake)
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text((72, 80), "본문 String getName", fontname="korea", fontsize=12)
    page.insert_image(pymupdf.Rect(72, 300, 372, 500), stream=png(), keep_proportion=False)
    doc.save(tmp_path / "f.pdf")

    out = tmp_path / "out.json"
    assert main([str(tmp_path / "f.pdf"), "-o", str(out)]) == 0
    data = json.loads(out.read_text(encoding="utf-8"))
    code = [d for d in data if "code" in d.get("flags", [])]
    assert [d["content"] for d in code] == ["01: String s = f.getName();\n02: if (s == null) {"]
    low = [d for d in data if "low_confidence" in d.get("flags", [])]
    assert [d["content"] for d in low] == ["흐릿한 글자"]
    assert "flags" not in data[0]                     # 본문 글자는 표시 없음
