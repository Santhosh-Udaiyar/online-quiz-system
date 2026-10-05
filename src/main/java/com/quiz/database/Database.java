package com.quiz.database;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages SQLite database connectivity, schema creation, hashing utilities,
 * and initial data seeding.
 */
public class Database {
    private static final Logger LOGGER = Logger.getLogger(Database.class.getName());
    private static final String DEFAULT_DB_PATH = "data/quiz.db";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String dbUrl;

    public Database() {
        this(DEFAULT_DB_PATH);
    }

    public Database(String dbFilePath) {
        // Ensure data directory exists
        File file = new File(dbFilePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        this.dbUrl = "jdbc:sqlite:" + dbFilePath;
        initialize();
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(dbUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }

    /**
     * Initializes database tables and inserts seed data if empty.
     */
    public synchronized void initialize() {
        try (Connection conn = getConnection()) {
            createTables(conn);
            seedInitialData(conn);
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database: " + e.getMessage(), e);
            throw new RuntimeException("Database initialization error", e);
        }
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // users table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    salt TEXT NOT NULL,
                    role TEXT NOT NULL,
                    created_at TEXT NOT NULL
                );
            """);

            // quizzes table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quizzes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT,
                    category TEXT NOT NULL,
                    duration INTEGER NOT NULL,
                    total_marks REAL NOT NULL,
                    negative_mark REAL NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1
                );
            """);

            // questions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS questions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    quiz_id INTEGER NOT NULL,
                    question_text TEXT NOT NULL,
                    option_a TEXT NOT NULL,
                    option_b TEXT NOT NULL,
                    option_c TEXT NOT NULL,
                    option_d TEXT NOT NULL,
                    correct_option TEXT NOT NULL,
                    marks REAL NOT NULL,
                    negative_marks REAL NOT NULL,
                    FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
                );
            """);

            // quiz_attempts table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quiz_attempts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    quiz_id INTEGER NOT NULL,
                    score REAL NOT NULL,
                    percentage REAL NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT NOT NULL,
                    status TEXT NOT NULL,
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                    FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
                );
            """);

            // answers table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS answers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    attempt_id INTEGER NOT NULL,
                    question_id INTEGER NOT NULL,
                    selected_option TEXT,
                    correct INTEGER NOT NULL,
                    marks_awarded REAL NOT NULL,
                    FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(id) ON DELETE CASCADE,
                    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
                );
            """);
        }
    }

    private void seedInitialData(Connection conn) throws SQLException {
        // Check if users already exist
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users;")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // Already seeded
            }
        }

        LOGGER.info("Seeding initial database data (users, quizzes, questions)...");
        String now = LocalDateTime.now().format(DATE_FORMATTER);

        // 1. Seed users
        // 1 Admin: admin / admin123
        // 3 Students: student1, student2, student3 (all password123)
        insertUser(conn, "admin", "admin123", "ADMIN", now);
        insertUser(conn, "student1", "password123", "STUDENT", now);
        insertUser(conn, "student2", "password123", "STUDENT", now);
        insertUser(conn, "student3", "password123", "STUDENT", now);

        // 2. Seed Quizzes and Questions
        // Quiz 1: Java Programming Mastery
        int q1Id = insertQuiz(conn, "Java Programming Mastery",
                "Comprehensive quiz testing core Java OOP, multithreading, collections, and streams.",
                "Java", 10, 25.0, 1.0, 1);

        insertQuestion(conn, q1Id,
                "Which interface does a class implement to define a task for ExecutorService?",
                "java.lang.Thread", "java.lang.Runnable", "java.util.concurrent.Executor", "java.util.Process",
                "B", 5.0, 1.0);

        insertQuestion(conn, q1Id,
                "What is the contract between equals() and hashCode() in Java?",
                "If hashCode() is equal, equals() must be true",
                "If equals() is true, hashCode() must return the same value",
                "They have no relationship",
                "Both methods must return positive integers",
                "B", 5.0, 1.0);

        insertQuestion(conn, q1Id,
                "Which collection class is synchronized and thread-safe by default?",
                "ArrayList", "HashMap", "Vector", "HashSet",
                "C", 5.0, 1.0);

        insertQuestion(conn, q1Id,
                "What does the 'transient' keyword indicate when applied to an instance field?",
                "The variable cannot be modified",
                "The variable is shared among all threads",
                "The variable will not be serialized",
                "The variable is garbage collected immediately",
                "C", 5.0, 1.0);

        insertQuestion(conn, q1Id,
                "Which Java 17 feature allows restricting which classes can extend an interface or class?",
                "Sealed Classes", "Records", "Pattern Matching", "Virtual Threads",
                "A", 5.0, 1.0);

        // Quiz 2: Computer Networks & TCP/IP
        int q2Id = insertQuiz(conn, "Computer Networks & TCP/IP",
                "Assess your understanding of socket programming, TCP/IP stack, headers, and protocol states.",
                "Networking", 10, 25.0, 1.0, 1);

        insertQuestion(conn, q2Id,
                "Which TCP socket method in Java is used by a server to listen and accept incoming connections?",
                "connect()", "accept()", "bind()", "listen()",
                "B", 5.0, 1.0);

        insertQuestion(conn, q2Id,
                "What is the fundamental mechanism TCP uses to ensure reliable in-order byte delivery?",
                "Parity bits", "Sequence numbers and ACKs", "Token ring rotation", "Broadcast flooding",
                "B", 5.0, 1.0);

        insertQuestion(conn, q2Id,
                "What is the three-way handshake sequence to establish a TCP connection?",
                "SYN -> SYN-ACK -> ACK", "ACK -> SYN -> SYN-ACK", "FIN -> ACK -> FIN-ACK", "RST -> SYN -> ACK",
                "A", 5.0, 1.0);

        insertQuestion(conn, q2Id,
                "At which layer of the OSI model does raw TCP socket communication operate?",
                "Application Layer", "Network Layer", "Transport Layer", "Data Link Layer",
                "C", 5.0, 1.0);

        insertQuestion(conn, q2Id,
                "What is the purpose of TCP flow control using sliding windows?",
                "To prevent intermediate routers from congesting",
                "To prevent the sender from overwhelming the receiver's buffer",
                "To encrypt packet payloads across networks",
                "To multiplex multiple IP addresses",
                "B", 5.0, 1.0);

        // Quiz 3: Database Systems & SQL
        int q3Id = insertQuiz(conn, "Database Systems & SQL",
                "Relational databases, indexing, transactions, and SQLite internals.",
                "Database", 10, 25.0, 1.0, 1);

        insertQuestion(conn, q3Id,
                "Which property in ACID ensures that all operations within a transaction succeed or all roll back?",
                "Atomicity", "Consistency", "Isolation", "Durability",
                "A", 5.0, 1.0);

        insertQuestion(conn, q3Id,
                "Why should PreparedStatement always be favored over raw Statement string concatenation?",
                "It enables multithreaded queries",
                "It precompiles SQL and prevents SQL Injection attacks",
                "It automatically indexes all columns",
                "It bypasses database file locks",
                "B", 5.0, 1.0);

        insertQuestion(conn, q3Id,
                "What does SQLite WAL mode stand for?",
                "Wide Area Locking", "Write-Ahead Logging", "Weighted Allocation Limit", "Write-Access Logic",
                "B", 5.0, 1.0);

        insertQuestion(conn, q3Id,
                "Which SQL constraint enforces referential integrity between two related tables?",
                "UNIQUE", "PRIMARY KEY", "FOREIGN KEY", "CHECK",
                "C", 5.0, 1.0);

        insertQuestion(conn, q3Id,
                "Which SQL JOIN returns all rows from the left table and matched rows from the right table?",
                "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "CROSS JOIN",
                "B", 5.0, 1.0);

        LOGGER.info("Initial seed data successfully inserted into SQLite database.");
    }

    private void insertUser(Connection conn, String username, String plainPassword, String role, String createdAt) throws SQLException {
        String salt = generateSalt();
        String hash = hashPassword(plainPassword, salt);
        String sql = "INSERT INTO users (username, password_hash, salt, role, created_at) VALUES (?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, role);
            ps.setString(5, createdAt);
            ps.executeUpdate();
        }
    }

    private int insertQuiz(Connection conn, String title, String desc, String cat,
                           int duration, double marks, double negMark, int active) throws SQLException {
        String sql = "INSERT INTO quizzes (title, description, category, duration, total_marks, negative_mark, active) VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, desc);
            ps.setString(3, cat);
            ps.setInt(4, duration);
            ps.setDouble(5, marks);
            ps.setDouble(6, negMark);
            ps.setInt(7, active);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    private void insertQuestion(Connection conn, int quizId, String text,
                                String a, String b, String c, String d,
                                String correct, double marks, double negMarks) throws SQLException {
        String sql = """
            INSERT INTO questions (quiz_id, question_text, option_a, option_b, option_c, option_d, correct_option, marks, negative_marks)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quizId);
            ps.setString(2, text);
            ps.setString(3, a);
            ps.setString(4, b);
            ps.setString(5, c);
            ps.setString(6, d);
            ps.setString(7, correct);
            ps.setDouble(8, marks);
            ps.setDouble(9, negMarks);
            ps.executeUpdate();
        }
    }

    // ==========================================
    // Security & Hashing Utilities
    // ==========================================

    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static boolean verifyPassword(String password, String salt, String expectedHash) {
        if (password == null || salt == null || expectedHash == null) {
            return false;
        }
        String calculated = hashPassword(password, salt);
        return calculated.equalsIgnoreCase(expectedHash);
    }
}
