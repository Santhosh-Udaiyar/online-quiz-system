package com.quiz.server;

import com.quiz.database.Database;
import com.quiz.database.QuizRepository;
import com.quiz.database.ResultRepository;
import com.quiz.database.UserRepository;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * High-performance multithreaded TCP Server for the Online Quiz System.
 * Listens on a configurable port and delegates client sockets to an ExecutorService.
 */
public class QuizServer {

    public static final int DEFAULT_PORT = 8888;
    private final int port;
    private final QuizManager quizManager;
    private final ExecutorService threadPool;

    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public QuizServer(int port) {
        this.port = port;

        // Initialize SQLite Database and Repositories
        Database database = new Database("data/quiz.db");
        UserRepository userRepository = new UserRepository(database);
        QuizRepository quizRepository = new QuizRepository(database);
        ResultRepository resultRepository = new ResultRepository(database);
        SessionManager sessionManager = new SessionManager();

        this.quizManager = new QuizManager(userRepository, quizRepository, resultRepository, sessionManager);
        this.threadPool = Executors.newFixedThreadPool(50);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            ServerLogger.info("==================================================");
            ServerLogger.info(" Online Quiz Server started on port: " + port);
            ServerLogger.info(" Thread Pool: Fixed (50 workers)");
            ServerLogger.info(" Database: SQLite (data/quiz.db)");
            ServerLogger.info(" Ready to accept client socket connections...");
            ServerLogger.info("==================================================");

            // Register JVM shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Server-Shutdown-Hook"));

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, quizManager);
                    threadPool.execute(handler);
                } catch (IOException e) {
                    if (!running) {
                        ServerLogger.info("Server socket closed during shutdown.");
                        break;
                    }
                    ServerLogger.error("Failed to accept client connection: " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            ServerLogger.error("Could not start server on port " + port + ": " + e.getMessage(), e);
        } finally {
            stop();
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        ServerLogger.info("Shutting down Quiz Server gracefully...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            ServerLogger.error("Error closing server socket: " + e.getMessage(), e);
        }

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        ServerLogger.info("Quiz Server stopped successfully.");
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port specified. Using default: " + DEFAULT_PORT);
            }
        }

        QuizServer server = new QuizServer(port);
        server.start();
    }
}
