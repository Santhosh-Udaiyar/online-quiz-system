package com.quiz;

import com.quiz.common.Question;
import com.quiz.common.Quiz;
import com.quiz.common.QuizResult;
import com.quiz.database.Database;
import com.quiz.database.QuizRepository;
import com.quiz.database.ResultRepository;
import com.quiz.database.UserRepository;
import com.quiz.server.QuizManager;
import com.quiz.server.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class QuizScoringTest {

    private String testDbPath;
    private Database database;
    private QuizManager quizManager;
    private SessionManager sessionManager;
    private int quizId;

    @BeforeEach
    public void setUp() throws Exception {
        testDbPath = "data/test_scoring_" + System.currentTimeMillis() + ".db";
        database = new Database(testDbPath);
        UserRepository userRepo = new UserRepository(database);
        QuizRepository quizRepo = new QuizRepository(database);
        ResultRepository resultRepo = new ResultRepository(database);
        sessionManager = new SessionManager();
        quizManager = new QuizManager(userRepo, quizRepo, resultRepo, sessionManager);

        // Create a dedicated test quiz with 3 questions
        // Question 1: Marks 10, Negative 2, Correct "A"
        // Question 2: Marks 10, Negative 2, Correct "B"
        // Question 3: Marks 10, Negative 2, Correct "C"
        Quiz quiz = new Quiz(0, "Test Scoring Quiz", "Evaluation test", "Test", 10, 30.0, 2.0, true);
        quizId = quizRepo.createQuiz(quiz);

        quizRepo.addQuestion(new Question(0, quizId, "Q1", "opt1", "opt2", "opt3", "opt4", "A", 10.0, 2.0));
        quizRepo.addQuestion(new Question(0, quizId, "Q2", "opt1", "opt2", "opt3", "opt4", "B", 10.0, 2.0));
        quizRepo.addQuestion(new Question(0, quizId, "Q3", "opt1", "opt2", "opt3", "opt4", "C", 10.0, 2.0));
    }

    @AfterEach
    public void tearDown() {
        File file = new File(testDbPath);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testPerfectScore() throws Exception {
        SessionManager.ActiveSession session = quizManager.startQuiz(1, "student1", quizId);

        // Submit the exact correct answer for each shuffled question
        Map<Integer, String> studentAnswers = new HashMap<>();
        for (Question q : session.getSanitizedQuestions()) {
            String correctLetter = session.getAnswerKey().get(q.getId());
            studentAnswers.put(q.getId(), correctLetter);
        }

        QuizResult result = quizManager.submitQuiz(session.getToken(), studentAnswers);

        assertEquals(3, result.getTotalQuestions());
        assertEquals(3, result.getAttempted());
        assertEquals(3, result.getCorrect());
        assertEquals(0, result.getIncorrect());
        assertEquals(0, result.getUnanswered());
        assertEquals(30.0, result.getScore());
        assertEquals(100.0, result.getPercentage());
        assertEquals("COMPLETED", result.getStatus());
    }

    @Test
    public void testNegativeMarkingAndUnanswered() throws Exception {
        SessionManager.ActiveSession session = quizManager.startQuiz(1, "student1", quizId);
        List<Question> questions = session.getSanitizedQuestions();

        Map<Integer, String> studentAnswers = new HashMap<>();

        // Question 0: Answer CORRECTLY (+10 marks)
        int q0Id = questions.get(0).getId();
        String correct0 = session.getAnswerKey().get(q0Id);
        studentAnswers.put(q0Id, correct0);

        // Question 1: Answer INCORRECTLY (-2 marks)
        int q1Id = questions.get(1).getId();
        String correct1 = session.getAnswerKey().get(q1Id);
        String wrong1 = correct1.equals("A") ? "B" : "A";
        studentAnswers.put(q1Id, wrong1);

        // Question 2: Leave UNANSWERED (0 marks, no penalty)
        // (Do not put in studentAnswers)

        QuizResult result = quizManager.submitQuiz(session.getToken(), studentAnswers);

        assertEquals(3, result.getTotalQuestions());
        assertEquals(2, result.getAttempted());
        assertEquals(1, result.getCorrect());
        assertEquals(1, result.getIncorrect());
        assertEquals(1, result.getUnanswered());

        // Score = 10 (correct) - 2 (negative) + 0 (unanswered) = 8.0
        assertEquals(8.0, result.getScore(), 0.001);

        // Percentage = (8.0 / 30.0) * 100 = 26.67%
        assertEquals(26.67, result.getPercentage(), 0.01);
    }
}
