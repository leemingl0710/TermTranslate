package ui.platform;

import ui.ApiClient;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 강의자료 업로드 화면: 단원 선택·생성, 파일 목록, 분석 진행률.
 *
 * <p>Analyze 를 누르면 목록에 보이는 순서 그대로 파일을 하나씩 서버에 올린다(BatchUploader).
 * 목록의 각 줄에 "1. a.pdf — 완료" 처럼 순번과 상태를 보여 준다.
 * 실패가 있으면 Retry Failed 로 다시 시도할 수 있는 파일만 다시 올린다 (성공한 파일은 다시 분석하지 않음).
 */
class UploadPage extends JPanel {

    /** 서버(ApiServer.isSupported)가 받는 형식만. */
    private static final String[] SUPPORTED_EXTENSIONS = {"pdf", "ppt", "pptx", "png", "jpg", "jpeg"};

    private static final String WAITING = "대기";
    private static final String ANALYZING = "분석 중";
    private static final String DONE = "완료";
    private static final String SERVER_DOWN_MESSAGE =
            "서버에 연결할 수 없습니다. FermServer 의 ApiServer 를 먼저 실행하세요.";

    private final Navigator navigator;
    private final Uploader uploader;
    private final Consumer<List<ApiClient.AnalysisResult>> resultHandler;

    /** 고른 순서 = 목록 순서 = 처리 순서. fileStatuses 와 같은 인덱스를 쓴다. */
    private final List<File> selectedFiles = new ArrayList<>();
    private final List<String> fileStatuses = new ArrayList<>();
    private final List<String> chapters = new ArrayList<>();
    private final DefaultListModel<String> fileModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileModel);
    private final JComboBox<String> chapterBox = new JComboBox<>();
    private final JProgressBar analysisProgressBar = new JProgressBar(0, 100);

    private JButton selectButton;
    private JButton removeButton;
    private JButton analyzeButton;
    private JButton retryButton;
    private boolean running;
    /** 마지막 분석의 파일별 결과 (selectedFiles 와 같은 순서). 목록을 바꾸면 비운다. */
    private List<BatchUploader.FileOutcome> lastOutcomes;

    /**
     * @param uploader      서버에 올리는 부분
     * @param resultHandler 분석이 끝나면 성공한 결과를 목록 순서대로 받는다 (화면 스레드에서 불림)
     */
    UploadPage(Navigator navigator, Uploader uploader,
               Consumer<List<ApiClient.AnalysisResult>> resultHandler) {
        super(new BorderLayout(15, 15));
        this.navigator = navigator;
        this.uploader = uploader;
        this.resultHandler = resultHandler;
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        add(createHeader(), BorderLayout.NORTH);
        add(createFileList(), BorderLayout.CENTER);
        add(createBottom(), BorderLayout.SOUTH);
    }

    // ---------------- 화면 구성 ----------------

    private JPanel createHeader() {
        chapterBox.addItem("Select Chapter");
        chapterBox.setPreferredSize(new Dimension(250, 35));
        chapterBox.setFont(Theme.koreanFont(14));

        JButton chapterButton = Theme.button("Create Chapter", 13, null);
        chapterButton.addActionListener(e -> createChapter());

        JPanel chapterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        chapterPanel.setBackground(Theme.BACKGROUND);
        chapterPanel.add(new JLabel("Chapter:"));
        chapterPanel.add(chapterBox);
        chapterPanel.add(chapterButton);

        JPanel headerPanel = new JPanel(new BorderLayout(5, 10));
        headerPanel.setBackground(Theme.BACKGROUND);
        headerPanel.add(Theme.header("Upload Learning Materials", 26,
                "Select your lecture materials (PDF, PPT, PPTX, or photos: PNG, JPG). They are analyzed in list order.", 14),
                BorderLayout.NORTH);
        headerPanel.add(chapterPanel, BorderLayout.SOUTH);
        return headerPanel;
    }

    private JScrollPane createFileList() {
        fileList.setFont(Theme.koreanFont(15));
        fileList.setBackground(Theme.CARD);
        fileList.setForeground(Theme.TITLE);
        fileList.setSelectionBackground(Theme.SELECTION);
        fileList.setSelectionForeground(Theme.TITLE);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setFixedCellHeight(40);

        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scrollPane.getViewport().setBackground(Theme.CARD);
        return scrollPane;
    }

    private JPanel createBottom() {
        // 버튼 다섯 개가 한 줄을 똑같이 나눠 쓴다 (창이 좁아도 줄이 넘어가 잘리지 않게)
        Dimension size = new Dimension(0, 42);
        selectButton = Theme.button("Select Files", 13, size);
        removeButton = Theme.button("Remove", 13, size);
        JButton ocrButton = Theme.button("OCR Review", 13, size);
        analyzeButton = Theme.button("Analyze", 13, size);
        retryButton = Theme.button("Retry Failed", 13, size);
        retryButton.setEnabled(false);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 5, 10, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        buttonPanel.setBackground(Theme.BACKGROUND);
        buttonPanel.add(selectButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(ocrButton);
        buttonPanel.add(analyzeButton);
        buttonPanel.add(retryButton);

        analysisProgressBar.setValue(0);
        analysisProgressBar.setStringPainted(true);
        analysisProgressBar.setString("Ready for analysis");
        analysisProgressBar.setFont(Theme.koreanFont(13));
        analysisProgressBar.setForeground(Theme.PRIMARY);
        analysisProgressBar.setPreferredSize(new Dimension(500, 25));

        ocrButton.addActionListener(e -> navigator.show(Page.OCR));
        selectButton.addActionListener(e -> selectFiles());
        removeButton.addActionListener(e -> removeSelectedFile());
        analyzeButton.addActionListener(e -> analyze());
        retryButton.addActionListener(e -> retryFailed());

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 10));
        bottomPanel.setBackground(Theme.BACKGROUND);
        bottomPanel.add(analysisProgressBar, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        return bottomPanel;
    }

    // ---------------- 파일 목록 ----------------

    /** 목록 한 줄: "1. a.pdf — 대기" */
    private static String listLabel(int position, File file, String status) {
        return position + ". " + file.getName() + " — " + status;
    }

    /** selectedFiles/fileStatuses 를 화면 목록에 그대로 다시 그린다 (순번도 다시 매김). */
    private void refreshFileList() {
        int selected = fileList.getSelectedIndex();
        fileModel.clear();
        for (int i = 0; i < selectedFiles.size(); i++) {
            fileModel.addElement(listLabel(i + 1, selectedFiles.get(i), fileStatuses.get(i)));
        }
        if (selected >= 0 && selected < fileModel.size()) {
            fileList.setSelectedIndex(selected);
        }
    }

    private void setStatus(int index, String status) {
        fileStatuses.set(index, status);
        fileModel.set(index, listLabel(index + 1, selectedFiles.get(index), status));
    }

    private boolean containsFileName(String name) {
        for (File file : selectedFiles) {
            if (file.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    // ---------------- 버튼 동작 ----------------

    private void selectFiles() {
        if (running) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Supported Files (PDF, PPT, PPTX, PNG, JPG)", SUPPORTED_EXTENSIONS));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        for (File file : chooser.getSelectedFiles()) {
            String name = file.getName();
            if (!isSupported(name)) {
                JOptionPane.showMessageDialog(this,
                        "Unsupported file format: " + name + "\nPlease select PDF, PPT, PPTX, PNG or JPG.",
                        "File Format Error", JOptionPane.ERROR_MESSAGE);
            } else if (!containsFileName(name)) {
                selectedFiles.add(file);
                fileStatuses.add(WAITING);
                forgetOutcomes();   // 목록이 바뀌면 지난 결과의 자리와 맞지 않음
            }
        }
        refreshFileList();
    }

    static boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    private void removeSelectedFile() {
        if (running) {
            return;
        }
        int index = fileList.getSelectedIndex();
        if (index == -1) {
            JOptionPane.showMessageDialog(this, "Please select a file to remove.");
            return;
        }
        selectedFiles.remove(index);
        fileStatuses.remove(index);
        forgetOutcomes();
        refreshFileList(); // 뒤 파일들의 순번을 다시 매긴다
    }

    private void analyze() {
        if (running) {
            return;
        }
        if (selectedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please upload a file first.");
            return;
        }
        List<File> files = new ArrayList<>(selectedFiles); // 지금 목록 순서를 고정
        for (int i = 0; i < files.size(); i++) {
            fileStatuses.set(i, WAITING);
        }
        refreshFileList();
        lastOutcomes = null;
        setRunning(true);
        showProgress(0, "서버 연결 확인 중...");
        new BatchWorker(files, null).execute();
    }

    /** 다시 시도할 수 있는 실패만 다시 올린다. 성공한 파일과 파일 자체가 문제인 실패는 그대로 둔다. */
    private void retryFailed() {
        if (running || lastOutcomes == null) {
            return;
        }
        List<BatchUploader.FileOutcome> targets = BatchUploader.retryTargets(lastOutcomes);
        if (targets.isEmpty()) {
            return;
        }
        for (BatchUploader.FileOutcome o : targets) {
            setStatus(o.position() - 1, WAITING);
        }
        setRunning(true);
        showProgress(0, "서버 연결 확인 중...");
        new BatchWorker(null, lastOutcomes).execute();
    }

    private void forgetOutcomes() {
        lastOutcomes = null;
        retryButton.setEnabled(false);
    }

    /** 목록에 보일 상태: 완료 / 실패: 이유 → 할 일 */
    static String statusText(BatchUploader.FileOutcome o) {
        if (o.succeeded()) {
            return DONE;
        }
        String head = o.retryable() ? "실패: " : "실패(다시 올려야 함): ";
        return head + o.errorMessage() + " → " + FailureAdvice.hint(o.errorCode());
    }

    private void setRunning(boolean value) {
        running = value;
        selectButton.setEnabled(!value);
        removeButton.setEnabled(!value);
        analyzeButton.setEnabled(!value);
        retryButton.setEnabled(!value && lastOutcomes != null
                && !BatchUploader.retryTargets(lastOutcomes).isEmpty());
        setCursor(value ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void showProgress(int percent, String text) {
        analysisProgressBar.setValue(percent);
        analysisProgressBar.setString(text);
    }

    /**
     * 목록의 파일들을 순서대로 처리한다 (files), 또는 지난 결과에서 실패만 다시 처리한다 (previous).
     * 화면 갱신은 publish → process 로 화면 스레드에서 한다.
     */
    private final class BatchWorker extends SwingWorker<List<BatchUploader.FileOutcome>, Runnable> {

        private final List<File> files;
        private final List<BatchUploader.FileOutcome> previous;
        private boolean serverDown;
        /** 이번에 처리한 순서 (진행률용, 작업 스레드에서만 씀) */
        private int step;

        BatchWorker(List<File> files, List<BatchUploader.FileOutcome> previous) {
            this.files = files;
            this.previous = previous;
        }

        @Override
        protected List<BatchUploader.FileOutcome> doInBackground() {
            if (!uploader.isServerUp()) {
                serverDown = true;
                return previous == null
                        ? BatchUploader.serverDown(files, SERVER_DOWN_MESSAGE)
                        : BatchUploader.serverDownOnRetry(previous, SERVER_DOWN_MESSAGE);
            }
            BatchUploader.Listener listener = new BatchUploader.Listener() {
                @Override
                public void onStart(int position, int total, File file) {
                    int current = ++step;
                    publish(() -> {
                        setStatus(position - 1, ANALYZING);
                        showProgress((current - 1) * 100 / total,
                                current + "/" + total + " · " + file.getName() + " 분석 중...");
                    });
                }

                @Override
                public void onFinish(BatchUploader.FileOutcome outcome, int total) {
                    int current = step;
                    publish(() -> {
                        setStatus(outcome.position() - 1, statusText(outcome));
                        showProgress(current * 100 / total, current + "/" + total + " 처리됨");
                    });
                }
            };
            BatchUploader batch = new BatchUploader(uploader);
            return previous == null ? batch.run(files, listener) : batch.retry(previous, listener);
        }

        @Override
        protected void process(List<Runnable> updates) {
            updates.forEach(Runnable::run);
        }

        @Override
        protected void done() {
            List<BatchUploader.FileOutcome> outcomes;
            try {
                outcomes = get();
            } catch (Exception e) {
                setRunning(false);
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                showProgress(0, "분석 실패");
                JOptionPane.showMessageDialog(UploadPage.this, String.valueOf(cause.getMessage()),
                        "Analysis failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int succeededBefore = previous == null ? 0 : BatchUploader.successfulResults(previous).size();
            lastOutcomes = outcomes;
            for (BatchUploader.FileOutcome o : outcomes) {
                setStatus(o.position() - 1, statusText(o));
            }
            setRunning(false);   // lastOutcomes 를 보고 Retry Failed 를 켠다
            if (serverDown) {
                showProgress(0, "서버에 연결할 수 없습니다");
                JOptionPane.showMessageDialog(UploadPage.this,
                        SERVER_DOWN_MESSAGE + "\n서버를 켠 뒤 Retry Failed 를 누르면 이어서 분석합니다.",
                        "Server unavailable", JOptionPane.ERROR_MESSAGE);
                return;
            }
            finish(outcomes, succeededBefore);
        }
    }

    /** @param succeededBefore 다시 시도 전에 이미 성공해 있던 개수 (처음 분석이면 0) */
    private void finish(List<BatchUploader.FileOutcome> outcomes, int succeededBefore) {
        List<ApiClient.AnalysisResult> results = BatchUploader.successfulResults(outcomes);
        List<BatchUploader.FileOutcome> failures = BatchUploader.failures(outcomes);
        showProgress(100, "완료: 성공 " + results.size() + " · 실패 " + failures.size());

        JOptionPane.showMessageDialog(this, summaryText(outcomes), "Analysis finished",
                failures.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);

        // 이번에 새로 성공한 것이 있을 때만 결과 화면으로 (지난 성공 결과도 목록 순서대로 함께)
        if (results.size() > succeededBefore) {
            resultHandler.accept(results);
            navigator.show(Page.RESULT);
        }
    }

    /** 끝 안내 창의 글: 성공·실패 수, 실패마다 이유와 할 일, 다시 시도 안내 */
    static String summaryText(List<BatchUploader.FileOutcome> outcomes) {
        List<BatchUploader.FileOutcome> failures = BatchUploader.failures(outcomes);
        int retryable = BatchUploader.retryTargets(outcomes).size();
        StringBuilder summary = new StringBuilder()
                .append("성공 ").append(BatchUploader.successfulResults(outcomes).size())
                .append("개, 실패 ").append(failures.size()).append("개");
        for (BatchUploader.FileOutcome f : failures) {
            summary.append("\n").append(f.position()).append(". ").append(f.file().getName())
                    .append(" — ").append(f.errorMessage())
                    .append("\n    → ").append(FailureAdvice.hint(f.errorCode()));
        }
        if (retryable > 0) {
            summary.append("\n\nRetry Failed 를 누르면 실패한 ").append(retryable).append("개만 다시 분석합니다.");
        }
        return summary.toString();
    }

    private void createChapter() {
        String chapterName = JOptionPane.showInputDialog(this, "Enter a chapter name:",
                "Create Chapter", JOptionPane.PLAIN_MESSAGE);
        if (chapterName == null) {
            return;
        }
        chapterName = chapterName.trim();
        if (chapterName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a chapter name.");
            return;
        }
        if (chapters.contains(chapterName)) {
            JOptionPane.showMessageDialog(this, "This chapter already exists.");
            return;
        }
        chapters.add(chapterName);
        chapterBox.addItem(chapterName);
        chapterBox.setSelectedItem(chapterName);
        JOptionPane.showMessageDialog(this, "Chapter created: " + chapterName);
    }

    // ---------------- 백엔드 연동용 ----------------

    /** 고른 순서 그대로의 파일 목록. */
    List<File> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }

    /** 고른 단원 이름. "Select Chapter" 그대로면 null. */
    String getSelectedChapter() {
        return chapterBox.getSelectedIndex() <= 0 ? null : (String) chapterBox.getSelectedItem();
    }

    void updateAnalysisProgress(int progress) {
        analysisProgressBar.setValue(progress);
        analysisProgressBar.setString("Analysis Progress: " + analysisProgressBar.getValue() + "%");
    }
}
