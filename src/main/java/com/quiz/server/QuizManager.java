package com.quiz.server;

import com.quiz.common.Question;
import com.quiz.common.Quiz;
import com.quiz.common.QuizResult;
import com.quiz.common.User;
import com.quiz.database.QuizRepository;
import com.quiz.database.ResultRepository;
import com.quiz.database.UserRepository;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Core business logic coordinator managing quizzes, scoring, sessions,
 * and database transactions.
 */
public class QuizManager {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final ResultRepository resultRepository;
    private final SessionManager sessionManager;

    public QuizManager(UserRepository userRepository, QuizRepository quizRepository,
                       ResultRepository resultRepository, SessionManager sessionManager) {
        this.userRepository = userRepository;
        this.quizRepository = quizRepository;
        this.resultRepository = resultRepository;
        this.sessionManager = sessionManager;
    }

    // ==========================================
    // Authentication
    // ==========================================

    public User authenticate(String username, String password, String role) {
        User user = userRepository.authenticate(username, password, role);
        if (user != null) {
            ServerLogger.auth("Login SUCCESS for user: '" + username + "' (Role: " + role + ")");
        } else {
            ServerLogger.auth("Login FAILED for user: '" + username + "' (Role: " + role + ")");
        }
        return user;
    }

    // ==========================================
    // Quizzes & Browsing
    // ==========================================

    public List<Quiz> getQuizzesForStudent(int studentId) {
        return quizRepository.getActiveQuizzesForStudent(studentId);
    }

    public List<Quiz> getAllQuizzesForAdmin() {
        return quizRepository.getAllQuizzes();
    }

    public Quiz getQuizById(int quizId) {
        return quizRepository.getQuizById(quizId);
    }

    // ==========================================
    // Quiz Session & Evaluation
    // ==========================================

    /**
     * Starts a quiz session for a student.
     */
    public SessionManager.ActiveSession startQuiz(int userId, String username, int quizId) {
        Quiz quiz = quizRepository.getQuizById(quizId);
        if (quiz == null) {
            throw new IllegalArgumentException("Quiz ID " + quizId + " not found.");
        }
        if (!quiz.isActive()) {
            throw new IllegalStateException("Quiz '" + quiz.getTitle() + "' is currently inactive.");
        }

        List<Question> questions = quizRepository.getQuestionsForQuiz(quizId);
        if (questions.isEmpty()) {
            throw new IllegalStateException("Quiz '" + quiz.getTitle() + "' has no questions available.");
        }

        SessionManager.ActiveSession session = sessionManager.startSession(userId, username, quiz, questions);
        ServerLogger.quiz("Quiz START: User '" + username + "' (ID: " + userId + ") started quiz '" +
                quiz.getTitle() + "' (ID: " + quizId + ") with " + questions.size() + " questions.");
        return session;
    }

    /**
     * Temporarily records an answer choice for a question during an active session.
     */
    public boolean recordAnswer(String sessionToken, int questionId, String option) {
        SessionManager.ActiveSession session = sessionManager.getSession(sessionToken);
        if (session == null || session.isSubmitted()) {
            return false;
        }
        session.recordAnswer(questionId, option);
        return true;
    }

    /**
     * Authoritatively evaluates and finalizes a student's quiz session.
     */
    public QuizResult submitQuiz(String sessionToken, Map<Integer, String> finalAnswers) throws SQLException {
        SessionManager.ActiveSession session = sessionManager.getSession(sessionToken);
        if (session == null) {
            throw new IllegalStateException("Invalid or expired quiz session.");
        }

        synchronized (session) {
            if (session.isSubmitted()) {
                throw new IllegalStateException("This quiz session has already been submitted.");
            }
            session.setSubmitted(true);
        }

        // Incorporate any answers submitted in final batch
        if (finalAnswers != null) {
            for (Map.Entry<Integer, String> entry : finalAnswers.entrySet()) {
                session.recordAnswer(entry.getKey(), entry.getValue());
            }
        }

        LocalDateTime endTime = LocalDateTime.now();
        boolean timedOut = session.isExpired();
        String status = timedOut ? "TIMED_OUT" : "COMPLETED";

        // Score calculation:
        // score = correct marks - negative marks
        double totalScore = 0.0;
        double maxPossibleMarks = 0.0;
        int attempted = 0;
        int correctCount = 0;
        int incorrectCount = 0;
        int unansweredCount = 0;

        List<QuizResult.QuestionReview> reviews = new ArrayList<>();

        for (Question q : session.getSanitizedQuestions()) {
            int qId = q.getId();
            double qMarks = session.getQuestionMarks().getOrDefault(qId, q.getMarks());
            double qNegMarks = session.getQuestionNegativeMarks().getOrDefault(qId, q.getNegativeMarks());
            maxPossibleMarks += qMarks;

            String studentAns = session.getStudentAnswers().get(qId);
            String correctAns = session.getAnswerKey().get(qId);

            boolean isCorrect = false;
            double marksAwarded = 0.0;

            if (studentAns == null || studentAns.trim().isEmpty()) {
                unansweredCount++;
                marksAwarded = 0.0;
            } else {
                attempted++;
                if (studentAns.equalsIgnoreCase(correctAns)) {
                    correctCount++;
                    isCorrect = true;
                    marksAwarded = qMarks;
                } else {
                    incorrectCount++;
                    isCorrect = false;
                    marksAwarded = -qNegMarks;
                }
            }

            totalScore += marksAwarded;

            // Build review item
            QuizResult.QuestionReview review = new QuizResult.QuestionReview(
                    qId,
                    q.getQuestionText(),
                    q.getOptionA(),
                    q.getOptionB(),
                    q.getOptionC(),
                    q.getOptionD(),
                    studentAns != null ? studentAns : "None",
                    correctAns,
                    isCorrect,
                    marksAwarded,
                    qMarks
            );
            reviews.add(review);
        }

        // Clamp minimum score display or calculate percentage
        double rawPercentage = maxPossibleMarks > 0 ? (totalScore / maxPossibleMarks) * 100.0 : 0.0;
        double percentage = Math.max(0.0, Math.round(rawPercentage * 100.0) / 100.0);
        double roundedScore = Math.round(totalScore * 100.0) / 100.0;

        long timeTakenSeconds = Duration.between(session.getStartTime(), endTime).getSeconds();

        QuizResult result = new QuizResult();
        result.setUserId(session.getUserId());
        result.setUsername(session.getUsername());
        result.setQuizId(session.getQuizId());
        result.setQuizTitle(session.getQuiz().getTitle());
        result.setScore(roundedScore);
        result.setMaxScore(maxPossibleMarks);
        result.setPercentage(percentage);
        result.setTotalQuestions(session.getSanitizedQuestions().size());
        result.setAttempted(attempted);
        result.setCorrect(correctCount);
        result.setIncorrect(incorrectCount);
        result.setUnanswered(unansweredCount);
        result.setStartTime(session.getStartTime().format(DATE_FORMATTER));
        result.setEndTime(endTime.format(DATE_FORMATTER));
        result.setTimeTakenSeconds(timeTakenSeconds);
        result.setStatus(status);
        result.setReviews(reviews);

        // Atomically persist in SQLite database
        resultRepository.recordAttempt(result);

        // Remove active session
        sessionManager.removeSession(sessionToken);

        ServerLogger.quiz(String.format(
                "Quiz SUBMIT: User '%s' completed Quiz '%s'. Score: %.2f/%.2f (%.2f%%), Correct: %d, Incorrect: %d, Unanswered: %d, Time: %ds, Status: %s",
                session.getUsername(), session.getQuiz().getTitle(), roundedScore, maxPossibleMarks,
                percentage, correctCount, incorrectCount, unansweredCount, timeTakenSeconds, status
        ));

        return result;
    }

    // ==========================================
    // History & Leaderboard
    // ==========================================

    public List<QuizResult> getStudentHistory(int studentId) {
        return resultRepository.getAttemptsByStudent(studentId);
    }

    public QuizResult getAttemptDetails(int attemptId) {
        return resultRepository.getAttemptDetails(attemptId);
    }

    public List<QuizResult> getLeaderboard(int quizId) {
        return resultRepository.getLeaderboard(quizId);
    }

    public List<QuizResult> getAllAttempts() {
        return resultRepository.getAllAttempts();
    }

    public Map<String, Object> getDatabaseStats() {
        return resultRepository.getDatabaseStats();
    }

    // ==========================================
    // Admin Quiz & Question Operations
    // ==========================================

    public int createQuiz(Quiz quiz) throws SQLException {
        int id = quizRepository.createQuiz(quiz);
        ServerLogger.admin("Admin created new Quiz: '" + quiz.getTitle() + "' (ID: " + id + ")");
        return id;
    }

    public boolean updateQuiz(Quiz quiz) {
        boolean ok = quizRepository.updateQuiz(quiz);
        if (ok) {
            ServerLogger.admin("Admin updated Quiz ID: " + quiz.getId() + " ('" + quiz.getTitle() + "')");
        }
        return ok;
    }

    public boolean deleteQuiz(int quizId) {
        boolean ok = quizRepository.deleteQuiz(quizId);
        if (ok) {
            ServerLogger.admin("Admin deleted Quiz ID: " + quizId);
        }
        return ok;
    }

    public List<Question> getQuestionsForQuiz(int quizId) {
        return quizRepository.getQuestionsForQuiz(quizId);
    }

    public int addQuestion(Question question) throws SQLException {
        int id = quizRepository.addQuestion(question);
        ServerLogger.admin("Admin added Question ID: " + id + " to Quiz ID: " + question.getQuizId());
        return id;
    }

    public boolean updateQuestion(Question question) {
        boolean ok = quizRepository.updateQuestion(question);
        if (ok) {
            ServerLogger.admin("Admin updated Question ID: " + question.getId());
        }
        return ok;
    }

    public boolean deleteQuestion(int questionId) {
        boolean ok = quizRepository.deleteQuestion(questionId);
        if (ok) {
            ServerLogger.admin("Admin deleted Question ID: " + questionId);
        }
        return ok;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }
}
