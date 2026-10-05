package com.quiz;

import com.quiz.common.User;
import com.quiz.database.Database;
import com.quiz.database.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class AuthTest {

    private String testDbPath;
    private Database database;
    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        testDbPath = "data/test_auth_" + System.currentTimeMillis() + ".db";
        database = new Database(testDbPath);
        userRepository = new UserRepository(database);
    }

    @AfterEach
    public void tearDown() {
        File file = new File(testDbPath);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testSaltAndHashPassword() {
        String salt1 = Database.generateSalt();
        String salt2 = Database.generateSalt();
        assertNotNull(salt1);
        assertNotNull(salt2);
        assertNotEquals(salt1, salt2, "Generated salts should be cryptographically unique.");

        String hash1 = Database.hashPassword("secretPass", salt1);
        String hash2 = Database.hashPassword("secretPass", salt1);
        assertEquals(hash1, hash2, "Hashing same password with same salt should be deterministic.");

        String hashDifferentSalt = Database.hashPassword("secretPass", salt2);
        assertNotEquals(hash1, hashDifferentSalt, "Different salts must produce distinct hashes.");

        assertTrue(Database.verifyPassword("secretPass", salt1, hash1), "Valid password must verify successfully.");
        assertFalse(Database.verifyPassword("wrongPass", salt1, hash1), "Invalid password must fail verification.");
    }

    @Test
    public void testAuthenticationSuccess() {
        // student1 / password123 is seeded in initialize()
        User student = userRepository.authenticate("student1", "password123", "STUDENT");
        assertNotNull(student, "Student authentication should succeed with correct credentials.");
        assertEquals("student1", student.getUsername());
        assertEquals("STUDENT", student.getRole());

        // admin / admin123 is seeded in initialize()
        User admin = userRepository.authenticate("admin", "admin123", "ADMIN");
        assertNotNull(admin, "Admin authentication should succeed with correct credentials.");
        assertEquals("admin", admin.getUsername());
        assertEquals("ADMIN", admin.getRole());
    }

    @Test
    public void testAuthenticationFailureWrongPassword() {
        User user = userRepository.authenticate("student1", "incorrectPassword", "STUDENT");
        assertNull(user, "Authentication must fail when password does not match.");
    }

    @Test
    public void testAuthenticationFailureRoleMismatch() {
        // student1 attempting to login as ADMIN
        User user = userRepository.authenticate("student1", "password123", "ADMIN");
        assertNull(user, "Authentication must fail when user role does not match requested role.");
    }

    @Test
    public void testCreateNewUser() throws Exception {
        User newUser = userRepository.createUser("newStudent", "pass987", "STUDENT");
        assertNotNull(newUser);
        assertTrue(newUser.getId() > 0);

        User authenticated = userRepository.authenticate("newStudent", "pass987", "STUDENT");
        assertNotNull(authenticated);
        assertEquals("newStudent", authenticated.getUsername());
    }
}
