package ui.platform;

import javax.swing.*;
import java.awt.*;

/**
 * 분석 결과 화면: 페이지별 원문 ↔ 번역 비교, 아래 탭(설명·요약·용어·체크리스트).
 * 지금은 예시 데이터로 보여 준다. 서버 결과를 받으면 아래 예시 배열 자리를 바꾸면 된다.
 */
class ResultPage extends JPanel {

    // ---------------- 예시 데이터 (백엔드 연동 전) ----------------
    private static final String[] ORIGINAL_PAGES = {
            "Data science combines statistics, programming, "
                    + "and domain knowledge to extract useful insights from data.",
            "Data analysis is the process of inspecting, cleaning, "
                    + "and interpreting data to find useful information."
    };
    private static final String[] TRANSLATED_PAGES = {
            "데이터 과학은 통계학, 프로그래밍 및 분야별 지식을 "
                    + "결합하여 데이터에서 유용한 정보를 추출하는 학문입니다.",
            "데이터 분석은 유용한 정보를 찾기 위해 데이터를 "
                    + "검토하고 정리하며 해석하는 과정입니다."
    };
    private static final String[] EXPLANATION_PAGES = {
            "Page Explanation\n\n"
                    + "Data science uses statistics and programming to discover useful patterns in data.",
            "Page Explanation\n\n"
                    + "Data analysis involves inspecting, cleaning, and interpreting data to obtain useful information."
    };

    private final JLabel pageLabel = new JLabel();
    private final JTextArea originalText = Theme.readOnlyText(
            "Data science combines statistics, programming, and domain knowledge to extract useful insights from data.");
    private final JTextArea translatedText = Theme.readOnlyText(
            "데이터 과학은 통계학, 프로그래밍 및 분야별 지식을 결합하여 데이터에서 유용한 정보를 추출하는 학문입니다.");
    private final JTextArea explanationArea = Theme.readOnlyText(
            "Page Explanation\n\nData science uses different methods to understand data and discover useful patterns.");
    private int currentPage = 0;

    ResultPage() {
        super(new BorderLayout(15, 15));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        add(Theme.header("Analysis Results", 25,
                "Review your lecture materials and prepare for exams.", 13), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(Theme.BACKGROUND);
        centerPanel.add(createPager(), BorderLayout.NORTH);
        centerPanel.add(createComparison(), BorderLayout.CENTER);
        centerPanel.add(createLearningTabs(), BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        JLabel warningLabel = new JLabel(
                "Notice: AI explanations may contain errors. Please check them against the original material.");
        warningLabel.setFont(Theme.font(Font.PLAIN, 12));
        warningLabel.setForeground(Theme.WARNING);
        add(warningLabel, BorderLayout.SOUTH);

        updatePageLabel();
    }

    // ---------------- 화면 구성 ----------------

    /** 이전 / Page n / 전체 / 다음. */
    private JPanel createPager() {
        JButton previousButton = new JButton("Previous");
        JButton nextButton = new JButton("Next");
        pageLabel.setFont(Theme.font(Font.BOLD, 14));

        previousButton.addActionListener(e -> showPage(currentPage - 1));
        nextButton.addActionListener(e -> showPage(currentPage + 1));

        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pagePanel.setBackground(Theme.BACKGROUND);
        pagePanel.add(previousButton);
        pagePanel.add(pageLabel);
        pagePanel.add(nextButton);
        return pagePanel;
    }

    /** 왼쪽 원문, 오른쪽 번역. */
    private JSplitPane createComparison() {
        originalText.setFont(Theme.font(Font.PLAIN, 14));
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
        JTextArea summaryArea = Theme.readOnlyText("Chapter Summary\n\n"
                + "Data science combines statistics, programming and "
                + "subject knowledge to analyze data and support decisions.");

        JTextArea termsArea = new JTextArea("Key Terms\n\n"
                + "1. Statistics - 통계학\n"
                + "2. Programming - 프로그래밍\n"
                + "3. Data - 데이터\n"
                + "4. Analysis - 분석");
        termsArea.setEditable(false);

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

    // ---------------- 페이지 이동 ----------------

    private void showPage(int page) {
        if (page < 0 || page >= ORIGINAL_PAGES.length) {
            return;
        }
        currentPage = page;
        originalText.setText(ORIGINAL_PAGES[page]);
        translatedText.setText(TRANSLATED_PAGES[page]);
        explanationArea.setText(EXPLANATION_PAGES[page]);
        updatePageLabel();
    }

    private void updatePageLabel() {
        pageLabel.setText("Page " + (currentPage + 1) + " / " + ORIGINAL_PAGES.length);
    }

    void showResults(java.util.List<ui.ApiClient.AnalysisResult> results) { /* 다른 작업에서 구현 */ }
}
