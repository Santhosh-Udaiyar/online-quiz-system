package com.quiz;

import com.quiz.common.Question;
import com.quiz.common.Quiz;
import com.quiz.server.SessionManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerTest {

    @Test
    public void testSessionCreationAndOptionRandomization() {
        SessionManager manager = new SessionManager();
        Quiz quiz = new Quiz(1, "Java Basics", "Test Quiz", "Java", 15, 20.0, 1.0, true);

        List<Question> originalQuestions = new ArrayList<>();
        originalQuestions.add(new Question(101, 1, "Which is a primitive type in Java?",
                "int", "String", "Integer", "Scanner", "A", 5.0, 1.0));
        originalQuestions.add(new Question(102, 1, "Which keyword stops loop iteration?",
                "continue", "break", "return", "exit", "B", 5.0, 1.0));

        SessionManager.ActiveSession session = manager.startSession(5, "studentUser", quiz, originalQuestions);

        assertNotNull(session);
        assertNotNull(session.getToken());
        assertEquals(5, session.getUserId());
        assertEquals("studentUser", session.getUsername());
        assertEquals(2, session.getSanitizedQuestions().size());

        // Verify correct answer mapping integrity after shuffling
        for (Question sanitized : session.getSanitizedQuestions()) {
            assertNull(sanitized.getCorrectOption(), "Sanitized question must NOT contain the correct option!");

            // Look up session answer key
            String sessionCorrectLetter = session.getAnswerKey().get(sanitized.getId());
            assertNotNull(sessionCorrectLetter);

            String textAtSessionLetter = sanitized.getOptionText(sessionCorrectLetter);

            // Find original question
            Question orig = originalQuestions.stream().filter(q -> q.getId() == sanitized.getId()).findFirst().orElse(null);
            assertNotNull(orig);

            String origCorrectText = orig.getOptionText(orig.getCorrectOption());

            assertEquals(origCorrectText, textAtSessionLetter,
                    "Shuffled option letter must resolve to the exact same correct answer text as original!");
        }
    }

    @Test
    public void testAnswerRecording() {
        SessionManager manager = new SessionManager();
        Quiz quiz = new Quiz(2, "Short Quiz", "Desc", "Cat", 5, 10.0, 0.0, true);

        List<Question> questions = new ArrayList<>();
        questions.add(new Question(1, 2, "Q1?", "A", "B", "C", "D", "A", 5.0, 0.0));

        SessionManager.ActiveSession session = manager.startSession(10, "student10", quiz, questions);

        session.recordAnswer(1, "B");
        assertEquals("B", session.getStudentAnswers().get(1));

        session.recordAnswer(1, "C");
        assertEquals("C", session.getStudentAnswers().get(1));

        session.recordAnswer(1, null);
        assertNull(session.getStudentAnswers().get(1));
    }

    @Test
    public void testSessionTimerRemaining() {
        SessionManager manager = new SessionManager();
        Quiz quiz = new Quiz(3, "Timed Quiz", "Desc", "Cat", 10, 10.0, 0.0, true); // 10 minutes = 600s

        List<Question> questions = new ArrayList<>();
        questions.add(new Question(1, 3, "Q1?", "A", "B", "C", "D", "A", 5.0, 0.0));

        SessionManager.ActiveSession session = manager.startSession(11, "student11", quiz, questions);

        long remaining = session.getRemainingSeconds();
        assertTrue(remaining >= 595 && remaining <= 600, "Remaining seconds should be close to 600s initially.");
        assertFalse(session.isExpired(), "Session should not be expired at start.");
    }
}
