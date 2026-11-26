package ui.platform;

import org.junit.jupiter.api.Test;
import ui.ApiClient;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BatchUploaderTest {

    /** 파일 이름으로 결과를 만드는 가짜 서버. failing 에 있는 이름은 실패시킨다. */
    private static final class FakeUploader implements Uploader {
        final List<String> uploadOrder = new ArrayList<>();
        final Set<String> failing;
        final Set<String> crashing;
        private final Map<Integer, String> names = new HashMap<>();
        private int nextId = 100;

        FakeUploader(Set<String> failing, Set<String> crashing) {
            this.failing = failing;
            this.crashing = crashing;
        }

        FakeUploader(String... failing) {
            this(Set.of(failing), Set.of());
        }

        @Override
        public boolean isServerUp() {
            return true;
        }

        @Override
        public ApiClient.UploadResult upload(Path file) throws ApiClient.ApiException {
            String name = file.getFileName().toString();
            uploadOrder.add(name);
            if (failing.contains(name)) {
                throw new ApiClient.ApiException(422, "EncryptedFileError", name + " 은 암호가 걸려 있습니다.");
            }
            if (crashing.contains(name)) {
                throw new IllegalStateException("boom");
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

    private static List<File> files(String... names) {
        List<File> files = new ArrayList<>();
        for (String name : names) {
            files.add(new File(name));
        }
        return files;
    }

    private static List<String> resultNames(List<BatchUploader.FileOutcome> outcomes) {
        List<String> names = new ArrayList<>();
        for (ApiClient.AnalysisResult r : BatchUploader.successfulResults(outcomes)) {
            names.add(r.fileName());
        }
        return names;
    }

    @Test
    void processesFilesSequentiallyInGivenOrder() {
        FakeUploader fake = new FakeUploader();
        // 일부러 알파벳 순이 아닌 순서
        List<BatchUploader.FileOutcome> outcomes =
                new BatchUploader(fake).run(files("c.pdf", "a.pptx", "b.ppt"));

        assertEquals(List.of("c.pdf", "a.pptx", "b.ppt"), fake.uploadOrder);
        assertEquals(List.of("c.pdf", "a.pptx", "b.ppt"), resultNames(outcomes));
        assertTrue(outcomes.stream().allMatch(BatchUploader.FileOutcome::succeeded));
    }

    @Test
    void outcomesCarryOneBasedPositionsAndFiles() {
        List<File> input = files("x.pdf", "y.pdf", "z.pdf");
        List<BatchUploader.FileOutcome> outcomes = new BatchUploader(new FakeUploader("y.pdf")).run(input);

        assertEquals(3, outcomes.size());
        for (int i = 0; i < outcomes.size(); i++) {
            assertEquals(i + 1, outcomes.get(i).position());
            assertSame(input.get(i), outcomes.get(i).file());
        }
    }

    @Test
    void middleFailureDoesNotStopLaterFilesOrChangeOrder() {
        FakeUploader fake = new FakeUploader("b.pdf");
        List<BatchUploader.FileOutcome> outcomes =
                new BatchUploader(fake).run(files("a.pdf", "b.pdf", "c.pdf"));

        assertEquals(List.of("a.pdf", "b.pdf", "c.pdf"), fake.uploadOrder);
        assertEquals(BatchUploader.Status.SUCCESS, outcomes.get(0).status());
        assertEquals(BatchUploader.Status.FAILED, outcomes.get(1).status());
        assertEquals(BatchUploader.Status.SUCCESS, outcomes.get(2).status());
        assertNull(outcomes.get(1).result());
        assertEquals("b.pdf 은 암호가 걸려 있습니다.", outcomes.get(1).errorMessage());
        assertEquals(List.of("a.pdf", "c.pdf"), resultNames(outcomes));

        List<BatchUploader.FileOutcome> failures = BatchUploader.failures(outcomes);
        assertEquals(1, failures.size());
        assertEquals(2, failures.get(0).position());
    }

    @Test
    void unexpectedRuntimeErrorIsRecordedAsFailureAndBatchContinues() {
        FakeUploader fake = new FakeUploader(Set.of(), Set.of("a.pdf"));
        List<BatchUploader.FileOutcome> outcomes =
                new BatchUploader(fake).run(files("a.pdf", "b.pdf"));

        assertFalse(outcomes.get(0).succeeded());
        assertTrue(outcomes.get(0).errorMessage().contains("boom"));
        assertTrue(outcomes.get(1).succeeded());
    }

    @Test
    void allFail() {
        FakeUploader fake = new FakeUploader("a.pdf", "b.pdf");
        List<BatchUploader.FileOutcome> outcomes =
                new BatchUploader(fake).run(files("a.pdf", "b.pdf"));

        assertEquals(List.of("a.pdf", "b.pdf"), fake.uploadOrder);
        assertTrue(BatchUploader.successfulResults(outcomes).isEmpty());
        assertEquals(List.of(1, 2),
                BatchUploader.failures(outcomes).stream().map(BatchUploader.FileOutcome::position).toList());
    }

    @Test
    void emptyList() {
        FakeUploader fake = new FakeUploader();
        List<String> events = new ArrayList<>();
        List<BatchUploader.FileOutcome> outcomes = new BatchUploader(fake).run(List.of(),
                new BatchUploader.Listener() {
                    @Override
                    public void onStart(int position, int total, File file) {
                        events.add("start");
                    }
                });

        assertTrue(outcomes.isEmpty());
        assertTrue(fake.uploadOrder.isEmpty());
        assertTrue(events.isEmpty());
    }

    @Test
    void progressCallbacksArriveInOrder() {
        List<String> events = new ArrayList<>();
        new BatchUploader(new FakeUploader("b.pdf")).run(files("a.pdf", "b.pdf", "c.pdf"),
                new BatchUploader.Listener() {
                    @Override
                    public void onStart(int position, int total, File file) {
                        events.add("start " + position + "/" + total + " " + file.getName());
                    }

                    @Override
                    public void onFinish(BatchUploader.FileOutcome outcome, int total) {
                        events.add("finish " + outcome.position() + "/" + total + " " + outcome.status());
                    }
                });

        assertEquals(List.of(
                "start 1/3 a.pdf", "finish 1/3 SUCCESS",
                "start 2/3 b.pdf", "finish 2/3 FAILED",
                "start 3/3 c.pdf", "finish 3/3 SUCCESS"), events);
    }

    @Test
    void changingInputListDuringRunDoesNotAffectOrder() {
        List<File> input = files("a.pdf", "b.pdf");
        FakeUploader fake = new FakeUploader();
        new BatchUploader(fake).run(input, new BatchUploader.Listener() {
            @Override
            public void onStart(int position, int total, File file) {
                if (position == 1) {
                    input.add(0, new File("z.pdf"));
                }
            }
        });
        assertEquals(List.of("a.pdf", "b.pdf"), fake.uploadOrder);
    }

    @Test
    void supportedExtensionsMatchServer() {
        assertTrue(UploadPage.isSupported("lecture.PDF"));
        assertTrue(UploadPage.isSupported("slides.pptx"));
        assertTrue(UploadPage.isSupported("old.ppt"));
        assertFalse(UploadPage.isSupported("notes.txt"));
        assertTrue(UploadPage.isSupported("photo.png"), "사진도 올릴 수 있음");
        assertTrue(UploadPage.isSupported("photo.JPG"));
        assertTrue(UploadPage.isSupported("photo.jpeg"));
        assertFalse(UploadPage.isSupported("photo.gif"));
    }
}
