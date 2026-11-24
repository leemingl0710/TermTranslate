package com.termtranslator.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** 모델 비교용 호출 통계 */
public final class RunStats {
    public int calls;               // 실제 API 호출 수 (JSON 재요청 포함)
    public int jsonRequests;        // JSON 을 기대한 논리적 요청 수
    public int jsonFirstTryOk;      // 첫 응답이 바로 올바른 JSON 이었던 요청 수
    public int jsonFailed;          // 재요청까지 해도 JSON 이 깨진 요청 수
    public long totalLatencyMs;
    public long promptTokens;
    public long completionTokens;

    public double avgLatencySec() {
        return calls == 0 ? 0 : totalLatencyMs / 1000.0 / calls;
    }

    public double jsonFirstTryRate() {
        return jsonRequests == 0 ? 0 : (double) jsonFirstTryOk / jsonRequests;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calls", calls);
        m.put("jsonRequests", jsonRequests);
        m.put("jsonFirstTryOk", jsonFirstTryOk);
        m.put("jsonFailed", jsonFailed);
        m.put("totalLatencyMs", totalLatencyMs);
        m.put("promptTokens", promptTokens);
        m.put("completionTokens", completionTokens);
        return m;
    }
}
