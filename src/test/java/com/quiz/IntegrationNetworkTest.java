package com.quiz;

import com.quiz.client.QuizClient;
import com.quiz.common.Question;
import com.quiz.common.Quiz;
import com.quiz.common.QuizResult;
import com.quiz.common.User;
import com.quiz.server.QuizServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationNetworkTest {

    private static final int TEST_PORT = 8990;
    private static QuizServer server;
    private static Thread serverThread;

    @BeforeAll
    public static void startServer() throws Exception {
        // Start server in background thread
        server = new QuizServer(TEST_PORT);
        serverThread = new Thread(server::start, "Test-Server-Thread");
        serverThread.start();
        Thread.sleep(1000); // Give server a second to bind and seed DB
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testFullStudentExaminationWorkflow() throws Exception {
        QuizClient client = new QuizClient();
        client.connect("localhost", TEST_PORT);

        // 1. Authenticate student
        User student = client.login("student1", "password123", "STUDENT");
        assertNotNull(student);
        assertEquals("student1", student.getUsername());

        // 2. Fetch available quizzes
        List<Quiz> quizzes = client.getQuizzes();
        assertNotNull(quizzes);
        assertFalse(quizzes.isEmpty());
        Quiz targetQuiz = quizzes.get(0);

        // 3. Start quiz
        QuizClient.QuizSessionData sessionData = client.startQuiz(targetQuiz.getId());
        assertNotNull(sessionData);
        assertNotNull(sessionData.sessionToken);
        assertEquals(5, sessionData.questions.size());

        // Verify correct answer is NOT leaked
        for (Question q : sessionData.questions) {
            assertNull(q.getCorrectOption(), "Server must never leak correct answers to student!");
        }

        // 4. Submit answers (answer option 'A' for all)
        Map<Integer, String> answers = new HashMap<>();
        for (Question q : sessionData.questions) {
            answers.put(q.getId(), "A");
            client.submitAnswer(q.getId(), "A");
        }

        // 5. Submit quiz
        QuizResult result = client.submitQuiz(answers);
        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(5, result.getTotalQuestions());
        assertEquals(5, result.getAttempted());
        assertNotNull(result.getReviews());
        assertEquals(5, result.getReviews().size());

        // 6. Check student history
        List<QuizResult> history = client.getStudentHistory();
        assertNotNull(history);
        assertFalse(history.isEmpty());

        // 7. Check leaderboard
        List<QuizResult> leaderboard = client.getLeaderboard(targetQuiz.getId());
        assertNotNull(leaderboard);
        assertFalse(leaderboard.isEmpty());

        client.disconnect();
    }

    @Test
    public void testAdminFullManagementWorkflow() throws Exception {
        QuizClient client = new QuizClient();
        client.connect("localhost", TEST_PORT);

        // 1. Authenticate Admin
        User admin = client.login("admin", "admin123", "ADMIN");
        assertNotNull(admin);
        assertEquals("ADMIN", admin.getRole());

        // 2. Create New Quiz
        Quiz newQuiz = new Quiz(0, "Cybersecurity Fundamentals", "Network defense", "Security", 15, 0.0, 1.0, true);
        int createdQuizId = client.adminCreateQuiz(newQuiz);
        assertTrue(createdQuizId > 0);

        // 3. Add Question to created quiz
        Question q = new Question(0, createdQuizId, "What does HTTPS use to encrypt traffic?",
                "TLS/SSL", "FTP", "SNMP", "BGP", "A", 10.0, 2.0);
        int qId = client.adminAddQuestion(q);
        assertTrue(qId > 0);

        // 4. Get questions for quiz
        List<Question> questions = client.adminGetQuestions(createdQuizId);
        assertEquals(1, questions.size());
        assertEquals("TLS/SSL", questions.get(0).getOptionA());

        // 5. Check statistics
        Map<String, Object> stats = client.adminGetStats();
        assertNotNull(stats);
        assertTrue(((Number) stats.get("totalQuizzes")).intValue() >= 4);

        client.disconnect();
    }

    @Test
    public void testConcurrentStudentsTakingQuizzesSimultaneously() throws Exception {
        int clientCount = 3;
        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        List<Future<QuizResult>> futures = new ArrayList<>();

        for (int i = 0; i < clientCount; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                QuizClient c = new QuizClient();
                c.connect("localhost", TEST_PORT);
                String username = "student" + (index + 1);
                c.login(username, "password123", "STUDENT");

                QuizClient.QuizSessionData data = c.startQuiz(1);
                Map<Integer, String> ans = new HashMap<>();
                for (Question q : data.questions) {
                    ans.put(q.getId(), "B");
                }
                latch.countDown();
                latch.await(); // Synchronize submission trigger across all 3 concurrent clients

                QuizResult res = c.submitQuiz(ans);
                c.disconnect();
                return res;
            }));
        }

        for (Future<QuizResult> f : futures) {
            QuizResult res = f.get(10, TimeUnit.SECONDS);
            assertNotNull(res);
            assertEquals("COMPLETED", res.getStatus());
        }

        pool.shutdown();
    }

    @Test
    public void testAbruptClientDisconnectionHandledGracefully() throws Exception {
        QuizClient abruptClient = new QuizClient();
        abruptClient.connect("localhost", TEST_PORT);
        abruptClient.login("student1", "password123", "STUDENT");
        abruptClient.startQuiz(2);

        // Abruptly close socket without logging out or submitting
        abruptClient.disconnect();

        // Ensure server continues to accept new clients normally
        QuizClient healthyClient = new QuizClient();
        healthyClient.connect("localhost", TEST_PORT);
        User user = healthyClient.login("admin", "admin123", "ADMIN");
        assertNotNull(user);
        healthyClient.disconnect();
    }
}
