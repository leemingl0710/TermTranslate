# TermTranslate
유학생을 위한 전공 용어 변환 프로그램

## 구성
```
[LearningSupportUI (Swing)] ──HTTP──▶ [FermServer] ──▶ [extractor (Python)]
                                          │
                                          ├──▶ [LLM 서버 (OpenAI 호환)]  번역·요약 (term-translator)
                                          │
                                          └──▶ [Neon PostgreSQL]
```
| 폴더 | 역할 |
|---|---|
| `LearningSupportUI/` | 클라이언트 (Swing). 파일 선택 → 서버로 업로드 → 결과 표시 |
| `FermServer/` | 서버 (HTTP, `localhost:8080`). 저장 → 텍스트 추출 → 번역·요약 → DB 저장 |
| `FermServer/src/main/java/com/termtranslator/` | 번역·요약 파이프라인. 번역 담당 모듈(term-translator)을 복사해 온 것으로, `Server.translate.LlmTranslator` 가 호출. 고칠 때는 원본도 같이 고침 |
| `extractor/` | PDF 텍스트 추출 (Python). 자세한 내용은 `extractor/README.md` |
| `db/` | DB 스키마, 마이그레이션 |

## 처음 한 번 준비
1. `extractor/setup.sh` (윈도우: `setup.bat`) 실행 → 마지막에 `준비 완료`
2. `FermServer/.env.example` 을 복사해 `FermServer/.env` 를 만들고 DB 값 채우기
3. 이클립스: 두 프로젝트를 **Existing Maven Projects** 로 가져오기
   (이미 가져왔다면 우클릭 → Maven → Update Project. `LearningSupportUI` 는 우클릭 → Configure → Convert to Maven Project)

> **Windows 스마트 앱 컨트롤이 켜진 PC**: 분석할 때 "애플리케이션 제어 정책에서 이 파일을 차단했습니다" 가 나오면
> (uv 가 받은 서명 없는 파이썬 파일이 막힌 것) `FermServer/.env` 에 `EXTRACTOR_ENGINE=java` 를 넣고 서버를 다시 켠다.
> 파이썬 extractor 대신 자바 추출기(`Server.extract.JavaExtractor`: PDF 글자 층은 pdftotext, 글자 없는 페이지는 Windows 내장 OCR)를 쓴다.
> pdftotext 는 Git for Windows 에 들어 있다. PPT 는 PPTX 나 PDF 로 저장해서 올린다.

## 실행 (순서대로)
1. **서버**: `FermServer` 의 `Server.http.ApiServer` 를 Java Application 으로 실행
   - 터미널: `cd FermServer && ./mvnw -q compile exec:java`
   - `서버 시작: http://localhost:8080` 이 나오면 준비 완료
2. **클라이언트**: `LearningSupportUI` 의 `ui.Main` 실행
   - 터미널: `cd LearningSupportUI && ./mvnw -q compile exec:java`
3. File Upload → Select Files (PDF) → Analyze → 결과 화면에 추출된 글자 표시

## 서버 API
| 요청 | 설명 | 성공 응답 |
|---|---|---|
| `GET /health` | 서버 확인 | `200 {"status":"ok"}` |
| `POST /documents?filename=a.pdf` (본문: 파일) | 업로드 · 추출 · 저장 | `201 {"result_id", "document_id", "file_name", "item_count"}` |
| `GET /results/{id}` | 결과 조회 | `200 {"file_name", "items", "summary", ...}` |
| `GET /results?q=강의` | 이력 목록 (최근 것부터, `q`: 파일 이름 검색, 생략하면 전체) | `200 [{"result_id", "file_name", "analyzed_at", "item_count", "translated", "term_count"}]` |
| `DELETE /results/{id}` | 이력 삭제 (문서 · 업로드 파일 · 용어 연결 포함, 용어 사전은 그대로) | `200 {"deleted":true}` |
| `GET /terms?q=응력&unverified=true` | 용어 사전 검색 (`q`: 한국어 · 영어, 생략하면 전체 / `unverified=true`: 확인 전 용어만) | `200 [{"term_id", "korean", "english", "explanation", "verified"}]` |
| `POST /terms` (본문: `{"korean", "english", "explanation"}`) | 용어 추가 | `201 {"term_id", ...}` |
| `PUT /terms/{id}` (본문 같음) | 용어 수정 | `200 {"term_id", ...}` |
| `POST /terms/{id}/approve` | 자동으로 들어간 용어 승인 | `200 {"term_id", ..., "verified":true}` |
| `DELETE /terms/{id}` | 용어 삭제 | `200 {"deleted":true}` |

실패하면 `{"error": "종류", "message": "사용자에게 보여 줄 문장"}`
(400 잘못된 요청 · 404 없음 · 405 잘못된 요청 방식 · 409 이미 있는 용어 · 413 너무 큼 · 500 서버 오류)

용어 규칙: 한국어는 꼭 있어야 하고 100자 이하, 영어도 100자 이하, 같은 한국어 용어는 하나만.
여기서 넣은 용어는 다음 분석부터 번역기의 사전으로 쓰인다.

## 번역 · 요약 (term-translator 연동)
업로드 처리 3단계(`DefaultAnalysisService`)에서 `Server.translate.LlmTranslator` 가 추출된 글자로
번역 · 요약 · 개념 설명 · 퀴즈 · 용어를 만들어 결과 화면의 카드 4개를 채운다.

| 결과 카드 (DB 칸) | 내용 |
|---|---|
| Lecture Summary (`summary`) | 단원 요약 + 페이지별 3줄 요약 |
| Translation (`translated_text` + `analysis_terms`) | 페이지별 쉬운 영어 번역 + 자료에 나온 사전 용어 + LLM 이 새로 찾은 전공용어 |
| Key Concepts (`explanation`) | 핵심 개념, 개념 설명, 페이지 사이의 연결 |
| Exam Preparation (`exam_questions`) | 복습 퀴즈 → 시험 대비 체크리스트 → 정답 |

- **켜는 법**: `FermServer/.env` 에 `LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL` 을 채운다 (`.env.example` 의 "번역·요약" 부분 참고).
  비워 두면 지금까지처럼 추출 결과만 저장하고 카드는 "Not available yet." 으로 나온다.
- **용어 사전**: DB 의 `terms` 테이블을 사전으로 쓴다. `TERM_DICT_PATH` 에 CSV/TSV 파일(`ko,en,description`)을 주면 함께 쓰고,
  자료에 나온 용어 중 `terms` 에 없는 것은 새로 넣은 뒤 `analysis_terms` 로 연결한다.
- **전공용어 자동 인식**: LLM 이 묶음마다 사전에 없는 전공용어 후보(`newTerms`)를 찾고, `TermFilter` 가 다시 거른다
  (원문에 실제로 있는지, 일반 단어·코드 이름·문장이 아닌지, 길이). 남은 용어는 `terms` 에 `verified = false`(확인 전)로 들어가고
  다음 분석부터 바로 사전으로 쓰인다. 용어 사전 화면에서 Approve(승인) · Edit(수정하면 확인됨) · Delete 로 다듬는다.
  DB 에 `verified` 칸이 필요하다 → `db/migrations/002_terms_verified.sql` (Neon 에 적용 완료).
- **실패해도 업로드는 성공**: LLM 서버가 꺼져 있거나 응답이 깨지면 그 칸만 비워 두고 추출 결과는 저장한다.
- **요청 한도**: API 키에 분당 요청 한도가 있으면 `LLM_RATE_LIMIT_PER_MIN` (예: 30) 에 적는다. 요청 사이를 띄우고, 한도 초과(429)면 기다렸다가 다시 시도한다.
- **시간**: 묶음(기본 4페이지)마다 LLM 을 한 번씩 부르므로 몇 분 걸릴 수 있다. 클라이언트는 업로드 응답을 최대 30분 기다린다.
- 결과 카드에는 앞부분만 보이고, 카드 오른쪽 위 **View all** 로 전체 내용을 본다.
- 서버 콘솔에 묶음별 진행 상황과 용어 일치율이 찍힌다 (`[번역 파일이름] ...`).

## 현재 상태
- 로그인 없음: 모든 업로드는 `admin` 사용자로 저장 (`Server.tools.CreateUser` 로 사용자 생성)
- 지원 형식: PDF, PPTX, PPT (PPT·PPTX 는 LibreOffice 로 PDF 로 바꿔서 처리, `extractor/README.md` 참고).
  자바 추출기(`EXTRACTOR_ENGINE=java`)는 PDF, PPTX 만
- 번역 · 요약 · 개념 · 시험 문제 · 용어: LLM 서버를 `.env` 에 설정하면 동작 (위 "번역 · 요약" 참고).
  실제 LLM(gemma-4-26b)과 팀 DB 로 업로드부터 저장까지 확인함 (3페이지 약 1분, 47페이지 약 10분)
- 공용 용어 사전(`terms`)은 분석할 때마다 LLM 이 찾은 전공용어로 채워짐 (확인 전 상태). 자동 인식은 LLM 판단이라 가끔 일반 단어가 섞이니 용어 사전 화면에서 확인 필요
- 새 UI(`ui/platform`, 실행: `ui.platform.MainFrame`): 업로드(여러 파일 순서대로) · 결과(페이지별 원문 ↔ 번역) ·
  이력(목록 · 검색 · 보기 · 삭제) · 용어 사전(검색 · 추가 · 수정 · 삭제 · 자동 추가 용어 승인)이 서버와 연결됨.
  로그인 · 단원 · 평가 · OCR 확인 화면은 아직 데모. 기존 화면 `ui.Main` 도 그대로 동작

## 테스트
```bash
cd FermServer && ./mvnw test           # 서버 (DB/uv 없으면 해당 테스트는 건너뜀)
cd LearningSupportUI && ./mvnw test    # 클라이언트
cd extractor && uv run pytest          # 추출
```
