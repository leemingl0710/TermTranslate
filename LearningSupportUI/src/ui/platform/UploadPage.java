package ui.platform;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 강의자료 업로드 화면: 단원 선택·생성, 파일 목록, 분석 진행률. */
class UploadPage extends JPanel {

    private static final String[] SUPPORTED_EXTENSIONS = {"pdf", "ppt", "pptx", "txt", "jpg", "jpeg", "png"};

    private final List<File> selectedFiles = new ArrayList<>();
    private final List<String> chapters = new ArrayList<>();
    private final DefaultListModel<String> fileModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileModel);
    private final JComboBox<String> chapterBox = new JComboBox<>();
    private final JProgressBar analysisProgressBar = new JProgressBar(0, 100);

    UploadPage(Navigator navigator) {
        super(new BorderLayout(15, 15));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));

        add(createHeader(), BorderLayout.NORTH);
        add(createFileList(), BorderLayout.CENTER);
        add(createBottom(navigator), BorderLayout.SOUTH);
    }

    // ---------------- 화면 구성 ----------------

    private JPanel createHeader() {
        chapterBox.addItem("Select Chapter");
        chapterBox.setPreferredSize(new Dimension(250, 35));
        chapterBox.setFont(Theme.koreanFont(14));

        JButton chapterButton = Theme.button("Create Chapter", 13, null);
        chapterButton.addActionListener(e -> createChapter());

        JPanel chapterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        chapterPanel.setBackground(Theme.BACKGROUND);
        chapterPanel.add(new JLabel("Chapter:"));
        chapterPanel.add(chapterBox);
        chapterPanel.add(chapterButton);

        JPanel headerPanel = new JPanel(new BorderLayout(5, 10));
        headerPanel.setBackground(Theme.BACKGROUND);
        headerPanel.add(Theme.header("Upload Learning Materials", 26,
                "Select your lecture materials to start learning.", 14), BorderLayout.NORTH);
        headerPanel.add(chapterPanel, BorderLayout.SOUTH);
        return headerPanel;
    }

    private JScrollPane createFileList() {
        fileList.setFont(Theme.koreanFont(15));
        fileList.setBackground(Theme.CARD);
        fileList.setForeground(Theme.TITLE);
        fileList.setSelectionBackground(Theme.SELECTION);
        fileList.setSelectionForeground(Theme.TITLE);
        fileList.setFixedCellHeight(40);

        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scrollPane.getViewport().setBackground(Theme.CARD);
        return scrollPane;
    }

    private JPanel createBottom(Navigator navigator) {
        Dimension size = new Dimension(160, 42);
        JButton selectButton = Theme.button("Select Files", 14, size);
        JButton removeButton = Theme.button("Remove Selected", 14, size);
        JButton ocrButton = Theme.button("OCR Review", 14, size);
        JButton analyzeButton = Theme.button("Analyze", 14, size);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.setBackground(Theme.BACKGROUND);
        buttonPanel.add(selectButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(ocrButton);
        buttonPanel.add(analyzeButton);

        analysisProgressBar.setValue(0);
        analysisProgressBar.setStringPainted(true);
        analysisProgressBar.setString("Ready for analysis");
        analysisProgressBar.setFont(Theme.font(Font.BOLD, 13));
        analysisProgressBar.setForeground(Theme.PRIMARY);
        analysisProgressBar.setPreferredSize(new Dimension(500, 25));

        ocrButton.addActionListener(e -> navigator.show(Page.OCR));
        selectButton.addActionListener(e -> selectFiles());
        removeButton.addActionListener(e -> removeSelectedFile());
        analyzeButton.addActionListener(e -> analyze());

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 10));
        bottomPanel.setBackground(Theme.BACKGROUND);
        bottomPanel.add(analysisProgressBar, BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
        return bottomPanel;
    }

    // ---------------- 버튼 동작 ----------------

    private void selectFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Supported Files (PDF, PPT, PPTX, TXT, Images)", SUPPORTED_EXTENSIONS));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        for (File file : chooser.getSelectedFiles()) {
            String name = file.getName();
            if (!isSupported(name)) {
                JOptionPane.showMessageDialog(this,
                        "Unsupported file format: " + name + "\nPlease select PDF, PPT, PPTX, TXT, JPG or PNG.",
                        "File Format Error", JOptionPane.ERROR_MESSAGE);
            } else if (!fileModel.contains(name)) {
                fileModel.addElement(name);
                selectedFiles.add(file);
            }
        }
    }

    private static boolean isSupported(String fileName) {
        String lower = fileName.toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    private void removeSelectedFile() {
        int index = fileList.getSelectedIndex();
        if (index == -1) {
            JOptionPane.showMessageDialog(this, "Please select a file to remove.");
            return;
        }
        fileModel.remove(index);
        selectedFiles.remove(index);
    }

    private void analyze() {
        if (fileModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please upload a file first.");
            return;
        }
        JOptionPane.showMessageDialog(this,
                "Files selected successfully.\nThe analysis feature will be connected to the backend.",
                "Analysis Request", JOptionPane.INFORMATION_MESSAGE);
    }

    private void createChapter() {
        String chapterName = JOptionPane.showInputDialog(this, "Enter a chapter name:",
                "Create Chapter", JOptionPane.PLAIN_MESSAGE);
        if (chapterName == null) {
            return;
        }
        chapterName = chapterName.trim();
        if (chapterName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a chapter name.");
            return;
        }
        if (chapters.contains(chapterName)) {
            JOptionPane.showMessageDialog(this, "This chapter already exists.");
            return;
        }
        chapters.add(chapterName);
        chapterBox.addItem(chapterName);
        chapterBox.setSelectedItem(chapterName);
        JOptionPane.showMessageDialog(this, "Chapter created: " + chapterName);
    }

    // ---------------- 백엔드 연동용 ----------------

    List<File> getSelectedFiles() {
        return new ArrayList<>(selectedFiles);
    }

    /** 고른 단원 이름. "Select Chapter" 그대로면 null. */
    String getSelectedChapter() {
        return chapterBox.getSelectedIndex() <= 0 ? null : (String) chapterBox.getSelectedItem();
    }

    void updateAnalysisProgress(int progress) {
        analysisProgressBar.setValue(progress);
        analysisProgressBar.setString("Analysis Progress: " + analysisProgressBar.getValue() + "%");
    }
}
