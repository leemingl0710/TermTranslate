package Server.service;

/** 용어 사전 한 줄 (GET/POST/PUT /terms). english·explanation 은 비어 있으면 null. */
public record TermEntry(int termId, String korean, String english, String explanation) {
}
