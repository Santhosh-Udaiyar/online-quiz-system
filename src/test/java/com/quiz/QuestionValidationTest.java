package com.quiz;

import com.quiz.common.Question;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QuestionValidationTest {

    @Test
    public void testQuestionSanitization() {
        Question q = new Question(1, 10, "What is TCP?",
                "Transmission Control Protocol", "Transfer Check Protocol",
                "Total Control Process", "Terminal Client Program",
                "A", 5.0, 1.0);

        assertEquals("A", q.getCorrectOption());

        Question sanitized = q.createSanitizedCopy();
        assertEquals(q.getId(), sanitized.getId());
        assertEquals(q.getQuizId(), sanitized.getQuizId());
        assertEquals(q.getQuestionText(), sanitized.getQuestionText());
        assertEquals(q.getOptionA(), sanitized.getOptionA());
        assertEquals(q.getOptionB(), sanitized.getOptionB());
        assertEquals(q.getOptionC(), sanitized.getOptionC());
        assertEquals(q.getOptionD(), sanitized.getOptionD());
        assertNull(sanitized.getCorrectOption(), "Sanitized question must NEVER leak correct answer to client!");
        assertEquals(5.0, sanitized.getMarks());
        assertEquals(1.0, sanitized.getNegativeMarks());
    }

    @Test
    public void testGetOptionText() {
        Question q = new Question(2, 10, "Sample question?",
                "Alpha", "Beta", "Gamma", "Delta",
                "C", 4.0, 0.5);

        assertEquals("Alpha", q.getOptionText("A"));
        assertEquals("Beta", q.getOptionText("B"));
        assertEquals("Gamma", q.getOptionText("C"));
        assertEquals("Delta", q.getOptionText("D"));
        assertEquals("Alpha", q.getOptionText("a")); // Case insensitivity
        assertEquals("", q.getOptionText("E"));      // Invalid key
        assertEquals("", q.getOptionText(null));   // Null key
    }
}
