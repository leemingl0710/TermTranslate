package Server.service;

/**
 * 이력 화면 한 줄 (GET /results).
 *
 * @param itemCount  추출된 문단 수
 * @param translated 번역문이 있는지 (LLM 설정 없이 분석했으면 false)
 * @param termCount  결과에 연결된 사전 용어 수
 */
public record ResultSummary(int resultId, int documentId, String fileName, String analyzedAt,
                            int itemCount, boolean translated, int termCount) {
}
