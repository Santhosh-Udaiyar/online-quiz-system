package com.quiz.database;

import com.quiz.common.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository handling user persistence, authentication, and role checking.
 */
public class UserRepository {
    private final Database database;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public UserRepository(Database database) {
        this.database = database;
    }

    /**
     * Authenticates a user given username, plain password, and expected role.
     * Returns the User object if authentication succeeds, or null otherwise.
     */
    public User authenticate(String username, String password, String expectedRole) {
        if (username == null || password == null || expectedRole == null) {
            return null;
        }

        String sql = "SELECT id, username, password_hash, salt, role, created_at FROM users WHERE username = ? COLLATE NOCASE;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("role");
                    if (!role.equalsIgnoreCase(expectedRole.trim())) {
                        return null; // Role mismatch
                    }

                    String salt = rs.getString("salt");
                    String storedHash = rs.getString("password_hash");

                    if (Database.verifyPassword(password, salt, storedHash)) {
                        return new User(
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getString("role"),
                                rs.getString("created_at")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error during user authentication: " + e.getMessage());
        }
        return null;
    }

    /**
     * Creates a new user with securely hashed password.
     */
    public User createUser(String username, String plainPassword, String role) throws SQLException {
        if (username == null || username.trim().isEmpty() || plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Username and password cannot be empty");
        }

        String salt = Database.generateSalt();
        String hash = Database.hashPassword(plainPassword, salt);
        String now = LocalDateTime.now().format(DATE_FORMATTER);

        String sql = "INSERT INTO users (username, password_hash, salt, role, created_at) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, role.toUpperCase().trim());
            ps.setString(5, now);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return new User(rs.getInt(1), username.trim(), role.toUpperCase().trim(), now);
                }
            }
        }
        return null;
    }

    public User getUserByUsername(String username) {
        String sql = "SELECT id, username, password_hash, salt, role, created_at FROM users WHERE username = ? COLLATE NOCASE;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("role"),
                            rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user: " + e.getMessage());
        }
        return null;
    }

    public User getUserById(int id) {
        String sql = "SELECT id, username, password_hash, salt, role, created_at FROM users WHERE id = ?;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("role"),
                            rs.getString("created_at")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user by id: " + e.getMessage());
        }
        return null;
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, username, role, created_at FROM users ORDER BY id ASC;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all users: " + e.getMessage());
        }
        return list;
    }

    public int getUserCount(String role) {
        String sql = role == null ? "SELECT COUNT(*) FROM users;" : "SELECT COUNT(*) FROM users WHERE role = ?;";
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (role != null) {
                ps.setString(1, role);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error counting users: " + e.getMessage());
        }
        return 0;
    }
}
