package com.quiz;

import com.quiz.common.MessageType;
import com.quiz.common.Protocol;
import com.quiz.common.QuizResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProtocolTest {

    @Test
    public void testProtocolSerializationAndDeserialization() {
        Protocol.Message message = new Protocol.Message(
                MessageType.LOGIN,
                true,
                "Authentication successful",
                "test-token-12345",
                "{\"username\":\"student1\",\"role\":\"STUDENT\"}"
        );

        String serialized = Protocol.serialize(message);
        assertNotNull(serialized);
        assertFalse(serialized.contains("\n"), "Serialized message must not contain internal newlines.");
        assertFalse(serialized.contains("\r"), "Serialized message must not contain carriage returns.");

        Protocol.Message deserialized = Protocol.deserialize(serialized);
        assertNotNull(deserialized);
        assertEquals(MessageType.LOGIN, deserialized.getType());
        assertTrue(deserialized.isSuccess());
        assertEquals("Authentication successful", deserialized.getMessage());
        assertEquals("test-token-12345", deserialized.getToken());
        assertEquals("{\"username\":\"student1\",\"role\":\"STUDENT\"}", deserialized.getPayload());
    }

    @Test
    public void testErrorProtocolMessage() {
        Protocol.Message err = Protocol.createError("Database connection failed");
        assertEquals(MessageType.ERROR, err.getType());
        assertFalse(err.isSuccess());
        assertEquals("Database connection failed", err.getMessage());

        String line = Protocol.serialize(err);
        Protocol.Message roundtrip = Protocol.deserialize(line);
        assertNotNull(roundtrip);
        assertEquals(MessageType.ERROR, roundtrip.getType());
        assertFalse(roundtrip.isSuccess());
        assertEquals("Database connection failed", roundtrip.getMessage());
    }

    @Test
    public void testQuizResultPayloadSerialization() {
        QuizResult result = new QuizResult();
        result.setQuizTitle("Java OOP Exam");
        result.setScore(45.5);
        result.setMaxScore(50.0);
        result.setPercentage(91.0);
        result.setCorrect(9);
        result.setIncorrect(1);
        result.setStatus("COMPLETED");

        String payload = Protocol.toJson(result);
        Protocol.Message msg = new Protocol.Message(MessageType.QUIZ_RESULT, true, "OK", null, payload);

        String line = Protocol.serialize(msg);
        Protocol.Message parsedMsg = Protocol.deserialize(line);
        QuizResult parsedResult = Protocol.fromJson(parsedMsg.getPayload(), QuizResult.class);

        assertNotNull(parsedResult);
        assertEquals("Java OOP Exam", parsedResult.getQuizTitle());
        assertEquals(45.5, parsedResult.getScore());
        assertEquals(91.0, parsedResult.getPercentage());
        assertEquals(9, parsedResult.getCorrect());
    }
}
