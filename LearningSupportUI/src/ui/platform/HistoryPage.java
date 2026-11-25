package ui.platform;

import ui.ApiClient.AnalysisResult;
import ui.ApiClient.ResultSummary;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 학습 이력 화면: 서버에 저장된 분석 결과 목록 (최근 것부터).
 * 파일 이름 검색, View(결과 화면에서 보기), Delete(결과·문서 삭제).
 * 화면에 들어올 때마다 {@link #refresh()} 로 서버에서 다시 읽는다.
 */
class HistoryPage extends JPanel {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Navigator navigator;
    private final HistoryApi api;
    private final Consumer<List<AnalysisResult>> resultHandler;
    private final Background background;

    private final JPanel historyPanel = new JPanel();
    private final JTextField searchField = new JTextField(20);
    private final JLabel statusLabel = new JLabel(" ");
    /** 지금 목록에 보이는 이력 (테스트에서 확인용). */
    private final List<ResultSummary> shown = new ArrayList<>();
    /** 삭제 확인. 테스트에서는 바꿔 끼운다. */
    private Predicate<String> confirmDelete = fileName -> JOptionPane.showConfirmDialog(this,
            "Do you want to delete this history record?\n" + fileName
                    + "\n(The analysis result and the uploaded file are deleted.)",
            "Delete History", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;

    /**
     * @param resultHandler View 를 누르면 그 결과 하나를 결과 화면에 넘긴다
     */
    HistoryPage(Navigator navigator, HistoryApi api, Consumer<List<AnalysisResult>> resultHandler,
                Background background) {
        super(new BorderLayout(20, 20));
        this.navigator = navigator;
        this.api = api;
        this.resultHandler = resultHandler;
        this.background = background;
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        add(createHeader(), BorderLayout.NORTH);

        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(Theme.BACKGROUND);
        JScrollPane scrollPane = new JScrollPane(historyPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Theme.BACKGROUND);
        add(scrollPane, BorderLayout.CENTER);

        statusLabel.setFont(Theme.koreanFont(13));
        statusLabel.setForeground(Theme.MUTED);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        searchField.setFont(Theme.koreanFont(14));
        JButton searchButton = new JButton("Search");
        JButton resetButton = new JButton("Reset");
        JButton refreshButton = new JButton("Refresh");

        searchButton.addActionListener(e -> refresh());
        searchField.addActionListener(e -> refresh());
        resetButton.addActionListener(e -> {
            searchField.setText("");
            refresh();
        });
        refreshButton.addActionListener(e -> refresh());

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.setBackground(Theme.BACKGROUND);
        searchPanel.add(new JLabel("Search File:"));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(resetButton);
        searchPanel.add(refreshButton);

        JPanel headerPanel = new JPanel(new BorderLayout(5, 10));
        headerPanel.setBackground(Theme.BACKGROUND);
        headerPanel.add(Theme.header("Analysis History", 26,
                "View your previously analyzed learning materials.", 14), BorderLayout.NORTH);
        headerPanel.add(searchPanel, BorderLayout.SOUTH);
        return headerPanel;
    }

    // ---------------- 서버에서 읽기 ----------------

    /** 지금 검색어로 목록을 서버에서 다시 읽는다. */
    void refresh() {
        String query = searchField.getText().strip();
        showStatus("Loading history...", false);
        background.run(() -> api.list(query), this::showRows, e -> showStatus(e.getMessage(), true));
    }

    private void showRows(List<ResultSummary> rows) {
        shown.clear();
        shown.addAll(rows);
        historyPanel.removeAll();
        for (ResultSummary row : rows) {
            historyPanel.add(createItem(row));
            historyPanel.add(Box.createVerticalStrut(12));
        }
        historyPanel.revalidate();
        historyPanel.repaint();
        if (rows.isEmpty()) {
            showStatus(searchField.getText().isBlank()
                    ? "No analysis history yet. Upload a file on the File Upload page."
                    : "No history matches \"" + searchField.getText().strip() + "\".", false);
        } else {
            showStatus(rows.size() + " record(s)", false);
        }
    }

    /** 이력 한 줄: 파일 이름, 분석 시각·문단 수·번역 여부, View·Delete 버튼. */
    private JPanel createItem(ResultSummary row) {
        JPanel item = new JPanel(new BorderLayout(10, 10));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
        item.setBackground(Theme.CARD);
        item.setBorder(Theme.cardBorder(15, 20));

        JLabel fileName = new JLabel(row.fileName());
        fileName.setFont(Theme.koreanFont(15).deriveFont(Font.BOLD));
        fileName.setForeground(Theme.TITLE);

        JLabel status = new JLabel(describe(row));
        status.setFont(Theme.font(Font.PLAIN, 12));
        status.setForeground(row.translated() ? Theme.SUCCESS : Theme.MUTED);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        infoPanel.setBackground(Theme.CARD);
        infoPanel.add(fileName);
        infoPanel.add(status);

        Dimension size = new Dimension(90, 35);
        JButton viewButton = Theme.button("View", 13, size);
        JButton deleteButton = Theme.coloredButton("Delete", 13, size, Theme.DANGER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setBackground(Theme.CARD);
        actionPanel.add(viewButton);
        actionPanel.add(deleteButton);

        item.add(infoPanel, BorderLayout.CENTER);
        item.add(actionPanel, BorderLayout.EAST);

        viewButton.addActionListener(e -> view(row));
        deleteButton.addActionListener(e -> delete(row));
        return item;
    }

    /** "Analyzed 2026-11-25 11:57 · 803 paragraphs · Translated · 3 terms" */
    static String describe(ResultSummary row) {
        StringBuilder sb = new StringBuilder("Analyzed ").append(localTime(row.analyzedAt()))
                .append(" · ").append(row.itemCount()).append(" paragraphs · ")
                .append(row.translated() ? "Translated" : "Not translated");
        if (row.termCount() > 0) {
            sb.append(" · ").append(row.termCount()).append(row.termCount() == 1 ? " term" : " terms");
        }
        return sb.toString();
    }

    /**
     * DB 시각(Neon 은 UTC 로 저장)을 이 PC 시간대로 바꿔 "yyyy-MM-dd HH:mm" 로 보여 준다.
     * 형식을 모르면 받은 글자 그대로.
     */
    static String localTime(String dbTime) {
        if (dbTime == null || dbTime.isBlank()) {
            return "-";
        }
        try {
            return LocalDateTime.parse(dbTime).atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault()).format(TIME);
        } catch (DateTimeParseException e) {
            return dbTime;
        }
    }

    // ---------------- 버튼 동작 ----------------

    private void view(ResultSummary row) {
        showStatus("Opening " + row.fileName() + "...", false);
        background.run(() -> api.get(row.resultId()), result -> {
            showStatus(" ", false);
            resultHandler.accept(List.of(result));
            navigator.show(Page.RESULT);
        }, e -> showStatus(e.getMessage(), true));
    }

    private void delete(ResultSummary row) {
        if (!confirmDelete.test(row.fileName())) {
            return;
        }
        showStatus("Deleting " + row.fileName() + "...", false);
        background.run(() -> {
            api.delete(row.resultId());
            return null;
        }, ignored -> refresh(), e -> showStatus(e.getMessage(), true));
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text == null || text.isBlank() ? " " : text);
        statusLabel.setForeground(error ? Theme.DANGER : Theme.MUTED);
    }

    // ---------------- 테스트용 ----------------

    List<ResultSummary> shownRows() {
        return List.copyOf(shown);
    }

    String statusText() {
        return statusLabel.getText();
    }

    void setSearchText(String text) {
        searchField.setText(text);
    }

    void setConfirmDelete(Predicate<String> confirm) {
        this.confirmDelete = confirm;
    }

    /** index 번째 줄의 View / Delete 버튼을 누른다. */
    void clickView(int index) {
        view(shown.get(index));
    }

    void clickDelete(int index) {
        delete(shown.get(index));
    }
}
