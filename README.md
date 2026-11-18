# TermTranslate
유학생을 위한 전공 용어 변환 프로그램

## 구성
```
[LearningSupportUI (Swing)] ──HTTP──▶ [FermServer] ──▶ [extractor (Python)]
                                          │
                                          └──▶ [Neon PostgreSQL]
```
| 폴더 | 역할 |
|---|---|
| `LearningSupportUI/` | 클라이언트 (Swing). 파일 선택 → 서버로 업로드 → 결과 표시 |
| `FermServer/` | 서버 (HTTP, `localhost:8080`). 저장 → 텍스트 추출 → DB 저장 |
| `extractor/` | PDF 텍스트 추출 (Python). 자세한 내용은 `extractor/README.md` |
| `db/` | DB 스키마, 마이그레이션 |

## 처음 한 번 준비
1. `extractor/setup.sh` (윈도우: `setup.bat`) 실행 → 마지막에 `준비 완료`
2. `FermServer/.env.example` 을 복사해 `FermServer/.env` 를 만들고 DB 값 채우기
3. 이클립스: 두 프로젝트를 **Existing Maven Projects** 로 가져오기
   (이미 가져왔다면 우클릭 → Maven → Update Project. `LearningSupportUI` 는 우클릭 → Configure → Convert to Maven Project)

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

실패하면 `{"error": "종류", "message": "사용자에게 보여 줄 문장"}`
(400 잘못된 파일 · 404 없음 · 405 잘못된 요청 방식 · 413 너무 큼 · 500 서버 오류)

## 현재 상태
- 로그인 없음: 모든 업로드는 `admin` 사용자로 저장 (`Server.tools.CreateUser` 로 사용자 생성)
- PDF만 지원 (PPT는 추후)
- 번역 · 요약 · 개념 · 시험 문제 · 용어는 아직 없음 → 결과 화면에 "Not available yet."
  만들어지면 `DefaultAnalysisService` 의 3번 자리에서 호출

## 테스트
```bash
cd FermServer && ./mvnw test           # 서버 (DB/uv 없으면 해당 테스트는 건너뜀)
cd LearningSupportUI && ./mvnw test    # 클라이언트
cd extractor && uv run pytest          # 추출
```
