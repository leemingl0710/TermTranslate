package ui.platform;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * 서버 요청처럼 오래 걸리는 일을 화면을 멈추지 않고 돌린다.
 * 일은 작업 스레드에서, 결과 처리(onSuccess / onFailure)는 화면 스레드에서 한다.
 * 테스트에서는 바로 돌리는 {@link #DIRECT} 로 바꿔 끼운다.
 */
interface Background {

    /** 작업 스레드에서 할 일. 예외를 던지면 onFailure 로 간다. */
    @FunctionalInterface
    interface Task<T> {
        T call() throws Exception;
    }

    <T> void run(Task<T> task, Consumer<T> onSuccess, Consumer<Exception> onFailure);

    /** 실제 화면용: SwingWorker 로 돌린다. */
    Background SWING = new Background() {
        @Override
        public <T> void run(Task<T> task, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
            new SwingWorker<T, Void>() {
                @Override
                protected T doInBackground() throws Exception {
                    return task.call();
                }

                @Override
                protected void done() {
                    T value;
                    try {
                        value = get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        Throwable cause = e.getCause();
                        onFailure.accept(cause instanceof Exception ex ? ex : new RuntimeException(cause));
                        return;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        onFailure.accept(e);
                        return;
                    }
                    onSuccess.accept(value);
                }
            }.execute();
        }
    };

    /** 테스트용: 부른 스레드에서 바로 돌린다. */
    Background DIRECT = new Background() {
        @Override
        public <T> void run(Task<T> task, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
            T value;
            try {
                value = task.call();
            } catch (Exception e) {
                onFailure.accept(e);
                return;
            }
            onSuccess.accept(value);
        }
    };
}
