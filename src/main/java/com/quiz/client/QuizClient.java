package com.quiz.client;

import com.google.gson.reflect.TypeToken;
import com.quiz.common.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;

/**
 * Client-side networking manager communicating with the QuizServer over raw TCP sockets.
 * Serializes requests into protocol messages and parses server responses.
 */
public class QuizClient {

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private User currentUser;
    private String sessionToken;

    private String host;
    private int port;

    public QuizClient() {
    }

    public QuizClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public synchronized void connect(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), 5000);
        this.socket.setSoTimeout(10000); // 10 second timeout

        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void disconnect() {
        try {
            if (currentUser != null && isConnected()) {
                try {
                    sendRequest(new Protocol.Message(MessageType.LOGOUT, true, null, null, null));
                } catch (Exception ignored) {
                }
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            currentUser = null;
            sessionToken = null;
            socket = null;
            reader = null;
            writer = null;
        }
    }

    /**
     * Sends a protocol message and synchronously waits for the server's single-line response.
     */
    public synchronized Protocol.Message sendRequest(Protocol.Message req) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to Quiz Server. Please check your network connection.");
        }

        if (sessionToken != null && req.getToken() == null) {
            req.setToken(sessionToken);
        }

        String json = Protocol.serialize(req);
        writer.println(json);

        String responseLine = reader.readLine();
        if (responseLine == null) {
            throw new IOException("Server closed the connection unexpectedly.");
        }

        Protocol.Message resp = Protocol.deserialize(responseLine);
        if (resp == null) {
            throw new IOException("Received unparseable response from server.");
        }

        return resp;
    }

    // ==========================================
    // Student & Common Operations
    // ==========================================

    public User login(String username, String password, String role) throws IOException {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);
        credentials.put("role", role);

        Protocol.Message req = new Protocol.Message(
                MessageType.LOGIN,
                true,
                null,
                null,
                Protocol.toJson(credentials)
        );

        Protocol.Message resp = sendRequest(req);
        if (resp.getType() == MessageType.LOGIN_SUCCESS && resp.isSuccess()) {
            this.currentUser = Protocol.fromJson(resp.getPayload(), User.class);
            return this.currentUser;
        } else {
            throw new IOException(resp.getMessage() != null ? resp.getMessage() : "Authentication failed.");
        }
    }

    public List<Quiz> getQuizzes() throws IOException {
        Protocol.Message req = new Protocol.Message(MessageType.GET_QUIZZES, true, null, null, null);
        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess() && resp.getPayload() != null) {
            return Protocol.fromJson(resp.getPayload(), new TypeToken<List<Quiz>>() {}.getType());
        } else {
            throw new IOException(resp.getMessage() != null ? resp.getMessage() : "Failed to fetch quizzes.");
        }
    }

    public static class QuizSessionData {
        public Quiz quiz;
        public List<Question> questions;
        public long durationSeconds;
        public String sessionToken;
    }

    public QuizSessionData startQuiz(int quizId) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.START_QUIZ,
                true,
                null,
                null,
                String.valueOf(quizId)
        );

        Protocol.Message resp = sendRequest(req);
        if (resp.getType() == MessageType.QUIZ_DATA && resp.isSuccess()) {
            this.sessionToken = resp.getToken();
            return Protocol.fromJson(resp.getPayload(), QuizSessionData.class);
        } else {
            throw new IOException(resp.getMessage() != null ? resp.getMessage() : "Failed to start quiz.");
        }
    }

    public void submitAnswer(int questionId, String selectedOption) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("questionId", questionId);
            data.put("selectedOption", selectedOption);

            Protocol.Message req = new Protocol.Message(
                    MessageType.SUBMIT_ANSWER,
                    true,
                    null,
                    sessionToken,
                    Protocol.toJson(data)
            );
            // Send asynchronously or synchronously
            sendRequest(req);
        } catch (Exception e) {
            System.err.println("Could not sync answer choice: " + e.getMessage());
        }
    }

    public QuizResult submitQuiz(Map<Integer, String> finalAnswers) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.SUBMIT_QUIZ,
                true,
                null,
                sessionToken,
                Protocol.toJson(finalAnswers)
        );

        Protocol.Message resp = sendRequest(req);
        this.sessionToken = null;

        if (resp.getType() == MessageType.QUIZ_RESULT && resp.isSuccess()) {
            return Protocol.fromJson(resp.getPayload(), QuizResult.class);
        } else {
            throw new IOException(resp.getMessage() != null ? resp.getMessage() : "Quiz submission failed.");
        }
    }

    public List<QuizResult> getLeaderboard(int quizId) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.GET_LEADERBOARD,
                true,
                null,
                null,
                String.valueOf(quizId)
        );

        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess() && resp.getPayload() != null) {
            return Protocol.fromJson(resp.getPayload(), new TypeToken<List<QuizResult>>() {}.getType());
        } else {
            throw new IOException(resp.getMessage() != null ? resp.getMessage() : "Failed to fetch leaderboard.");
        }
    }

    public List<QuizResult> getStudentHistory() throws IOException {
        Protocol.Message req = new Protocol.Message(MessageType.GET_STUDENT_HISTORY, true, null, null, null);
        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess() && resp.getPayload() != null) {
            return Protocol.fromJson(resp.getPayload(), new TypeToken<List<QuizResult>>() {}.getType());
        } else {
            throw new IOException(resp.getMessage() != null ? resp.getMessage() : "Failed to fetch history.");
        }
    }

    public QuizResult getResultDetails(int attemptId) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.GET_RESULT_DETAILS,
                true,
                null,
                null,
                String.valueOf(attemptId)
        );

        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess() && resp.getPayload() != null) {
            return Protocol.fromJson(resp.getPayload(), QuizResult.class);
        } else {
            throw new IOException(resp.getMessage() != null ? resp.getMessage() : "Failed to fetch result details.");
        }
    }

    // ==========================================
    // Admin Operations
    // ==========================================

    public int adminCreateQuiz(Quiz quiz) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.ADMIN_CREATE_QUIZ,
                true,
                null,
                null,
                Protocol.toJson(quiz)
        );
        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess()) {
            return Integer.parseInt(resp.getMessage() != null && resp.getPayload() != null ? resp.getPayload() : "-1");
        } else {
            throw new IOException(resp.getMessage());
        }
    }

    public void adminUpdateQuiz(Quiz quiz) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.ADMIN_UPDATE_QUIZ,
                true,
                null,
                null,
                Protocol.toJson(quiz)
        );
        Protocol.Message resp = sendRequest(req);
        if (!resp.isSuccess()) {
            throw new IOException(resp.getMessage());
        }
    }

    public void adminDeleteQuiz(int quizId) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.ADMIN_DELETE_QUIZ,
                true,
                null,
                null,
                String.valueOf(quizId)
        );
        Protocol.Message resp = sendRequest(req);
        if (!resp.isSuccess()) {
            throw new IOException(resp.getMessage());
        }
    }

    public List<Question> adminGetQuestions(int quizId) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.ADMIN_GET_QUESTIONS,
                true,
                null,
                null,
                String.valueOf(quizId)
        );
        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess() && resp.getPayload() != null) {
            return Protocol.fromJson(resp.getPayload(), new TypeToken<List<Question>>() {}.getType());
        } else {
            throw new IOException(resp.getMessage());
        }
    }

    public int adminAddQuestion(Question question) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.ADMIN_ADD_QUESTION,
                true,
                null,
                null,
                Protocol.toJson(question)
        );
        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess()) {
            return Integer.parseInt(resp.getPayload() != null ? resp.getPayload() : "-1");
        } else {
            throw new IOException(resp.getMessage());
        }
    }

    public void adminUpdateQuestion(Question question) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.ADMIN_UPDATE_QUESTION,
                true,
                null,
                null,
                Protocol.toJson(question)
        );
        Protocol.Message resp = sendRequest(req);
        if (!resp.isSuccess()) {
            throw new IOException(resp.getMessage());
        }
    }

    public void adminDeleteQuestion(int questionId) throws IOException {
        Protocol.Message req = new Protocol.Message(
                MessageType.ADMIN_DELETE_QUESTION,
                true,
                null,
                null,
                String.valueOf(questionId)
        );
        Protocol.Message resp = sendRequest(req);
        if (!resp.isSuccess()) {
            throw new IOException(resp.getMessage());
        }
    }

    public Map<String, Object> adminGetStats() throws IOException {
        Protocol.Message req = new Protocol.Message(MessageType.ADMIN_GET_STATS, true, null, null, null);
        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess() && resp.getPayload() != null) {
            return Protocol.fromJson(resp.getPayload(), new TypeToken<Map<String, Object>>() {}.getType());
        } else {
            throw new IOException(resp.getMessage());
        }
    }

    public List<QuizResult> adminGetAllResults() throws IOException {
        Protocol.Message req = new Protocol.Message(MessageType.ADMIN_GET_ALL_RESULTS, true, null, null, null);
        Protocol.Message resp = sendRequest(req);
        if (resp.isSuccess() && resp.getPayload() != null) {
            return Protocol.fromJson(resp.getPayload(), new TypeToken<List<QuizResult>>() {}.getType());
        } else {
            throw new IOException(resp.getMessage());
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
