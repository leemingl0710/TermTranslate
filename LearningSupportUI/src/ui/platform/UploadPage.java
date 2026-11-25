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
 */
class UploadPage extends JPanel {

    /** 서버(ApiServer.isSupported)가 받는 형식만. */
    private static final String[] SUPPORTED_EXTENSIONS = {"pdf", "ppt", "pptx"};

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
    private boolean running;

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
                "Select your lecture materials (PDF, PPT, PPTX). They are analyzed in list order.", 14),
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
        Dimension size = new Dimension(160, 42);
        selectButton = Theme.button("Select Files", 14, size);
        removeButton = Theme.button("Remove Selected", 14, size);
        JButton ocrButton = Theme.button("OCR Review", 14, size);
        analyzeButton = Theme.button("Analyze", 14, size);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(Theme.BACKGROUND);
        buttonPanel.add(selectButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(ocrButton);
        buttonPanel.add(analyzeButton);

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
                "Supported Files (PDF, PPT, PPTX)", SUPPORTED_EXTENSIONS));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        for (File file : chooser.getSelectedFiles()) {
            String name = file.getName();
            if (!isSupported(name)) {
                JOptionPane.showMessageDialog(this,
                        "Unsupported file format: " + name + "\nPlease select PDF, PPT or PPTX.",
                        "File Format Error", JOptionPane.ERROR_MESSAGE);
            } else if (!containsFileName(name)) {
                selectedFiles.add(file);
                fileStatuses.add(WAITING);
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
        setRunning(true);
        showProgress(0, "서버 연결 확인 중...");
        new BatchWorker(files).execute();
    }

    private void setRunning(boolean value) {
        running = value;
        selectButton.setEnabled(!value);
        removeButton.setEnabled(!value);
        analyzeButton.setEnabled(!value);
        setCursor(value ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void showProgress(int percent, String text) {
        analysisProgressBar.setValue(percent);
        analysisProgressBar.setString(text);
    }

    /** 목록의 파일들을 순서대로 처리한다. 화면 갱신은 publish → process 로 화면 스레드에서 한다. */
    private final class BatchWorker extends SwingWorker<List<BatchUploader.FileOutcome>, Runnable> {

        private final List<File> files;
        private boolean serverDown;

        BatchWorker(List<File> files) {
            this.files = files;
        }

        @Override
        protected List<BatchUploader.FileOutcome> doInBackground() {
            if (!uploader.isServerUp()) {
                serverDown = true;
                return List.of();
            }
            return new BatchUploader(uploader).run(files, new BatchUploader.Listener() {
                @Override
                public void onStart(int position, int total, File file) {
                    publish(() -> {
                        setStatus(position - 1, ANALYZING);
                        showProgress((position - 1) * 100 / total,
                                position + "/" + total + " · " + file.getName() + " 분석 중...");
                    });
                }

                @Override
                public void onFinish(BatchUploader.FileOutcome outcome, int total) {
                    publish(() -> {
                        setStatus(outcome.position() - 1,
                                outcome.succeeded() ? DONE : "실패: " + outcome.errorMessage());
                        showProgress(outcome.position() * 100 / total,
                                outcome.position() + "/" + total + " 처리됨");
                    });
                }
            });
        }

        @Override
        protected void process(List<Runnable> updates) {
            updates.forEach(Runnable::run);
        }

        @Override
        protected void done() {
            setRunning(false);
            List<BatchUploader.FileOutcome> outcomes;
            try {
                outcomes = get();
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                showProgress(0, "분석 실패");
                JOptionPane.showMessageDialog(UploadPage.this, String.valueOf(cause.getMessage()),
                        "Analysis failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (serverDown) {
                showProgress(0, "서버에 연결할 수 없습니다");
                JOptionPane.showMessageDialog(UploadPage.this, SERVER_DOWN_MESSAGE,
                        "Server unavailable", JOptionPane.ERROR_MESSAGE);
                return;
            }
            finish(outcomes);
        }
    }

    private void finish(List<BatchUploader.FileOutcome> outcomes) {
        List<ApiClient.AnalysisResult> results = BatchUploader.successfulResults(outcomes);
        List<BatchUploader.FileOutcome> failures = BatchUploader.failures(outcomes);
        showProgress(100, "완료: 성공 " + results.size() + " · 실패 " + failures.size());

        StringBuilder summary = new StringBuilder()
                .append("성공 ").append(results.size()).append("개, 실패 ")
                .append(failures.size()).append("개");
        for (BatchUploader.FileOutcome f : failures) {
            summary.append("\n").append(f.position()).append(". ").append(f.file().getName())
                    .append(" — ").append(f.errorMessage());
        }
        JOptionPane.showMessageDialog(this, summary.toString(), "Analysis finished",
                failures.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);

        if (!results.isEmpty()) {
            resultHandler.accept(results);
            navigator.show(Page.RESULT);
        }
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
