package com.anchor.models;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * User model class demonstrating encapsulation.
 * Stores user credentials with hashed passwords.
 *
 * NOTE: This is a prototype implementation.
 * Production version will use database storage.
 */
public class User {

    private int id;
    private String username;
    private String passwordHash;
    private String salt;
    private String email;
    private String role; // ADMIN or USER
    private boolean isPublic = true;  // Enhancement 4: private users hidden from normal users

    public User() {}

    public User(String username, String password, String role) {
        this.username = username;
        this.salt = generateSalt();
        this.passwordHash = hashPassword(password, this.salt);
        this.role = role;
    }

    // Getters and Setters (Encapsulation)
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }

    public String getSalt() { return salt; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    /**
     * Verify password against stored hash
     * Demonstrates use of java.security.MessageDigest
     */
    public boolean verifyPassword(String password) {
        String hash = hashPassword(password, this.salt);
        return this.passwordHash.equals(hash);
    }

    /**
     * Generate random salt using SecureRandom
     */
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * Hash password with SHA-256
     * Demonstrates java.security.MessageDigest usage
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + salt;
            byte[] hashedBytes = md.digest(saltedPassword.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
