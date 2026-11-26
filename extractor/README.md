# extractor — PDF·PPT·사진 텍스트 추출

PDF/PPT/사진(PNG, JPG)에서 텍스트 레이어 문장 + 이미지 속 글자(OCR)를 뽑아 번역 단계용 JSON으로 넘긴다.
설계: `PDF·PPT 텍스트 추출 구현 계획` 문서 참고.

## 설치 (팀원 누구나, 처음 한 번)
1. **LibreOffice 설치** (PPT 변환용. PDF만 다룰 땐 없어도 됨)
   - macOS: `brew install --cask libreoffice`
   - Windows: `winget install TheDocumentFoundation.LibreOffice`
   - 다른 위치에 설치했다면 환경변수 `LIBREOFFICE_PATH` 에 `soffice` 실행 파일 경로를 넣는다.
2. **설치 스크립트 실행**
   - macOS: `./setup.sh`
   - Windows: `setup.bat` 더블클릭
   - 파이썬이 없어도 됩니다. uv가 파이썬 3.11과 패키지를 알아서 설치합니다.
3. 마지막에 `준비 완료`가 나오면 끝.

## 자주 쓰는 명령
```bash
uv run python -m extractor 파일.pdf -o out.json   # 추출 (같은 파일은 저장된 결과를 바로 돌려줌)
uv run python -m extractor 파일.pdf --no-cache    # 저장된 결과 무시하고 새로 추출
uv run pytest                                     # 테스트
uv run python -m extractor.check_env              # 환경 점검
uv add 패키지이름                                  # 패키지 추가 (uv.lock 같이 커밋)
```

> OCR 도구는 계획 문서의 EasyOCR 대신 **RapidOCR**(가볍고 torch 불필요)을 쓴다.
> 테스트 파일로 정확도를 확인해 부족하면 `stage4_ocr.py`만 바꾸면 된다.

## 자바(FermServer)에서 쓰기
```java
import Server.extract.*;

ExtractorClient client = ExtractorClient.fromEnv();
try {
    List<Item> items = client.extract(Path.of("lecture.pdf"));
    for (Item it : items) {
        it.page();        // 페이지 번호
        it.content();     // 문단 글자
        it.origin();      // Item.Origin.TEXT / OCR
    }
} catch (ExtractionException e) {
    e.getErrorType();     // 원인 (아래 표)
    e.getMessage();       // 사용자에게 보여 줄 한국어 문장
}
```
- 시간이 걸릴 수 있으니 Swing 화면 스레드에서 직접 부르지 말고 `SwingWorker` 등에서 부른다.
- 설정은 `FermServer/.env` (생략 가능): `EXTRACTOR_DIR`(기본 `../extractor`), `UV_PATH`, `EXTRACTOR_TIMEOUT_SEC`(기본 600).
  이클립스에서 `UV_NOT_FOUND`가 나면 `UV_PATH`에 uv 전체 경로를 적는다.
- extractor에 필드가 늘어나도 자바 쪽은 깨지지 않는다 (모르는 필드는 무시).

| errorType | 뜻 |
|---|---|
| `InputNotFoundError` | 파일이 없음 |
| `UnsupportedFormatError` | 지원하지 않는 형식 (현재 PDF만) |
| `CorruptedFileError` | 손상된 파일 |
| `EncryptedFileError` | 암호가 걸린 파일 |
| `UV_NOT_FOUND` | uv를 실행할 수 없음 (설치/`UV_PATH` 확인) |
| `EXTRACTOR_NOT_FOUND` | extractor 폴더를 못 찾음 (`EXTRACTOR_DIR` 확인) |
| `TIMEOUT` | 최대 시간 초과 |
| `EXTRACTOR_FAILED` | 그 밖의 오류 (메시지에 상세 내용) |

터미널에서 자바 빌드·테스트: `cd FermServer && ./mvnw test` (윈도우: `mvnw.cmd test`, Maven 설치 불필요)

## 구조 (단계별 모듈)
| 파일 | 단계 |
|---|---|
| `stage0_input.py` | 0 형식 판별 → PDF 변환 (PPT: LibreOffice, 사진: Pillow + PyMuPDF) |
| `stage1_classify.py` | 1 페이지 유형 판별 |
| `stage2_text.py` | 2 텍스트 레이어 추출 |
| `stage3_select.py` | 3 OCR 대상 선정 + 사전 중복 검사 |
| `stage4_ocr.py` | 4 렌더링/이미지 추출 → RapidOCR |
| `stage5_merge.py` | 5 통합 + 최종 중복 제거 + 정렬 |
| `__main__.py` | 6 JSON 출력 CLI (`python -m extractor 파일 -o out.json`) |
| `result_cache.py` | 9 같은 파일 결과 다시 쓰기 |
| `config.py` | 기준값 (테스트 파일로 조정) |
| `models.py` | 출력 항목 형식 |

## 개발 순서
자바 연결을 앞당겨, 결과 JSON 형식을 먼저 고정하고 팀원이 번역 부분을 병렬로 만들 수 있게 한다.
이후 단계는 JSON 형식을 바꾸지 않고 항목만 늘린다.

1. [x] 텍스트 PDF에서 텍스트만 추출 (2단계)
2. [x] 자바 연결: FermServer에서 실행 → `List<Item>` (JSON 형식 확정)
3. [x] 스캔 PDF 전체 OCR (4단계)
4. [x] 페이지 유형 판별 (1단계)
5. [x] 사진 OCR + 사전 중복 검사 (3·4단계)
6. [x] 결과 통합, 최종 중복 제거, 정렬 (5단계) + OCR 영어 글자 고치기 (I↔l, O↔0)
7. [x] 입력 형식 판별, PPT → PDF 변환 (0단계, LibreOffice)
8. [x] 품질 개선 (실제 강의 PDF 7개 조사 결과로 순서 정함)
   - [x] 8-1 잡음 지우기: 페이지 번호, 반복 머리말·꼬리말, 글머리 기호 (`cleanup.py`, `stage2_text.clean_text`)
   - [x] 8-3 코드 캡처: 줄바꿈 유지, 말풍선 분리, `code` 표시, `getName0` → `getName()` (`ocr_group.py`, `ocr_fix.py`)
   - [x] 8-2 그림 속 표: 칸끼리 합치지 않고 행 단위로 `칸 | 칸` (`ocr_group.py`)
   - [x] 8-4 자바 `Item` 에 `flags` 추가, `low_confidence` 표시 (신뢰도 0.6 미만)
   - 미룸: 2단 편집, 글자로 된 표 (실제 자료에 거의 없음)
9. [x] 속도 개선: OCR 엔진 설정, 같은 파일 결과 다시 쓰기, 서버 제한 시간 600초

## OCR 참고
- 속도 (9단계에서 줄임, 이 개발용 Mac(Apple Silicon) 기준): 시간의 95%가 OCR 이다.
  - 글자 위치 찾기는 그림 긴 변을 1280px 이하로 줄여서, 글자 읽기는 줄 하나씩 한다 (`stage4_ocr.get_engine`).
    2_파일입출력(그림 55개) OCR 105초 → 54초, 글자 결과 98.8% 같음.
  - 효과 없어서 안 한 것: 여러 프로세스로 나눠 돌리기(CPU 를 이미 다 씀), 스레드 수 조정, 줄 묶음 크게.
    여러 페이지의 같은 그림은 이미 한 번만 OCR 한다 (강의 7개에 겹치는 그림은 거의 없었음).
  - 서버 제한 시간은 `.env` 의 `EXTRACTOR_TIMEOUT_SEC`(기본 600초). 느린 PC 를 생각해 넉넉히 잡았다.
- 같은 파일 결과 다시 쓰기 (`result_cache.py`): 파일 내용 + extractor 코드가 같으면 `extractor/.cache/results` 에
  저장한 결과를 바로 돌려준다. 파일 이름은 달라도 된다. 코드(.py)를 고치면 예전 결과는 안 쓴다.
  끄기: `EXTRACTOR_CACHE=0` 또는 `--no-cache`. 위치 바꾸기: `EXTRACTOR_CACHE_DIR`. 최근 100개만 남긴다.
- 글자 방향 판별(use_cls)은 끈다. 켜 두면 멀쩡한 긴 줄을 거꾸로 뒤집어 깨진 글자로 읽었다.
- 고딕체의 I/l, O/0 은 모양으로 구별이 안 되어, 문서 본문 단어 + 자바 기본 이름 목록으로 고친다 (`ocr_fix.py`).
  본문에 없는 단어는 못 고친다 (예: 사진에만 나오는 클래스 이름).
- 아직 안 되는 것: 2단 편집 읽는 순서, 글자로 된 표 (실제 자료에 거의 없어 미룸).

## flags (결과 JSON 의 표시, 없으면 필드가 빠짐)
| 값 | 뜻 | 번역 단계에서 |
|---|---|---|
| `code` | 그림 속 코드 캡처 한 덩어리. 줄바꿈(`\n`) 유지, 줄 번호 `01:` 포함 | 번역하지 않고 그대로 보여 주면 됨 |
| `table` | 그림 속 표 한 행. 칸은 ` \| ` 로 구분 (`boolean canRead() \| 파일이 읽기 가능하면 …`) | 칸별로 나눠 번역 가능 |
| `low_confidence` | OCR 신뢰도 0.6 미만 (`config.py` 의 `low_ocr_confidence`) | 틀렸을 수 있음 표시 |

자바: `item.flags()`, `item.hasFlag(Item.CODE)`.

## 코드·표 판별 참고 (8-2, 8-3)
- 코드: 줄 번호 `01:` 나 `; { }`, 자바 예약어(`import public if else …`)가 있는 OCR 조각. 한 줄짜리이고 줄 번호도 없으면 코드로 보지 않는다.
  줄 번호만 따로 인식된 경우 같은 높이 오른쪽 코드를 거리와 상관없이 그 줄에 붙인다 (깊은 들여쓰기).
  코드 옆 한글 말풍선, `코드;← 설명` 처럼 붙어 인식된 설명은 떼어서 일반 글자로 둔다.
- 표: 왼쪽 끝이 같은 세로줄 2개 이상이 같은 높이에 3번 이상 나란하면 표. 두 줄로 감긴 칸은 한 행으로 합친다.
  표 제목(`표 13-1 …`)은 한 칸짜리라 표에 넣지 않는다.
- 빈 괄호 고치기는 코드·표 안에서만: `getName0` → `getName()`, `mkdir)` → `mkdir()`.
- 결과 (OCR 포함): 2_파일입출력 668 → 448 항목 (표 행 120, 코드 17), 4 스윙 474 항목 (표 행 81, 코드 29).
- 한계: 클래스 계층 그림처럼 상자가 줄 맞춰 있는 그림도 표로 잡힌다 (`File | Binary(byte)`).
  OCR 일반 오타(`C:WAWindows`, `talse`)는 못 고친다.

## 잡음 지우기 참고 (8-1)
- 위·아래 띠 = 페이지 높이의 12%. 장 제목(위 6% 근처)은 걸리고, 슬라이드 제목(위 10~11% 에서 시작)은 안 걸린다.
- 띠 안 숫자만 있는 칸 → 페이지 번호로 보고 지움.
- 띠 안 글자가 3페이지 이상 반복: 아래 띠(버튼 글자 등)는 모두 지우고, 위 띠(장 제목)는 처음 한 번만 남김.
  비교할 때 앞뒤 숫자는 무시한다 (`11 소스코드 다운로드` = `소스코드 다운로드`).
- 글머리 기호(`• ⚫ ▪ -` 등)는 줄 맨 앞만 지운다. 번호(`1.` `①`)와 음수(`-1`)는 남긴다.
- 강의 PDF 5개에서 항목이 약 35~45% 줄었다 (예: 2_파일입출력 199 → 114, OCR 제외 기준).

## 사진(PNG, JPG) 참고
- 사진 한 장을 한 페이지 PDF 로 바꾼다. 페이지 크기는 300dpi 로 그렸을 때 원본 픽셀 크기가 되게 정해서 OCR 할 때 늘거나 줄지 않는다.
- 글자 층이 없어서 1단계에서 스캔 페이지로 분류되고 페이지 전체를 OCR 한다. 결과의 `page` 는 1, `source_format` 은 `png`/`jpg`.
- 휴대폰 사진의 회전 정보(EXIF)를 반영해 바로 세운 뒤 읽는다.
- 시그니처는 맞는데 내용이 깨진 사진은 `CorruptedFileError`. GIF·BMP 등 다른 그림 형식은 지원하지 않는다.
- 사진 여러 장은 각각 따로 분석된다 (한 문서로 묶지 않음).

## PPT 참고
- 형식은 확장자가 아니라 파일 앞부분(시그니처)으로 판별한다. 지원: `pdf`, `pptx`, `ppt`, `png`, `jpg`(`.jpeg` 포함).
- PPT/PPTX 는 LibreOffice 로 임시 폴더에 PDF 를 만든 뒤 PDF 와 똑같이 처리한다.
  결과의 `page` 는 슬라이드 번호, `source_format` 은 `pptx`/`ppt`.
- 변환 최대 시간 120초 (`config.py` 의 `convert_timeout_sec`). 첫 변환은 LibreOffice 가 켜지느라 몇 초 더 걸린다.
- 발표자 노트는 결과에 없다. 본문 글머리 기호(•)는 지워진다 (8-1).
- 암호 걸린 PPTX 는 `EncryptedFileError`. 암호 걸린 옛날 PPT 는 변환 실패(`ConversionError`)나 시간 초과로 나올 것으로 본다 (진짜 암호 파일로는 아직 확인 못 함).

## 테스트 파일 (`tests/samples/`)
- [ ] 텍스트만 있는 PDF
- [ ] 스캔본 PDF
- [ ] 텍스트와 사진이 섞인 PDF
- [ ] 검색 가능한 스캔 PDF
- [ ] 사진이 들어간 PPTX
