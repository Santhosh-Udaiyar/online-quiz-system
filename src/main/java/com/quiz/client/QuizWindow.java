package com.quiz.client;

import com.quiz.common.Question;
import com.quiz.common.Quiz;
import com.quiz.common.QuizResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quiz examination window featuring live countdown timer, question navigation,
 * option selection, background answer synchronization, and auto-submission on timeout.
 */
public class QuizWindow extends JFrame {

    private final QuizClient client;
    private final Quiz quiz;
    private final List<Question> questions;
    private final Runnable onFinishCallback;

    private int currentIndex = 0;
    private final Map<Integer, String> studentAnswers = new ConcurrentHashMap<>();
    private long remainingSeconds;
    private javax.swing.Timer timer;
    private volatile boolean submitted = false;

    // UI components
    private JLabel timerLabel;
    private JLabel questionNumLabel;
    private JLabel marksBadge;
    private JLabel questionTextLabel;
    private JRadioButton optARadio;
    private JRadioButton optBRadio;
    private JRadioButton optCRadio;
    private JRadioButton optDRadio;
    private ButtonGroup optionGroup;
    private JButton prevButton;
    private JButton nextButton;
    private JButton submitButton;
    private JButton[] questionNavButtons;
    private JPanel navButtonPanel;

    public QuizWindow(QuizClient client, Quiz quiz, List<Question> questions,
                      long durationSeconds, Runnable onFinishCallback) {
        super("Online Examination - " + quiz.getTitle());
        this.client = client;
        this.quiz = quiz;
        this.questions = questions;
        this.remainingSeconds = durationSeconds;
        this.onFinishCallback = onFinishCallback;

        UIUtils.applyModernLookAndFeel();
        setSize(950, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Warn before closing active exam
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmEarlyExit();
            }
        });

        initUI();
        startTimer();
        loadQuestion(0);
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIUtils.BG_LIGHT);

        // Top Header
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(UIUtils.PRIMARY);
        topBar.setBorder(new EmptyBorder(12, 24, 12, 24));

        JPanel titlePanel = new JPanel(new GridLayout(2, 1));
        titlePanel.setBackground(UIUtils.PRIMARY);

        JLabel quizTitleLabel = new JLabel(quiz.getTitle());
        quizTitleLabel.setFont(UIUtils.FONT_SUBTITLE);
        quizTitleLabel.setForeground(Color.WHITE);

        JLabel categoryLabel = new JLabel("Subject: " + quiz.getCategory() + " | Total Questions: " + questions.size());
        categoryLabel.setFont(UIUtils.FONT_SMALL);
        categoryLabel.setForeground(new Color(224, 231, 255));

        titlePanel.add(quizTitleLabel);
        titlePanel.add(categoryLabel);
        topBar.add(titlePanel, BorderLayout.WEST);

        // Timer Panel
        JPanel timerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        timerPanel.setBackground(UIUtils.PRIMARY);

        JLabel timeText = new JLabel("Time Remaining:");
        timeText.setFont(UIUtils.FONT_BODY_BOLD);
        timeText.setForeground(Color.WHITE);

        timerLabel = new JLabel(formatTimer(remainingSeconds));
        timerLabel.setFont(UIUtils.FONT_TIMER);
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setOpaque(true);
        timerLabel.setBackground(UIUtils.PRIMARY_DARK);
        timerLabel.setBorder(new EmptyBorder(6, 12, 6, 12));

        timerPanel.add(timeText);
        timerPanel.add(timerLabel);
        topBar.add(timerPanel, BorderLayout.EAST);

        mainPanel.add(topBar, BorderLayout.NORTH);

        // Center Content Split (Questions & Navigation Sidebar)
        JPanel centerPanel = new JPanel(new BorderLayout(16, 16));
        centerPanel.setBackground(UIUtils.BG_LIGHT);
        centerPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Question Card
        JPanel questionCard = UIUtils.createCardPanel();
        questionCard.setLayout(new BorderLayout(12, 12));

        JPanel qHeader = new JPanel(new BorderLayout());
        qHeader.setBackground(Color.WHITE);

        questionNumLabel = new JLabel("Question 1 of " + questions.size());
        questionNumLabel.setFont(UIUtils.FONT_HEADER);
        questionNumLabel.setForeground(UIUtils.TEXT_MAIN);

        marksBadge = new JLabel();
        marksBadge.setFont(UIUtils.FONT_SMALL);
        marksBadge.setForeground(UIUtils.TEXT_MUTED);

        qHeader.add(questionNumLabel, BorderLayout.WEST);
        qHeader.add(marksBadge, BorderLayout.EAST);
        questionCard.add(qHeader, BorderLayout.NORTH);

        // Question Body
        JPanel qBody = new JPanel();
        qBody.setLayout(new BoxLayout(qBody, BoxLayout.Y_AXIS));
        qBody.setBackground(Color.WHITE);

        questionTextLabel = new JLabel();
        questionTextLabel.setFont(UIUtils.FONT_SUBTITLE);
        questionTextLabel.setForeground(UIUtils.TEXT_MAIN);
        questionTextLabel.setBorder(new EmptyBorder(10, 0, 20, 0));
        qBody.add(questionTextLabel);

        // Options
        optionGroup = new ButtonGroup();
        optARadio = createOptionRadioButton("A");
        optBRadio = createOptionRadioButton("B");
        optCRadio = createOptionRadioButton("C");
        optDRadio = createOptionRadioButton("D");

        qBody.add(optARadio);
        qBody.add(Box.createVerticalStrut(10));
        qBody.add(optBRadio);
        qBody.add(Box.createVerticalStrut(10));
        qBody.add(optCRadio);
        qBody.add(Box.createVerticalStrut(10));
        qBody.add(optDRadio);
        qBody.add(Box.createVerticalStrut(16));

        JButton clearButton = new JButton("Clear Selection");
        clearButton.setFont(UIUtils.FONT_SMALL);
        clearButton.setFocusPainted(false);
        clearButton.addActionListener(e -> clearCurrentSelection());
        qBody.add(clearButton);

        questionCard.add(qBody, BorderLayout.CENTER);
        centerPanel.add(questionCard, BorderLayout.CENTER);

        // Right Navigation Sidebar
        JPanel navSidebar = UIUtils.createCardPanel();
        navSidebar.setPreferredSize(new Dimension(200, 400));
        navSidebar.setLayout(new BorderLayout(8, 8));

        JLabel navTitle = new JLabel("Question Palette", SwingConstants.CENTER);
        navTitle.setFont(UIUtils.FONT_HEADER);
        navTitle.setForeground(UIUtils.TEXT_MAIN);
        navSidebar.add(navTitle, BorderLayout.NORTH);

        navButtonPanel = new JPanel(new GridLayout(0, 4, 6, 6));
        navButtonPanel.setBackground(Color.WHITE);

        questionNavButtons = new JButton[questions.size()];
        for (int i = 0; i < questions.size(); i++) {
            final int idx = i;
            JButton btn = new JButton(String.valueOf(i + 1));
            btn.setFont(UIUtils.FONT_BODY_BOLD);
            btn.setFocusPainted(false);
            btn.setBackground(new Color(241, 245, 249));
            btn.setForeground(UIUtils.TEXT_MAIN);
            btn.addActionListener(e -> loadQuestion(idx));
            questionNavButtons[i] = btn;
            navButtonPanel.add(btn);
        }

        JScrollPane navScroll = new JScrollPane(navButtonPanel);
        navScroll.setBorder(null);
        navSidebar.add(navScroll, BorderLayout.CENTER);

        centerPanel.add(navSidebar, BorderLayout.EAST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom Controls Bar
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(Color.WHITE);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.BORDER_COLOR),
                new EmptyBorder(12, 24, 12, 24)
        ));

        JPanel navControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        navControls.setBackground(Color.WHITE);

        prevButton = UIUtils.createStyledButton("← Previous", new Color(100, 116, 139), Color.WHITE);
        prevButton.addActionListener(e -> {
            if (currentIndex > 0) loadQuestion(currentIndex - 1);
        });

        nextButton = UIUtils.createStyledButton("Next →", UIUtils.PRIMARY, Color.WHITE);
        nextButton.addActionListener(e -> {
            if (currentIndex < questions.size() - 1) loadQuestion(currentIndex + 1);
        });

        navControls.add(prevButton);
        navControls.add(nextButton);
        bottomBar.add(navControls, BorderLayout.WEST);

        submitButton = UIUtils.createStyledButton("Submit Quiz", UIUtils.SUCCESS, Color.WHITE);
        submitButton.addActionListener(e -> confirmSubmit());
        bottomBar.add(submitButton, BorderLayout.EAST);

        mainPanel.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JRadioButton createOptionRadioButton(String key) {
        JRadioButton radio = new JRadioButton();
        radio.setFont(UIUtils.FONT_BODY);
        radio.setBackground(Color.WHITE);
        radio.setFocusPainted(false);
        radio.addActionListener(e -> {
            Question currentQ = questions.get(currentIndex);
            studentAnswers.put(currentQ.getId(), key);
            updateNavButtonState(currentIndex);

            // Asynchronously report selection to server
            new Thread(() -> client.submitAnswer(currentQ.getId(), key)).start();
        });
        optionGroup.add(radio);
        return radio;
    }

    private void loadQuestion(int index) {
        if (index < 0 || index >= questions.size()) return;
        currentIndex = index;

        Question q = questions.get(index);
        questionNumLabel.setText("Question " + (index + 1) + " of " + questions.size());
        marksBadge.setText(String.format("Marks: +%.1f | Negative: -%.1f", q.getMarks(), q.getNegativeMarks()));
        questionTextLabel.setText("<html><body style='width: 550px;'>" + q.getQuestionText() + "</body></html>");

        optARadio.setText("<html><body style='width: 500px;'><b>(A)</b> " + q.getOptionA() + "</body></html>");
        optBRadio.setText("<html><body style='width: 500px;'><b>(B)</b> " + q.getOptionB() + "</body></html>");
        optCRadio.setText("<html><body style='width: 500px;'><b>(C)</b> " + q.getOptionC() + "</body></html>");
        optDRadio.setText("<html><body style='width: 500px;'><b>(D)</b> " + q.getOptionD() + "</body></html>");

        optionGroup.clearSelection();
        String selected = studentAnswers.get(q.getId());
        if (selected != null) {
            switch (selected.toUpperCase()) {
                case "A" -> optARadio.setSelected(true);
                case "B" -> optBRadio.setSelected(true);
                case "C" -> optCRadio.setSelected(true);
                case "D" -> optDRadio.setSelected(true);
            }
        }

        prevButton.setEnabled(index > 0);
        nextButton.setEnabled(index < questions.size() - 1);

        refreshAllNavButtons();
    }

    private void clearCurrentSelection() {
        Question currentQ = questions.get(currentIndex);
        studentAnswers.remove(currentQ.getId());
        optionGroup.clearSelection();
        updateNavButtonState(currentIndex);
        new Thread(() -> client.submitAnswer(currentQ.getId(), null)).start();
    }

    private void updateNavButtonState(int index) {
        if (index < 0 || index >= questions.size()) return;
        Question q = questions.get(index);
        JButton btn = questionNavButtons[index];

        if (studentAnswers.containsKey(q.getId())) {
            btn.setBackground(UIUtils.SUCCESS);
            btn.setForeground(Color.WHITE);
        } else {
            btn.setBackground(new Color(241, 245, 249));
            btn.setForeground(UIUtils.TEXT_MAIN);
        }

        if (index == currentIndex) {
            btn.setBorder(new LineBorder(UIUtils.PRIMARY, 2));
        } else {
            btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        }
    }

    private void refreshAllNavButtons() {
        for (int i = 0; i < questions.size(); i++) {
            updateNavButtonState(i);
        }
    }

    private void startTimer() {
        timer = new javax.swing.Timer(1000, e -> {
            if (submitted) return;
            remainingSeconds--;

            timerLabel.setText(formatTimer(remainingSeconds));

            if (remainingSeconds <= 120) { // Warning: under 2 minutes
                timerLabel.setBackground(UIUtils.DANGER);
            }

            if (remainingSeconds <= 0) {
                timer.stop();
                JOptionPane.showMessageDialog(this,
                        "Time has expired! Automatically submitting your examination answers.",
                        "Time Out", JOptionPane.WARNING_MESSAGE);
                performSubmission();
            }
        });
        timer.start();
    }

    private String formatTimer(long totalSecs) {
        if (totalSecs < 0) totalSecs = 0;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void confirmSubmit() {
        int answered = studentAnswers.size();
        int total = questions.size();
        int result = JOptionPane.showConfirmDialog(
                this,
                String.format("You have answered %d of %d questions.\nAre you sure you want to submit your exam now?", answered, total),
                "Confirm Quiz Submission",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.YES_OPTION) {
            performSubmission();
        }
    }

    private void confirmEarlyExit() {
        int result = JOptionPane.showConfirmDialog(
                this,
                "Exiting the window will submit your current answers. Are you sure you want to exit?",
                "Abandon Quiz",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION) {
            performSubmission();
        }
    }

    private void performSubmission() {
        if (submitted) return;
        submitted = true;
        if (timer != null) timer.stop();

        submitButton.setEnabled(false);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);

        // Submit via background worker to keep UI responsive
        new SwingWorker<QuizResult, Void>() {
            @Override
            protected QuizResult doInBackground() throws Exception {
                return client.submitQuiz(new HashMap<>(studentAnswers));
            }

            @Override
            protected void done() {
                try {
                    QuizResult result = get();
                    dispose(); // Close quiz window

                    SwingUtilities.invokeLater(() -> {
                        ResultWindow resWin = new ResultWindow(result, onFinishCallback);
                        resWin.setVisible(true);
                    });
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(QuizWindow.this,
                            "Submission failed: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    if (onFinishCallback != null) {
                        onFinishCallback.run();
                    }
                    dispose();
                }
            }
        }.execute();
    }
}
