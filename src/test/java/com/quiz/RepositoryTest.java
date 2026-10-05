package com.quiz;

import com.quiz.common.Question;
import com.quiz.common.Quiz;
import com.quiz.common.QuizResult;
import com.quiz.database.Database;
import com.quiz.database.QuizRepository;
import com.quiz.database.ResultRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RepositoryTest {

    private String testDbPath;
    private Database database;
    private QuizRepository quizRepository;
    private ResultRepository resultRepository;

    @BeforeEach
    public void setUp() {
        testDbPath = "data/test_repo_" + System.currentTimeMillis() + ".db";
        database = new Database(testDbPath);
        quizRepository = new QuizRepository(database);
        resultRepository = new ResultRepository(database);
    }

    @AfterEach
    public void tearDown() {
        File file = new File(testDbPath);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testQuizAndQuestionLifecycle() throws Exception {
        // Create Quiz
        Quiz quiz = new Quiz(0, "Algorithms & Data Structures", "DSA Quiz", "CS", 20, 0.0, 1.0, true);
        int quizId = quizRepository.createQuiz(quiz);
        assertTrue(quizId > 0);

        // Add Questions
        Question q1 = new Question(0, quizId, "What is the time complexity of binary search?",
                "O(1)", "O(log n)", "O(n)", "O(n^2)", "B", 10.0, 2.0);
        int q1Id = quizRepository.addQuestion(q1);
        assertTrue(q1Id > 0);

        Question q2 = new Question(0, quizId, "Which data structure uses LIFO order?",
                "Queue", "Stack", "Tree", "Graph", "B", 15.0, 3.0);
        int q2Id = quizRepository.addQuestion(q2);
        assertTrue(q2Id > 0);

        // Verify total marks recalculated: 10 + 15 = 25.0
        Quiz loadedQuiz = quizRepository.getQuizById(quizId);
        assertNotNull(loadedQuiz);
        assertEquals(25.0, loadedQuiz.getTotalMarks());
        assertEquals(2, loadedQuiz.getQuestionCount());

        // Update Question
        q1.setId(q1Id);
        q1.setMarks(12.0);
        boolean updated = quizRepository.updateQuestion(q1);
        assertTrue(updated);

        // Recalculated total marks: 12 + 15 = 27.0
        loadedQuiz = quizRepository.getQuizById(quizId);
        assertEquals(27.0, loadedQuiz.getTotalMarks());

        // Delete Question
        boolean deletedQ = quizRepository.deleteQuestion(q2Id);
        assertTrue(deletedQ);

        // Recalculated total marks: 12.0
        loadedQuiz = quizRepository.getQuizById(quizId);
        assertEquals(12.0, loadedQuiz.getTotalMarks());
        assertEquals(1, loadedQuiz.getQuestionCount());

        // Delete Quiz
        boolean deletedQuiz = quizRepository.deleteQuiz(quizId);
        assertTrue(deletedQuiz);
        assertNull(quizRepository.getQuizById(quizId));
    }

    @Test
    public void testResultRecordingAndLeaderboardOrdering() throws Exception {
        // Prepare Quiz
        Quiz quiz = new Quiz(0, "Networking Exam", "Networking", "Network", 15, 50.0, 0.0, true);
        int quizId = quizRepository.createQuiz(quiz);

        // Student 1 attempt: Score 40.0, took 300 seconds
        QuizResult res1 = new QuizResult();
        res1.setUserId(2); // student1
        res1.setUsername("student1");
        res1.setQuizId(quizId);
        res1.setScore(40.0);
        res1.setMaxScore(50.0);
        res1.setPercentage(80.0);
        res1.setStartTime("2026-10-04 10:00:00");
        res1.setEndTime("2026-10-04 10:05:00");
        res1.setStatus("COMPLETED");

        List<QuizResult.QuestionReview> reviews = new ArrayList<>();
        reviews.add(new QuizResult.QuestionReview(1, "Q?", "A", "B", "C", "D", "A", "A", true, 40.0, 50.0));
        res1.setReviews(reviews);

        int attemptId1 = resultRepository.recordAttempt(res1);
        assertTrue(attemptId1 > 0);

        // Student 2 attempt: Score 45.0, took 400 seconds (higher score, should rank #1)
        QuizResult res2 = new QuizResult();
        res2.setUserId(3); // student2
        res2.setUsername("student2");
        res2.setQuizId(quizId);
        res2.setScore(45.0);
        res2.setMaxScore(50.0);
        res2.setPercentage(90.0);
        res2.setStartTime("2026-10-04 10:00:00");
        res2.setEndTime("2026-10-04 10:06:40");
        res2.setStatus("COMPLETED");

        int attemptId2 = resultRepository.recordAttempt(res2);
        assertTrue(attemptId2 > 0);

        // Fetch Leaderboard
        List<QuizResult> leaderboard = resultRepository.getLeaderboard(quizId);
        assertEquals(2, leaderboard.size());
        assertEquals("student2", leaderboard.get(0).getUsername(), "Rank 1 must be student2 (highest score 45.0)");
        assertEquals("student1", leaderboard.get(1).getUsername(), "Rank 2 must be student1 (score 40.0)");

        // Fetch Attempt details
        QuizResult details = resultRepository.getAttemptDetails(attemptId1);
        assertNotNull(details);
        assertEquals(1, details.getReviews().size());
        assertEquals("student1", details.getUsername());
    }
}
