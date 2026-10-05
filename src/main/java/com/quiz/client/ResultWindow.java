package com.quiz.client;

import com.quiz.common.QuizResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Window presenting the comprehensive result of a quiz attempt, including
 * summary statistics and item-by-item question reviews.
 */
public class ResultWindow extends JFrame {

    private final QuizResult result;
    private final Runnable onReturnToDashboard;

    public ResultWindow(QuizResult result, Runnable onReturnToDashboard) {
        super("Quiz Result - " + result.getQuizTitle());
        this.result = result;
        this.onReturnToDashboard = onReturnToDashboard;

        UIUtils.applyModernLookAndFeel();
        setSize(850, 750);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(UIUtils.BG_LIGHT);

        // Header Banner
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UIUtils.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JLabel titleLabel = new JLabel("QUIZ RESULT: " + result.getQuizTitle());
        titleLabel.setFont(UIUtils.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        JLabel statusBadge = new JLabel(result.getStatus(), SwingConstants.CENTER);
        statusBadge.setFont(UIUtils.FONT_BODY_BOLD);
        statusBadge.setOpaque(true);
        statusBadge.setBackground("TIMED_OUT".equalsIgnoreCase(result.getStatus()) ? UIUtils.WARNING : UIUtils.SUCCESS);
        statusBadge.setForeground(Color.WHITE);
        statusBadge.setBorder(new EmptyBorder(6, 12, 6, 12));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusBadge, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Content
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(UIUtils.BG_LIGHT);
        contentPanel.setBorder(new EmptyBorder(16, 24, 16, 24));

        // 1. Summary Metrics Card
        contentPanel.add(createSummaryPanel());
        contentPanel.add(Box.createVerticalStrut(16));

        // 2. Section Header: Review
        JLabel reviewHeader = new JLabel("Detailed Question-by-Question Review");
        reviewHeader.setFont(UIUtils.FONT_SUBTITLE);
        reviewHeader.setForeground(UIUtils.TEXT_MAIN);
        reviewHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(reviewHeader);
        contentPanel.add(Box.createVerticalStrut(10));

        // 3. Question Reviews List
        JPanel reviewListPanel = new JPanel();
        reviewListPanel.setLayout(new BoxLayout(reviewListPanel, BoxLayout.Y_AXIS));
        reviewListPanel.setBackground(UIUtils.BG_LIGHT);

        if (result.getReviews() != null && !result.getReviews().isEmpty()) {
            int qIndex = 1;
            for (QuizResult.QuestionReview review : result.getReviews()) {
                reviewListPanel.add(createQuestionReviewCard(qIndex++, review));
                reviewListPanel.add(Box.createVerticalStrut(12));
            }
        } else {
            JLabel emptyLabel = new JLabel("No itemized review data available.");
            emptyLabel.setFont(UIUtils.FONT_BODY);
            emptyLabel.setForeground(UIUtils.TEXT_MUTED);
            reviewListPanel.add(emptyLabel);
        }

        contentPanel.add(reviewListPanel);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 12));
        bottomBar.setBackground(Color.WHITE);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.BORDER_COLOR));

        JButton backButton = UIUtils.createStyledButton("Return to Dashboard", UIUtils.PRIMARY, Color.WHITE);
        backButton.addActionListener(e -> {
            dispose();
            if (onReturnToDashboard != null) {
                onReturnToDashboard.run();
            }
        });
        bottomBar.add(backButton);
        mainPanel.add(bottomBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createSummaryPanel() {
        JPanel panel = UIUtils.createCardPanel();
        panel.setLayout(new GridLayout(2, 4, 16, 16));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(createMetricBox("Score", String.format("%.2f / %.2f", result.getScore(), result.getMaxScore()), UIUtils.PRIMARY));
        panel.add(createMetricBox("Percentage", String.format("%.2f%%", result.getPercentage()),
                result.getPercentage() >= 50 ? UIUtils.SUCCESS : UIUtils.DANGER));
        panel.add(createMetricBox("Correct", String.valueOf(result.getCorrect()), UIUtils.SUCCESS));
        panel.add(createMetricBox("Incorrect", String.valueOf(result.getIncorrect()), UIUtils.DANGER));

        panel.add(createMetricBox("Total Questions", String.valueOf(result.getTotalQuestions()), UIUtils.TEXT_MAIN));
        panel.add(createMetricBox("Attempted", String.valueOf(result.getAttempted()), UIUtils.ACCENT));
        panel.add(createMetricBox("Unanswered", String.valueOf(result.getUnanswered()), UIUtils.TEXT_MUTED));
        panel.add(createMetricBox("Time Taken", result.getFormattedTimeTaken(), UIUtils.TEXT_MAIN));

        return panel;
    }

    private JPanel createMetricBox(String title, String value, Color valueColor) {
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 2));
        box.setBackground(new Color(248, 250, 252));
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UIUtils.BORDER_COLOR, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(UIUtils.FONT_SMALL);
        titleLbl.setForeground(UIUtils.TEXT_MUTED);

        JLabel valLbl = new JLabel(value);
        valLbl.setFont(UIUtils.FONT_HEADER);
        valLbl.setForeground(valueColor);

        box.add(titleLbl);
        box.add(valLbl);
        return box;
    }

    private JPanel createQuestionReviewCard(int index, QuizResult.QuestionReview review) {
        JPanel card = UIUtils.createCardPanel();
        card.setLayout(new BorderLayout(8, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Header of card
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);

        JLabel qNum = new JLabel("Question " + index);
        qNum.setFont(UIUtils.FONT_BODY_BOLD);
        qNum.setForeground(UIUtils.TEXT_MAIN);

        String badgeText;
        Color badgeBg;
        if (review.getSelectedOption() == null || review.getSelectedOption().equalsIgnoreCase("None")) {
            badgeText = "UNANSWERED (0.0 marks)";
            badgeBg = UIUtils.TEXT_MUTED;
        } else if (review.isCorrect()) {
            badgeText = String.format("CORRECT (+%.1f marks)", review.getMarksAwarded());
            badgeBg = UIUtils.SUCCESS;
        } else {
            badgeText = String.format("INCORRECT (%.1f marks)", review.getMarksAwarded());
            badgeBg = UIUtils.DANGER;
        }

        JLabel badge = new JLabel(badgeText);
        badge.setFont(UIUtils.FONT_SMALL);
        badge.setOpaque(true);
        badge.setBackground(badgeBg);
        badge.setForeground(Color.WHITE);
        badge.setBorder(new EmptyBorder(4, 8, 4, 8));

        top.add(qNum, BorderLayout.WEST);
        top.add(badge, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        // Question text
        JLabel qText = new JLabel("<html><body style='width: 600px;'>" + review.getQuestionText() + "</body></html>");
        qText.setFont(UIUtils.FONT_BODY);
        qText.setForeground(UIUtils.TEXT_MAIN);
        card.add(qText, BorderLayout.CENTER);

        // Answers breakdown
        JPanel ansPanel = new JPanel(new GridLayout(4, 1, 4, 4));
        ansPanel.setBackground(Color.WHITE);
        ansPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

        ansPanel.add(createOptionRow("A", review.getOptionA(), review.getSelectedOption(), review.getCorrectOption()));
        ansPanel.add(createOptionRow("B", review.getOptionB(), review.getSelectedOption(), review.getCorrectOption()));
        ansPanel.add(createOptionRow("C", review.getOptionC(), review.getSelectedOption(), review.getCorrectOption()));
        ansPanel.add(createOptionRow("D", review.getOptionD(), review.getSelectedOption(), review.getCorrectOption()));

        card.add(ansPanel, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createOptionRow(String letter, String text, String selected, String correct) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.setBackground(Color.WHITE);

        boolean isChosen = letter.equalsIgnoreCase(selected);
        boolean isCorrectAnswer = letter.equalsIgnoreCase(correct);

        JLabel label = new JLabel(String.format("(%s) %s", letter, text));
        label.setFont(UIUtils.FONT_BODY);

        if (isCorrectAnswer) {
            label.setForeground(new Color(5, 150, 105)); // dark emerald
            label.setFont(UIUtils.FONT_BODY_BOLD);
            JLabel check = new JLabel("[CORRECT ANSWER]");
            check.setFont(UIUtils.FONT_SMALL);
            check.setForeground(UIUtils.SUCCESS);
            row.add(label);
            row.add(check);
        } else if (isChosen) {
            label.setForeground(UIUtils.DANGER);
            JLabel wrong = new JLabel("[YOUR CHOICE - WRONG]");
            wrong.setFont(UIUtils.FONT_SMALL);
            wrong.setForeground(UIUtils.DANGER);
            row.add(label);
            row.add(wrong);
        } else {
            label.setForeground(UIUtils.TEXT_MUTED);
            row.add(label);
        }

        if (isChosen && isCorrectAnswer) {
            JLabel chosenBadge = new JLabel("[YOUR CHOICE]");
            chosenBadge.setFont(UIUtils.FONT_SMALL);
            chosenBadge.setForeground(UIUtils.SUCCESS);
            row.add(chosenBadge);
        }

        return row;
    }
}
