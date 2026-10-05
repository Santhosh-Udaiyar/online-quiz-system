package com.quiz.common;

/**
 * Message types for the Online Quiz System TCP client-server protocol.
 */
public enum MessageType {
    // Authentication
    LOGIN,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,

    // Quiz Browsing & Student
    GET_QUIZZES,
    QUIZ_LIST,
    GET_STUDENT_HISTORY,
    STUDENT_HISTORY_DATA,
    GET_RESULT_DETAILS,
    RESULT_DETAILS_DATA,
    GET_LEADERBOARD,
    LEADERBOARD,

    // Quiz Session
    START_QUIZ,
    QUIZ_DATA,
    SUBMIT_ANSWER,
    SUBMIT_QUIZ,
    QUIZ_RESULT,

    // Admin Operations
    ADMIN_CREATE_QUIZ,
    ADMIN_UPDATE_QUIZ,
    ADMIN_DELETE_QUIZ,
    ADMIN_GET_QUESTIONS,
    ADMIN_ADD_QUESTION,
    ADMIN_UPDATE_QUESTION,
    ADMIN_DELETE_QUESTION,
    ADMIN_GET_STATS,
    ADMIN_GET_ALL_RESULTS,

    // General Responses
    SUCCESS,
    ERROR
}
