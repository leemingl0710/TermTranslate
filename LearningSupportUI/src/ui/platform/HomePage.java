package ui.platform;

import javax.swing.*;
import java.awt.*;

/** 홈 화면: 환영 문구 + 기능 소개 카드 4개. */
class HomePage extends JPanel {

    HomePage() {
        super(new BorderLayout(20, 20));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(30, 35, 30, 35));

        JLabel heading = new JLabel("Welcome to Learning Support Platform");
        heading.setFont(Theme.font(Font.BOLD, 26));
        heading.setForeground(Theme.TITLE);

        JLabel description = new JLabel("Understand your lectures and prepare for exams more easily.");
        description.setFont(Theme.font(Font.BOLD, 15));
        description.setForeground(Theme.MUTED);

        JPanel welcomePanel = new JPanel(new GridLayout(2, 1));
        welcomePanel.setBackground(Theme.BACKGROUND);
        welcomePanel.add(heading);
        welcomePanel.add(description);
        add(welcomePanel, BorderLayout.NORTH);

        JPanel featurePanel = new JPanel(new GridLayout(2, 2, 15, 15));
        featurePanel.setBackground(Theme.BACKGROUND);
        featurePanel.add(featureCard("Lecture Summary", "Understand long lecture materials easily."));
        featurePanel.add(featureCard("Translation", "Understand difficult Korean academic terms."));
        featurePanel.add(featureCard("Key Concepts", "Find important topics from your lectures."));
        featurePanel.add(featureCard("Exam Preparation", "Review important points before exams."));
        add(featurePanel, BorderLayout.CENTER);
    }

    private static JPanel featureCard(String title, String description) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.cardBorder(25, 20));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(Font.BOLD, 18));
        titleLabel.setForeground(Theme.TITLE);

        JLabel descriptionLabel = new JLabel("<html>" + description + "</html>");
        descriptionLabel.setFont(Theme.font(Font.PLAIN, 14));
        descriptionLabel.setForeground(Theme.MUTED);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(12));
        card.add(descriptionLabel);
        return card;
    }
}
