package ui.platform;

import ui.ApiClient;
import ui.ApiClient.AnalysisResult;
import ui.ApiClient.ApiException;
import ui.ApiClient.ResultSummary;

import java.util.List;

/** 이력 화면이 쓰는 서버 기능. 테스트에서는 가짜로 바꿔 끼운다. */
interface HistoryApi {

    List<ResultSummary> list(String fileQuery) throws ApiException;

    AnalysisResult get(int resultId) throws ApiException;

    void delete(int resultId) throws ApiException;

    /** 실제 서버(ApiClient)를 쓰는 구현. */
    static HistoryApi of(ApiClient api) {
        return new HistoryApi() {
            @Override
            public List<ResultSummary> list(String fileQuery) throws ApiException {
                return api.listResults(fileQuery);
            }

            @Override
            public AnalysisResult get(int resultId) throws ApiException {
                return api.getResult(resultId);
            }

            @Override
            public void delete(int resultId) throws ApiException {
                api.deleteResult(resultId);
            }
        };
    }
}
