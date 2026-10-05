package com.quiz.server;

import com.google.gson.reflect.TypeToken;
import com.quiz.common.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.*;

/**
 * Handles communication with an individual client over a raw TCP socket connection.
 * Dispatches protocol messages and maintains client session context.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final QuizManager quizManager;
    private final String clientAddress;

    private BufferedReader reader;
    private PrintWriter writer;
    private User currentUser;
    private String activeSessionToken;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, QuizManager quizManager) {
        this.socket = socket;
        this.quizManager = quizManager;
        this.clientAddress = socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        ServerLogger.info("Client connected from: " + clientAddress);
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                handleMessage(line.trim());
            }
        } catch (SocketException e) {
            ServerLogger.info("Client socket closed: " + clientAddress);
        } catch (Exception e) {
            ServerLogger.error("Client communication error (" + clientAddress + "): " + e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    private void handleMessage(String line) {
        try {
            Protocol.Message req = Protocol.deserialize(line);
            if (req == null || req.getType() == null) {
                sendError("Malformed or unrecognized message.");
                return;
            }

            switch (req.getType()) {
                case LOGIN -> handleLogin(req);
                case LOGOUT -> handleLogout();
                case GET_QUIZZES -> handleGetQuizzes();
                case START_QUIZ -> handleStartQuiz(req);
                case SUBMIT_ANSWER -> handleSubmitAnswer(req);
                case SUBMIT_QUIZ -> handleSubmitQuiz(req);
                case GET_LEADERBOARD -> handleGetLeaderboard(req);
                case GET_STUDENT_HISTORY -> handleGetStudentHistory();
                case GET_RESULT_DETAILS -> handleGetResultDetails(req);

                // Admin Operations
                case ADMIN_CREATE_QUIZ -> handleAdminCreateQuiz(req);
                case ADMIN_UPDATE_QUIZ -> handleAdminUpdateQuiz(req);
                case ADMIN_DELETE_QUIZ -> handleAdminDeleteQuiz(req);
                case ADMIN_GET_QUESTIONS -> handleAdminGetQuestions(req);
                case ADMIN_ADD_QUESTION -> handleAdminAddQuestion(req);
                case ADMIN_UPDATE_QUESTION -> handleAdminUpdateQuestion(req);
                case ADMIN_DELETE_QUESTION -> handleAdminDeleteQuestion(req);
                case ADMIN_GET_STATS -> handleAdminGetStats();
                case ADMIN_GET_ALL_RESULTS -> handleAdminGetAllResults();

                default -> sendError("Unhandled request type: " + req.getType());
            }
        } catch (Exception e) {
            ServerLogger.error("Error processing request from " + clientAddress + ": " + e.getMessage(), e);
            sendError("Server error processing request: " + e.getMessage());
        }
    }

    // ==========================================
    // Handlers
    // ==========================================

    private void handleLogin(Protocol.Message req) {
        Map<String, String> credentials = Protocol.fromJson(req.getPayload(), new TypeToken<Map<String, String>>() {}.getType());
        if (credentials == null) {
            send(Protocol.createMessage(MessageType.LOGIN_FAILED, false, "Invalid login credentials format.", null));
            return;
        }

        String username = credentials.get("username");
        String password = credentials.get("password");
        String role = credentials.get("role");

        User user = quizManager.authenticate(username, password, role);
        if (user != null) {
            this.currentUser = user;
            Protocol.Message resp = new Protocol.Message(
                    MessageType.LOGIN_SUCCESS,
                    true,
                    "Login successful. Welcome " + user.getUsername() + "!",
                    UUID.randomUUID().toString(),
                    Protocol.toJson(user)
            );
            send(resp);
        } else {
            send(Protocol.createMessage(MessageType.LOGIN_FAILED, false, "Invalid username, password, or role.", null));
        }
    }

    private void handleLogout() {
        if (currentUser != null) {
            ServerLogger.auth("User logged out: " + currentUser.getUsername());
            if (activeSessionToken != null) {
                quizManager.getSessionManager().removeSession(activeSessionToken);
                activeSessionToken = null;
            }
            currentUser = null;
        }
        send(Protocol.createSuccess("Logged out successfully."));
    }

    private void handleGetQuizzes() {
        if (!ensureAuthenticated()) return;

        List<Quiz> quizzes;
        if ("ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            quizzes = quizManager.getAllQuizzesForAdmin();
        } else {
            quizzes = quizManager.getQuizzesForStudent(currentUser.getId());
        }

        Protocol.Message resp = new Protocol.Message(
                MessageType.QUIZ_LIST,
                true,
                "Quizzes retrieved",
                null,
                Protocol.toJson(quizzes)
        );
        send(resp);
    }

    private void handleStartQuiz(Protocol.Message req) {
        if (!ensureAuthenticated()) return;

        int quizId;
        try {
            quizId = Integer.parseInt(req.getPayload().trim());
        } catch (Exception e) {
            sendError("Invalid quiz ID.");
            return;
        }

        try {
            SessionManager.ActiveSession session = quizManager.startQuiz(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    quizId
            );
            this.activeSessionToken = session.getToken();

            Map<String, Object> quizData = new HashMap<>();
            quizData.put("quiz", session.getQuiz());
            quizData.put("questions", session.getSanitizedQuestions());
            quizData.put("durationSeconds", session.getDurationSeconds());
            quizData.put("sessionToken", session.getToken());

            Protocol.Message resp = new Protocol.Message(
                    MessageType.QUIZ_DATA,
                    true,
                    "Quiz session started",
                    session.getToken(),
                    Protocol.toJson(quizData)
            );
            send(resp);
        } catch (Exception e) {
            sendError(e.getMessage());
        }
    }

    private void handleSubmitAnswer(Protocol.Message req) {
        if (!ensureAuthenticated()) return;

        Map<String, Object> data = Protocol.fromJson(req.getPayload(), new TypeToken<Map<String, Object>>() {}.getType());
        if (data == null) return;

        int questionId = ((Number) data.get("questionId")).intValue();
        String selectedOption = (String) data.get("selectedOption");
        String token = req.getToken() != null ? req.getToken() : activeSessionToken;

        boolean recorded = quizManager.recordAnswer(token, questionId, selectedOption);
        if (recorded) {
            send(Protocol.createSuccess("Answer recorded."));
        } else {
            sendError("Unable to record answer: session invalid or inactive.");
        }
    }

    private void handleSubmitQuiz(Protocol.Message req) {
        if (!ensureAuthenticated()) return;

        String token = req.getToken() != null ? req.getToken() : activeSessionToken;
        if (token == null) {
            sendError("No active quiz session found to submit.");
            return;
        }

        Map<Integer, String> finalAnswers = null;
        if (req.getPayload() != null && !req.getPayload().trim().isEmpty()) {
            finalAnswers = Protocol.fromJson(req.getPayload(), new TypeToken<Map<Integer, String>>() {}.getType());
        }

        try {
            QuizResult result = quizManager.submitQuiz(token, finalAnswers);
            this.activeSessionToken = null;

            Protocol.Message resp = new Protocol.Message(
                    MessageType.QUIZ_RESULT,
                    true,
                    "Quiz submitted successfully",
                    null,
                    Protocol.toJson(result)
            );
            send(resp);
        } catch (Exception e) {
            ServerLogger.error("Quiz submission error: " + e.getMessage(), e);
            sendError("Error submitting quiz: " + e.getMessage());
        }
    }

    private void handleGetLeaderboard(Protocol.Message req) {
        if (!ensureAuthenticated()) return;

        int quizId = 0;
        if (req.getPayload() != null && !req.getPayload().trim().isEmpty()) {
            try {
                quizId = Integer.parseInt(req.getPayload().trim());
            } catch (Exception ignored) {
            }
        }

        List<QuizResult> list = quizManager.getLeaderboard(quizId);
        Protocol.Message resp = new Protocol.Message(
                MessageType.LEADERBOARD,
                true,
                "Leaderboard retrieved",
                null,
                Protocol.toJson(list)
        );
        send(resp);
    }

    private void handleGetStudentHistory() {
        if (!ensureAuthenticated()) return;

        List<QuizResult> history = quizManager.getStudentHistory(currentUser.getId());
        Protocol.Message resp = new Protocol.Message(
                MessageType.STUDENT_HISTORY_DATA,
                true,
                "History retrieved",
                null,
                Protocol.toJson(history)
        );
        send(resp);
    }

    private void handleGetResultDetails(Protocol.Message req) {
        if (!ensureAuthenticated()) return;

        try {
            int attemptId = Integer.parseInt(req.getPayload().trim());
            QuizResult result = quizManager.getAttemptDetails(attemptId);
            if (result == null) {
                sendError("Attempt not found.");
                return;
            }

            // Security check: non-admins can only see their own attempt details
            if (!"ADMIN".equalsIgnoreCase(currentUser.getRole()) && result.getUserId() != currentUser.getId()) {
                sendError("Unauthorized to view this attempt result.");
                return;
            }

            Protocol.Message resp = new Protocol.Message(
                    MessageType.RESULT_DETAILS_DATA,
                    true,
                    "Result details retrieved",
                    null,
                    Protocol.toJson(result)
            );
            send(resp);
        } catch (Exception e) {
            sendError("Invalid attempt ID.");
        }
    }

    // ==========================================
    // Admin Operations
    // ==========================================

    private void handleAdminCreateQuiz(Protocol.Message req) throws SQLException {
        if (!ensureAdmin()) return;

        Quiz quiz = Protocol.fromJson(req.getPayload(), Quiz.class);
        if (quiz == null) {
            sendError("Invalid quiz data.");
            return;
        }

        int id = quizManager.createQuiz(quiz);
        send(Protocol.createMessage(MessageType.SUCCESS, true, "Quiz created successfully.", String.valueOf(id)));
    }

    private void handleAdminUpdateQuiz(Protocol.Message req) {
        if (!ensureAdmin()) return;

        Quiz quiz = Protocol.fromJson(req.getPayload(), Quiz.class);
        if (quiz == null) {
            sendError("Invalid quiz data.");
            return;
        }

        boolean ok = quizManager.updateQuiz(quiz);
        if (ok) {
            send(Protocol.createSuccess("Quiz updated successfully."));
        } else {
            sendError("Failed to update quiz.");
        }
    }

    private void handleAdminDeleteQuiz(Protocol.Message req) {
        if (!ensureAdmin()) return;

        try {
            int quizId = Integer.parseInt(req.getPayload().trim());
            boolean ok = quizManager.deleteQuiz(quizId);
            if (ok) {
                send(Protocol.createSuccess("Quiz deleted successfully."));
            } else {
                sendError("Failed to delete quiz.");
            }
        } catch (Exception e) {
            sendError("Invalid quiz ID.");
        }
    }

    private void handleAdminGetQuestions(Protocol.Message req) {
        if (!ensureAdmin()) return;

        try {
            int quizId = Integer.parseInt(req.getPayload().trim());
            List<Question> questions = quizManager.getQuestionsForQuiz(quizId);
            send(Protocol.createMessage(MessageType.SUCCESS, true, "Questions retrieved", Protocol.toJson(questions)));
        } catch (Exception e) {
            sendError("Invalid quiz ID.");
        }
    }

    private void handleAdminAddQuestion(Protocol.Message req) throws SQLException {
        if (!ensureAdmin()) return;

        Question q = Protocol.fromJson(req.getPayload(), Question.class);
        if (q == null) {
            sendError("Invalid question data.");
            return;
        }

        int id = quizManager.addQuestion(q);
        send(Protocol.createMessage(MessageType.SUCCESS, true, "Question added successfully.", String.valueOf(id)));
    }

    private void handleAdminUpdateQuestion(Protocol.Message req) {
        if (!ensureAdmin()) return;

        Question q = Protocol.fromJson(req.getPayload(), Question.class);
        if (q == null) {
            sendError("Invalid question data.");
            return;
        }

        boolean ok = quizManager.updateQuestion(q);
        if (ok) {
            send(Protocol.createSuccess("Question updated successfully."));
        } else {
            sendError("Failed to update question.");
        }
    }

    private void handleAdminDeleteQuestion(Protocol.Message req) {
        if (!ensureAdmin()) return;

        try {
            int qId = Integer.parseInt(req.getPayload().trim());
            boolean ok = quizManager.deleteQuestion(qId);
            if (ok) {
                send(Protocol.createSuccess("Question deleted successfully."));
            } else {
                sendError("Failed to delete question.");
            }
        } catch (Exception e) {
            sendError("Invalid question ID.");
        }
    }

    private void handleAdminGetStats() {
        if (!ensureAdmin()) return;

        Map<String, Object> stats = quizManager.getDatabaseStats();
        send(Protocol.createMessage(MessageType.SUCCESS, true, "Statistics retrieved", Protocol.toJson(stats)));
    }

    private void handleAdminGetAllResults() {
        if (!ensureAdmin()) return;

        List<QuizResult> list = quizManager.getAllAttempts();
        send(Protocol.createMessage(MessageType.SUCCESS, true, "All results retrieved", Protocol.toJson(list)));
    }

    // ==========================================
    // Helpers
    // ==========================================

    private boolean ensureAuthenticated() {
        if (currentUser == null) {
            sendError("Authentication required. Please log in.");
            return false;
        }
        return true;
    }

    private boolean ensureAdmin() {
        if (!ensureAuthenticated()) return false;
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            sendError("Access denied. Admin privileges required.");
            return false;
        }
        return true;
    }

    public synchronized void send(Protocol.Message msg) {
        if (writer != null) {
            String json = Protocol.serialize(msg);
            writer.println(json);
        }
    }

    private void sendError(String errorMsg) {
        send(Protocol.createError(errorMsg));
    }

    public void cleanup() {
        running = false;
        ServerLogger.info("Client disconnected: " + clientAddress +
                (currentUser != null ? " (User: " + currentUser.getUsername() + ")" : ""));
        if (activeSessionToken != null) {
            quizManager.getSessionManager().removeSession(activeSessionToken);
            activeSessionToken = null;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
