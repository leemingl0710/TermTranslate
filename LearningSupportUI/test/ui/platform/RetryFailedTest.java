package ui.platform;

import org.junit.jupiter.api.Test;
import ui.ApiClient;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 실패한 파일만 다시 시도하기, 실패 종류별 안내. */
class RetryFailedTest {

    /** 이름별로 정해 둔 오류를 낸다. 오류는 한 번 내면 지워진다 (다음 시도는 성공). */
    private static final class ScriptedUploader implements Uploader {
        final List<String> uploadOrder = new ArrayList<>();
        final Map<String, ApiClient.ApiException> errors = new HashMap<>();
        /** 계속 실패하는 이름 (지워지지 않음) */
        final Map<String, ApiClient.ApiException> permanent = new HashMap<>();
        private final Map<Integer, String> names = new HashMap<>();
        private int nextId = 1;

        @Override
        public boolean isServerUp() {
            return true;
        }

        @Override
        public ApiClient.UploadResult upload(Path file) throws ApiClient.ApiException {
            String name = file.getFileName().toString();
            uploadOrder.add(name);
            if (permanent.containsKey(name)) {
                throw permanent.get(name);
            }
            ApiClient.ApiException e = errors.remove(name);
            if (e != null) {
                throw e;
            }
            int id = nextId++;
            names.put(id, name);
            return new ApiClient.UploadResult(id, id, name, 1);
        }

        @Override
        public ApiClient.AnalysisResult getResult(int resultId) {
            return new ApiClient.AnalysisResult(resultId, names.get(resultId), null,
                    List.of(), null, null, null, null, List.of());
        }
    }

    private static final ApiClient.ApiException NETWORK =
            new ApiClient.ApiException(0, "NETWORK_ERROR", "서버와 통신 중 오류가 발생했습니다");
    private static final ApiClient.ApiException CORRUPTED =
            new ApiClient.ApiException(400, "CorruptedFileError", "PDF 파일이 손상되었습니다: c.pdf");

    private static List<File> files(String... names) {
        return java.util.Arrays.stream(names).map(File::new).toList();
    }

    private static List<String> resultNames(List<BatchUploader.FileOutcome> outcomes) {
        return BatchUploader.successfulResults(outcomes).stream().map(ApiClient.AnalysisResult::fileName).toList();
    }

    @Test
    void retryOnlyRetryableFailuresAndKeepOrder() {
        ScriptedUploader fake = new ScriptedUploader();
        fake.errors.put("b.pdf", NETWORK);
        fake.permanent.put("c.pdf", CORRUPTED);
        BatchUploader batch = new BatchUploader(fake);

        List<BatchUploader.FileOutcome> first = batch.run(files("a.pdf", "b.pdf", "c.pdf"));
        assertEquals(List.of("a.pdf"), resultNames(first));
        assertEquals("NETWORK_ERROR", first.get(1).errorCode());
        assertTrue(first.get(1).retryable());
        assertFalse(first.get(2).retryable(), "손상된 파일은 다시 보내도 같음");
        assertEquals(1, BatchUploader.retryTargets(first).size());

        fake.uploadOrder.clear();
        List<String> started = new ArrayList<>();
        List<BatchUploader.FileOutcome> second = batch.retry(first, new BatchUploader.Listener() {
            @Override
            public void onStart(int position, int total, File file) {
                started.add(position + "/" + total + ":" + file.getName());
            }
        });
        assertEquals(List.of("b.pdf"), fake.uploadOrder, "성공한 a 와 손상된 c 는 다시 보내지 않음");
        assertEquals(List.of("2/1:b.pdf"), started, "자리는 원래 순번, total 은 다시 하는 개수");
        assertEquals(List.of("a.pdf", "b.pdf"), resultNames(second), "원래 목록 순서대로 합침");
        assertEquals(3, second.size());
        assertTrue(second.get(1).succeeded());
        assertEquals(CORRUPTED.getMessage(), second.get(2).errorMessage(), "건너뛴 실패는 그대로");
        assertTrue(BatchUploader.retryTargets(second).isEmpty());
    }

    @Test
    void retryThatFailsAgainStaysRetryable() {
        ScriptedUploader fake = new ScriptedUploader();
        fake.permanent.put("a.pdf", NETWORK);
        BatchUploader batch = new BatchUploader(fake);
        List<BatchUploader.FileOutcome> again = batch.retry(batch.run(files("a.pdf")), new BatchUploader.Listener() { });
        assertTrue(again.get(0).retryable(), "또 실패해도 다시 시도할 수 있음");
        assertEquals(List.of("a.pdf", "a.pdf"), fake.uploadOrder);
    }

    @Test
    void unknownErrorHasNoCodeButIsRetryable() {
        Uploader crashing = new Uploader() {
            public boolean isServerUp() { return true; }
            public ApiClient.UploadResult upload(Path f) { throw new IllegalStateException("boom"); }
            public ApiClient.AnalysisResult getResult(int id) { return null; }
        };
        BatchUploader.FileOutcome o = new BatchUploader(crashing).run(files("x.pdf")).get(0);
        assertNull(o.errorCode());
        assertTrue(o.retryable());
    }

    @Test
    void serverDownMarksFilesForRetry() {
        List<BatchUploader.FileOutcome> down = BatchUploader.serverDown(files("a.pdf", "b.pdf"), "서버 꺼짐");
        assertEquals(2, BatchUploader.retryTargets(down).size());
        assertEquals(List.of(1, 2), down.stream().map(BatchUploader.FileOutcome::position).toList());

        // 다시 시도할 때 서버가 꺼져 있으면: 성공·파일 문제는 그대로, 나머지만 연결 실패로
        ScriptedUploader fake = new ScriptedUploader();
        fake.errors.put("b.pdf", NETWORK);
        fake.permanent.put("c.pdf", CORRUPTED);
        List<BatchUploader.FileOutcome> first = new BatchUploader(fake).run(files("a.pdf", "b.pdf", "c.pdf"));
        List<BatchUploader.FileOutcome> marked = BatchUploader.serverDownOnRetry(first, "서버 꺼짐");
        assertTrue(marked.get(0).succeeded());
        assertEquals(BatchUploader.SERVER_UNAVAILABLE, marked.get(1).errorCode());
        assertEquals("CorruptedFileError", marked.get(2).errorCode());
    }

    @Test
    void adviceByErrorCode() {
        for (String code : List.of("CorruptedFileError", "EncryptedFileError", "UnsupportedFormatError",
                "FILE_TOO_LARGE", "FILE_READ_ERROR", "BAD_REQUEST")) {
            assertFalse(FailureAdvice.retryable(code), code);
        }
        for (String code : new String[]{"SERVER_UNAVAILABLE", "NETWORK_ERROR", "EXTRACTOR_FAILED",
                "ConversionError", "INTERNAL_ERROR", null}) {
            assertTrue(FailureAdvice.retryable(code), String.valueOf(code));
        }
        assertEquals("암호를 풀고 다시 올리세요", FailureAdvice.hint("EncryptedFileError"));
        assertEquals("서버를 확인한 뒤 Retry Failed 를 누르세요", FailureAdvice.hint("SERVER_UNAVAILABLE"));
        assertEquals("파일을 나눠서 올리세요", FailureAdvice.hint("FILE_TOO_LARGE"));
        assertEquals("Retry Failed 로 다시 시도할 수 있어요", FailureAdvice.hint("SOMETHING_NEW"));
    }

    @Test
    void listStatusAndSummaryText() {
        ScriptedUploader fake = new ScriptedUploader();
        fake.errors.put("b.pdf", NETWORK);
        fake.permanent.put("c.pdf", CORRUPTED);
        List<BatchUploader.FileOutcome> outcomes = new BatchUploader(fake).run(files("a.pdf", "b.pdf", "c.pdf"));

        assertEquals("완료", UploadPage.statusText(outcomes.get(0)));
        assertEquals("실패: 서버와 통신 중 오류가 발생했습니다 → 서버를 확인한 뒤 Retry Failed 를 누르세요",
                UploadPage.statusText(outcomes.get(1)));
        assertTrue(UploadPage.statusText(outcomes.get(2)).startsWith("실패(다시 올려야 함): PDF 파일이 손상되었습니다"));

        String summary = UploadPage.summaryText(outcomes);
        assertTrue(summary.startsWith("성공 1개, 실패 2개"), summary);
        assertTrue(summary.contains("3. c.pdf — PDF 파일이 손상되었습니다: c.pdf\n    → 파일을 다시 저장하거나"), summary);
        assertTrue(summary.endsWith("Retry Failed 를 누르면 실패한 1개만 다시 분석합니다."), summary);

        String allGood = UploadPage.summaryText(new BatchUploader(fake).run(files("d.pdf")));
        assertEquals("성공 1개, 실패 0개", allGood);
    }
}
