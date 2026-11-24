package Server.translate;

import Server.db.AnalysisResultDao.Term;

import java.util.List;

/**
 * 3단계(번역·요약·개념·시험 문제·용어) 결과. analysis_results 의 칸과 1:1 로 대응한다.
 * 만들지 못한 항목은 null 이고, 결과 화면에는 "Not available yet." 으로 보인다.
 *
 * @param translatedText Translation 카드 (translated_text): 페이지별 쉬운 영어 번역
 * @param explanation    Key Concepts 카드 (explanation): 핵심 개념, 개념 설명, 페이지 사이의 연결
 * @param summary        Lecture Summary 카드 (summary): 단원 요약 + 페이지별 3줄 요약
 * @param examQuestions  Exam Preparation 카드 (exam_questions): 복습 퀴즈 + 시험 대비 체크리스트
 * @param terms          자료에 나온 사전 용어. terms 테이블에 없으면 추가하고 analysis_terms 로 연결한다.
 */
public record Translation(String translatedText, String explanation, String summary,
                          String examQuestions, List<Term> terms) {

    public Translation {
        terms = terms == null ? List.of() : List.copyOf(terms);
    }

    /** 번역을 하지 않았을 때 (설정 없음, LLM 서버 꺼짐 등). 지금까지처럼 모두 null 로 저장된다. */
    public static Translation empty() {
        return new Translation(null, null, null, null, List.of());
    }
}
