package Server.service;

/**
 * 용어 사전 한 줄 (GET/POST/PUT /terms). english·explanation 은 비어 있으면 null.
 * verified: 사람이 넣거나 확인했으면 true, 분석 중 LLM 이 자동으로 넣어 아직 확인 전이면 false.
 */
public record TermEntry(int termId, String korean, String english, String explanation, boolean verified) {
}
