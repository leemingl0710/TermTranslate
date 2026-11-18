package Server.service;

import Server.db.AnalysisResultDao.Term;
import Server.extract.Item;

import java.util.List;

/**
 * 결과 화면에 필요한 모든 값. 번역·요약 등은 아직 만들어지지 않았으면 null.
 *
 * @param items 추출된 원문 문단 (analysis_results.source_text)
 * @param terms Translation 카드의 용어 목록 (analysis_terms)
 */
public record ResultDetail(int resultId, int documentId, String fileName, String analyzedAt,
                           List<Item> items, String translatedText, String explanation,
                           String summary, String examQuestions, List<Term> terms) {
}
