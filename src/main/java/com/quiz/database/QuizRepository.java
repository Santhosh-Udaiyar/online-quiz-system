package com.quiz.database;

import com.quiz.common.Question;
import com.quiz.common.Quiz;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository handling CRUD operations for Quizzes and their associated Questions.
 */
public class QuizRepository {
    private final Database database;

    public QuizRepository(Database database) {
        this.database = database;
    }

    /**
     * Retrieves all quizzes in the database (admin view).
     */
    public List<Quiz> getAllQuizzes() {
        List<Quiz> quizzes = new ArrayList<>();
        String sql = """
            SELECT q.id, q.title, q.description, q.category, q.duration, q.total_marks, q.negative_mark, q.active,
                   (SELECT COUNT(*) FROM questions WHERE quiz_id = q.id) AS question_count
            FROM quizzes q
            ORDER BY q.id ASC;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Quiz quiz = mapRowToQuiz(rs);
                quiz.setQuestionCount(rs.getInt("question_count"));
                quizzes.add(quiz);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all quizzes: " + e.getMessage());
        }
        return quizzes;
    }

    /**
     * Retrieves all active quizzes with student-specific attempt count and best score.
     */
    public List<Quiz> getActiveQuizzesForStudent(int studentUserId) {
        List<Quiz> quizzes = new ArrayList<>();
        String sql = """
            SELECT q.id, q.title, q.description, q.category, q.duration, q.total_marks, q.negative_mark, q.active,
                   (SELECT COUNT(*) FROM questions WHERE quiz_id = q.id) AS question_count,
                   (SELECT COUNT(*) FROM quiz_attempts WHERE quiz_id = q.id AND user_id = ?) AS attempts_count,
                   (SELECT MAX(score) FROM quiz_attempts WHERE quiz_id = q.id AND user_id = ?) AS best_score
            FROM quizzes q
            WHERE q.active = 1
            ORDER BY q.id ASC;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentUserId);
            ps.setInt(2, studentUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Quiz quiz = mapRowToQuiz(rs);
                    quiz.setQuestionCount(rs.getInt("question_count"));
                    quiz.setPreviousAttempts(rs.getInt("attempts_count"));
                    double best = rs.getDouble("best_score");
                    if (!rs.wasNull()) {
                        quiz.setBestScore(best);
                    }
                    quizzes.add(quiz);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching active quizzes for student: " + e.getMessage());
        }
        return quizzes;
    }

    /**
     * Retrieves a single quiz by ID.
     */
    public Quiz getQuizById(int id) {
        String sql = """
            SELECT q.id, q.title, q.description, q.category, q.duration, q.total_marks, q.negative_mark, q.active,
                   (SELECT COUNT(*) FROM questions WHERE quiz_id = q.id) AS question_count
            FROM quizzes q
            WHERE q.id = ?;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Quiz quiz = mapRowToQuiz(rs);
                    quiz.setQuestionCount(rs.getInt("question_count"));
                    return quiz;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching quiz by id: " + e.getMessage());
        }
        return null;
    }

    /**
     * Creates a new quiz.
     */
    public int createQuiz(Quiz quiz) throws SQLException {
        String sql = """
            INSERT INTO quizzes (title, description, category, duration, total_marks, negative_mark, active)
            VALUES (?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, quiz.getTitle());
            ps.setString(2, quiz.getDescription());
            ps.setString(3, quiz.getCategory());
            ps.setInt(4, quiz.getDurationMinutes());
            ps.setDouble(5, quiz.getTotalMarks());
            ps.setDouble(6, quiz.getNegativeMark());
            ps.setInt(7, quiz.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    quiz.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * Updates an existing quiz.
     */
    public boolean updateQuiz(Quiz quiz) {
        String sql = """
            UPDATE quizzes
            SET title = ?, description = ?, category = ?, duration = ?, total_marks = ?, negative_mark = ?, active = ?
            WHERE id = ?;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, quiz.getTitle());
            ps.setString(2, quiz.getDescription());
            ps.setString(3, quiz.getCategory());
            ps.setInt(4, quiz.getDurationMinutes());
            ps.setDouble(5, quiz.getTotalMarks());
            ps.setDouble(6, quiz.getNegativeMark());
            ps.setInt(7, quiz.isActive() ? 1 : 0);
            ps.setInt(8, quiz.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating quiz: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a quiz by ID along with questions and attempts (via cascade or explicit deletion).
     */
    public boolean deleteQuiz(int id) {
        String sql = "DELETE FROM quizzes WHERE id = ?;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting quiz: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sets a quiz's active state.
     */
    public boolean setQuizActive(int id, boolean active) {
        String sql = "UPDATE quizzes SET active = ? WHERE id = ?;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating quiz active status: " + e.getMessage());
            return false;
        }
    }

    // ==========================================
    // Question Operations
    // ==========================================

    /**
     * Gets all questions for a given quiz (including correct_option).
     */
    public List<Question> getQuestionsForQuiz(int quizId) {
        List<Question> questions = new ArrayList<>();
        String sql = """
            SELECT id, quiz_id, question_text, option_a, option_b, option_c, option_d, correct_option, marks, negative_marks
            FROM questions
            WHERE quiz_id = ?
            ORDER BY id ASC;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quizId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapRowToQuestion(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching questions for quiz " + quizId + ": " + e.getMessage());
        }
        return questions;
    }

    /**
     * Gets a single question by ID.
     */
    public Question getQuestionById(int questionId) {
        String sql = """
            SELECT id, quiz_id, question_text, option_a, option_b, option_c, option_d, correct_option, marks, negative_marks
            FROM questions
            WHERE id = ?;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToQuestion(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching question by id: " + e.getMessage());
        }
        return null;
    }

    /**
     * Adds a question to a quiz.
     */
    public int addQuestion(Question question) throws SQLException {
        String sql = """
            INSERT INTO questions (quiz_id, question_text, option_a, option_b, option_c, option_d, correct_option, marks, negative_marks)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, question.getQuizId());
            ps.setString(2, question.getQuestionText());
            ps.setString(3, question.getOptionA());
            ps.setString(4, question.getOptionB());
            ps.setString(5, question.getOptionC());
            ps.setString(6, question.getOptionD());
            ps.setString(7, question.getCorrectOption().toUpperCase().trim());
            ps.setDouble(8, question.getMarks());
            ps.setDouble(9, question.getNegativeMarks());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    question.setId(id);
                    recalculateTotalMarks(question.getQuizId());
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * Updates an existing question.
     */
    public boolean updateQuestion(Question question) {
        String sql = """
            UPDATE questions
            SET question_text = ?, option_a = ?, option_b = ?, option_c = ?, option_d = ?,
                correct_option = ?, marks = ?, negative_marks = ?
            WHERE id = ?;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, question.getQuestionText());
            ps.setString(2, question.getOptionA());
            ps.setString(3, question.getOptionB());
            ps.setString(4, question.getOptionC());
            ps.setString(5, question.getOptionD());
            ps.setString(6, question.getCorrectOption().toUpperCase().trim());
            ps.setDouble(7, question.getMarks());
            ps.setDouble(8, question.getNegativeMarks());
            ps.setInt(9, question.getId());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) {
                recalculateTotalMarks(question.getQuizId());
            }
            return ok;
        } catch (SQLException e) {
            System.err.println("Error updating question: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a question by ID.
     */
    public boolean deleteQuestion(int questionId) {
        Question q = getQuestionById(questionId);
        if (q == null) return false;

        String sql = "DELETE FROM questions WHERE id = ?;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, questionId);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) {
                recalculateTotalMarks(q.getQuizId());
            }
            return ok;
        } catch (SQLException e) {
            System.err.println("Error deleting question: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recalculates total_marks in quizzes table based on sum of marks in questions table.
     */
    public void recalculateTotalMarks(int quizId) {
        String sql = """
            UPDATE quizzes
            SET total_marks = (SELECT COALESCE(SUM(marks), 0) FROM questions WHERE quiz_id = ?)
            WHERE id = ?;
        """;
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quizId);
            ps.setInt(2, quizId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error recalculating total marks: " + e.getMessage());
        }
    }

    private Quiz mapRowToQuiz(ResultSet rs) throws SQLException {
        return new Quiz(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getInt("duration"),
                rs.getDouble("total_marks"),
                rs.getDouble("negative_mark"),
                rs.getInt("active") == 1
        );
    }

    private Question mapRowToQuestion(ResultSet rs) throws SQLException {
        return new Question(
                rs.getInt("id"),
                rs.getInt("quiz_id"),
                rs.getString("question_text"),
                rs.getString("option_a"),
                rs.getString("option_b"),
                rs.getString("option_c"),
                rs.getString("option_d"),
                rs.getString("correct_option"),
                rs.getDouble("marks"),
                rs.getDouble("negative_marks")
        );
    }
}
