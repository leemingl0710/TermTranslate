package ui.platform;

import ui.ApiClient.TermEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 용어 사전 화면: 팀 공용 전공용어 사전(DB terms)을 검색·추가·수정·삭제·승인한다.
 * 여기서 넣은 용어는 다음 분석부터 번역기가 사전으로 쓰고, 결과 화면 Key Terms 에 나온다.
 * 분석 중 LLM 이 찾은 용어는 "Needs review" 로 들어오고, Approve 하거나 Edit 로 고치면 "Verified" 가 된다.
 * 화면에 들어올 때마다 {@link #refresh()} 로 서버에서 다시 읽는다.
 */
class TermsPage extends JPanel {

    /** 추가·수정 창에서 받은 값. */
    record TermForm(String korean, String english, String explanation) {
    }

    /** 추가·수정 입력 창. 취소하면 empty. 테스트에서는 바꿔 끼운다. */
    @FunctionalInterface
    interface FormDialog {
        Optional<TermForm> ask(String title, TermEntry initial);
    }

    private static final String[] COLUMNS = {"Korean", "English", "Explanation", "Status"};
    static final String VERIFIED = "Verified";
    static final String NEEDS_REVIEW = "Needs review (auto)";

    private final TermApi api;
    private final Background background;

    private final JTextField searchField = new JTextField(20);
    private final JCheckBox reviewOnly = new JCheckBox("Needs review only");
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;   // 고칠 때는 Edit 버튼으로
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel statusLabel = new JLabel(" ");
    /** 표의 줄 순서와 같은 용어 목록. */
    private final List<TermEntry> rows = new ArrayList<>();

    private FormDialog formDialog = this::showFormDialog;
    private Predicate<String> confirmDelete = korean -> JOptionPane.showConfirmDialog(this,
            "Delete this term from the shared dictionary?\n" + korean,
            "Delete Term", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;

    TermsPage(TermApi api, Background background) {
        super(new BorderLayout(15, 15));
        this.api = api;
        this.background = background;
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        add(createHeader(), BorderLayout.NORTH);
        add(createTable(), BorderLayout.CENTER);
        add(createBottom(), BorderLayout.SOUTH);
    }

    // ---------------- 화면 구성 ----------------

    private JPanel createHeader() {
        searchField.setFont(Theme.koreanFont(14));
        JButton searchButton = new JButton("Search");
        JButton resetButton = new JButton("Reset");
        searchButton.addActionListener(e -> refresh());
        searchField.addActionListener(e -> refresh());
        resetButton.addActionListener(e -> {
            searchField.setText("");
            refresh();
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.setBackground(Theme.BACKGROUND);
        searchPanel.add(new JLabel("Search Term:"));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(resetButton);
        reviewOnly.setBackground(Theme.BACKGROUND);
        reviewOnly.setFont(Theme.font(Font.PLAIN, 13));
        reviewOnly.addActionListener(e -> refresh());
        searchPanel.add(reviewOnly);

        JPanel headerPanel = new JPanel(new BorderLayout(5, 10));
        headerPanel.setBackground(Theme.BACKGROUND);
        headerPanel.add(Theme.header("Term Dictionary", 26,
                "Shared dictionary of major terms. Terms found by the AI are marked \"Needs review\" until approved.",
                14),
                BorderLayout.NORTH);
        headerPanel.add(searchPanel, BorderLayout.SOUTH);
        return headerPanel;
    }

    private JScrollPane createTable() {
        table.setFont(Theme.koreanFont(14));
        table.getTableHeader().setFont(Theme.font(Font.BOLD, 13));
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.TITLE);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(420);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                boolean review = row < rows.size() && rows.get(row).needsReview();
                if (!selected) {
                    c.setForeground(review && column == 3 ? Theme.DANGER : Theme.TITLE);
                }
                return c;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.CARD);
        return scroll;
    }

    private JPanel createBottom() {
        Dimension size = new Dimension(140, 38);
        JButton addButton = Theme.button("Add Term", 13, size);
        JButton editButton = Theme.button("Edit", 13, size);
        JButton approveButton = Theme.button("Approve", 13, size);
        approveButton.addActionListener(e -> approveSelected());
        JButton deleteButton = Theme.coloredButton("Delete", 13, size, Theme.DANGER);
        addButton.addActionListener(e -> addTerm());
        editButton.addActionListener(e -> editSelected());
        deleteButton.addActionListener(e -> deleteSelected());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBackground(Theme.BACKGROUND);
        buttonPanel.add(addButton);
        buttonPanel.add(approveButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);

        statusLabel.setFont(Theme.koreanFont(13));
        statusLabel.setForeground(Theme.MUTED);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setBackground(Theme.BACKGROUND);
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(buttonPanel, BorderLayout.EAST);
        return bottom;
    }

    // ---------------- 서버에서 읽기 ----------------

    /** 지금 검색어로 용어를 서버에서 다시 읽는다. */
    void refresh() {
        String query = searchField.getText().strip();
        boolean onlyReview = reviewOnly.isSelected();
        showStatus("Loading terms...", false);
        background.run(() -> api.search(query, onlyReview), this::showRows, e -> showStatus(e.getMessage(), true));
    }

    private void showRows(List<TermEntry> terms) {
        rows.clear();
        rows.addAll(terms);
        model.setRowCount(0);
        for (TermEntry t : terms) {
            model.addRow(new Object[]{t.korean(), orEmpty(t.english()), orEmpty(t.explanation()),
                    t.needsReview() ? NEEDS_REVIEW : VERIFIED});
        }
        long review = terms.stream().filter(TermEntry::needsReview).count();
        if (terms.isEmpty()) {
            if (!searchField.getText().isBlank()) {
                showStatus("No term matches \"" + searchField.getText().strip() + "\".", false);
            } else if (reviewOnly.isSelected()) {
                showStatus("No term is waiting for review.", false);
            } else {
                showStatus("The dictionary is empty. Click \"Add Term\" to add one.", false);
            }
        } else {
            showStatus(terms.size() + " term(s)" + (review > 0 ? " · " + review + " need review" : ""), false);
        }
    }

    // ---------------- 버튼 동작 ----------------

    private void addTerm() {
        Optional<TermForm> form = formDialog.ask("Add Term", null);
        if (form.isEmpty()) {
            return;
        }
        TermForm f = form.get();
        background.run(() -> api.add(f.korean(), f.english(), f.explanation()),
                saved -> {
                    refresh();
                    showStatus("Added: " + saved.korean(), false);
                },
                e -> showStatus(e.getMessage(), true));
    }

    private void editSelected() {
        Optional<TermEntry> selected = selected();
        if (selected.isEmpty()) {
            showStatus("Select a term to edit.", true);
            return;
        }
        TermEntry current = selected.get();
        Optional<TermForm> form = formDialog.ask("Edit Term", current);
        if (form.isEmpty()) {
            return;
        }
        TermForm f = form.get();
        background.run(() -> api.update(current.termId(), f.korean(), f.english(), f.explanation()),
                saved -> {
                    refresh();
                    showStatus("Saved: " + saved.korean(), false);
                },
                e -> showStatus(e.getMessage(), true));
    }

    private void approveSelected() {
        Optional<TermEntry> selected = selected();
        if (selected.isEmpty()) {
            showStatus("Select a term to approve.", true);
            return;
        }
        TermEntry current = selected.get();
        if (!current.needsReview()) {
            showStatus("Already verified: " + current.korean(), false);
            return;
        }
        background.run(() -> api.approve(current.termId()),
                saved -> {
                    refresh();
                    showStatus("Approved: " + saved.korean(), false);
                },
                e -> showStatus(e.getMessage(), true));
    }

    private void deleteSelected() {
        Optional<TermEntry> selected = selected();
        if (selected.isEmpty()) {
            showStatus("Select a term to delete.", true);
            return;
        }
        TermEntry current = selected.get();
        if (!confirmDelete.test(current.korean())) {
            return;
        }
        background.run(() -> {
            api.delete(current.termId());
            return null;
        }, ignored -> {
            refresh();
            showStatus("Deleted: " + current.korean(), false);
        }, e -> showStatus(e.getMessage(), true));
    }

    private Optional<TermEntry> selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= rows.size() ? Optional.empty() : Optional.of(rows.get(row));
    }

    /** 한국어·영어·설명을 받는 창. 한국어가 비면 다시 묻는다. */
    private Optional<TermForm> showFormDialog(String title, TermEntry initial) {
        JTextField korean = new JTextField(initial == null ? "" : initial.korean(), 25);
        JTextField english = new JTextField(initial == null ? "" : orEmpty(initial.english()), 25);
        JTextArea explanation = new JTextArea(initial == null ? "" : orEmpty(initial.explanation()), 4, 25);
        korean.setFont(Theme.koreanFont(14));
        english.setFont(Theme.koreanFont(14));
        explanation.setFont(Theme.koreanFont(14));
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, c, 0, "Korean *", korean);
        addRow(form, c, 1, "English", english);
        addRow(form, c, 2, "Explanation", new JScrollPane(explanation));

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            if (!korean.getText().isBlank()) {
                return Optional.of(new TermForm(korean.getText(), english.getText(), explanation.getText()));
            }
            JOptionPane.showMessageDialog(this, "Please enter the Korean term.");
        }
    }

    private static void addRow(JPanel form, GridBagConstraints c, int y, String label, JComponent field) {
        c.gridy = y;
        c.gridx = 0;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }

    private void showStatus(String text, boolean error) {
        statusLabel.setText(text == null || text.isBlank() ? " " : text);
        statusLabel.setForeground(error ? Theme.DANGER : Theme.MUTED);
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    // ---------------- 테스트용 ----------------

    List<TermEntry> shownRows() {
        return List.copyOf(rows);
    }

    String statusText() {
        return statusLabel.getText();
    }

    Object cell(int row, int column) {
        return model.getValueAt(row, column);
    }

    void setSearchText(String text) {
        searchField.setText(text);
    }

    void selectRow(int row) {
        table.setRowSelectionInterval(row, row);
    }

    void setFormDialog(FormDialog dialog) {
        this.formDialog = dialog;
    }

    void setConfirmDelete(Predicate<String> confirm) {
        this.confirmDelete = confirm;
    }

    void clickAdd() {
        addTerm();
    }

    void clickEdit() {
        editSelected();
    }

    void clickDelete() {
        deleteSelected();
    }

    void clickApprove() {
        approveSelected();
    }

    void setReviewOnly(boolean only) {
        reviewOnly.setSelected(only);
    }
}
