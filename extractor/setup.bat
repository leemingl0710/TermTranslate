@echo off
rem Windows 설치: setup.bat 더블클릭 또는 명령 프롬프트에서 실행
chcp 65001 >nul
cd /d "%~dp0"

where uv >nul 2>nul
if errorlevel 1 (
  echo [1/3] uv 설치
  powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
  set "PATH=%USERPROFILE%\.local\bin;%PATH%"
)

echo [2/3] 파이썬 3.11 + 패키지 설치
uv sync || goto :fail

echo [3/3] 환경 점검 (첫 실행은 OCR 모델 다운로드)
uv run python -m extractor.check_env || goto :fail

rem PPT 변환용 LibreOffice (크기가 커서 자동 설치는 안 함)
uv run python -c "from extractor.stage0_input import find_soffice; import sys; sys.exit(0 if find_soffice() else 1)"
if errorlevel 1 (
  echo.
  echo 참고: PPT 파일도 분석하려면 LibreOffice 를 설치하세요 ^(PDF만 쓸 땐 필요 없음^)
  echo   winget install TheDocumentFoundation.LibreOffice
)
pause
exit /b 0

:fail
echo 설치 실패. 위 메시지를 확인하세요.
pause
exit /b 1
