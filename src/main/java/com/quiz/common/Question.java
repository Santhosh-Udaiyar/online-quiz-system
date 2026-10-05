package com.quiz.common;

import java.io.Serializable;
import java.util.*;

/**
 * Model representing a multiple-choice question in a quiz.
 */
public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private int quizId;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctOption; // "A", "B", "C", or "D" (hidden from students during quiz)
    private double marks;
    private double negativeMarks;

    public Question() {
    }

    public Question(int id, int quizId, String questionText,
                    String optionA, String optionB, String optionC, String optionD,
                    String correctOption, double marks, double negativeMarks) {
        this.id = id;
        this.quizId = quizId;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctOption = correctOption;
        this.marks = marks;
        this.negativeMarks = negativeMarks;
    }

    /**
     * Creates a copy of this question with the correct answer stripped out,
     * safe to send to students during an active quiz session.
     */
    public Question createSanitizedCopy() {
        return new Question(this.id, this.quizId, this.questionText,
                this.optionA, this.optionB, this.optionC, this.optionD,
                null, this.marks, this.negativeMarks);
    }

    /**
     * Returns the text for a given option key ("A", "B", "C", or "D").
     */
    public String getOptionText(String key) {
        if (key == null) return "";
        return switch (key.trim().toUpperCase()) {
            case "A" -> optionA;
            case "B" -> optionB;
            case "C" -> optionC;
            case "D" -> optionD;
            default -> "";
        };
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getQuizId() {
        return quizId;
    }

    public void setQuizId(int quizId) {
        this.quizId = quizId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public String getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(String correctOption) {
        this.correctOption = correctOption;
    }

    public double getMarks() {
        return marks;
    }

    public void setMarks(double marks) {
        this.marks = marks;
    }

    public double getNegativeMarks() {
        return negativeMarks;
    }

    public void setNegativeMarks(double negativeMarks) {
        this.negativeMarks = negativeMarks;
    }

    @Override
    public String toString() {
        return "Question{" +
                "id=" + id +
                ", quizId=" + quizId +
                ", questionText='" + questionText + '\'' +
                ", marks=" + marks +
                ", negativeMarks=" + negativeMarks +
                '}';
    }
}
