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
2. [ ] 자바 연결: FermServer에서 실행 → `List<Item>` (JSON 형식 확정)
3. [ ] 스캔 PDF 전체 OCR (4단계)
4. [ ] 페이지 유형 판별 (1단계)
5. [ ] 사진 OCR + 사전 중복 검사 (3·4단계)
6. [ ] 결과 통합, 최종 중복 제거, 정렬 (5단계)
7. [ ] 입력 형식 판별, PPT → PDF 변환 (0단계, LibreOffice 여부 결정)
8. [ ] 품질 개선: 표, 2단 편집, 머리말 제거, 기준값 조정

## 테스트 파일 (`tests/samples/`)
- [ ] 텍스트만 있는 PDF
- [ ] 스캔본 PDF
- [ ] 텍스트와 사진이 섞인 PDF
- [ ] 검색 가능한 스캔 PDF
- [ ] 사진이 들어간 PPTX
