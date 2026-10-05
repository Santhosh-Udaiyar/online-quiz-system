package com.quiz.server;

import com.quiz.common.Question;
import com.quiz.common.Quiz;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active student quiz sessions, timer authoritativeness,
 * question/option randomization, and in-flight student answers.
 */
public class SessionManager {

    private static final long GRACE_PERIOD_SECONDS = 15; // 15s grace for network latency

    /**
     * Represents an active quiz session for a student.
     */
    public static class ActiveSession {
        private final String token;
        private final int userId;
        private final String username;
        private final int quizId;
        private final Quiz quiz;
        private final LocalDateTime startTime;
        private final long durationSeconds;

        // Randomized questions sanitized for student (correct answers stripped)
        private final List<Question> sanitizedQuestions;

        // Maps questionId -> correct option letter ("A", "B", "C", "D") for this session's shuffled options
        private final Map<Integer, String> answerKey;

        // Maps questionId -> max marks
        private final Map<Integer, Double> questionMarks;

        // Maps questionId -> negative marks
        private final Map<Integer, Double> questionNegativeMarks;

        // Maps questionId -> student's chosen option ("A", "B", "C", "D")
        private final Map<Integer, String> studentAnswers;

        private volatile boolean submitted;

        public ActiveSession(String token, int userId, String username, Quiz quiz,
                             List<Question> sanitizedQuestions, Map<Integer, String> answerKey,
                             Map<Integer, Double> questionMarks, Map<Integer, Double> questionNegativeMarks,
                             long durationSeconds) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.quizId = quiz.getId();
            this.quiz = quiz;
            this.startTime = LocalDateTime.now();
            this.durationSeconds = durationSeconds;
            this.sanitizedQuestions = sanitizedQuestions;
            this.answerKey = answerKey;
            this.questionMarks = questionMarks;
            this.questionNegativeMarks = questionNegativeMarks;
            this.studentAnswers = new ConcurrentHashMap<>();
            this.submitted = false;
        }

        public String getToken() {
            return token;
        }

        public int getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public int getQuizId() {
            return quizId;
        }

        public Quiz getQuiz() {
            return quiz;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public long getDurationSeconds() {
            return durationSeconds;
        }

        public List<Question> getSanitizedQuestions() {
            return sanitizedQuestions;
        }

        public Map<Integer, String> getAnswerKey() {
            return answerKey;
        }

        public Map<Integer, Double> getQuestionMarks() {
            return questionMarks;
        }

        public Map<Integer, Double> getQuestionNegativeMarks() {
            return questionNegativeMarks;
        }

        public Map<Integer, String> getStudentAnswers() {
            return studentAnswers;
        }

        public boolean isSubmitted() {
            return submitted;
        }

        public void setSubmitted(boolean submitted) {
            this.submitted = submitted;
        }

        public void recordAnswer(int questionId, String option) {
            if (option == null || option.trim().isEmpty()) {
                studentAnswers.remove(questionId);
            } else {
                studentAnswers.put(questionId, option.trim().toUpperCase());
            }
        }

        /**
         * Checks if the quiz session has expired beyond the duration + grace period.
         */
        public boolean isExpired() {
            long elapsedSeconds = Duration.between(startTime, LocalDateTime.now()).getSeconds();
            return elapsedSeconds > (durationSeconds + GRACE_PERIOD_SECONDS);
        }

        public long getRemainingSeconds() {
            long elapsedSeconds = Duration.between(startTime, LocalDateTime.now()).getSeconds();
            long remaining = durationSeconds - elapsedSeconds;
            return Math.max(0, remaining);
        }
    }

    // Maps sessionToken -> ActiveSession
    private final Map<String, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    // Maps userId -> sessionToken for quick lookup
    private final Map<Integer, String> userActiveSessionTokens = new ConcurrentHashMap<>();

    /**
     * Starts a new quiz session for a student. Randomizes questions and options.
     */
    public ActiveSession startSession(int userId, String username, Quiz quiz, List<Question> originalQuestions) {
        // End any existing dangling session for this user
        String existingToken = userActiveSessionTokens.get(userId);
        if (existingToken != null) {
            activeSessions.remove(existingToken);
            userActiveSessionTokens.remove(userId);
        }

        String sessionToken = UUID.randomUUID().toString();
        long durationSecs = quiz.getDurationMinutes() * 60L;

        // 1. Randomize question order
        List<Question> questionsShuffled = new ArrayList<>(originalQuestions);
        Collections.shuffle(questionsShuffled);

        List<Question> sanitizedList = new ArrayList<>();
        Map<Integer, String> answerKey = new HashMap<>();
        Map<Integer, Double> marksMap = new HashMap<>();
        Map<Integer, Double> negMarksMap = new HashMap<>();

        // 2. Randomize option order for each question while maintaining correct answer mapping
        for (Question q : questionsShuffled) {
            marksMap.put(q.getId(), q.getMarks());
            negMarksMap.put(q.getId(), q.getNegativeMarks());

            // Identify the correct text
            String correctLetter = q.getCorrectOption() != null ? q.getCorrectOption().trim().toUpperCase() : "A";
            String correctText = q.getOptionText(correctLetter);

            // Create list of 4 options
            List<String> optionTexts = new ArrayList<>(Arrays.asList(
                    q.getOptionA(),
                    q.getOptionB(),
                    q.getOptionC(),
                    q.getOptionD()
            ));
            Collections.shuffle(optionTexts);

            // Shuffled options A, B, C, D
            String newA = optionTexts.get(0);
            String newB = optionTexts.get(1);
            String newC = optionTexts.get(2);
            String newD = optionTexts.get(3);

            // Determine which new letter corresponds to correctText
            String newCorrectLetter = "A";
            if (newA.equals(correctText)) {
                newCorrectLetter = "A";
            } else if (newB.equals(correctText)) {
                newCorrectLetter = "B";
            } else if (newC.equals(correctText)) {
                newCorrectLetter = "C";
            } else if (newD.equals(correctText)) {
                newCorrectLetter = "D";
            }

            answerKey.put(q.getId(), newCorrectLetter);

            // Create sanitized question for student (no correct option!)
            Question sanitized = new Question(
                    q.getId(),
                    q.getQuizId(),
                    q.getQuestionText(),
                    newA,
                    newB,
                    newC,
                    newD,
                    null, // Hidden from student!
                    q.getMarks(),
                    q.getNegativeMarks()
            );
            sanitizedList.add(sanitized);
        }

        ActiveSession session = new ActiveSession(
                sessionToken,
                userId,
                username,
                quiz,
                sanitizedList,
                answerKey,
                marksMap,
                negMarksMap,
                durationSecs
        );

        activeSessions.put(sessionToken, session);
        userActiveSessionTokens.put(userId, sessionToken);
        return session;
    }

    public ActiveSession getSession(String token) {
        if (token == null) return null;
        return activeSessions.get(token);
    }

    public ActiveSession getSessionByUserId(int userId) {
        String token = userActiveSessionTokens.get(userId);
        if (token != null) {
            return activeSessions.get(token);
        }
        return null;
    }

    public void removeSession(String token) {
        if (token != null) {
            ActiveSession s = activeSessions.remove(token);
            if (s != null) {
                userActiveSessionTokens.remove(s.getUserId());
            }
        }
    }
}
