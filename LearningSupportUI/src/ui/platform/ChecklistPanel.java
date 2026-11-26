package ui.platform;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 결과 화면의 Checklist 탭: LLM 이 자료에 맞게 만든 자기 점검 항목.
 * 체크한 개수를 위에 보여 준다. 체크 상태는 저장하지 않는다 (파일을 바꾸면 처음부터).
 */
class ChecklistPanel extends JPanel {

    static final String NO_CHECKLIST = "No checklist for this material.";

    private final JLabel countLabel = new JLabel();
    private final JPanel itemsPanel = new JPanel();
    private final List<JCheckBox> boxes = new ArrayList<>();

    ChecklistPanel() {
        super(new BorderLayout());
        setBackground(Theme.CARD);
        countLabel.setFont(Theme.font(Font.BOLD, 13));
        countLabel.setForeground(Theme.TITLE);
        countLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 10));
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(Theme.CARD);
        itemsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
        JScrollPane scroll = new JScrollPane(itemsPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        add(countLabel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        show(List.of());
    }

    /** 새 결과의 항목으로 바꾼다 (모두 체크 안 된 상태). */
    void show(List<String> items) {
        itemsPanel.removeAll();
        boxes.clear();
        for (String item : items == null ? List.<String>of() : items) {
            JCheckBox box = new JCheckBox(item);
            box.setFont(Theme.koreanFont(13));
            box.setBackground(Theme.CARD);
            box.addActionListener(e -> updateCount());
            boxes.add(box);
            itemsPanel.add(box);
        }
        updateCount();
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private void updateCount() {
        if (boxes.isEmpty()) {
            countLabel.setText(NO_CHECKLIST);
            return;
        }
        long done = boxes.stream().filter(AbstractButton::isSelected).count();
        countLabel.setText("Self-check (" + done + " / " + boxes.size() + " done)");
    }

    // ---------------- 테스트용 ----------------

    String countText() {
        return countLabel.getText();
    }

    List<String> itemTexts() {
        return boxes.stream().map(AbstractButton::getText).toList();
    }

    void toggle(int i) {
        boxes.get(i).doClick();
    }
}
