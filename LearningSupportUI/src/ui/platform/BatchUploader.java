package ui.platform;

import ui.ApiClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 여러 파일을 목록 순서 그대로 하나씩 차례로 서버에 올린다.
 *
 * <p>한 파일이 실패해도 다음 파일을 계속 처리하고, 결과 목록은 언제나 처음 순서와 같다.
 * {@link #retry} 는 다시 시도할 수 있는 실패만 같은 자리에서 다시 처리한다 (성공한 파일은 그대로).
 * Swing 과 무관하므로 단위 테스트할 수 있다. 시간이 걸리니 화면 스레드에서 부르지 말 것.
 */
final class BatchUploader {

    enum Status { SUCCESS, FAILED }

    /** 서버에 연결하지 못했을 때의 오류 코드 (ApiClient 와 같음) */
    static final String SERVER_UNAVAILABLE = "SERVER_UNAVAILABLE";

    /**
     * 파일 하나의 처리 결과. position 은 목록에서의 위치(1부터).
     * errorCode 는 서버가 알려 준 오류 종류 (예: CorruptedFileError). 모르는 오류면 null.
     */
    record FileOutcome(int position, File file, Status status,
                       ApiClient.AnalysisResult result, String errorMessage, String errorCode) {

        boolean succeeded() {
            return status == Status.SUCCESS;
        }

        /** 실패했고, 다시 보내면 성공할 수도 있음 (파일 자체 문제가 아님) */
        boolean retryable() {
            return !succeeded() && FailureAdvice.retryable(errorCode);
        }
    }

    /** 진행 알림. 작업 스레드에서 불린다. */
    interface Listener {
        /** position 번째 파일 처리를 시작한다 (1부터). */
        default void onStart(int position, int total, File file) {
        }

        /** 파일 하나의 처리가 끝났다 (성공·실패 모두). */
        default void onFinish(FileOutcome outcome, int total) {
        }
    }

    private final Uploader uploader;

    BatchUploader(Uploader uploader) {
        this.uploader = uploader;
    }

    List<FileOutcome> run(List<File> files) {
        return run(files, new Listener() { });
    }

    /** files 를 주어진 순서대로 하나씩 처리하고, 같은 순서의 결과 목록을 돌려준다. */
    List<FileOutcome> run(List<File> files, Listener listener) {
        List<File> ordered = List.copyOf(files); // 처리 중 원본 목록이 바뀌어도 순서는 그대로
        int total = ordered.size();
        List<FileOutcome> outcomes = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int position = i + 1;
            File file = ordered.get(i);
            listener.onStart(position, total, file);
            FileOutcome outcome = process(position, file);
            outcomes.add(outcome);
            listener.onFinish(outcome, total);
        }
        return Collections.unmodifiableList(outcomes);
    }

    /**
     * previous 에서 다시 시도할 수 있는 실패만 순서대로 다시 처리하고, 같은 자리에 바꿔 넣은 목록을 돌려준다.
     * 알림의 total 은 다시 처리하는 파일 수다.
     */
    List<FileOutcome> retry(List<FileOutcome> previous, Listener listener) {
        List<FileOutcome> merged = new ArrayList<>(previous);
        List<FileOutcome> todo = retryTargets(previous);
        for (FileOutcome old : todo) {
            listener.onStart(old.position(), todo.size(), old.file());
            FileOutcome outcome = process(old.position(), old.file());
            merged.set(indexOf(merged, old.position()), outcome);
            listener.onFinish(outcome, todo.size());
        }
        return Collections.unmodifiableList(merged);
    }

    /** 다시 시도할 실패들 (처음 순서) */
    static List<FileOutcome> retryTargets(List<FileOutcome> outcomes) {
        return outcomes.stream().filter(FileOutcome::retryable).toList();
    }

    /** 서버가 꺼져 있어 시작하지 못한 경우: 모든 파일을 "연결 실패" 로 (나중에 다시 시도 가능) */
    static List<FileOutcome> serverDown(List<File> files, String message) {
        List<FileOutcome> out = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            out.add(new FileOutcome(i + 1, files.get(i), Status.FAILED, null, message, SERVER_UNAVAILABLE));
        }
        return Collections.unmodifiableList(out);
    }

    /** 다시 시도하려는데 서버가 꺼져 있는 경우: 다시 시도할 파일만 "연결 실패" 로 바꾼다 */
    static List<FileOutcome> serverDownOnRetry(List<FileOutcome> previous, String message) {
        List<FileOutcome> out = new ArrayList<>();
        for (FileOutcome o : previous) {
            out.add(o.retryable()
                    ? new FileOutcome(o.position(), o.file(), Status.FAILED, null, message, SERVER_UNAVAILABLE)
                    : o);
        }
        return Collections.unmodifiableList(out);
    }

    private static int indexOf(List<FileOutcome> outcomes, int position) {
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i).position() == position) {
                return i;
            }
        }
        throw new IllegalArgumentException("없는 위치: " + position);
    }

    private FileOutcome process(int position, File file) {
        try {
            ApiClient.UploadResult uploaded = uploader.upload(file.toPath());
            ApiClient.AnalysisResult result = uploader.getResult(uploaded.resultId());
            return new FileOutcome(position, file, Status.SUCCESS, result, null, null);
        } catch (ApiClient.ApiException e) {
            return failed(position, file, e.getMessage(), e.getError());
        } catch (RuntimeException e) {
            // 예상 못 한 오류도 이 파일만 실패로 두고 다음 파일로 넘어간다
            return failed(position, file, "알 수 없는 오류: " + e, null);
        }
    }

    private static FileOutcome failed(int position, File file, String message, String code) {
        String text = message == null || message.isBlank() ? "알 수 없는 오류" : message;
        return new FileOutcome(position, file, Status.FAILED, null, text, code);
    }

    /** 성공한 결과만 처음 순서대로. */
    static List<ApiClient.AnalysisResult> successfulResults(List<FileOutcome> outcomes) {
        List<ApiClient.AnalysisResult> results = new ArrayList<>();
        for (FileOutcome outcome : outcomes) {
            if (outcome.succeeded()) {
                results.add(outcome.result());
            }
        }
        return results;
    }

    /** 실패한 파일만 처음 순서대로. */
    static List<FileOutcome> failures(List<FileOutcome> outcomes) {
        List<FileOutcome> failed = new ArrayList<>();
        for (FileOutcome outcome : outcomes) {
            if (!outcome.succeeded()) {
                failed.add(outcome);
            }
        }
        return failed;
    }
}
