package ui.platform;

import javax.swing.*;
import java.awt.*;

/** 사용자 평가 화면: 전체 별점, 항목별 점수, 의견. 아직 DB 저장은 연결하지 않았다. */
class EvaluationPage extends JPanel {

    private static final String[] SCORES = {"1", "2", "3", "4", "5"};

    private final ButtonGroup ratingGroup = new ButtonGroup();
    private final JComboBox<String> translationRating = new JComboBox<>(SCORES);
    private final JComboBox<String> explanationRating = new JComboBox<>(SCORES);
    private final JComboBox<String> summaryRating = new JComboBox<>(SCORES);
    private final JTextArea commentArea = new JTextArea(6, 30);

    EvaluationPage() {
        super(new BorderLayout(20, 20));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        add(Theme.header("User Evaluation", 26,
                "Share your experience and help us improve the platform", 14), BorderLayout.NORTH);
        add(createForm(), BorderLayout.CENTER);
    }

    private JPanel createForm() {
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(Theme.CARD);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(25, 25, 25, 25)));

        formPanel.add(sectionLabel("How would you rate our platform?"));
        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(createStarRating());
        formPanel.add(Box.createVerticalStrut(25));
        formPanel.add(createQualityScores());
        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(sectionLabel("Your comments"));
        formPanel.add(Box.createVerticalStrut(10));

        commentArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        commentArea.setBackground(Theme.CARD);
        commentArea.setFont(Theme.font(Font.PLAIN, 14));
        commentArea.setLineWrap(true);
        commentArea.setWrapStyleWord(true);
        JScrollPane commentScroll = new JScrollPane(commentArea);
        commentScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        formPanel.add(commentScroll);
        formPanel.add(Box.createVerticalStrut(25));

        JButton submitButton = Theme.button("Submit Evaluation", 14, new Dimension(180, 42));
        submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        submitButton.addActionListener(e -> submit());
        formPanel.add(submitButton);
        return formPanel;
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, 17));
        label.setForeground(Theme.TITLE);
        return label;
    }

    /** 1~5 Star 중 하나 고르기. */
    private JPanel createStarRating() {
        JPanel ratingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ratingPanel.setBackground(Theme.CARD);
        for (int i = 1; i <= 5; i++) {
            JRadioButton rating = new JRadioButton(i + " Star");
            rating.setActionCommand(String.valueOf(i));
            rating.setBackground(Theme.CARD);
            rating.setFont(Theme.font(Font.PLAIN, 14));
            rating.setFocusPainted(false);
            ratingGroup.add(rating);
            ratingPanel.add(rating);
        }
        return ratingPanel;
    }

    /** 번역·설명·요약 품질 점수 (1~5). */
    private JPanel createQualityScores() {
        JPanel qualityPanel = new JPanel(new GridLayout(3, 2, 10, 8));
        qualityPanel.setBackground(Theme.CARD);
        qualityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        qualityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        qualityPanel.add(new JLabel("Translation Quality"));
        qualityPanel.add(translationRating);
        qualityPanel.add(new JLabel("Explanation Quality"));
        qualityPanel.add(explanationRating);
        qualityPanel.add(new JLabel("Summary Quality"));
        qualityPanel.add(summaryRating);
        return qualityPanel;
    }

    private void submit() {
        if (ratingGroup.getSelection() == null) {
            JOptionPane.showMessageDialog(this, "Please select an overall rating.",
                    "Evaluation Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this,
                "Evaluation Preview\n\n"
                        + "Overall Rating: " + ratingGroup.getSelection().getActionCommand() + "/5\n"
                        + "Translation Quality: " + translationRating.getSelectedItem() + "/5\n"
                        + "Explanation Quality: " + explanationRating.getSelectedItem() + "/5\n"
                        + "Summary Quality: " + summaryRating.getSelectedItem() + "/5\n"
                        + "Comments: " + commentArea.getText() + "\n\n"
                        + "Database integration is pending.",
                "User Evaluation", JOptionPane.INFORMATION_MESSAGE);
    }
}
