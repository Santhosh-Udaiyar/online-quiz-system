package com.quiz.server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe logger writing events to logs/server.log and standard output.
 */
public class ServerLogger {
    private static final String LOG_FILE_PATH = "logs/server.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final Object LOCK = new Object();

    static {
        File dir = new File("logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public enum Level {
        INFO, WARN, ERROR, AUTH, QUIZ, ADMIN
    }

    public static void log(Level level, String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String threadName = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%s] [%s] %s", timestamp, threadName, level.name(), message);

        System.out.println(formatted);

        synchronized (LOCK) {
            try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE_PATH, true))) {
                out.println(formatted);
            } catch (IOException e) {
                System.err.println("Failed to write to " + LOG_FILE_PATH + ": " + e.getMessage());
            }
        }
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void error(String message, Throwable t) {
        String msg = message + (t != null ? " - " + t.getMessage() : "");
        log(Level.ERROR, msg);
    }

    public static void auth(String message) {
        log(Level.AUTH, message);
    }

    public static void quiz(String message) {
        log(Level.QUIZ, message);
    }

    public static void admin(String message) {
        log(Level.ADMIN, message);
    }
}
