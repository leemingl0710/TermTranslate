package ui.platform;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 결과 화면의 Quiz 탭: 한 문제씩 풀고, Check 를 눌러야 정답·해설이 보인다.
 * 모든 문제를 확인하면 점수와 Try again 버튼이 나온다. 푼 기록은 저장하지 않는다.
 */
class QuizPanel extends JPanel {

    static final String NO_QUIZ = "No quiz for this material.";
    static final String CORRECT = "✔ Correct!";
    static final String WRONG_PREFIX = "✘ Wrong. Answer: ";
    static final String ANSWER_PREFIX = "Answer: ";

    private static final String QUIZ_CARD = "quiz";
    private static final String MESSAGE_CARD = "message";

    private final CardLayout cards = new CardLayout();
    private final JTextArea messageArea = Theme.readOnlyText(NO_QUIZ);

    private final JLabel counterLabel = new JLabel();
    private final JTextArea questionArea = wrapText(Font.BOLD);
    private final JPanel optionsPanel = new JPanel();
    private final JLabel feedbackLabel = new JLabel(" ");
    private final JTextArea explanationArea = wrapText(Font.PLAIN);
    private final JLabel scoreLabel = new JLabel(" ");
    private final JButton previousButton = new JButton("◀ Prev");
    private final JButton checkButton = new JButton("Check");
    private final JButton nextButton = new JButton("Next ▶");
    private final JButton tryAgainButton = new JButton("Try again");

    private final List<JRadioButton> optionButtons = new ArrayList<>();
    private List<ExamContent.Quiz> quizzes = List.of();
    private int index;
    /** 문제마다 고른 보기 (-1 = 안 고름) */
    private int[] chosen = new int[0];
    /** 문제마다 Check 를 눌렀는지 */
    private boolean[] checked = new boolean[0];

    QuizPanel() {
        setLayout(cards);
        setBackground(Theme.CARD);
        messageArea.setFont(Theme.koreanFont(13));
        add(new JScrollPane(messageArea), MESSAGE_CARD);
        add(createQuizCard(), QUIZ_CARD);

        previousButton.addActionListener(e -> showQuestion(index - 1));
        nextButton.addActionListener(e -> showQuestion(index + 1));
        checkButton.addActionListener(e -> check());
        tryAgainButton.addActionListener(e -> restart());
        show(ExamContent.EMPTY);
    }

    private JPanel createQuizCard() {
        counterLabel.setFont(Theme.font(Font.BOLD, 12));
        counterLabel.setForeground(Theme.MUTED);
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBackground(Theme.CARD);
        feedbackLabel.setFont(Theme.koreanFont(13).deriveFont(Font.BOLD));
        explanationArea.setForeground(Theme.MUTED);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Theme.CARD);
        body.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        for (JComponent c : List.of(counterLabel, questionArea, optionsPanel, explanationArea)) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            body.add(c);
            body.add(Box.createVerticalStrut(4));
        }
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);

        scoreLabel.setFont(Theme.font(Font.BOLD, 13));
        scoreLabel.setForeground(Theme.PRIMARY);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBackground(Theme.CARD);
        buttons.add(scoreLabel);
        buttons.add(tryAgainButton);
        buttons.add(previousButton);
        buttons.add(checkButton);
        buttons.add(nextButton);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
        // 결과(맞음/틀림)는 스크롤과 상관없이 항상 보이게 아래 줄에 둔다
        bottom.add(feedbackLabel, BorderLayout.WEST);
        bottom.add(buttons, BorderLayout.EAST);

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Theme.CARD);
        card.add(scroll, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    /** 새 결과의 퀴즈를 넣고 1번 문제부터 보여 준다. */
    void show(ExamContent exam) {
        ExamContent e = exam == null ? ExamContent.EMPTY : exam;
        quizzes = e.quizzes();
        if (quizzes.isEmpty()) {
            // 나눠 읽지 못했으면 원래 글이라도 보여 준다
            messageArea.setText(e.unparsed() ? e.raw() : NO_QUIZ);
            messageArea.setCaretPosition(0);
            cards.show(this, MESSAGE_CARD);
            return;
        }
        cards.show(this, QUIZ_CARD);
        restart();
    }

    private void restart() {
        chosen = new int[quizzes.size()];
        Arrays.fill(chosen, -1);
        checked = new boolean[quizzes.size()];
        showQuestion(0);
    }

    private void showQuestion(int i) {
        if (i < 0 || i >= quizzes.size()) {
            return;
        }
        index = i;
        ExamContent.Quiz q = quizzes.get(i);
        counterLabel.setText("Question " + (i + 1) + " of " + quizzes.size());
        questionArea.setText(q.question());

        optionsPanel.removeAll();
        optionButtons.clear();
        ButtonGroup group = new ButtonGroup();
        for (int k = 0; k < q.options().size(); k++) {
            JRadioButton b = new JRadioButton((char) ('A' + k) + ". " + q.options().get(k));
            b.setFont(Theme.koreanFont(13));
            b.setBackground(Theme.CARD);
            int option = k;
            b.addActionListener(e -> {
                chosen[index] = option;
                updateButtons();
            });
            b.setSelected(chosen[i] == k);
            group.add(b);
            optionButtons.add(b);
            optionsPanel.add(b);
        }
        optionsPanel.revalidate();
        optionsPanel.repaint();
        updateFeedback();
        updateButtons();
    }

    private void check() {
        ExamContent.Quiz q = quizzes.get(index);
        // 보기가 없거나 정답을 모르는 문제는 고르지 않아도 정답을 보여 준다
        if (chosen[index] < 0 && q.gradable()) {
            return;
        }
        checked[index] = true;
        updateFeedback();
        updateButtons();
    }

    private void updateFeedback() {
        ExamContent.Quiz q = quizzes.get(index);
        if (!checked[index]) {
            feedbackLabel.setText(" ");
            explanationArea.setText("");
            return;
        }
        if (!q.gradable()) {
            feedbackLabel.setForeground(Theme.PRIMARY);
            feedbackLabel.setText(q.answerLabel() == null ? " " : ANSWER_PREFIX + q.answerLabel());
        } else if (chosen[index] == q.answerIndex()) {
            feedbackLabel.setForeground(Theme.SUCCESS);
            feedbackLabel.setText(CORRECT);
        } else {
            feedbackLabel.setForeground(Theme.DANGER);
            feedbackLabel.setText(WRONG_PREFIX + q.answerLabel());
        }
        explanationArea.setText(q.explanation() == null ? "" : q.explanation());
        // 해설이 탭 아래로 밀려 있으면 보이는 곳까지 내린다
        SwingUtilities.invokeLater(() -> explanationArea.scrollRectToVisible(
                new Rectangle(0, 0, 1, explanationArea.getHeight())));
    }

    private void updateButtons() {
        ExamContent.Quiz q = quizzes.get(index);
        boolean done = checked[index];
        for (JRadioButton b : optionButtons) {
            b.setEnabled(!done);       // 확인한 뒤에는 답을 바꾸지 못함
        }
        checkButton.setEnabled(!done && (chosen[index] >= 0 || !q.gradable()));
        previousButton.setEnabled(index > 0);
        nextButton.setEnabled(index < quizzes.size() - 1);

        boolean allChecked = true;
        for (boolean c : checked) {
            allChecked &= c;
        }
        tryAgainButton.setVisible(allChecked);
        scoreLabel.setText(allChecked ? computeScore() : " ");
    }

    /** "Score: 2 / 3" (정답을 모르는 문제는 빼고 센다) */
    private String computeScore() {
        int right = 0;
        int total = 0;
        for (int k = 0; k < quizzes.size(); k++) {
            if (quizzes.get(k).gradable()) {
                total++;
                if (chosen[k] == quizzes.get(k).answerIndex()) {
                    right++;
                }
            }
        }
        return total == 0 ? " " : "Score: " + right + " / " + total;
    }

    private static JTextArea wrapText(int style) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(Theme.koreanFont(13).deriveFont(style));
        area.setBorder(null);
        return area;
    }

    // ---------------- 테스트용 ----------------

    boolean showingMessage() {
        return quizzes.isEmpty();
    }

    String messageText() {
        return messageArea.getText();
    }

    String counterText() {
        return counterLabel.getText();
    }

    String questionText() {
        return questionArea.getText();
    }

    List<String> optionTexts() {
        return optionButtons.stream().map(AbstractButton::getText).toList();
    }

    void choose(int option) {
        optionButtons.get(option).doClick();
    }

    boolean optionsEnabled() {
        return optionButtons.stream().allMatch(Component::isEnabled);
    }

    void clickCheck() {
        checkButton.doClick();
    }

    void clickNext() {
        nextButton.doClick();
    }

    void clickPrevious() {
        previousButton.doClick();
    }

    void clickTryAgain() {
        tryAgainButton.doClick();
    }

    boolean checkEnabled() {
        return checkButton.isEnabled();
    }

    boolean tryAgainVisible() {
        return tryAgainButton.isVisible();
    }

    String feedbackText() {
        return feedbackLabel.getText();
    }

    String explanationText() {
        return explanationArea.getText();
    }

    String scoreText() {
        return scoreLabel.getText();
    }
}
