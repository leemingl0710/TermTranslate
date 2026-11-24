package ui.platform;

import javax.swing.*;
import java.awt.*;

/** OCR 결과 확인·수정 화면: 추출된 글을 고친 뒤 확정한다. */
class OcrReviewPage extends JPanel {

    private final JTextArea ocrTextArea = new JTextArea();

    OcrReviewPage(Navigator navigator) {
        super(new BorderLayout(15, 15));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        add(Theme.header("OCR Text Review", 26,
                "Review and correct the extracted text before analysis.", 14), BorderLayout.NORTH);

        ocrTextArea.setFont(Theme.koreanFont(15));
        ocrTextArea.setLineWrap(true);
        ocrTextArea.setWrapStyleWord(true);
        ocrTextArea.setEditable(true);
        ocrTextArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        add(new JScrollPane(ocrTextArea), BorderLayout.CENTER);

        Dimension size = new Dimension(180, 42);
        JButton backButton = Theme.button("Back to Upload", 14, size);
        JButton confirmButton = Theme.button("Confirm Text", 14, size);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(Theme.BACKGROUND);
        buttonPanel.add(backButton);
        buttonPanel.add(confirmButton);
        add(buttonPanel, BorderLayout.SOUTH);

        backButton.addActionListener(e -> navigator.show(Page.UPLOAD));
        confirmButton.addActionListener(e -> {
            if (ocrTextArea.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "There is no text to confirm.");
                return;
            }
            JOptionPane.showMessageDialog(this, "Text confirmed. Ready for analysis.");
        });
    }

    void setText(String text) {
        ocrTextArea.setText(text);
    }

    String getText() {
        return ocrTextArea.getText();
    }
}
