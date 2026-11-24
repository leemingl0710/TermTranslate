/**
 * 번역·요약 파이프라인 (번역 담당 모듈 term-translator 를 그대로 복사해 온 것).
 *
 * <pre>
 * core     페이지 묶기, 용어 사전 매칭, 프롬프트, 응답 JSON 해석   (TranslationPipeline 이 중심)
 * llm      OpenAI 호환 LLM 호출 (요청 한도, 재시도), 데모용 가짜 응답
 * eval     전공용어 번역 일치율, 한국어 잔존율, 영어 난이도
 * extract  자바 추출기 (PDF·PPTX·그림). 서버에서는 EXTRACTOR_ENGINE=java 일 때만 씀
 * Config   설정 읽기: 환경변수 → 실행 폴더의 .env (FermServer/.env)
 * </pre>
 *
 * 서버와의 연결부는 {@code Server.translate.LlmTranslator} 와 {@code Server.extract.JavaExtractor} 이다.
 * 이 패키지를 고칠 때는 원본 term-translator 쪽도 같이 고쳐서 두 곳을 똑같이 유지한다.
 * 일부 메서드(TextExtractor.joinPages 등)는 원본 모듈의 화면·명령행에서만 쓰인다.
 */
package com.termtranslator;
