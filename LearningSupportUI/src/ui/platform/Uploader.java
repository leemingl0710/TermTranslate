package ui.platform;

import ui.ApiClient;

import java.nio.file.Path;

/** 서버에 파일을 올리고 결과를 받는 부분. 테스트에서는 가짜로 바꿔 끼운다. */
interface Uploader {

    boolean isServerUp();

    ApiClient.UploadResult upload(Path file) throws ApiClient.ApiException;

    ApiClient.AnalysisResult getResult(int resultId) throws ApiClient.ApiException;

    /** 실제 서버(ApiClient)를 쓰는 Uploader. */
    static Uploader of(ApiClient api) {
        return new Uploader() {
            @Override
            public boolean isServerUp() {
                return api.isServerUp();
            }

            @Override
            public ApiClient.UploadResult upload(Path file) throws ApiClient.ApiException {
                return api.upload(file);
            }

            @Override
            public ApiClient.AnalysisResult getResult(int resultId) throws ApiClient.ApiException {
                return api.getResult(resultId);
            }
        };
    }
}
