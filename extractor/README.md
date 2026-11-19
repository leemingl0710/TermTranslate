# extractor — PDF·PPT 텍스트 추출

PDF/PPT에서 텍스트 레이어 문장 + 이미지 속 글자(OCR)를 뽑아 번역 단계용 JSON으로 넘긴다.
설계: `PDF·PPT 텍스트 추출 구현 계획` 문서 참고.

## 설치 (팀원 누구나, 처음 한 번)
1. **LibreOffice 설치** — https://www.libreoffice.org/download (PPT 변환용. PDF만 다룰 땐 없어도 됨)
2. **설치 스크립트 실행**
   - macOS: `./setup.sh`
   - Windows: `setup.bat` 더블클릭
   - 파이썬이 없어도 됩니다. uv가 파이썬 3.11과 패키지를 알아서 설치합니다.
3. 마지막에 `준비 완료`가 나오면 끝.

## 자주 쓰는 명령
```bash
uv run python -m extractor 파일.pdf -o out.json   # 추출
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
- 설정은 `FermServer/.env` (생략 가능): `EXTRACTOR_DIR`(기본 `../extractor`), `UV_PATH`, `EXTRACTOR_TIMEOUT_SEC`(기본 300).
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
| `stage0_input.py` | 0 형식 판별 → PDF 변환 (LibreOffice) |
| `stage1_classify.py` | 1 페이지 유형 판별 |
| `stage2_text.py` | 2 텍스트 레이어 추출 |
| `stage3_select.py` | 3 OCR 대상 선정 + 사전 중복 검사 |
| `stage4_ocr.py` | 4 렌더링/이미지 추출 → RapidOCR |
| `stage5_merge.py` | 5 통합 + 최종 중복 제거 + 정렬 |
| `__main__.py` | 6 JSON 출력 CLI (`python -m extractor 파일 -o out.json`) |
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
7. [ ] 입력 형식 판별, PPT → PDF 변환 (0단계, LibreOffice 여부 결정)
8. [ ] 품질 개선: 표, 2단 편집, 머리말 제거, 기준값 조정

## OCR 참고
- 속도: 이 개발용 Mac(Apple Silicon) 기준 스캔 페이지 한 장 약 3~4초, 사진 한 장 약 1~4초.
  사진 55장이 든 강의 PDF 47쪽은 약 110초 걸렸다. 서버 제한 시간은 `.env` 의 `EXTRACTOR_TIMEOUT_SEC`(기본 300초).
- 글자 방향 판별(use_cls)은 끈다. 켜 두면 멀쩡한 긴 줄을 거꾸로 뒤집어 깨진 글자로 읽었다.
- 고딕체의 I/l, O/0 은 모양으로 구별이 안 되어, 문서 본문 단어 + 자바 기본 이름 목록으로 고친다 (`ocr_fix.py`).
  본문에 없는 단어는 못 고친다 (예: 사진에만 나오는 클래스 이름).
- 아직 안 되는 것: 코드 캡처의 줄 순서가 가끔 섞임, 2단 편집 읽는 순서 (8번 품질 개선에서).

## 테스트 파일 (`tests/samples/`)
- [ ] 텍스트만 있는 PDF
- [ ] 스캔본 PDF
- [ ] 텍스트와 사진이 섞인 PDF
- [ ] 검색 가능한 스캔 PDF
- [ ] 사진이 들어간 PPTX
