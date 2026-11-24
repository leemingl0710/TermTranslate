package ui.platform;

import javax.swing.*;
import java.awt.*;

/** 로그인 화면. 아직 인증은 연결하지 않았다 (데모). */
class LoginPage extends JPanel {

    /** onLogin: 로그인 버튼을 눌렀고 칸이 다 채워졌을 때 할 일 (메뉴 보이기, 홈으로 이동). */
    LoginPage(Runnable onLogin) {
        super(new GridBagLayout());
        setBackground(Theme.BACKGROUND);

        JPanel loginPanel = new JPanel();
        loginPanel.setLayout(new BoxLayout(loginPanel, BoxLayout.Y_AXIS));
        loginPanel.setBackground(Theme.CARD);
        loginPanel.setPreferredSize(new Dimension(380, 350));
        loginPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(30, 30, 30, 30)));

        JLabel heading = new JLabel("Welcome Back");
        heading.setFont(Theme.font(Font.BOLD, 26));
        heading.setForeground(Theme.TITLE);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Sign in to your learning platform.");
        subtitle.setFont(Theme.font(Font.PLAIN, 14));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel idLabel = new JLabel("User ID");
        idLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField idField = new JTextField();
        idField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        idField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPasswordField passwordField = new JPasswordField();
        passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton loginButton = Theme.button("Login", 14, null);
        loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        loginPanel.add(heading);
        loginPanel.add(Box.createVerticalStrut(8));
        loginPanel.add(subtitle);
        loginPanel.add(Box.createVerticalStrut(25));
        loginPanel.add(idLabel);
        loginPanel.add(Box.createVerticalStrut(5));
        loginPanel.add(idField);
        loginPanel.add(Box.createVerticalStrut(15));
        loginPanel.add(passwordLabel);
        loginPanel.add(Box.createVerticalStrut(5));
        loginPanel.add(passwordField);
        loginPanel.add(Box.createVerticalStrut(25));
        loginPanel.add(loginButton);

        loginButton.addActionListener(e -> {
            if (idField.getText().trim().isEmpty() || passwordField.getPassword().length == 0) {
                JOptionPane.showMessageDialog(this,
                        "Please enter your User ID and Password.",
                        "Login Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JOptionPane.showMessageDialog(this, "Demo login only. Authentication is not connected.");
            passwordField.setText("");
            onLogin.run();
        });

        add(loginPanel);
    }
}
