# Online Quiz & Examination System

An enterprise-grade, standalone **Java 17** examination platform built with raw **TCP Sockets**, concurrent multi-client multithreading using **ExecutorService**, persistent embedded **SQLite with JDBC**, and a responsive **Java Swing desktop GUI**.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Networking Concepts](#2-networking-concepts)
3. [Architecture](#3-architecture)
4. [TCP Communication](#4-tcp-communication)
5. [Multithreading & Concurrency](#5-multithreading--concurrency)
6. [Database Design](#6-database-design)
7. [Application Protocol](#7-application-protocol)
8. [Authentication & Security](#8-authentication--security)
9. [Quiz Flow & Randomization](#9-quiz-flow--randomization)
10. [Scoring Algorithm](#10-scoring-algorithm)
11. [Project Structure](#11-project-structure)
12. [System Requirements](#12-system-requirements)
13. [Installation & Setup](#13-installation--setup)
14. [Maven Commands](#14-maven-commands)
15. [Server Startup](#15-server-startup)
16. [Client Startup (Student / Admin)](#16-client-startup-student--admin)
17. [Administrator Guide](#17-administrator-guide)
18. [Student Guide](#18-student-guide)
19. [Sample Credentials](#19-sample-credentials)
20. [Testing & Verification](#20-testing--verification)
21. [Troubleshooting](#21-troubleshooting)

---

## 1. Project Overview

The **Online Quiz System** simulates a real-world, high-concurrency online examination portal. The platform separates client and server concerns:

- **Server (`QuizServer`)**: Listens on raw TCP sockets, handles client connections via a worker thread pool, enforces authoritative exam countdown timers, randomizes questions and options per student session, calculates scores with negative marking penalties, and writes results atomically into SQLite.
- **Client (`QuizClient` + Swing UI)**: Connects to the server over a persistent TCP stream. Neither student nor admin clients have direct database connectivity; every operation travels through the structured JSON-based application protocol.

---

## 2. Networking Concepts

This system demonstrates foundational Computer Networking and Distributed Systems principles:

1. **Transport Layer Protocols (TCP vs. UDP)**:
   - Built directly on **Transmission Control Protocol (TCP)** using Java `ServerSocket` and `Socket`.
   - Guaranteed reliability, zero packet loss, in-order packet arrival, and three-way handshake (`SYN` $\rightarrow$ `SYN-ACK` $\rightarrow$ `ACK`).
2. **Stream Sockets & Framing**:
   - TCP is a byte-stream protocol without inherent message boundaries.
   - Message demarcation is implemented via **line-delimited framing** (`\n`), allowing single-line JSON protocol frames to be read deterministically using `BufferedReader.readLine()`.
3. **Connection Lifecycle & State Machine**:
   - Socket connection $\rightarrow$ Authentication handshake $\rightarrow$ Authorized session operations $\rightarrow$ Active exam synchronization $\rightarrow$ Graceful disconnect / TCP FIN closure.
4. **Resilience & Timeout Handling**:
   - Sockets employ read timeouts (`setSoTimeout(10000)`) to guard against hung connections or network partitions.
   - Abrupt socket closures by crashing clients are trapped gracefully without crashing the server.

---

## 3. Architecture

```
+-------------------------------------------------------------+
|                     Swing Desktop Clients                   |
|   +-----------------------+     +-----------------------+   |
|   |   Student Dashboard   |     |    Admin Dashboard    |   |
|   +-----------------------+     +-----------------------+   |
+-------------------------------------------------------------+
                              |
                     TCP Socket Stream
                 (Single-line JSON Frames)
                              |
                              v
+-------------------------------------------------------------+
|                  QuizServer (TCP Port 8888)                 |
|   +-----------------------------------------------------+   |
|   |         ServerSocket.accept() Connection Loop       |   |
|   +-----------------------------------------------------+   |
|                             |                               |
|                             v                               |
|   +-----------------------------------------------------+   |
|   |      ExecutorService (Fixed Thread Pool - 50)       |   |
|   |   ClientHandler #1 | ClientHandler #2 | ...         |   |
|   +-----------------------------------------------------+   |
|                             |                               |
|                             v                               |
|   +-----------------------------------------------------+   |
|   |      QuizManager & SessionManager (State Core)      |   |
|   |   - Active session tracking                             |   |
|   |   - Authoritative timer & option shuffling              |   |
|   |   - Server-side score evaluation                        |   |
|   +-----------------------------------------------------+   |
|                             |                               |
|                             v                               |
|   +-----------------------------------------------------+   |
|   |               SQLite Repository Layer               |   |
|   |   UserRepository | QuizRepository | ResultRepo      |   |
|   +-----------------------------------------------------+   |
|                             |                               |
|                             v                               |
|   +-----------------------------------------------------+   |
|   |             data/quiz.db (SQLite JDBC, WAL)         |   |
|   +-----------------------------------------------------+   |
+-------------------------------------------------------------+
```

---

## 4. TCP Communication

The client and server communicate strictly using raw TCP sockets:

```
Client                                                  Server
  |                                                       |
  |--- TCP 3-Way Handshake (SYN -> SYN-ACK -> ACK) ------>|
  |                                                       |
  |--- LOGIN { username, password, role } --------------->|
  |<-- LOGIN_SUCCESS / LOGIN_FAILED ----------------------|
  |                                                       |
  |--- GET_QUIZZES -------------------------------------->|
  |<-- QUIZ_LIST [ { id, title, duration, marks... } ] ---|
  |                                                       |
  |--- START_QUIZ (quizId) ------------------------------>|
  |<-- QUIZ_DATA { sanitized questions, timerSecs } ------|
  |    (Options shuffled; correct answers HIDDEN)         |
  |                                                       |
  |--- SUBMIT_ANSWER (questionId, optionKey) ------------>| (Synchronized in-memory)
  |<-- SUCCESS ("Answer recorded") -----------------------|
  |                                                       |
  |--- SUBMIT_QUIZ (final answers map) ------------------>|
  |<-- QUIZ_RESULT { score, percentage, review } ---------|
  |    (Persisted to SQLite; Session closed)              |
  |                                                       |
  |--- LOGOUT ------------------------------------------->|
  |<-- TCP FIN / ACK Connection Teardown -----------------|
```

---

## 5. Multithreading & Concurrency

- **ExecutorService**: The server runs an `ExecutorService` with 50 worker threads (`Executors.newFixedThreadPool(50)`).
- **Session Isolation**: Each student who begins an exam receives an independent `ActiveSession` tracked in a thread-safe `ConcurrentHashMap`.
- **Thread Safety**: Answer updates, timer calculations, and quiz submissions use synchronized session blocks to eliminate race conditions.
- **SQLite Concurrency**: SQLite runs in Write-Ahead Logging (`WAL`) mode with `PRAGMA busy_timeout = 5000` and `PRAGMA foreign_keys = ON`, enabling concurrent readers and thread-safe serial writes.

---

## 6. Database Design

Embedded SQLite with JDBC (`data/quiz.db`).

### Tables Schema

```sql
-- Users (Students and Administrators)
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    role TEXT NOT NULL,
    created_at TEXT NOT NULL
);

-- Quizzes
CREATE TABLE IF NOT EXISTS quizzes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL,
    duration INTEGER NOT NULL,          -- Duration in minutes
    total_marks REAL NOT NULL,
    negative_mark REAL NOT NULL,        -- Negative marking penalty
    active INTEGER NOT NULL DEFAULT 1   -- 1 = Active, 0 = Inactive
);

-- Multiple Choice Questions
CREATE TABLE IF NOT EXISTS questions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    quiz_id INTEGER NOT NULL,
    question_text TEXT NOT NULL,
    option_a TEXT NOT NULL,
    option_b TEXT NOT NULL,
    option_c TEXT NOT NULL,
    option_d TEXT NOT NULL,
    correct_option TEXT NOT NULL,       -- "A", "B", "C", or "D"
    marks REAL NOT NULL,
    negative_marks REAL NOT NULL,
    FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
);

-- Quiz Attempts (Student exam submissions)
CREATE TABLE IF NOT EXISTS quiz_attempts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    quiz_id INTEGER NOT NULL,
    score REAL NOT NULL,
    percentage REAL NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    status TEXT NOT NULL,               -- "COMPLETED", "TIMED_OUT", etc.
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
);

-- Itemized Answers
CREATE TABLE IF NOT EXISTS answers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    attempt_id INTEGER NOT NULL,
    question_id INTEGER NOT NULL,
    selected_option TEXT,
    correct INTEGER NOT NULL,           -- 1 = true, 0 = false
    marks_awarded REAL NOT NULL,
    FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);
```

---

## 7. Application Protocol

All network communications use the `Protocol.java` specification with single-line JSON messages:

```json
{
  "type": "LOGIN",
  "success": true,
  "message": "Status description",
  "token": "session-uuid-token",
  "payload": "..."
}
```

### Protocol Message Types

| Message Type | Direction | Description |
|---|---|---|
| `LOGIN` | Client $\rightarrow$ Server | Credentials payload `{username, password, role}` |
| `LOGIN_SUCCESS` | Server $\rightarrow$ Client | User profile and authentication confirmation |
| `LOGIN_FAILED` | Server $\rightarrow$ Client | Failure reason |
| `LOGOUT` | Client $\rightarrow$ Server | Terminates authenticated session |
| `GET_QUIZZES` | Client $\rightarrow$ Server | Requests list of available quizzes |
| `QUIZ_LIST` | Server $\rightarrow$ Client | Array of Quiz objects |
| `START_QUIZ` | Client $\rightarrow$ Server | Starts timed quiz session for `quizId` |
| `QUIZ_DATA` | Server $\rightarrow$ Client | Sanitized questions (no correct answers) & timer duration |
| `SUBMIT_ANSWER` | Client $\rightarrow$ Server | Intermediary answer choice synchronization |
| `SUBMIT_QUIZ` | Client $\rightarrow$ Server | Submits final answers for evaluation |
| `QUIZ_RESULT` | Server $\rightarrow$ Client | Complete evaluation score & question review |
| `GET_LEADERBOARD` | Client $\rightarrow$ Server | Requests leaderboard for a quiz |
| `LEADERBOARD` | Server $\rightarrow$ Client | Ranked list of student results |
| `GET_STUDENT_HISTORY` | Client $\rightarrow$ Server | Student requests previous exam attempts |
| `STUDENT_HISTORY_DATA` | Server $\rightarrow$ Client | Historical submissions list |
| `GET_RESULT_DETAILS` | Client $\rightarrow$ Server | Requests item-by-item review of past attempt |
| `RESULT_DETAILS_DATA` | Server $\rightarrow$ Client | Full breakdown with question reviews |
| `ADMIN_CREATE_QUIZ` | Admin $\rightarrow$ Server | Creates new quiz entity |
| `ADMIN_UPDATE_QUIZ` | Admin $\rightarrow$ Server | Modifies quiz properties |
| `ADMIN_DELETE_QUIZ` | Admin $\rightarrow$ Server | Removes quiz and cascades deletions |
| `ADMIN_GET_QUESTIONS` | Admin $\rightarrow$ Server | Retrieves questions (including correct answers) |
| `ADMIN_ADD_QUESTION` | Admin $\rightarrow$ Server | Adds multiple choice question |
| `ADMIN_UPDATE_QUESTION` | Admin $\rightarrow$ Server | Modifies existing question |
| `ADMIN_DELETE_QUESTION` | Admin $\rightarrow$ Server | Deletes question |
| `ADMIN_GET_STATS` | Admin $\rightarrow$ Server | Aggregated platform metrics |
| `ADMIN_GET_ALL_RESULTS` | Admin $\rightarrow$ Server | All student submissions across platform |
| `SUCCESS` / `ERROR` | Server $\rightarrow$ Client | Generic status / error frames |

---

## 8. Authentication & Security

1. **Cryptographic Hashing**:
   - Passwords are never stored in plaintext.
   - For every user, a unique 16-byte cryptographically secure salt (`SecureRandom`) is generated.
   - Hashing uses **salted SHA-256**:
     $$\text{Hash} = \text{SHA-256}(\text{Password} + \text{Salt})$$
2. **PreparedStatement SQL Injection Prevention**:
   - Every database query uses parameterized `PreparedStatement`. No string concatenation is used.
3. **Role-Based Access Control (RBAC)**:
   - Administrative commands (`ADMIN_*`) verify that the active socket session belongs to a user with the `ADMIN` role.
4. **Answer Key Protection**:
   - The server strips out `correct_option` when delivering questions to student clients. Answer keys remain exclusively server-side.

---

## 9. Quiz Flow & Randomization

1. **Question Shuffling**: Questions are ordered randomly for every student attempt.
2. **Option Shuffling**: The 4 options (A, B, C, D) for each question are randomized. The server dynamically recalculates which option letter corresponds to the original correct answer and stores this in the session's authoritative `answerKey`.
3. **Countdown Timer**:
   - The client runs a visual countdown timer.
   - The server records `startTime` and allowed `durationSeconds`.
   - On submission, the server verifies `(now - startTime) <= duration + gracePeriod`. Submissions beyond the duration are marked as `TIMED_OUT`.
4. **Automatic Submission**: If the client timer hits `00:00`, the exam submits automatically.

---

## 10. Scoring Algorithm

For each question:
- **Correct Answer**: Award $+M$ marks (where $M$ is the question's configured marks).
- **Incorrect Answer**: Deduct $-N$ marks (where $N$ is the question's configured negative marks).
- **Unanswered**: $0$ marks awarded (no penalty).

$$\text{Final Score} = \sum (\text{Correct Marks}) - \sum (\text{Negative Marks})$$

$$\text{Percentage} = \max\left(0.0, \; \frac{\text{Final Score}}{\text{Total Possible Marks}} \times 100\right)$$

The leaderboard sorts entries:
1. **Primary**: Highest `Score` descending.
2. **Secondary**: Fastest `Completion Time` ascending.

---

## 11. Project Structure

```
online-quiz-system/
├── pom.xml
├── README.md
├── logs/
│   └── server.log
├── data/
│   └── quiz.db
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/quiz/
│   │           ├── server/
│   │           │   ├── QuizServer.java          # TCP ServerSocket & thread pool
│   │           │   ├── ClientHandler.java       # Per-connection socket dispatcher
│   │           │   ├── QuizManager.java         # Scoring & business coordination
│   │           │   ├── SessionManager.java      # Shuffling, timers, active sessions
│   │           │   └── ServerLogger.java        # Thread-safe server logging
│   │           ├── client/
│   │           │   ├── QuizClient.java          # TCP client socket manager
│   │           │   ├── UIUtils.java             # Shared Swing styling & palettes
│   │           │   ├── LoginGUI.java            # Student & Admin authentication window
│   │           │   ├── StudentDashboard.java    # Quizzes, history, leaderboard UI
│   │           │   ├── QuizWindow.java          # Live exam, timer, question palette
│   │           │   └── ResultWindow.java        # Comprehensive review window
│   │           ├── admin/
│   │           │   └── AdminDashboard.java      # Full management console
│   │           ├── common/
│   │           │   ├── User.java                # User data model
│   │           │   ├── Question.java            # Multiple-choice question model
│   │           │   ├── Quiz.java                # Quiz data model
│   │           │   ├── QuizResult.java          # Attempt evaluation & reviews
│   │           │   ├── Protocol.java            # Message framing & JSON serialization
│   │           │   └── MessageType.java         # Protocol command enumerations
│   │           └── database/
│   │               ├── Database.java            # SQLite JDBC connection & seeding
│   │               ├── UserRepository.java      # User queries & authentication
│   │               ├── QuizRepository.java      # Quizzes & questions CRUD
│   │               └── ResultRepository.java    # Attempts, reviews, & leaderboards
│   └── test/
│       └── java/
│           └── com/quiz/
│               ├── AuthTest.java                # Hashing & auth tests
│               ├── QuestionValidationTest.java  # Sanitization & option tests
│               ├── QuizScoringTest.java         # Correct & negative marking tests
│               ├── SessionManagerTest.java      # Shuffling & timer tests
│               ├── RepositoryTest.java          # SQLite DB operations tests
│               ├── ProtocolTest.java            # Protocol framing tests
│               └── IntegrationNetworkTest.java  # End-to-end multi-client TCP tests
```

---

## 12. System Requirements

- **Java Development Kit (JDK)**: Java 17 (verified with OpenJDK 17)
- **Apache Maven**: Version 3.8+ (e.g., 3.9.6)
- **Operating System**: Windows, macOS, or Linux

---

## 13. Installation & Setup

1. Open PowerShell or Terminal in the project root:
   ```powershell
   cd "c:\Users\SANTHOSH UDAIYAR\Desktop\online-quiz-system"
   ```
2. Build the project using Maven:
   ```powershell
   & "C:\Users\SANTHOSH UDAIYAR\maven\apache-maven-3.9.6\bin\mvn.cmd" clean package -DskipTests
   ```
   *(Or simply `mvn clean package -DskipTests` if Maven is in your system PATH).*

---

## 14. Maven Commands

| Action | Command |
|---|---|
| Run all unit and integration tests | `mvn clean test` |
| Compile source files | `mvn compile` |
| Package application JAR | `mvn package -DskipTests` |
| Launch Quiz Server | `mvn exec:java -Dexec.mainClass="com.quiz.server.QuizServer"` |
| Launch Client (Student / Admin) | `mvn exec:java -Dexec.mainClass="com.quiz.client.LoginGUI"` |

---

## 15. Server Startup

To start the raw TCP socket server:

```powershell
& "C:\Users\SANTHOSH UDAIYAR\maven\apache-maven-3.9.6\bin\mvn.cmd" exec:java -Dexec.mainClass="com.quiz.server.QuizServer"
```

The server binds to port **8888** by default and outputs:
```
[INFO] ==================================================
[INFO]  Online Quiz Server started on port: 8888
[INFO]  Thread Pool: Fixed (50 workers)
[INFO]  Database: SQLite (data/quiz.db)
[INFO]  Ready to accept client socket connections...
[INFO] ==================================================
```

---

## 16. Client Startup (Student / Admin)

Open a new terminal window and run:

```powershell
& "C:\Users\SANTHOSH UDAIYAR\maven\apache-maven-3.9.6\bin\mvn.cmd" exec:java -Dexec.mainClass="com.quiz.client.LoginGUI"
```

The Login GUI will appear:
- Choose **Server Host** (`localhost`) and **Port** (`8888`).
- Select role: **STUDENT** or **ADMIN**.
- Enter credentials and click **Log In**.

---

## 17. Administrator Guide

Log in as `admin` / `admin123`.

The Admin Console provides:
1. **Quiz Management**:
   - Create new quizzes with custom title, category, duration (minutes), and negative marks.
   - Edit quiz metadata.
   - Toggle quiz active/inactive status.
   - Delete quizzes (cascades to related questions and attempts).
2. **Question Management**:
   - Select a quiz to manage its question pool.
   - Add multiple choice questions (Question text, Options A, B, C, D, Correct Option, Marks, Negative Marks).
   - Edit or delete existing questions.
   - Total quiz marks recalculate automatically.
3. **Student Results**:
   - Inspect all student exam attempts across the entire platform.
   - Click **Inspect Full Submission Review** to view an itemized review showing which option the student selected, the correct answer, and marks awarded.
4. **Leaderboard**:
   - Filter rankings by quiz or view overall top performers.
5. **System Statistics**:
   - Real-time dashboard tiles showing Total Users, Total Students, Total Quizzes, Question Pool count, Total Submissions, and Platform Average Percentage.

---

## 18. Student Guide

Log in as `student1` / `password123`.

1. **Available Quizzes Tab**:
   - View active quizzes, categories, question count, duration, marks, previous attempts, and your best score.
   - Select a quiz and click **Start Selected Quiz**.
2. **Examination Window (`QuizWindow`)**:
   - Read the question and choose from the four radio options.
   - The selection is synchronized in real-time to the server.
   - Click question buttons in the **Question Palette** on the right to jump between questions. Answered questions turn green.
   - Watch the **Time Remaining** counter. When time runs low ($< 2$ minutes), the timer turns warning red.
   - Click **Submit Quiz** when finished, or let the timer reach `00:00` for automatic submission.
3. **Result Window (`ResultWindow`)**:
   - View your overall score, percentage, correct/incorrect/unanswered breakdown, and time taken.
   - Scroll through the **Detailed Question-by-Question Review** to see explanations, correct options, and marks earned.
4. **My History Tab**:
   - Browse your past exam submissions and re-open full reviews at any time.
5. **Leaderboard Tab**:
   - Check where you rank against other students based on score and completion speed.

---

## 19. Sample Credentials

The database automatically seeds these accounts on first startup:

| Role | Username | Plain Password | Description |
|---|---|---|---|
| **Administrator** | `admin` | `admin123` | System Administrator with full quiz/question control |
| **Student** | `student1` | `password123` | Standard student account |
| **Student** | `student2` | `password123` | Standard student account |
| **Student** | `student3` | `password123` | Standard student account |

### Pre-loaded Quizzes:
1. **Java Programming Mastery** (5 questions, 10 mins, 25.0 marks)
2. **Computer Networks & TCP/IP** (5 questions, 10 mins, 25.0 marks)
3. **Database Systems & SQL** (5 questions, 10 mins, 25.0 marks)

---

## 20. Testing & Verification

Run the entire automated JUnit 5 test suite:

```powershell
& "C:\Users\SANTHOSH UDAIYAR\maven\apache-maven-3.9.6\bin\mvn.cmd" test
```

### Verified Test Suite (21 Tests, 0 Failures):
- **`AuthTest`**: Tests password salting, SHA-256 hashing, successful logins, wrong password rejections, and role mismatch guards.
- **`QuestionValidationTest`**: Verifies question sanitization, ensuring answer keys are stripped before sending to students.
- **`QuizScoringTest`**: Tests perfect scores, negative marking arithmetic, and unanswered question handling.
- **`SessionManagerTest`**: Tests option shuffling, answer mapping preservation, in-flight answer caching, and countdown expiration.
- **`RepositoryTest`**: Tests SQLite transactions, question/quiz CRUD, total mark recalculation, and leaderboard score/time ordering.
- **`ProtocolTest`**: Tests JSON message serialization, single-line framing, and roundtrip deserialization.
- **`IntegrationNetworkTest`**: Starts an actual TCP server, connects multiple concurrent student clients taking exams simultaneously, tests admin workflows, and verifies abrupt socket disconnect resilience.

---

## 21. Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| `Address already in use: bind` | Another server instance is running on port 8888 | Terminate the previous process or pass a custom port: `java -cp target/... com.quiz.server.QuizServer 8999` |
| `Not connected to Quiz Server` | Server is not running or incorrect host/port in Login GUI | Ensure `QuizServer` is started and verify port matches `8888` |
| `Authentication failed` | Incorrect username, password, or role selected | Ensure role dropdown matches the account type (`STUDENT` vs `ADMIN`). Refer to sample credentials |
| `Quiz session invalid or expired` | Exam was already submitted or time exceeded grace window | Re-open the student dashboard and start a new quiz attempt |
| Maven command not found | Maven is not configured in current shell environment | Use the full path: `& "C:\Users\SANTHOSH UDAIYAR\maven\apache-maven-3.9.6\bin\mvn.cmd"` |
