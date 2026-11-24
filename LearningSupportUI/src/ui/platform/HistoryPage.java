package ui.platform;

import javax.swing.*;
import java.awt.*;

/** 학습 이력 화면: 파일 이름 검색, 결과 보기, 이력 삭제. 지금은 예시 파일 목록을 쓴다. */
class HistoryPage extends JPanel {

    // 예시 데이터 (백엔드 연동 전)
    private static final String[] SAMPLE_FILES = {
            "Introduction to Data Science.pdf",
            "Database Lecture 03.pptx",
            "Computer Advanced Math.pdf"
    };

    private final JPanel historyPanel = new JPanel();
    private final JTextField searchField = new JTextField(20);

    HistoryPage(Navigator navigator) {
        super(new BorderLayout(20, 20));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        add(createHeader(), BorderLayout.NORTH);

        historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
        historyPanel.setBackground(Theme.BACKGROUND);
        for (String file : SAMPLE_FILES) {
            historyPanel.add(createItem(file, navigator));
            historyPanel.add(Box.createVerticalStrut(12));
        }

        JScrollPane scrollPane = new JScrollPane(historyPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Theme.BACKGROUND);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        searchField.setFont(Theme.koreanFont(14));
        JButton searchButton = new JButton("Search");
        JButton resetButton = new JButton("Reset");

        searchButton.addActionListener(e -> filter(searchField.getText()));
        searchField.addActionListener(e -> filter(searchField.getText()));
        resetButton.addActionListener(e -> {
            searchField.setText("");
            filter("");
        });

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.setBackground(Theme.BACKGROUND);
        searchPanel.add(new JLabel("Search File:"));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchPanel.add(resetButton);

        JPanel headerPanel = new JPanel(new BorderLayout(5, 10));
        headerPanel.setBackground(Theme.BACKGROUND);
        headerPanel.add(Theme.header("Analysis History", 26,
                "View your previously analyzed learning materials.", 14), BorderLayout.NORTH);
        headerPanel.add(searchPanel, BorderLayout.SOUTH);
        return headerPanel;
    }

    /** 이력 한 줄: 파일 이름·상태 + View·Delete 버튼. */
    private JPanel createItem(String file, Navigator navigator) {
        JPanel item = new HistoryItem(file);
        item.setLayout(new BorderLayout(10, 10));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
        item.setBackground(Theme.CARD);
        item.setBorder(Theme.cardBorder(15, 20));

        JLabel fileName = new JLabel(file);
        fileName.setFont(Theme.font(Font.BOLD, 15));
        fileName.setForeground(new Color(35, 33, 90));

        JLabel status = new JLabel("Analysis completed");
        status.setFont(Theme.font(Font.PLAIN, 12));
        status.setForeground(Theme.SUCCESS);

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

        viewButton.addActionListener(e -> navigator.show(Page.RESULT));
        deleteButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Do you want to delete this history record?\n" + file,
                    "Delete History", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                historyPanel.remove(item);
                historyPanel.revalidate();
                historyPanel.repaint();
            }
        });
        return item;
    }

    /** 파일 이름에 keyword 가 들어간 이력만 보인다 (대소문자 무시). 빈 문자열이면 전부. */
    private void filter(String keyword) {
        String lower = keyword.trim().toLowerCase();
        for (Component component : historyPanel.getComponents()) {
            if (component instanceof HistoryItem item) {
                item.setVisible(item.fileName.toLowerCase().contains(lower));
            }
        }
        historyPanel.revalidate();
        historyPanel.repaint();
    }

    /** 이력 한 줄. 검색할 때 파일 이름을 바로 꺼내 쓰려고 이름을 같이 들고 있다. */
    private static final class HistoryItem extends JPanel {
        final String fileName;

        HistoryItem(String fileName) {
            this.fileName = fileName;
        }
    }
}
