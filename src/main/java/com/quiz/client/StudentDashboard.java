package com.quiz.client;

import com.quiz.common.Quiz;
import com.quiz.common.QuizResult;
import com.quiz.common.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Main desktop portal for students: browse quizzes, start timed exams,
 * review historical submissions, and view live leaderboards.
 */
public class StudentDashboard extends JFrame {

    private final QuizClient client;
    private final User currentUser;

    // Available Quizzes Tab
    private JTable quizzesTable;
    private DefaultTableModel quizzesModel;
    private List<Quiz> currentQuizzes;

    // History Tab
    private JTable historyTable;
    private DefaultTableModel historyModel;
    private List<QuizResult> currentHistory;

    // Leaderboard Tab
    private JTable leaderboardTable;
    private DefaultTableModel leaderboardModel;
    private JComboBox<String> quizFilterCombo;

    public StudentDashboard(QuizClient client) {
        super("Online Examination System - Student Dashboard");
        this.client = client;
        this.currentUser = client.getCurrentUser();

        UIUtils.applyModernLookAndFeel();
        setSize(1000, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIUtils.BG_LIGHT);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UIUtils.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel userInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        userInfoPanel.setBackground(UIUtils.PRIMARY);

        JLabel welcomeLabel = new JLabel("Welcome, " + (currentUser != null ? currentUser.getUsername() : "Student"));
        welcomeLabel.setFont(UIUtils.FONT_TITLE);
        welcomeLabel.setForeground(Color.WHITE);

        JLabel badge = new JLabel(" STUDENT ");
        badge.setFont(UIUtils.FONT_SMALL);
        badge.setOpaque(true);
        badge.setBackground(UIUtils.ACCENT);
        badge.setForeground(Color.WHITE);
        badge.setBorder(new EmptyBorder(4, 8, 4, 8));

        userInfoPanel.add(welcomeLabel);
        userInfoPanel.add(badge);
        headerPanel.add(userInfoPanel, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setBackground(UIUtils.PRIMARY);

        JButton refreshButton = UIUtils.createStyledButton("Refresh", UIUtils.PRIMARY_DARK, Color.WHITE);
        refreshButton.addActionListener(e -> refreshAllData());

        JButton logoutButton = UIUtils.createStyledButton("Logout", UIUtils.DANGER, Color.WHITE);
        logoutButton.addActionListener(e -> performLogout());

        actionPanel.add(refreshButton);
        actionPanel.add(logoutButton);
        headerPanel.add(actionPanel, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UIUtils.FONT_HEADER);
        tabbedPane.setBackground(UIUtils.BG_LIGHT);

        tabbedPane.addTab("Available Quizzes", createQuizzesTab());
        tabbedPane.addTab("My History", createHistoryTab());
        tabbedPane.addTab("Leaderboard", createLeaderboardTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // Initial Data Load
        refreshAllData();
    }

    // ==========================================
    // Tab 1: Available Quizzes
    // ==========================================

    private JPanel createQuizzesTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        String[] columns = {"ID", "Quiz Title", "Category", "Questions", "Duration", "Total Marks", "Negative Mark", "Attempts", "Best Score"};
        quizzesModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        quizzesTable = new JTable(quizzesModel);
        UIUtils.styleTable(quizzesTable);
        quizzesTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        quizzesTable.getColumnModel().getColumn(1).setPreferredWidth(220);
        quizzesTable.getColumnModel().getColumn(2).setPreferredWidth(120);

        JScrollPane scrollPane = new JScrollPane(quizzesTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Start Button
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        bottomBar.setBackground(UIUtils.BG_LIGHT);

        JButton startButton = UIUtils.createStyledButton("Start Selected Quiz →", UIUtils.SUCCESS, Color.WHITE);
        startButton.setFont(UIUtils.FONT_HEADER);
        startButton.addActionListener(e -> startSelectedQuiz());
        bottomBar.add(startButton);

        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private void startSelectedQuiz() {
        int selectedRow = quizzesTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a quiz to start.", "No Quiz Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int quizId = (int) quizzesModel.getValueAt(selectedRow, 0);
        String quizTitle = (String) quizzesModel.getValueAt(selectedRow, 1);
        int duration = (int) quizzesModel.getValueAt(selectedRow, 4);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format("You are about to start:\n'%s'\nDuration: %d minutes\n\nOnce started, the timer cannot be paused. Proceed?", quizTitle, duration),
                "Start Examination",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

        // Fetch quiz session from server
        new SwingWorker<QuizClient.QuizSessionData, Void>() {
            @Override
            protected QuizClient.QuizSessionData doInBackground() throws Exception {
                return client.startQuiz(quizId);
            }

            @Override
            protected void done() {
                try {
                    QuizClient.QuizSessionData sessionData = get();
                    setVisible(false); // Hide dashboard while taking test

                    SwingUtilities.invokeLater(() -> {
                        QuizWindow quizWindow = new QuizWindow(
                                client,
                                sessionData.quiz,
                                sessionData.questions,
                                sessionData.durationSeconds,
                                () -> {
                                    setVisible(true);
                                    refreshAllData();
                                }
                        );
                        quizWindow.setVisible(true);
                    });
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(StudentDashboard.this,
                            "Could not start quiz: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ==========================================
    // Tab 2: Attempt History
    // ==========================================

    private JPanel createHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        String[] columns = {"Attempt ID", "Quiz Title", "Score", "Percentage", "Completed At", "Status"};
        historyModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        historyTable = new JTable(historyModel);
        UIUtils.styleTable(historyTable);

        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        bottomBar.setBackground(UIUtils.BG_LIGHT);

        JButton viewDetailsButton = UIUtils.createStyledButton("View Detailed Review", UIUtils.PRIMARY, Color.WHITE);
        viewDetailsButton.addActionListener(e -> viewSelectedAttemptDetails());
        bottomBar.add(viewDetailsButton);

        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private void viewSelectedAttemptDetails() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select an attempt from the history table to review.",
                    "No Attempt Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int attemptId = (int) historyModel.getValueAt(selectedRow, 0);

        new SwingWorker<QuizResult, Void>() {
            @Override
            protected QuizResult doInBackground() throws Exception {
                return client.getResultDetails(attemptId);
            }

            @Override
            protected void done() {
                try {
                    QuizResult result = get();
                    SwingUtilities.invokeLater(() -> {
                        ResultWindow resWin = new ResultWindow(result, () -> {});
                        resWin.setVisible(true);
                    });
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(StudentDashboard.this,
                            "Failed to load details: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ==========================================
    // Tab 3: Leaderboard
    // ==========================================

    private JPanel createLeaderboardTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Filter Bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterBar.setBackground(UIUtils.BG_LIGHT);

        filterBar.add(new JLabel("Select Quiz:"));
        quizFilterCombo = new JComboBox<>();
        quizFilterCombo.setFont(UIUtils.FONT_BODY);
        quizFilterCombo.setPreferredSize(new Dimension(280, 32));
        quizFilterCombo.addActionListener(e -> loadLeaderboardData());
        filterBar.add(quizFilterCombo);

        panel.add(filterBar, BorderLayout.NORTH);

        String[] columns = {"Rank", "Student Username", "Quiz Title", "Score", "Percentage", "Completion Time", "Status"};
        leaderboardModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        leaderboardTable = new JTable(leaderboardModel);
        UIUtils.styleTable(leaderboardTable);
        leaderboardTable.getColumnModel().getColumn(0).setPreferredWidth(50);

        JScrollPane scrollPane = new JScrollPane(leaderboardTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    // ==========================================
    // Data Loading & Refresh
    // ==========================================

    public void refreshAllData() {
        loadQuizzesData();
        loadHistoryData();
    }

    private void loadQuizzesData() {
        new SwingWorker<List<Quiz>, Void>() {
            @Override
            protected List<Quiz> doInBackground() throws Exception {
                return client.getQuizzes();
            }

            @Override
            protected void done() {
                try {
                    currentQuizzes = get();
                    quizzesModel.setRowCount(0);

                    // Update Quiz Filter dropdown in Leaderboard tab
                    quizFilterCombo.removeAllItems();
                    quizFilterCombo.addItem("All Quizzes");

                    for (Quiz q : currentQuizzes) {
                        quizzesModel.addRow(new Object[]{
                                q.getId(),
                                q.getTitle(),
                                q.getCategory(),
                                q.getQuestionCount(),
                                q.getDurationMinutes(),
                                String.format("%.1f", q.getTotalMarks()),
                                String.format("%.1f", q.getNegativeMark()),
                                q.getPreviousAttempts(),
                                q.getBestScore() != null ? String.format("%.2f", q.getBestScore()) : "N/A"
                        });
                        quizFilterCombo.addItem(q.getId() + " - " + q.getTitle());
                    }

                    loadLeaderboardData();
                } catch (Exception ex) {
                    System.err.println("Failed to fetch quizzes: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void loadHistoryData() {
        new SwingWorker<List<QuizResult>, Void>() {
            @Override
            protected List<QuizResult> doInBackground() throws Exception {
                return client.getStudentHistory();
            }

            @Override
            protected void done() {
                try {
                    currentHistory = get();
                    historyModel.setRowCount(0);
                    for (QuizResult r : currentHistory) {
                        historyModel.addRow(new Object[]{
                                r.getAttemptId(),
                                r.getQuizTitle(),
                                String.format("%.2f / %.2f", r.getScore(), r.getMaxScore()),
                                String.format("%.2f%%", r.getPercentage()),
                                r.getEndTime(),
                                r.getStatus()
                        });
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to load history: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void loadLeaderboardData() {
        int quizId = 0;
        String selected = (String) quizFilterCombo.getSelectedItem();
        if (selected != null && !selected.equals("All Quizzes")) {
            try {
                quizId = Integer.parseInt(selected.split(" - ")[0].trim());
            } catch (Exception ignored) {
            }
        }

        final int qId = quizId;
        new SwingWorker<List<QuizResult>, Void>() {
            @Override
            protected List<QuizResult> doInBackground() throws Exception {
                return client.getLeaderboard(qId);
            }

            @Override
            protected void done() {
                try {
                    List<QuizResult> list = get();
                    leaderboardModel.setRowCount(0);
                    for (QuizResult r : list) {
                        leaderboardModel.addRow(new Object[]{
                                "#" + r.getRank(),
                                r.getUsername(),
                                r.getQuizTitle(),
                                String.format("%.2f / %.2f", r.getScore(), r.getMaxScore()),
                                String.format("%.2f%%", r.getPercentage()),
                                r.getFormattedTimeTaken(),
                                r.getStatus()
                        });
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to fetch leaderboard: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to log out?",
                "Logout Confirmation",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            client.disconnect();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginGUI().setVisible(true));
        }
    }
}
