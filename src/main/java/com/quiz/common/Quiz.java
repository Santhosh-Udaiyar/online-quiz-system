package com.quiz.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a Quiz.
 */
public class Quiz implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String title;
    private String description;
    private String category;
    private int durationMinutes; // Duration in minutes
    private double totalMarks;
    private double negativeMark;
    private boolean active;

    // Additional metadata for dashboard display
    private int questionCount;
    private int previousAttempts;
    private Double bestScore;

    // Optional question collection (for administration or server processing)
    private List<Question> questions = new ArrayList<>();

    public Quiz() {
    }

    public Quiz(int id, String title, String description, String category,
                int durationMinutes, double totalMarks, double negativeMark, boolean active) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.durationMinutes = durationMinutes;
        this.totalMarks = totalMarks;
        this.negativeMark = negativeMark;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public double getTotalMarks() {
        return totalMarks;
    }

    public void setTotalMarks(double totalMarks) {
        this.totalMarks = totalMarks;
    }

    public double getNegativeMark() {
        return negativeMark;
    }

    public void setNegativeMark(double negativeMark) {
        this.negativeMark = negativeMark;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public int getPreviousAttempts() {
        return previousAttempts;
    }

    public void setPreviousAttempts(int previousAttempts) {
        this.previousAttempts = previousAttempts;
    }

    public Double getBestScore() {
        return bestScore;
    }

    public void setBestScore(Double bestScore) {
        this.bestScore = bestScore;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }

    @Override
    public String toString() {
        return "Quiz{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", durationMinutes=" + durationMinutes +
                ", totalMarks=" + totalMarks +
                ", active=" + active +
                '}';
    }
}
