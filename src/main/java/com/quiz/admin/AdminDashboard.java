package com.quiz.admin;

import com.quiz.client.LoginGUI;
import com.quiz.client.QuizClient;
import com.quiz.client.ResultWindow;
import com.quiz.client.UIUtils;
import com.quiz.common.Question;
import com.quiz.common.Quiz;
import com.quiz.common.QuizResult;
import com.quiz.common.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Administrative GUI console for managing quizzes, question banks,
 * inspecting student examination results, and monitoring system statistics.
 */
public class AdminDashboard extends JFrame {

    private final QuizClient client;
    private final User currentUser;

    // Tabs & Models
    private DefaultTableModel quizTableModel;
    private JTable quizTable;
    private List<Quiz> quizList;

    private DefaultTableModel questionTableModel;
    private JTable questionTable;
    private JComboBox<String> quizSelectorCombo;
    private List<Question> currentQuestionList;

    private DefaultTableModel resultsTableModel;
    private JTable resultsTable;

    private DefaultTableModel leaderboardTableModel;
    private JTable leaderboardTable;
    private JComboBox<String> leaderboardQuizCombo;

    private JPanel statsPanel;

    public AdminDashboard(QuizClient client) {
        super("Online Quiz System - Administrator Console");
        this.client = client;
        this.currentUser = client.getCurrentUser();

        UIUtils.applyModernLookAndFeel();
        setSize(1100, 780);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIUtils.BG_LIGHT);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(15, 23, 42)); // Slate 900
        headerPanel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel userInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        userInfo.setBackground(new Color(15, 23, 42));

        JLabel titleLabel = new JLabel("Admin Control Center");
        titleLabel.setFont(UIUtils.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        JLabel roleBadge = new JLabel(" SUPER ADMIN ");
        roleBadge.setFont(UIUtils.FONT_SMALL);
        roleBadge.setOpaque(true);
        roleBadge.setBackground(UIUtils.DANGER);
        roleBadge.setForeground(Color.WHITE);
        roleBadge.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel userLabel = new JLabel("Logged in as: " + (currentUser != null ? currentUser.getUsername() : "Admin"));
        userLabel.setFont(UIUtils.FONT_BODY);
        userLabel.setForeground(new Color(203, 213, 225));

        userInfo.add(titleLabel);
        userInfo.add(roleBadge);
        userInfo.add(userLabel);
        headerPanel.add(userInfo, BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerActions.setBackground(new Color(15, 23, 42));

        JButton refreshBtn = UIUtils.createStyledButton("Refresh", UIUtils.PRIMARY, Color.WHITE);
        refreshBtn.addActionListener(e -> refreshAllTabs());

        JButton logoutBtn = UIUtils.createStyledButton("Logout", UIUtils.DANGER, Color.WHITE);
        logoutBtn.addActionListener(e -> performLogout());

        headerActions.add(refreshBtn);
        headerActions.add(logoutBtn);
        headerPanel.add(headerActions, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UIUtils.FONT_HEADER);

        tabbedPane.addTab("Quiz Management", createQuizManagementTab());
        tabbedPane.addTab("Question Management", createQuestionManagementTab());
        tabbedPane.addTab("Student Results", createStudentResultsTab());
        tabbedPane.addTab("Leaderboard", createLeaderboardTab());
        tabbedPane.addTab("System Statistics", createStatisticsTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);

        // Initial Data Load
        refreshAllTabs();
    }

    // ==========================================
    // Tab 1: Quiz Management
    // ==========================================

    private JPanel createQuizManagementTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        String[] cols = {"ID", "Title", "Category", "Duration (mins)", "Total Marks", "Negative Mark", "Status", "Questions"};
        quizTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        quizTable = new JTable(quizTableModel);
        UIUtils.styleTable(quizTable);
        quizTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        quizTable.getColumnModel().getColumn(1).setPreferredWidth(200);

        JScrollPane scrollPane = new JScrollPane(quizTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actions.setBackground(UIUtils.BG_LIGHT);

        JButton createBtn = UIUtils.createStyledButton("+ Create Quiz", UIUtils.SUCCESS, Color.WHITE);
        createBtn.addActionListener(e -> showCreateQuizDialog());

        JButton editBtn = UIUtils.createStyledButton("Edit Quiz", UIUtils.PRIMARY, Color.WHITE);
        editBtn.addActionListener(e -> showEditQuizDialog());

        JButton toggleBtn = UIUtils.createStyledButton("Toggle Active / Inactive", UIUtils.WARNING, Color.WHITE);
        toggleBtn.addActionListener(e -> toggleQuizActive());

        JButton deleteBtn = UIUtils.createStyledButton("Delete Quiz", UIUtils.DANGER, Color.WHITE);
        deleteBtn.addActionListener(e -> deleteSelectedQuiz());

        actions.add(createBtn);
        actions.add(editBtn);
        actions.add(toggleBtn);
        actions.add(deleteBtn);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private void showCreateQuizDialog() {
        JTextField titleField = new JTextField();
        JTextField catField = new JTextField();
        JTextArea descArea = new JTextArea(3, 20);
        JTextField durField = new JTextField("10");
        JTextField negField = new JTextField("1.0");
        JCheckBox activeCheck = new JCheckBox("Active", true);

        JPanel panel = new JPanel(new GridLayout(6, 2, 8, 8));
        panel.add(new JLabel("Quiz Title:"));
        panel.add(titleField);
        panel.add(new JLabel("Category / Subject:"));
        panel.add(catField);
        panel.add(new JLabel("Description:"));
        panel.add(new JScrollPane(descArea));
        panel.add(new JLabel("Duration (Minutes):"));
        panel.add(durField);
        panel.add(new JLabel("Negative Mark (per wrong ans):"));
        panel.add(negField);
        panel.add(new JLabel("Status:"));
        panel.add(activeCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create New Quiz", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String title = titleField.getText().trim();
            String cat = catField.getText().trim();
            String desc = descArea.getText().trim();
            if (title.isEmpty() || cat.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Title and category are required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int dur;
            double neg;
            try {
                dur = Integer.parseInt(durField.getText().trim());
                neg = Double.parseDouble(negField.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Duration and negative mark must be valid numbers.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Quiz quiz = new Quiz(0, title, desc, cat, dur, 0.0, neg, activeCheck.isSelected());
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.adminCreateQuiz(quiz);
                    return null;
                }

                @Override
                protected void done() {
                    refreshAllTabs();
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Quiz created successfully! Now add questions to it.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    private void showEditQuizDialog() {
        int selectedRow = quizTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a quiz to edit.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quizId = (int) quizTableModel.getValueAt(selectedRow, 0);
        Quiz quiz = quizList.stream().filter(q -> q.getId() == quizId).findFirst().orElse(null);
        if (quiz == null) return;

        JTextField titleField = new JTextField(quiz.getTitle());
        JTextField catField = new JTextField(quiz.getCategory());
        JTextArea descArea = new JTextArea(quiz.getDescription(), 3, 20);
        JTextField durField = new JTextField(String.valueOf(quiz.getDurationMinutes()));
        JTextField negField = new JTextField(String.valueOf(quiz.getNegativeMark()));
        JCheckBox activeCheck = new JCheckBox("Active", quiz.isActive());

        JPanel panel = new JPanel(new GridLayout(6, 2, 8, 8));
        panel.add(new JLabel("Quiz Title:"));
        panel.add(titleField);
        panel.add(new JLabel("Category / Subject:"));
        panel.add(catField);
        panel.add(new JLabel("Description:"));
        panel.add(new JScrollPane(descArea));
        panel.add(new JLabel("Duration (Minutes):"));
        panel.add(durField);
        panel.add(new JLabel("Negative Mark:"));
        panel.add(negField);
        panel.add(new JLabel("Status:"));
        panel.add(activeCheck);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Quiz (ID: " + quiz.getId() + ")", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            quiz.setTitle(titleField.getText().trim());
            quiz.setCategory(catField.getText().trim());
            quiz.setDescription(descArea.getText().trim());
            try {
                quiz.setDurationMinutes(Integer.parseInt(durField.getText().trim()));
                quiz.setNegativeMark(Double.parseDouble(negField.getText().trim()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid number inputs.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            quiz.setActive(activeCheck.isSelected());

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.adminUpdateQuiz(quiz);
                    return null;
                }

                @Override
                protected void done() {
                    refreshAllTabs();
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Quiz updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    private void toggleQuizActive() {
        int selectedRow = quizTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a quiz to toggle active status.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quizId = (int) quizTableModel.getValueAt(selectedRow, 0);
        Quiz quiz = quizList.stream().filter(q -> q.getId() == quizId).findFirst().orElse(null);
        if (quiz == null) return;

        quiz.setActive(!quiz.isActive());
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                client.adminUpdateQuiz(quiz);
                return null;
            }

            @Override
            protected void done() {
                refreshAllTabs();
            }
        }.execute();
    }

    private void deleteSelectedQuiz() {
        int selectedRow = quizTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a quiz to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quizId = (int) quizTableModel.getValueAt(selectedRow, 0);
        String title = (String) quizTableModel.getValueAt(selectedRow, 1);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to permanently delete quiz '" + title + "' (ID: " + quizId + ")?\nAll its questions and student attempts will also be removed.",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.adminDeleteQuiz(quizId);
                    return null;
                }

                @Override
                protected void done() {
                    refreshAllTabs();
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Quiz deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    // ==========================================
    // Tab 2: Question Management
    // ==========================================

    private JPanel createQuestionManagementTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Selector Bar
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        topBar.setBackground(UIUtils.BG_LIGHT);
        topBar.add(new JLabel("Select Quiz to Manage Questions:"));

        quizSelectorCombo = new JComboBox<>();
        quizSelectorCombo.setFont(UIUtils.FONT_BODY);
        quizSelectorCombo.setPreferredSize(new Dimension(300, 32));
        quizSelectorCombo.addActionListener(e -> loadQuestionsForSelectedQuiz());
        topBar.add(quizSelectorCombo);
        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"ID", "Question Text", "Option A", "Option B", "Option C", "Option D", "Correct", "Marks", "Neg Marks"};
        questionTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        questionTable = new JTable(questionTableModel);
        UIUtils.styleTable(questionTable);
        questionTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        questionTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        questionTable.getColumnModel().getColumn(6).setPreferredWidth(50);

        JScrollPane scrollPane = new JScrollPane(questionTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actions.setBackground(UIUtils.BG_LIGHT);

        JButton addBtn = UIUtils.createStyledButton("+ Add Question", UIUtils.SUCCESS, Color.WHITE);
        addBtn.addActionListener(e -> showAddQuestionDialog());

        JButton editBtn = UIUtils.createStyledButton("Edit Question", UIUtils.PRIMARY, Color.WHITE);
        editBtn.addActionListener(e -> showEditQuestionDialog());

        JButton deleteBtn = UIUtils.createStyledButton("Delete Question", UIUtils.DANGER, Color.WHITE);
        deleteBtn.addActionListener(e -> deleteSelectedQuestion());

        actions.add(addBtn);
        actions.add(editBtn);
        actions.add(deleteBtn);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private int getSelectedQuizIdFromCombo() {
        String selected = (String) quizSelectorCombo.getSelectedItem();
        if (selected == null || selected.trim().isEmpty()) return -1;
        try {
            return Integer.parseInt(selected.split(" - ")[0].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private void loadQuestionsForSelectedQuiz() {
        int quizId = getSelectedQuizIdFromCombo();
        if (quizId <= 0) return;

        new SwingWorker<List<Question>, Void>() {
            @Override
            protected List<Question> doInBackground() throws Exception {
                return client.adminGetQuestions(quizId);
            }

            @Override
            protected void done() {
                try {
                    currentQuestionList = get();
                    questionTableModel.setRowCount(0);
                    for (Question q : currentQuestionList) {
                        questionTableModel.addRow(new Object[]{
                                q.getId(),
                                q.getQuestionText(),
                                q.getOptionA(),
                                q.getOptionB(),
                                q.getOptionC(),
                                q.getOptionD(),
                                q.getCorrectOption(),
                                String.format("%.1f", q.getMarks()),
                                String.format("%.1f", q.getNegativeMarks())
                        });
                    }
                } catch (Exception ex) {
                    System.err.println("Error fetching questions: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void showAddQuestionDialog() {
        int quizId = getSelectedQuizIdFromCombo();
        if (quizId <= 0) {
            JOptionPane.showMessageDialog(this, "Please select a quiz first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextArea qTextArea = new JTextArea(3, 25);
        JTextField optAField = new JTextField();
        JTextField optBField = new JTextField();
        JTextField optCField = new JTextField();
        JTextField optDField = new JTextField();
        JComboBox<String> correctCombo = new JComboBox<>(new String[]{"A", "B", "C", "D"});
        JTextField marksField = new JTextField("5.0");
        JTextField negField = new JTextField("1.0");

        JPanel form = new JPanel(new GridLayout(8, 2, 6, 6));
        form.add(new JLabel("Question Text:"));
        form.add(new JScrollPane(qTextArea));
        form.add(new JLabel("Option A:"));
        form.add(optAField);
        form.add(new JLabel("Option B:"));
        form.add(optBField);
        form.add(new JLabel("Option C:"));
        form.add(optCField);
        form.add(new JLabel("Option D:"));
        form.add(optDField);
        form.add(new JLabel("Correct Option:"));
        form.add(correctCombo);
        form.add(new JLabel("Marks:"));
        form.add(marksField);
        form.add(new JLabel("Negative Marks:"));
        form.add(negField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add Question to Quiz ID " + quizId,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String text = qTextArea.getText().trim();
            String a = optAField.getText().trim();
            String b = optBField.getText().trim();
            String c = optCField.getText().trim();
            String d = optDField.getText().trim();
            String correct = (String) correctCombo.getSelectedItem();

            if (text.isEmpty() || a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Question text and all four options are required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            double marks, neg;
            try {
                marks = Double.parseDouble(marksField.getText().trim());
                neg = Double.parseDouble(negField.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Marks must be numeric.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Question q = new Question(0, quizId, text, a, b, c, d, correct, marks, neg);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.adminAddQuestion(q);
                    return null;
                }

                @Override
                protected void done() {
                    loadQuestionsForSelectedQuiz();
                    refreshQuizzesTableOnly();
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Question added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    private void showEditQuestionDialog() {
        int selectedRow = questionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a question to edit.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int qId = (int) questionTableModel.getValueAt(selectedRow, 0);
        Question q = currentQuestionList.stream().filter(item -> item.getId() == qId).findFirst().orElse(null);
        if (q == null) return;

        JTextArea qTextArea = new JTextArea(q.getQuestionText(), 3, 25);
        JTextField optAField = new JTextField(q.getOptionA());
        JTextField optBField = new JTextField(q.getOptionB());
        JTextField optCField = new JTextField(q.getOptionC());
        JTextField optDField = new JTextField(q.getOptionD());
        JComboBox<String> correctCombo = new JComboBox<>(new String[]{"A", "B", "C", "D"});
        correctCombo.setSelectedItem(q.getCorrectOption());
        JTextField marksField = new JTextField(String.valueOf(q.getMarks()));
        JTextField negField = new JTextField(String.valueOf(q.getNegativeMarks()));

        JPanel form = new JPanel(new GridLayout(8, 2, 6, 6));
        form.add(new JLabel("Question Text:"));
        form.add(new JScrollPane(qTextArea));
        form.add(new JLabel("Option A:"));
        form.add(optAField);
        form.add(new JLabel("Option B:"));
        form.add(optBField);
        form.add(new JLabel("Option C:"));
        form.add(optCField);
        form.add(new JLabel("Option D:"));
        form.add(optDField);
        form.add(new JLabel("Correct Option:"));
        form.add(correctCombo);
        form.add(new JLabel("Marks:"));
        form.add(marksField);
        form.add(new JLabel("Negative Marks:"));
        form.add(negField);

        int result = JOptionPane.showConfirmDialog(this, form, "Edit Question (ID: " + q.getId() + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            q.setQuestionText(qTextArea.getText().trim());
            q.setOptionA(optAField.getText().trim());
            q.setOptionB(optBField.getText().trim());
            q.setOptionC(optCField.getText().trim());
            q.setOptionD(optDField.getText().trim());
            q.setCorrectOption((String) correctCombo.getSelectedItem());

            try {
                q.setMarks(Double.parseDouble(marksField.getText().trim()));
                q.setNegativeMarks(Double.parseDouble(negField.getText().trim()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Marks must be numeric.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.adminUpdateQuestion(q);
                    return null;
                }

                @Override
                protected void done() {
                    loadQuestionsForSelectedQuiz();
                    refreshQuizzesTableOnly();
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Question updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    private void deleteSelectedQuestion() {
        int selectedRow = questionTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a question to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int qId = (int) questionTableModel.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Permanently delete Question ID " + qId + "?",
                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    client.adminDeleteQuestion(qId);
                    return null;
                }

                @Override
                protected void done() {
                    loadQuestionsForSelectedQuiz();
                    refreshQuizzesTableOnly();
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Question deleted successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    // ==========================================
    // Tab 3: Student Results
    // ==========================================

    private JPanel createStudentResultsTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        String[] cols = {"Attempt ID", "Student Username", "Quiz Title", "Score / Max", "Percentage", "Start Time", "End Time", "Status"};
        resultsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        resultsTable = new JTable(resultsTableModel);
        UIUtils.styleTable(resultsTable);

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actions.setBackground(UIUtils.BG_LIGHT);

        JButton inspectBtn = UIUtils.createStyledButton("Inspect Full Submission Review", UIUtils.PRIMARY, Color.WHITE);
        inspectBtn.addActionListener(e -> inspectSelectedSubmission());
        actions.add(inspectBtn);

        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void inspectSelectedSubmission() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a student result to inspect.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int attemptId = (int) resultsTableModel.getValueAt(selectedRow, 0);
        new SwingWorker<QuizResult, Void>() {
            @Override
            protected QuizResult doInBackground() throws Exception {
                return client.getResultDetails(attemptId);
            }

            @Override
            protected void done() {
                try {
                    QuizResult res = get();
                    SwingUtilities.invokeLater(() -> {
                        ResultWindow win = new ResultWindow(res, () -> {});
                        win.setVisible(true);
                    });
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(AdminDashboard.this, "Failed to load result: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ==========================================
    // Tab 4: Leaderboard
    // ==========================================

    private JPanel createLeaderboardTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        topBar.setBackground(UIUtils.BG_LIGHT);
        topBar.add(new JLabel("Filter Leaderboard by Quiz:"));

        leaderboardQuizCombo = new JComboBox<>();
        leaderboardQuizCombo.setFont(UIUtils.FONT_BODY);
        leaderboardQuizCombo.setPreferredSize(new Dimension(300, 32));
        leaderboardQuizCombo.addActionListener(e -> loadLeaderboardData());
        topBar.add(leaderboardQuizCombo);

        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"Rank", "Student Username", "Quiz Title", "Score / Max", "Percentage", "Time Taken", "Status"};
        leaderboardTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        leaderboardTable = new JTable(leaderboardTableModel);
        UIUtils.styleTable(leaderboardTable);
        leaderboardTable.getColumnModel().getColumn(0).setPreferredWidth(50);

        JScrollPane scrollPane = new JScrollPane(leaderboardTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIUtils.BORDER_COLOR));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void loadLeaderboardData() {
        int quizId = 0;
        String selected = (String) leaderboardQuizCombo.getSelectedItem();
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
                    leaderboardTableModel.setRowCount(0);
                    for (QuizResult r : list) {
                        leaderboardTableModel.addRow(new Object[]{
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
                    System.err.println("Failed to load leaderboard: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ==========================================
    // Tab 5: Statistics Tab
    // ==========================================

    private JPanel createStatisticsTab() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBackground(UIUtils.BG_LIGHT);
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));

        statsPanel = new JPanel(new GridLayout(2, 3, 20, 20));
        statsPanel.setBackground(UIUtils.BG_LIGHT);

        panel.add(statsPanel, BorderLayout.NORTH);
        return panel;
    }

    private void loadStatistics() {
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return client.adminGetStats();
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> stats = get();
                    statsPanel.removeAll();

                    statsPanel.add(createStatCard("Total Users Registered", String.valueOf(stats.getOrDefault("totalUsers", 0)), UIUtils.PRIMARY));
                    statsPanel.add(createStatCard("Total Active Students", String.valueOf(stats.getOrDefault("totalStudents", 0)), UIUtils.ACCENT));
                    statsPanel.add(createStatCard("Total Quizzes Available", String.valueOf(stats.getOrDefault("totalQuizzes", 0)), UIUtils.SUCCESS));
                    statsPanel.add(createStatCard("Total Questions in Pool", String.valueOf(stats.getOrDefault("totalQuestions", 0)), new Color(147, 51, 234)));
                    statsPanel.add(createStatCard("Total Exam Submissions", String.valueOf(stats.getOrDefault("totalAttempts", 0)), UIUtils.WARNING));
                    statsPanel.add(createStatCard("Average Percentage", stats.getOrDefault("averagePercentage", 0.0) + "%", new Color(16, 185, 129)));

                    statsPanel.revalidate();
                    statsPanel.repaint();
                } catch (Exception ex) {
                    System.err.println("Failed to load stats: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private JPanel createStatCard(String title, String val, Color accent) {
        JPanel card = UIUtils.createCardPanel();
        card.setLayout(new BorderLayout(8, 8));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(UIUtils.FONT_BODY);
        titleLbl.setForeground(UIUtils.TEXT_MUTED);

        JLabel valLbl = new JLabel(val);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 28));
        valLbl.setForeground(accent);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        return card;
    }

    // ==========================================
    // Data Refresh Coordination
    // ==========================================

    public void refreshAllTabs() {
        refreshQuizzes();
        loadAllResults();
        loadStatistics();
    }

    private void refreshQuizzes() {
        new SwingWorker<List<Quiz>, Void>() {
            @Override
            protected List<Quiz> doInBackground() throws Exception {
                return client.getQuizzes();
            }

            @Override
            protected void done() {
                try {
                    quizList = get();
                    quizTableModel.setRowCount(0);

                    quizSelectorCombo.removeAllItems();
                    leaderboardQuizCombo.removeAllItems();
                    leaderboardQuizCombo.addItem("All Quizzes");

                    for (Quiz q : quizList) {
                        quizTableModel.addRow(new Object[]{
                                q.getId(),
                                q.getTitle(),
                                q.getCategory(),
                                q.getDurationMinutes(),
                                String.format("%.1f", q.getTotalMarks()),
                                String.format("%.1f", q.getNegativeMark()),
                                q.isActive() ? "ACTIVE" : "INACTIVE",
                                q.getQuestionCount()
                        });

                        String comboItem = q.getId() + " - " + q.getTitle();
                        quizSelectorCombo.addItem(comboItem);
                        leaderboardQuizCombo.addItem(comboItem);
                    }

                    loadQuestionsForSelectedQuiz();
                    loadLeaderboardData();
                } catch (Exception ex) {
                    System.err.println("Error fetching quizzes for admin: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void refreshQuizzesTableOnly() {
        new SwingWorker<List<Quiz>, Void>() {
            @Override
            protected List<Quiz> doInBackground() throws Exception {
                return client.getQuizzes();
            }

            @Override
            protected void done() {
                try {
                    quizList = get();
                    quizTableModel.setRowCount(0);
                    for (Quiz q : quizList) {
                        quizTableModel.addRow(new Object[]{
                                q.getId(),
                                q.getTitle(),
                                q.getCategory(),
                                q.getDurationMinutes(),
                                String.format("%.1f", q.getTotalMarks()),
                                String.format("%.1f", q.getNegativeMark()),
                                q.isActive() ? "ACTIVE" : "INACTIVE",
                                q.getQuestionCount()
                        });
                    }
                } catch (Exception ex) {
                    System.err.println("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void loadAllResults() {
        new SwingWorker<List<QuizResult>, Void>() {
            @Override
            protected List<QuizResult> doInBackground() throws Exception {
                return client.adminGetAllResults();
            }

            @Override
            protected void done() {
                try {
                    List<QuizResult> list = get();
                    resultsTableModel.setRowCount(0);
                    for (QuizResult r : list) {
                        resultsTableModel.addRow(new Object[]{
                                r.getAttemptId(),
                                r.getUsername(),
                                r.getQuizTitle(),
                                String.format("%.2f / %.2f", r.getScore(), r.getMaxScore()),
                                String.format("%.2f%%", r.getPercentage()),
                                r.getStartTime(),
                                r.getEndTime(),
                                r.getStatus()
                        });
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to load all results: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void performLogout() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to log out of the Admin Console?",
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
