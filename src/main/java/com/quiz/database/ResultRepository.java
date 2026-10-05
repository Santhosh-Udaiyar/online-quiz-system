package com.quiz.database;

import com.quiz.common.QuizResult;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository handling quiz attempts, student answer breakdowns, and leaderboard queries.
 */
public class ResultRepository {
    private final Database database;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ResultRepository(Database database) {
        this.database = database;
    }

    /**
     * Saves a quiz attempt and its individual question answers inside an atomic transaction.
     */
    public int recordAttempt(QuizResult result) throws SQLException {
        String insertAttemptSql = """
            INSERT INTO quiz_attempts (user_id, quiz_id, score, percentage, start_time, end_time, status)
            VALUES (?, ?, ?, ?, ?, ?, ?);
        """;

        String insertAnswerSql = """
            INSERT INTO answers (attempt_id, question_id, selected_option, correct, marks_awarded)
            VALUES (?, ?, ?, ?, ?);
        """;

        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int attemptId;
                try (PreparedStatement ps = conn.prepareStatement(insertAttemptSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, result.getUserId());
                    ps.setInt(2, result.getQuizId());
                    ps.setDouble(3, result.getScore());
                    ps.setDouble(4, result.getPercentage());
                    ps.setString(5, result.getStartTime());
                    ps.setString(6, result.getEndTime());
                    ps.setString(7, result.getStatus());
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            attemptId = rs.getInt(1);
                            result.setAttemptId(attemptId);
                        } else {
                            throw new SQLException("Failed to obtain generated attempt ID.");
                        }
                    }
                }

                // Insert answer breakdown
                if (result.getReviews() != null && !result.getReviews().isEmpty()) {
                    try (PreparedStatement psAnswer = conn.prepareStatement(insertAnswerSql)) {
                        for (QuizResult.QuestionReview review : result.getReviews()) {
                            psAnswer.setInt(1, attemptId);
                            psAnswer.setInt(2, review.getQuestionId());
                            psAnswer.setString(3, review.getSelectedOption());
                            psAnswer.setInt(4, review.isCorrect() ? 1 : 0);
                            psAnswer.setDouble(5, review.getMarksAwarded());
                            psAnswer.addBatch();
                        }
                        psAnswer.executeBatch();
                    }
                }

                conn.commit();
                return attemptId;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Retrieves quiz attempts for a given student.
     */
    public List<QuizResult> getAttemptsByStudent(int userId) {
        List<QuizResult> results = new ArrayList<>();
        String sql = """
            SELECT a.id, a.user_id, u.username, a.quiz_id, q.title AS quiz_title,
                   a.score, q.total_marks AS max_score, a.percentage,
                   a.start_time, a.end_time, a.status
            FROM quiz_attempts a
            JOIN users u ON a.user_id = u.id
            JOIN quizzes q ON a.quiz_id = q.id
            WHERE a.user_id = ?
            ORDER BY a.id DESC;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToSummaryResult(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching student attempts: " + e.getMessage());
        }
        return results;
    }

    /**
     * Retrieves all attempts across all students (for Admin dashboard).
     */
    public List<QuizResult> getAllAttempts() {
        List<QuizResult> results = new ArrayList<>();
        String sql = """
            SELECT a.id, a.user_id, u.username, a.quiz_id, q.title AS quiz_title,
                   a.score, q.total_marks AS max_score, a.percentage,
                   a.start_time, a.end_time, a.status
            FROM quiz_attempts a
            JOIN users u ON a.user_id = u.id
            JOIN quizzes q ON a.quiz_id = q.id
            ORDER BY a.id DESC;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRowToSummaryResult(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all attempts: " + e.getMessage());
        }
        return results;
    }

    /**
     * Retrieves a complete attempt including full question reviews.
     */
    public QuizResult getAttemptDetails(int attemptId) {
        String sql = """
            SELECT a.id, a.user_id, u.username, a.quiz_id, q.title AS quiz_title,
                   a.score, q.total_marks AS max_score, a.percentage,
                   a.start_time, a.end_time, a.status
            FROM quiz_attempts a
            JOIN users u ON a.user_id = u.id
            JOIN quizzes q ON a.quiz_id = q.id
            WHERE a.id = ?;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    QuizResult result = mapRowToSummaryResult(rs);
                    loadQuestionReviews(conn, result);
                    return result;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching attempt details: " + e.getMessage());
        }
        return null;
    }

    private void loadQuestionReviews(Connection conn, QuizResult result) throws SQLException {
        String reviewSql = """
            SELECT ans.question_id, q.question_text, q.option_a, q.option_b, q.option_c, q.option_d,
                   ans.selected_option, q.correct_option, ans.correct, ans.marks_awarded, q.marks AS max_marks
            FROM answers ans
            JOIN questions q ON ans.question_id = q.id
            WHERE ans.attempt_id = ?
            ORDER BY ans.id ASC;
        """;

        List<QuizResult.QuestionReview> list = new ArrayList<>();
        int attempted = 0;
        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;

        try (PreparedStatement ps = conn.prepareStatement(reviewSql)) {
            ps.setInt(1, result.getAttemptId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String selected = rs.getString("selected_option");
                    boolean isCorr = rs.getInt("correct") == 1;
                    if (selected == null || selected.trim().isEmpty()) {
                        unanswered++;
                    } else {
                        attempted++;
                        if (isCorr) {
                            correct++;
                        } else {
                            incorrect++;
                        }
                    }

                    list.add(new QuizResult.QuestionReview(
                            rs.getInt("question_id"),
                            rs.getString("question_text"),
                            rs.getString("option_a"),
                            rs.getString("option_b"),
                            rs.getString("option_c"),
                            rs.getString("option_d"),
                            selected,
                            rs.getString("correct_option"),
                            isCorr,
                            rs.getDouble("marks_awarded"),
                            rs.getDouble("max_marks")
                    ));
                }
            }
        }
        result.setReviews(list);
        result.setTotalQuestions(list.size());
        result.setAttempted(attempted);
        result.setCorrect(correct);
        result.setIncorrect(incorrect);
        result.setUnanswered(unanswered);
    }

    /**
     * Retrieves the leaderboard for a specific quiz (or all quizzes if quizId <= 0).
     * Sorted primarily by score DESC, and secondarily by completion duration ASC.
     */
    public List<QuizResult> getLeaderboard(int quizId) {
        List<QuizResult> list = new ArrayList<>();
        String sql;
        if (quizId > 0) {
            sql = """
                SELECT a.id, a.user_id, u.username, a.quiz_id, q.title AS quiz_title,
                       a.score, q.total_marks AS max_score, a.percentage,
                       a.start_time, a.end_time, a.status,
                       (strftime('%s', a.end_time) - strftime('%s', a.start_time)) AS duration_secs
                FROM quiz_attempts a
                JOIN users u ON a.user_id = u.id
                JOIN quizzes q ON a.quiz_id = q.id
                WHERE a.quiz_id = ? AND a.status IN ('COMPLETED', 'TIMED_OUT')
                ORDER BY a.score DESC, duration_secs ASC
                LIMIT 50;
            """;
        } else {
            sql = """
                SELECT a.id, a.user_id, u.username, a.quiz_id, q.title AS quiz_title,
                       a.score, q.total_marks AS max_score, a.percentage,
                       a.start_time, a.end_time, a.status,
                       (strftime('%s', a.end_time) - strftime('%s', a.start_time)) AS duration_secs
                FROM quiz_attempts a
                JOIN users u ON a.user_id = u.id
                JOIN quizzes q ON a.quiz_id = q.id
                WHERE a.status IN ('COMPLETED', 'TIMED_OUT')
                ORDER BY a.score DESC, duration_secs ASC
                LIMIT 50;
            """;
        }

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (quizId > 0) {
                ps.setInt(1, quizId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    QuizResult res = mapRowToSummaryResult(rs);
                    res.setRank(rank++);
                    long secs = rs.getLong("duration_secs");
                    if (secs > 0) {
                        res.setTimeTakenSeconds(secs);
                    }
                    list.add(res);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching leaderboard: " + e.getMessage());
        }
        return list;
    }

    /**
     * Aggregates database statistics for the Admin dashboard.
     */
    public Map<String, Object> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users;")) {
                if (rs.next()) stats.put("totalUsers", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE role = 'STUDENT';")) {
                if (rs.next()) stats.put("totalStudents", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM quizzes;")) {
                if (rs.next()) stats.put("totalQuizzes", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM questions;")) {
                if (rs.next()) stats.put("totalQuestions", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM quiz_attempts;")) {
                if (rs.next()) stats.put("totalAttempts", rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COALESCE(AVG(score), 0), COALESCE(AVG(percentage), 0) FROM quiz_attempts;")) {
                if (rs.next()) {
                    stats.put("averageScore", Math.round(rs.getDouble(1) * 100.0) / 100.0);
                    stats.put("averagePercentage", Math.round(rs.getDouble(2) * 100.0) / 100.0);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching database stats: " + e.getMessage());
        }
        return stats;
    }

    private QuizResult mapRowToSummaryResult(ResultSet rs) throws SQLException {
        QuizResult res = new QuizResult();
        res.setAttemptId(rs.getInt("id"));
        res.setUserId(rs.getInt("user_id"));
        res.setUsername(rs.getString("username"));
        res.setQuizId(rs.getInt("quiz_id"));
        res.setQuizTitle(rs.getString("quiz_title"));
        res.setScore(rs.getDouble("score"));
        res.setMaxScore(rs.getDouble("max_score"));
        res.setPercentage(rs.getDouble("percentage"));
        res.setStartTime(rs.getString("start_time"));
        res.setEndTime(rs.getString("end_time"));
        res.setStatus(rs.getString("status"));

        // Calculate time taken from start and end times if available
        if (res.getStartTime() != null && res.getEndTime() != null) {
            try {
                LocalDateTime start = LocalDateTime.parse(res.getStartTime(), DATE_FORMATTER);
                LocalDateTime end = LocalDateTime.parse(res.getEndTime(), DATE_FORMATTER);
                res.setTimeTakenSeconds(Duration.between(start, end).toSeconds());
            } catch (Exception ignored) {
            }
        }
        return res;
    }
}
