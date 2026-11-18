package ui;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.*;
import java.awt.*;

public class Main extends JFrame {

    
    private CardLayout cardLayout;
    private JPanel contentPanel;

    // 서버 통신
    private final ApiClient api = ApiClient.fromSystem();
    // 업로드 목록에 보이는 이름 → 실제 파일
    private final Map<String, File> selectedFiles = new LinkedHashMap<>();
    // 결과 화면에서 내용을 바꿔 넣을 부분
    private JLabel resultFileLabel;
    private JTextArea extractedTextArea;
    private final Map<String, JLabel> resultCardLabels = new HashMap<>();

    public Main() {


        setTitle("Learning Support Platform");
        setSize(1000, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        
        setLayout(new BorderLayout());

        
        JLabel title = new JLabel("Learning Support Platform");
        title.setFont(new Font("Arial", Font.BOLD, 24));
        title.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        add(title, BorderLayout.NORTH);

        
        JPanel menuPanel = new JPanel();
        menuPanel.setLayout(new GridLayout(5, 1, 5, 10));
        menuPanel.setPreferredSize(new Dimension(190, 0));

        JButton homeButton = new JButton("Home");
        JButton uploadButton = new JButton("File Upload");
        JButton resultButton = new JButton("Analysis Results");
        JButton historyButton = new JButton("History");
        JButton evaluationButton = new JButton("Evaluation");

        menuPanel.add(homeButton);
        menuPanel.add(uploadButton);
        menuPanel.add(resultButton);
        menuPanel.add(historyButton);
        menuPanel.add(evaluationButton);

        add(menuPanel, BorderLayout.WEST);

        
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        contentPanel.add(createHomePage(), "HOME");
        contentPanel.add(createUploadPage(), "UPLOAD");
        contentPanel.add(createResultPage(), "RESULT");
        contentPanel.add(createHistoryPage(), "HISTORY");
        contentPanel.add(createEvaluationPage(), "EVALUATION");

        add(contentPanel, BorderLayout.CENTER);

     
        homeButton.addActionListener(e -> showPage("HOME"));
        uploadButton.addActionListener(e -> showPage("UPLOAD"));
        resultButton.addActionListener(e -> showPage("RESULT"));
        historyButton.addActionListener(e -> showPage("HISTORY"));
        evaluationButton.addActionListener(e -> showPage("EVALUATION"));

        
        showPage("HOME");

        setVisible(true);
    }

    
    private JPanel createPage(String message) {
    	
        JPanel panel = new JPanel(new GridBagLayout());

        JLabel label = new JLabel(message);
        label.setFont(new Font("Arial", Font.PLAIN, 22));

        panel.add(label);

        return panel;
    }
    
    private JPanel createHomePage() {
    	JPanel panel = new JPanel(new BorderLayout(20,20));
    	panel.setBackground(new Color(245, 248, 253));
    	panel.setBorder(BorderFactory.createEmptyBorder(30,35,30,35));
    	JPanel welcomePanel = new JPanel(new GridLayout(2,1));
    	welcomePanel.setBackground(new Color(245, 248, 253));
    	JLabel heading = new JLabel("Welcome to Learning Support Platform");
    	heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
    	heading.setForeground(new Color(35, 55, 90));
    	
    	JLabel description = new JLabel("Understand your lectures and prepare for exams more easily.");
    	description.setFont(new Font("Segoe UI", Font.BOLD, 15 ));
    	description.setForeground(new Color(100,110,125));
    	
    	welcomePanel.add(heading);
    	welcomePanel.add(description);
    	
    	panel.add(welcomePanel, BorderLayout.NORTH);
    	

       JPanel featurePanel = new JPanel(new GridLayout(2, 2, 15, 15));
       featurePanel.setBackground(new Color(245, 248, 253));

      featurePanel.add(createFeatureCard(
             "Lecture Summary",
             "Understand long lecture materials easily."
         ));

     featurePanel.add(createFeatureCard(
    		 "Translation",
             "Understand difficult Korean academic terms."
        ));

     featurePanel.add(createFeatureCard(
           "Key Concepts",
           "Find important topics from your lectures."
       ));

      featurePanel.add(createFeatureCard(
           "Exam Preparation",
           "Review important points before exams."
       ));

     panel.add(featurePanel, BorderLayout.CENTER);

    	
    	return panel;
    	
    }
    

private JPanel createFeatureCard(String title, String description) {
    return createFeatureCard(title, description, null);
}

// register 가 있으면 설명 글자를 나중에 바꿀 수 있게 제목으로 저장해 둔다 (결과 화면용)
private JPanel createFeatureCard(String title, String description, Map<String, JLabel> register) {

    JPanel card = new JPanel();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

    card.setBackground(Color.WHITE);

    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(
            new Color(225, 232, 242), 1
        ),
        BorderFactory.createEmptyBorder(25, 20, 25, 20)
    ));

    JLabel titleLabel = new JLabel(title);
    titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
    titleLabel.setForeground(new Color(35, 55, 90));

    JLabel descriptionLabel = new JLabel(
        "<html>" + description + "</html>"
    );
    descriptionLabel.setFont(
        new Font("Segoe UI", Font.PLAIN, 14)
    );
    descriptionLabel.setForeground(new Color(100, 110, 125));
    if (register != null) {
        register.put(title, descriptionLabel);
    }

    card.add(titleLabel);
    card.add(Box.createVerticalStrut(12));
    card.add(descriptionLabel);

    return card;
}



private JPanel createResultPage() {

    JPanel panel = new JPanel(new BorderLayout(20, 20));
    panel.setBackground(new Color(245, 248, 253));
    panel.setBorder(
        BorderFactory.createEmptyBorder(25, 30, 25, 30)
    );

    
    JLabel heading = new JLabel("Analysis Results");
    heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
    heading.setForeground(new Color(35, 55, 90));

    JLabel subtitle = new JLabel(
        "No analysis yet. Upload a PDF on the File Upload page."
    );
    subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    subtitle.setForeground(new Color(100, 110, 125));
    resultFileLabel = subtitle;

    JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
    topPanel.setBackground(new Color(245, 248, 253));
    topPanel.add(heading);
    topPanel.add(subtitle);

    panel.add(topPanel, BorderLayout.NORTH);

    JPanel resultPanel = new JPanel(new GridLayout(2, 2, 15, 15));
    resultPanel.setBackground(new Color(245, 248, 253));

    resultPanel.add(createFeatureCard(
        "Lecture Summary",
        "A short summary of the uploaded lecture material.",
        resultCardLabels
    ));

    resultPanel.add(createFeatureCard(
        "Translation",
        "Important Korean terms with English meanings.",
        resultCardLabels
    ));

    resultPanel.add(createFeatureCard(
        "Key Concepts",
        "Important topics and concepts to remember.",
        resultCardLabels
    ));

    resultPanel.add(createFeatureCard(
        "Exam Preparation",
        "Practice questions for reviewing your knowledge.",
        resultCardLabels
    ));

    // 추출된 원문 (서버가 PDF 에서 뽑은 문단)
    extractedTextArea = new JTextArea();
    extractedTextArea.setEditable(false);
    extractedTextArea.setLineWrap(true);
    extractedTextArea.setWrapStyleWord(true);
    extractedTextArea.setFont(new Font("Dialog", Font.PLAIN, 14));
    extractedTextArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    JScrollPane extractedScroll = new JScrollPane(extractedTextArea);
    extractedScroll.setBorder(BorderFactory.createTitledBorder(
        BorderFactory.createLineBorder(new Color(225, 232, 242)), "Extracted Text"));
    extractedScroll.setPreferredSize(new Dimension(0, 200));

    JPanel centerPanel = new JPanel(new BorderLayout(15, 15));
    centerPanel.setBackground(new Color(245, 248, 253));
    centerPanel.add(resultPanel, BorderLayout.CENTER);
    centerPanel.add(extractedScroll, BorderLayout.SOUTH);

    panel.add(centerPanel, BorderLayout.CENTER);

    return panel;
}

private JPanel createHistoryPage() {
	JPanel panel = new JPanel(new BorderLayout(20,20));
	panel.setBackground(new Color(245,248,253));
	panel.setBorder(BorderFactory.createEmptyBorder(25,30,25,30));
	
	JLabel heading = new JLabel("Analysis History");
	heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
	heading.setForeground(new Color(35,55,90));
	
	
	JLabel subtitle = new JLabel("View your previously analyzed learning materials.");
	subtitle.setFont(new Font("Segoe UI",Font.PLAIN,14));
	subtitle.setForeground(new Color(100,110,125));
	
	JPanel topPanel = new JPanel(new GridLayout(2,1,5,5));
	topPanel.setBackground(new Color(245,248,253));
	topPanel.add(heading);
	topPanel.add(subtitle);
	
	panel.add(topPanel, BorderLayout.NORTH);
	
	JPanel historyPanel = new JPanel();
	historyPanel.setLayout(new BoxLayout(historyPanel, BoxLayout.Y_AXIS));
	historyPanel.setBackground(new Color(245,248,253));
	
	String[] files = {
			"Introduction to Data Science.pdf",
			"Database Lecture 03.pptx",
			"Computer Advanced Math.pdf"
	};
	
	for(String file:files) {
		JPanel item = new JPanel(new BorderLayout(10,10));
		item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
		item.setBackground(Color.WHITE);
		item.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(225,232,242),1
						),
				BorderFactory.createEmptyBorder(15,20,15,20)
				));
		
		JLabel fileName = new JLabel(file);
		fileName.setFont(new Font("Segoe UI",Font.BOLD,15));
		fileName.setForeground(new Color(35,33,90));
		
		JLabel status = new JLabel("Analysis completed");
		status.setFont(new Font("Segoe UI",Font.PLAIN,12));
		status.setForeground(new Color(70,145,110));
		
		JButton viewButton = new JButton("View");
		viewButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
		viewButton.setBackground(new Color(45, 90, 150));
		viewButton.setForeground(Color.WHITE);
		viewButton.setFocusPainted(false);
		viewButton.setOpaque(true);
		viewButton.setBorderPainted(false);
		viewButton.setPreferredSize(new Dimension(90, 35));
		
		JPanel infoPanel = new JPanel(new GridLayout(2,1,5,5));
		infoPanel.setBackground(Color.WHITE);
		infoPanel.add(fileName);
		infoPanel.add(status);
		
		item.add(infoPanel, BorderLayout.CENTER);
		item.add(viewButton,BorderLayout.EAST);
		
		viewButton.addActionListener(e -> JOptionPane.showMessageDialog(
				this,
				"Selected file: "+ file
				)
				);
		
		historyPanel.add(item);
		historyPanel.add(Box.createVerticalStrut(12));
		
		}
	
	JScrollPane scrollPane = new JScrollPane(historyPanel);
	scrollPane.setBorder(null);
	scrollPane.getViewport().setBackground(new Color(245,248,253));
	panel.add(scrollPane,BorderLayout.CENTER);
	
	return panel;
	
}

private JPanel createEvaluationPage() {
	JPanel panel = new JPanel(new BorderLayout(20,20));
	panel.setBackground(new Color(245,248,253));
	panel.setBorder(BorderFactory.createEmptyBorder(25,30,25,30));
	
	JLabel heading = new JLabel("User Evaluation");
	heading.setFont(new Font("Segoe UI",Font.BOLD,26));
	heading.setForeground(new Color(35,55,90));
	
	JLabel subtitle = new JLabel("Share your experience and help us improve the platform");
	subtitle.setFont(new Font("Segoe UI",Font.PLAIN,14));
	subtitle.setForeground(new Color(100,110,125));
	
	JPanel topPanel = new JPanel(new GridLayout(2,1,5,5));
	topPanel.setBackground(new Color(245,248,253));
	topPanel.add(heading);
	topPanel.add(subtitle);
	
	panel.add(topPanel,BorderLayout.NORTH);
	
	JPanel formPanel = new JPanel();
	formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
	formPanel.setBackground(Color.WHITE);
	
	formPanel.setBorder(BorderFactory.createCompoundBorder(
			 BorderFactory.createLineBorder(new Color(225, 232, 242)),
		        BorderFactory.createEmptyBorder(25, 25, 25, 25)
			
			));
	
	JLabel ratingLabel = new JLabel("How would you rate our platform?");
	ratingLabel.setFont(new Font("Segoe UI",Font.BOLD,17));
	ratingLabel.setForeground(new Color(35,55,90));
	
	formPanel.add(ratingLabel);
	formPanel.add(Box.createVerticalStrut(15));
	
	JPanel ratingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	ratingPanel.setBackground(Color.WHITE);
	
	ButtonGroup ratingGroup = new ButtonGroup();
	
	for(int i =1; i<=5;i++) {
		JRadioButton rating = new JRadioButton(i + " Star");
		rating.setBackground(Color.WHITE);
		rating.setFont(new Font("Segoe UI", Font.PLAIN, 14));
		rating.setFocusPainted(false);
		ratingGroup.add(rating);
		ratingPanel.add(rating);
		
	}
	
	formPanel.add(ratingPanel);
	formPanel.add(Box.createVerticalStrut(25));
	
	JLabel commentLabel = new JLabel("Your comments");
	commentLabel.setFont((new Font("Segoe UI",Font.BOLD,17)));
	commentLabel.setForeground(new Color(35,55,90));
	
	formPanel.add(commentLabel);
	formPanel.add(Box.createVerticalStrut(10));
	
	JTextArea commentArea = new JTextArea(6,30);
	commentArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
	commentArea.setBackground(Color.WHITE);
	commentArea.setFont(new Font("Segoe UI",Font.PLAIN,14));
	commentArea.setLineWrap(true);
	commentArea.setWrapStyleWord(true);
	
	JScrollPane commentScroll = new JScrollPane(commentArea);
	commentScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
	
	formPanel.add(commentScroll);
	formPanel.add(Box.createVerticalStrut(25));
	
	JButton submitButton = new JButton("Submit Evaluation");
	submitButton.setFont(new Font("Segoe UI",Font.BOLD, 14));
	submitButton.setBackground(new Color(45,90,150));
	submitButton.setForeground(Color.WHITE);
	submitButton.setFocusPainted(false);
	
	submitButton.setOpaque(true);
	submitButton.setBorderPainted(false);
	submitButton.setPreferredSize(new Dimension(180, 42));
	submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
	
	formPanel.add(submitButton);
	
	panel.add(formPanel, BorderLayout.CENTER);
	
	return panel;
	
}
    

private JPanel createUploadPage() {

	JPanel panel = new JPanel(new BorderLayout(15, 15));
	panel.setBackground(new Color(245, 248, 253));
	panel.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

    
	JLabel heading = new JLabel("Upload Learning Materials");
	heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
	heading.setForeground(new Color(35, 55, 90));
	
	JLabel subtitle = new JLabel(
		    "Select your lecture materials to start learning."
		);
		subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
		subtitle.setForeground(new Color(100, 110, 125));
		
		JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
		topPanel.setBackground(new Color(245, 248, 253));

		topPanel.add(heading);
		topPanel.add(subtitle);

		panel.add(topPanel, BorderLayout.NORTH);

    

DefaultListModel<String> fileModel = new DefaultListModel<>();
JList<String> fileList = new JList<>(fileModel);

fileList.setFont(new Font("Segoe UI", Font.PLAIN, 15));
fileList.setBackground(Color.WHITE);
fileList.setForeground(new Color(35, 55, 90));
fileList.setSelectionBackground(new Color(220, 233, 248));
fileList.setSelectionForeground(new Color(35, 55, 90));
fileList.setFixedCellHeight(40);

JScrollPane scrollPane = new JScrollPane(fileList);
scrollPane.setBorder(
    BorderFactory.createLineBorder(new Color(225, 232, 242))
);
scrollPane.getViewport().setBackground(Color.WHITE);

panel.add(scrollPane, BorderLayout.CENTER);


    

JPanel buttonPanel = new JPanel(
    new FlowLayout(FlowLayout.CENTER, 15, 10)
);
buttonPanel.setBackground(new Color(245, 248, 253));

JButton selectButton = new JButton("Select Files");
JButton removeButton = new JButton("Remove Selected");
JButton analyzeButton = new JButton("Analyze");


JButton[] buttons = {
    selectButton, removeButton, analyzeButton
};

for (JButton button : buttons) {
    button.setFont(new Font("Segoe UI", Font.BOLD, 14));
    button.setBackground(new Color(45, 90, 150));
    button.setForeground(Color.WHITE);
    button.setFocusPainted(false);
    button.setOpaque(true);
    button.setBorderPainted(false);
    button.setPreferredSize(new Dimension(160, 42));
}


    buttonPanel.add(selectButton);
    buttonPanel.add(removeButton);
    buttonPanel.add(analyzeButton);

    // 분석 진행 상황 표시
    JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    statusLabel.setFont(new Font("Dialog", Font.PLAIN, 13));
    statusLabel.setForeground(new Color(100, 110, 125));

    JPanel southPanel = new JPanel(new BorderLayout());
    southPanel.setBackground(new Color(245, 248, 253));
    southPanel.add(buttonPanel, BorderLayout.CENTER);
    southPanel.add(statusLabel, BorderLayout.SOUTH);

    panel.add(southPanel, BorderLayout.SOUTH);

   
    selectButton.addActionListener(e -> {

        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);

        // 서버가 아직 PDF 만 처리할 수 있어 PDF 만 고르게 한다 (PPT 등은 추후 추가)
        FileNameExtensionFilter filter =
            new FileNameExtensionFilter("PDF Files (*.pdf)", "pdf");

        chooser.setFileFilter(filter);

        int result = chooser.showOpenDialog(panel);

        if (result == JFileChooser.APPROVE_OPTION) {

            File[] files = chooser.getSelectedFiles();

            for (File file : files) {

                String name = file.getName();

                if (!fileModel.contains(name)) {
                    fileModel.addElement(name);
                    selectedFiles.put(name, file);
                }
            }
        }
    });

   
    removeButton.addActionListener(e -> {

        int index = fileList.getSelectedIndex();

        if (index != -1) {
            selectedFiles.remove(fileModel.get(index));
            fileModel.remove(index);
        } else {
            JOptionPane.showMessageDialog(
                panel, "Please select a file to remove."
            );
        }
    });

   
    analyzeButton.addActionListener(e -> {

        if (fileModel.isEmpty()) {

            JOptionPane.showMessageDialog(
                panel, "Please upload a file first."
            );

        } else {

            analyzeFiles(panel, analyzeButton, statusLabel);
        }
    });

    return panel;
}


    
    // ---------------- 서버로 업로드 · 결과 표시 ----------------

    // 목록의 파일을 하나씩 서버에 올린다. 시간이 걸리므로 SwingWorker 로 화면이 멈추지 않게 한다.
    private void analyzeFiles(JPanel panel, JButton analyzeButton, JLabel statusLabel) {
        List<File> files = new ArrayList<>(selectedFiles.values());
        analyzeButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new SwingWorker<ApiClient.AnalysisResult, String>() {
            private final List<String> failures = new ArrayList<>();

            @Override
            protected ApiClient.AnalysisResult doInBackground() throws Exception {
                publish("Connecting to server...");
                if (!api.isServerUp()) {
                    throw new ApiClient.ApiException(0, "SERVER_UNAVAILABLE",
                        "서버에 연결할 수 없습니다. FermServer 의 ApiServer 를 먼저 실행하세요.");
                }
                ApiClient.AnalysisResult last = null;
                for (int i = 0; i < files.size(); i++) {
                    File file = files.get(i);
                    publish("Analyzing " + (i + 1) + "/" + files.size() + ": " + file.getName() + " ...");
                    try {
                        ApiClient.UploadResult uploaded = api.upload(file.toPath());
                        last = api.getResult(uploaded.resultId());
                    } catch (ApiClient.ApiException ex) {
                        failures.add(file.getName() + " — " + ex.getMessage());
                    }
                }
                return last;
            }

            @Override
            protected void process(List<String> messages) {
                statusLabel.setText(messages.get(messages.size() - 1));
            }

            @Override
            protected void done() {
                analyzeButton.setEnabled(true);
                setCursor(Cursor.getDefaultCursor());
                try {
                    ApiClient.AnalysisResult result = get();
                    int ok = files.size() - failures.size();
                    statusLabel.setText("Done: " + ok + " succeeded, " + failures.size() + " failed.");
                    if (!failures.isEmpty()) {
                        JOptionPane.showMessageDialog(panel,
                            "분석하지 못한 파일:\n" + String.join("\n", failures),
                            "Analysis failed", JOptionPane.WARNING_MESSAGE);
                    }
                    if (result != null) {
                        showResult(result);
                        showPage("RESULT");
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Failed.");
                    JOptionPane.showMessageDialog(panel, cause.getMessage(),
                        "Analysis failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // 결과 화면에 서버 결과를 채운다. 아직 만들어지지 않은 항목은 안내 문구를 보여 준다.
    private void showResult(ApiClient.AnalysisResult r) {
        List<ApiClient.Item> items = r.items() == null ? List.of() : r.items();
        resultFileLabel.setText(r.fileName() + "  ·  " + items.size() + " paragraphs extracted");

        StringBuilder text = new StringBuilder();
        int page = -1;
        for (ApiClient.Item item : items) {
            if (item.page() != page) {
                page = item.page();
                text.append("[Page ").append(page).append("]\n");
            }
            text.append(item.content()).append("\n\n");
        }
        extractedTextArea.setText(items.isEmpty() ? "No text found in this file." : text.toString().strip());
        extractedTextArea.setCaretPosition(0);

        String translation = r.translatedText();
        if (translation == null && r.terms() != null && !r.terms().isEmpty()) {
            StringBuilder t = new StringBuilder();
            for (ApiClient.Term term : r.terms()) {
                t.append(term.korean()).append(" — ").append(term.english() == null ? "" : term.english()).append("\n");
            }
            translation = t.toString();
        }
        setCardText("Lecture Summary", r.summary());
        setCardText("Translation", translation);
        setCardText("Key Concepts", r.explanation());
        setCardText("Exam Preparation", r.examQuestions());
    }

    private void setCardText(String card, String value) {
        JLabel label = resultCardLabels.get(card);
        if (label == null) {
            return;
        }
        String shown = value == null || value.isBlank() ? "Not available yet." : value.strip();
        if (shown.length() > 300) {
            shown = shown.substring(0, 300) + "...";
        }
        String html = shown.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        label.setText("<html>" + html + "</html>");
    }

    private void showPage(String pageName) {
        cardLayout.show(contentPanel, pageName);
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            new Main();
        });

    }
}

