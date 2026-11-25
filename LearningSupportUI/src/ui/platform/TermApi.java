package ui.platform;

import ui.ApiClient;
import ui.ApiClient.ApiException;
import ui.ApiClient.TermEntry;

import java.util.List;

/** 용어 사전 화면이 쓰는 서버 기능. 테스트에서는 가짜로 바꿔 끼운다. */
interface TermApi {

    /** unverifiedOnly 면 자동으로 들어와 확인 전인 용어만. */
    List<TermEntry> search(String query, boolean unverifiedOnly) throws ApiException;

    TermEntry add(String korean, String english, String explanation) throws ApiException;

    TermEntry update(int termId, String korean, String english, String explanation) throws ApiException;

    /** 자동으로 들어간 용어를 그대로 승인한다. */
    TermEntry approve(int termId) throws ApiException;

    void delete(int termId) throws ApiException;

    /** 실제 서버(ApiClient)를 쓰는 구현. */
    static TermApi of(ApiClient api) {
        return new TermApi() {
            @Override
            public List<TermEntry> search(String query, boolean unverifiedOnly) throws ApiException {
                return api.searchTerms(query, unverifiedOnly);
            }

            @Override
            public TermEntry approve(int termId) throws ApiException {
                return api.approveTerm(termId);
            }

            @Override
            public TermEntry add(String korean, String english, String explanation) throws ApiException {
                return api.addTerm(korean, english, explanation);
            }

            @Override
            public TermEntry update(int termId, String korean, String english, String explanation)
                    throws ApiException {
                return api.updateTerm(termId, korean, english, explanation);
            }

            @Override
            public void delete(int termId) throws ApiException {
                api.deleteTerm(termId);
            }
        };
    }
}
