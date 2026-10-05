package com.quiz.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Type;

/**
 * Custom application protocol for the Online Quiz System.
 * Formats and parses structured JSON-delimited frames sent across raw TCP sockets.
 */
public class Protocol {

    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    public static class Message {
        private MessageType type;
        private boolean success;
        private String message;
        private String token;
        private String payload;

        public Message() {
        }

        public Message(MessageType type, boolean success, String message, String token, String payload) {
            this.type = type;
            this.success = success;
            this.message = message;
            this.token = token;
            this.payload = payload;
        }

        public MessageType getType() {
            return type;
        }

        public void setType(MessageType type) {
            this.type = type;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }
    }

    /**
     * Serializes a message into a single-line string with no internal newlines.
     */
    public static String serialize(Message message) {
        if (message == null) return "";
        return gson.toJson(message).replace("\n", "").replace("\r", "");
    }

    /**
     * Deserializes a message from a raw line received over TCP.
     */
    public static Message deserialize(String line) {
        if (line == null || line.trim().isEmpty()) return null;
        return gson.fromJson(line.trim(), Message.class);
    }

    /**
     * Serializes any object to JSON string.
     */
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    /**
     * Deserializes JSON string to an object of specified class.
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        if (json == null || json.trim().isEmpty()) return null;
        return gson.fromJson(json, classOfT);
    }

    /**
     * Deserializes JSON string using a generic Type.
     */
    public static <T> T fromJson(String json, Type typeOfT) {
        if (json == null || json.trim().isEmpty()) return null;
        return gson.fromJson(json, typeOfT);
    }

    // ==========================================
    // Convenience Builders
    // ==========================================

    public static Message createMessage(MessageType type, boolean success, String message, String payload) {
        return new Message(type, success, message, null, payload);
    }

    public static Message createError(String errorMessage) {
        return new Message(MessageType.ERROR, false, errorMessage, null, null);
    }

    public static Message createSuccess(String msg) {
        return new Message(MessageType.SUCCESS, true, msg, null, null);
    }
}
