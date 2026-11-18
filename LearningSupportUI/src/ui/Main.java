package ui;
import java.io.File;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.*;
import java.awt.*;

public class Main extends JFrame {

    
    private CardLayout cardLayout;
    private JPanel contentPanel;

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
        "Review your lecture materials and prepare for exams."
    );
    subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    subtitle.setForeground(new Color(100, 110, 125));

    JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
    topPanel.setBackground(new Color(245, 248, 253));
    topPanel.add(heading);
    topPanel.add(subtitle);

    panel.add(topPanel, BorderLayout.NORTH);

    JPanel resultPanel = new JPanel(new GridLayout(2, 2, 15, 15));
    resultPanel.setBackground(new Color(245, 248, 253));

    resultPanel.add(createFeatureCard(
        "Lecture Summary",
        "A short summary of the uploaded lecture material."
    ));

    resultPanel.add(createFeatureCard(
        "Translation",
        "Important Korean terms with English meanings."
    ));

    resultPanel.add(createFeatureCard(
        "Key Concepts",
        "Important topics and concepts to remember."
    ));

    resultPanel.add(createFeatureCard(
        "Exam Preparation",
        "Practice questions for reviewing your knowledge."
    ));

    panel.add(resultPanel, BorderLayout.CENTER);

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

    panel.add(buttonPanel, BorderLayout.SOUTH);

   
    selectButton.addActionListener(e -> {

        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);

        FileNameExtensionFilter filter =
            new FileNameExtensionFilter(
                "Supported Files (PDF, PPT, PPTX, TXT, Images)",
                "pdf", "ppt", "pptx", "txt",
                "jpg", "jpeg", "png"
            );

        chooser.setFileFilter(filter);

        int result = chooser.showOpenDialog(panel);

        if (result == JFileChooser.APPROVE_OPTION) {

            File[] files = chooser.getSelectedFiles();

            for (File file : files) {

                String name = file.getName();

                if (!fileModel.contains(name)) {
                    fileModel.addElement(name);
                }
            }
        }
    });

   
    removeButton.addActionListener(e -> {

        int index = fileList.getSelectedIndex();

        if (index != -1) {
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

            JOptionPane.showMessageDialog(
                panel, "Files are ready for analysis!"
            );
        }
    });

    return panel;
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

