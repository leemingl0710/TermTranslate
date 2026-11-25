package ui.platform;

import ui.ApiClient;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * 학습지원 플랫폼 새 화면의 창. 왼쪽 메뉴 + 가운데 화면(CardLayout).
 *
 * <p>화면마다 클래스가 따로 있다:
 * LoginPage, HomePage, UploadPage, OcrReviewPage, ResultPage, HistoryPage, TermsPage, EvaluationPage.
 * 색·글꼴·버튼 모양은 Theme 에 모았다.
 *
 * <p>아래 public 메서드들은 백엔드(서버)와 연결할 때 쓰는 자리다.
 */
public class MainFrame extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final JPanel menuPanel = new JPanel(new GridLayout(6, 1, 5, 10));

    private final UploadPage uploadPage;
    private final OcrReviewPage ocrPage;
    private final ResultPage resultPage;
    private final HistoryPage historyPage;
    private final TermsPage termsPage;

    public MainFrame() {
        setTitle("Learning Support Platform");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Learning Support Platform");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        add(title, BorderLayout.NORTH);

        menuPanel.setPreferredSize(new Dimension(190, 0));
        addMenuButton("Home", Page.HOME);
        addMenuButton("File Upload", Page.UPLOAD);
        addMenuButton("Analysis Results", Page.RESULT);
        addMenuButton("History", Page.HISTORY);
        addMenuButton("Term Dictionary", Page.TERMS);
        addMenuButton("Evaluation", Page.EVALUATION);
        add(menuPanel, BorderLayout.WEST);

        Navigator navigator = this::showPage;
        ApiClient api = ApiClient.fromSystem();
        resultPage = new ResultPage();
        // 업로드 화면은 목록 순서대로 서버에 올리고, 성공한 결과를 같은 순서로 결과 화면에 넘긴다
        uploadPage = new UploadPage(navigator, Uploader.of(api), resultPage::showResults);
        // 이력: 서버에 저장된 결과 목록. View 를 누르면 그 결과를 결과 화면에서 본다
        historyPage = new HistoryPage(navigator, HistoryApi.of(api), resultPage::showResults, Background.SWING);
        termsPage = new TermsPage(TermApi.of(api), Background.SWING);
        ocrPage = new OcrReviewPage(navigator);

        addPage(Page.LOGIN, new LoginPage(() -> {
            menuPanel.setVisible(true);
            showPage(Page.HOME);
        }));
        addPage(Page.HOME, new HomePage());
        addPage(Page.UPLOAD, uploadPage);
        addPage(Page.OCR, ocrPage);
        addPage(Page.RESULT, resultPage);
        addPage(Page.HISTORY, historyPage);
        addPage(Page.TERMS, termsPage);
        addPage(Page.EVALUATION, new EvaluationPage());
        add(contentPanel, BorderLayout.CENTER);

        // 로그인 전에는 메뉴를 숨긴다
        menuPanel.setVisible(false);
        showPage(Page.LOGIN);
    }

    private void addMenuButton(String text, Page page) {
        JButton button = new JButton(text);
        button.addActionListener(e -> showPage(page));
        menuPanel.add(button);
    }

    private void addPage(Page page, JPanel panel) {
        contentPanel.add(panel, page.name());
    }

    void showPage(Page page) {
        cardLayout.show(contentPanel, page.name());
        // 이력·용어 사전은 들어올 때마다 서버에서 새로 읽는다 (다른 화면에서 바뀌었을 수 있음)
        if (page == Page.HISTORY) {
            historyPage.refresh();
        } else if (page == Page.TERMS) {
            termsPage.refresh();
        }
    }

    // ---------------- 백엔드 연동용 ----------------

    public List<File> getSelectedFiles() {
        return uploadPage.getSelectedFiles();
    }

    /** 고른 단원 이름. 고르지 않았으면 null. */
    public String getSelectedChapter() {
        return uploadPage.getSelectedChapter();
    }

    /** 추출된 글을 OCR 확인 화면에 넣고 그 화면으로 이동한다. 어느 스레드에서 불러도 된다. */
    public void showOCRResult(String extractedText) {
        SwingUtilities.invokeLater(() -> {
            ocrPage.setText(extractedText);
            showPage(Page.OCR);
        });
    }

    /** OCR 확인 화면에서 사용자가 고친 글. */
    public String getCorrectedOCRText() {
        return ocrPage.getText();
    }

    /** 업로드 화면 진행률 표시 (0~100). 어느 스레드에서 불러도 된다. */
    public void updateAnalysisProgress(int progress) {
        SwingUtilities.invokeLater(() -> uploadPage.updateAnalysisProgress(progress));
    }

    /** 분석 실패 알림. Retry 를 누르면 retryAction 을 다시 실행한다. */
    public void showAnalysisError(String errorMessage, Runnable retryAction) {
        Object[] options = {"Retry", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                errorMessage + "\nWould you like to try again?",
                "Analysis Failed", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE,
                null, options, options[0]);
        if (choice == 0 && retryAction != null) {
            retryAction.run();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
