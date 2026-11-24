package ui.platform;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/** 모든 화면이 같이 쓰는 색·글꼴·자주 쓰는 부품. */
final class Theme {

    static final Color BACKGROUND = new Color(245, 248, 253);
    static final Color CARD = Color.WHITE;
    static final Color BORDER = new Color(225, 232, 242);
    static final Color TITLE = new Color(35, 55, 90);
    static final Color MUTED = new Color(100, 110, 125);
    static final Color PRIMARY = new Color(45, 90, 150);
    static final Color DANGER = new Color(180, 65, 65);
    static final Color SUCCESS = new Color(70, 145, 110);
    static final Color WARNING = new Color(170, 100, 40);
    static final Color SELECTION = new Color(220, 233, 248);

    private Theme() {
    }

    /** 영문 글꼴 (Segoe UI). */
    static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    /** 한글이 들어가는 칸에 쓰는 글꼴 (맑은 고딕). */
    static Font koreanFont(int size) {
        return new Font("Malgun Gothic", Font.PLAIN, size);
    }

    /** 화면 바깥 틀: 연한 배경 + 안쪽 여백. */
    static JPanel page(int gap, int top, int side) {
        JPanel panel = new JPanel(new BorderLayout(gap, gap));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(top, side, top, side));
        return panel;
    }

    /** 화면 위쪽 제목 + 설명 두 줄. */
    static JPanel header(String title, int titleSize, String subtitle, int subtitleSize) {
        JLabel heading = new JLabel(title);
        heading.setFont(font(Font.BOLD, titleSize));
        heading.setForeground(TITLE);

        JLabel sub = new JLabel(subtitle);
        sub.setFont(font(Font.PLAIN, subtitleSize));
        sub.setForeground(MUTED);

        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));
        panel.setBackground(BACKGROUND);
        panel.add(heading);
        panel.add(sub);
        return panel;
    }

    /** 흰 카드 테두리: 연한 선 + 안쪽 여백. */
    static Border cardBorder(int vertical, int horizontal) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
    }

    /** 파란 바탕 흰 글씨 버튼. size 가 null 이면 크기는 정하지 않는다. */
    static JButton button(String text, int fontSize, Dimension size) {
        return coloredButton(text, fontSize, size, PRIMARY);
    }

    static JButton coloredButton(String text, int fontSize, Dimension size, Color background) {
        JButton button = new JButton(text);
        button.setFont(font(Font.BOLD, fontSize));
        button.setBackground(background);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorderPainted(false);
        if (size != null) {
            button.setPreferredSize(size);
        }
        return button;
    }

    /** 읽기 전용, 줄 바꿈 되는 글 상자. */
    static JTextArea readOnlyText(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }
}
