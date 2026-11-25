package ui.platform;

import ui.ApiClient;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 분석 결과 화면: 페이지별 원문 ↔ 번역 비교, 아래 탭(설명·요약·용어·체크리스트).
 * 서버 결과는 {@link #showResults(List)} (여러 파일) 또는 {@link #showResult(ApiClient.AnalysisResult)} 로 넣는다.
 * 글 해석은 {@link PagedResult} 가 맡는다.
 */
class ResultPage extends JPanel {

    static final String EMPTY_SUBTITLE = "Review your lecture materials and prepare for exams.";
    static final String EMPTY_TEXT = "No analysis yet. Upload a lecture file to see the results here.";

    private final JLabel subtitleLabel = new JLabel(EMPTY_SUBTITLE);
    private final JComboBox<String> fileSelector = new JComboBox<>();
    private final JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JButton previousButton = new JButton("Previous");
    private final JButton nextButton = new JButton("Next");
    private final JLabel pageLabel = new JLabel();
    private final JTextArea originalText = Theme.readOnlyText(EMPTY_TEXT);
    private final JTextArea translatedText = Theme.readOnlyText(EMPTY_TEXT);
    private final JTextArea explanationArea = Theme.readOnlyText(EMPTY_TEXT);
    private final JTextArea summaryArea = Theme.readOnlyText(EMPTY_TEXT);
    private final JTextArea termsArea = Theme.readOnlyText(EMPTY_TEXT);

    /** 파일 선택 순서 그대로의 결과 목록. */
    private final List<ApiClient.AnalysisResult> results = new ArrayList<>();
    private PagedResult current = PagedResult.of(null);
    private int currentPage = 0;
    /** 콤보 상자를 코드로 채우는 동안 선택 이벤트를 무시한다. */
    private boolean fillingSelector = false;

    ResultPage() {
        super(new BorderLayout(15, 15));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        add(createHeader(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(Theme.BACKGROUND);
        centerPanel.add(createNavigation(), BorderLayout.NORTH);
        centerPanel.add(createComparison(), BorderLayout.CENTER);
        centerPanel.add(createLearningTabs(), BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        JLabel warningLabel = new JLabel(
                "Notice: AI explanations may contain errors. Please check them against the original material.");
        warningLabel.setFont(Theme.font(Font.PLAIN, 12));
        warningLabel.setForeground(Theme.WARNING);
        add(warningLabel, BorderLayout.SOUTH);

        showEmptyState();
    }

    // ---------------- 외부에서 부르는 곳 ----------------

    /** 여러 파일 결과. 목록 순서(사용자가 고른 순서) 그대로 파일 선택 상자에 넣고 첫 파일을 보여 준다. */
    void showResults(List<ApiClient.AnalysisResult> list) {
        if (!SwingUtilities.isEventDispatchThread()) {
            List<ApiClient.AnalysisResult> copy = list == null ? List.of() : new ArrayList<>(list);
            SwingUtilities.invokeLater(() -> showResults(copy));
            return;
        }
        results.clear();
        if (list != null) {
            for (ApiClient.AnalysisResult r : list) {
                if (r != null) {
                    results.add(r);
                }
            }
        }
        fillingSelector = true;
        try {
            fileSelector.removeAllItems();
            for (int i = 0; i < results.size(); i++) {
                fileSelector.addItem((i + 1) + ". " + displayName(results.get(i)));
            }
        } finally {
            fillingSelector = false;
        }
        if (results.isEmpty()) {
            showEmptyState();
            return;
        }
        fileRow.setVisible(true);
        fileSelector.setSelectedIndex(0);
        display(results.get(0));
    }

    /** 결과 하나. 파일 선택 상자에는 이 파일 하나만 남는다. */
    void showResult(ApiClient.AnalysisResult result) {
        showResults(result == null ? List.of() : List.of(result));
    }

    // ---------------- 화면 구성 ----------------

    private JPanel createHeader() {
        JLabel heading = new JLabel("Analysis Results");
        heading.setFont(Theme.font(Font.BOLD, 25));
        heading.setForeground(Theme.TITLE);

        // 파일 이름이 한글일 수 있다
        subtitleLabel.setFont(Theme.koreanFont(13));
        subtitleLabel.setForeground(Theme.MUTED);

        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));
        panel.setBackground(Theme.BACKGROUND);
        panel.add(heading);
        panel.add(subtitleLabel);
        return panel;
    }

    /** 파일 선택 + 이전 / Page n / 전체 / 다음. */
    private JPanel createNavigation() {
        JLabel fileLabel = new JLabel("File:");
        fileLabel.setFont(Theme.font(Font.BOLD, 13));
        fileSelector.setFont(Theme.koreanFont(13));
        fileSelector.addActionListener(e -> {
            int index = fileSelector.getSelectedIndex();
            if (!fillingSelector && index >= 0 && index < results.size()) {
                display(results.get(index));
            }
        });
        fileRow.setBackground(Theme.BACKGROUND);
        fileRow.add(fileLabel);
        fileRow.add(fileSelector);

        pageLabel.setFont(Theme.font(Font.BOLD, 14));
        previousButton.addActionListener(e -> showPage(currentPage - 1));
        nextButton.addActionListener(e -> showPage(currentPage + 1));

        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pagePanel.setBackground(Theme.BACKGROUND);
        pagePanel.add(previousButton);
        pagePanel.add(pageLabel);
        pagePanel.add(nextButton);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Theme.BACKGROUND);
        panel.add(fileRow, BorderLayout.NORTH);
        panel.add(pagePanel, BorderLayout.CENTER);
        return panel;
    }

    /** 왼쪽 원문, 오른쪽 번역. 원문에도 한글이 섞일 수 있어 둘 다 한글 글꼴. */
    private JSplitPane createComparison() {
        originalText.setFont(Theme.koreanFont(14));
        translatedText.setFont(Theme.koreanFont(14));

        JSplitPane comparisonPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titledBox("Original Text", originalText),
                titledBox("Korean Translation", translatedText));
        comparisonPanel.setResizeWeight(0.5);
        comparisonPanel.setDividerLocation(0.5);
        return comparisonPanel;
    }

    private static JPanel titledBox(String title, JTextArea text) {
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(Font.BOLD, 15));

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Theme.CARD);
        panel.add(label, BorderLayout.NORTH);
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        return panel;
    }

    private JTabbedPane createLearningTabs() {
        explanationArea.setFont(Theme.koreanFont(13));
        summaryArea.setFont(Theme.koreanFont(13));
        termsArea.setFont(Theme.koreanFont(13));

        JPanel checklistPanel = new JPanel();
        checklistPanel.setLayout(new BoxLayout(checklistPanel, BoxLayout.Y_AXIS));
        checklistPanel.setBackground(Theme.CARD);
        checklistPanel.add(new JCheckBox("I understand the main concept."));
        checklistPanel.add(new JCheckBox("I reviewed the important terms."));
        checklistPanel.add(new JCheckBox("I checked the original lecture text."));

        JTabbedPane learningTabs = new JTabbedPane();
        learningTabs.addTab("Page Explanation", new JScrollPane(explanationArea));
        learningTabs.addTab("Chapter Summary", new JScrollPane(summaryArea));
        learningTabs.addTab("Key Terms", new JScrollPane(termsArea));
        learningTabs.addTab("Checklist", checklistPanel);
        learningTabs.setPreferredSize(new Dimension(700, 160));
        return learningTabs;
    }

    // ---------------- 내용 채우기 ----------------

    private void showEmptyState() {
        current = PagedResult.of(null);
        currentPage = 0;
        fileRow.setVisible(false);
        subtitleLabel.setText(EMPTY_SUBTITLE);
        originalText.setText(EMPTY_TEXT);
        translatedText.setText(EMPTY_TEXT);
        explanationArea.setText(EMPTY_TEXT);
        summaryArea.setText(EMPTY_TEXT);
        termsArea.setText(EMPTY_TEXT);
        updatePager();
    }

    /** 결과 하나를 화면에 펼친다 (1페이지부터). */
    private void display(ApiClient.AnalysisResult result) {
        current = PagedResult.of(result);
        subtitleLabel.setText(displayName(result) + "  ·  " + current.pageCount() + " pages");
        summaryArea.setText(chapterSummary(current));
        termsArea.setText(orNotAvailable(current.termsText()));
        currentPage = 0;
        if (current.pageCount() == 0) {
            originalText.setText("No text was extracted from this file.");
            translatedText.setText(PagedResult.NOT_AVAILABLE);
            explanationArea.setText(PagedResult.NOT_AVAILABLE);
            updatePager();
            return;
        }
        showPage(0);
    }

    private void showPage(int page) {
        if (page < 0 || page >= current.pageCount()) {
            return;
        }
        currentPage = page;
        PagedResult.PageView view = current.pages().get(page);
        originalText.setText(view.original().isEmpty()
                ? "(No text was extracted from this page.)" : view.original());
        translatedText.setText(orNotAvailable(view.translation()));
        explanationArea.setText(orNotAvailable(view.explanationText()));
        originalText.setCaretPosition(0);
        translatedText.setCaretPosition(0);
        explanationArea.setCaretPosition(0);
        updatePager();
    }

    private void updatePager() {
        int total = current.pageCount();
        if (total == 0) {
            pageLabel.setText("Page 0 / 0");
        } else {
            int number = current.pages().get(currentPage).number();
            String label = "Page " + (currentPage + 1) + " / " + total;
            // 빈 페이지가 빠져 순번과 실제 쪽 번호가 다르면 실제 번호도 보여 준다
            pageLabel.setText(number == currentPage + 1 ? label : label + "  (p." + number + ")");
        }
        previousButton.setEnabled(currentPage > 0);
        nextButton.setEnabled(currentPage < total - 1);
    }

    /** 단원 요약 + 핵심 개념·개념 설명 (페이지 연결은 페이지별 탭에 있으므로 뺀다). */
    private static String chapterSummary(PagedResult r) {
        String unit = r.unitSummary();
        String concepts = r.conceptText();
        if (unit == null && concepts == null) {
            return PagedResult.NOT_AVAILABLE;
        }
        if (concepts == null) {
            return unit;
        }
        return (unit == null ? PagedResult.NOT_AVAILABLE : unit) + "\n\n" + concepts;
    }

    private static String orNotAvailable(String s) {
        return s == null || s.isBlank() ? PagedResult.NOT_AVAILABLE : s;
    }

    private static String displayName(ApiClient.AnalysisResult r) {
        return r == null || r.fileName() == null || r.fileName().isBlank()
                ? "(untitled)" : r.fileName();
    }

    // ---------------- 테스트용 ----------------

    String pageLabelText() {
        return pageLabel.getText();
    }

    String subtitleText() {
        return subtitleLabel.getText();
    }

    String originalTextValue() {
        return originalText.getText();
    }

    String translationTextValue() {
        return translatedText.getText();
    }

    String explanationTextValue() {
        return explanationArea.getText();
    }

    String summaryTextValue() {
        return summaryArea.getText();
    }

    String termsTextValue() {
        return termsArea.getText();
    }

    JComboBox<String> fileSelector() {
        return fileSelector;
    }

    void clickNext() {
        nextButton.doClick();
    }

    void clickPrevious() {
        previousButton.doClick();
    }
}
