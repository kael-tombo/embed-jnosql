package org.embeddedjnosql.db.security;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Password Policy Enforcement
 * 
 * Implements OWASP password guidelines:
 * - Minimum 12 characters
 * - Maximum 128 characters
 * - Requires uppercase, lowercase, numbers, and special characters
 * - Checks for common passwords
 * - Detects sequential and repeated characters
 */
public class PasswordPolicy {
    
    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;
    private static final Pattern COMMON_PASSWORDS = Pattern.compile(
        "(?i)(password|admin|123456|qwerty|letmein|welcome|monkey|dragon|master|login|admin123)"
    );
    
    /**
     * Validate password against policy requirements
     * @param password the password to validate
     * @return validation result with violations list
     */
    public static PasswordValidationResult validate(String password) {
        List<String> violations = new ArrayList<>();
        
        if (password == null || password.isEmpty()) {
            violations.add("Password is required");
            return new PasswordValidationResult(false, violations);
        }
        
        if (password.length() < MIN_LENGTH) {
            violations.add("Password must be at least " + MIN_LENGTH + " characters");
        }
        
        if (password.length() > MAX_LENGTH) {
            violations.add("Password must not exceed " + MAX_LENGTH + " characters");
        }
        
        if (!Pattern.compile("[a-z]").matcher(password).find()) {
            violations.add("Password must contain at least one lowercase letter");
        }
        
        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            violations.add("Password must contain at least one uppercase letter");
        }
        
        if (!Pattern.compile("[0-9]").matcher(password).find()) {
            violations.add("Password must contain at least one number");
        }
        
        if (!Pattern.compile("[^a-zA-Z0-9]").matcher(password).find()) {
            violations.add("Password must contain at least one special character");
        }
        
        if (COMMON_PASSWORDS.matcher(password).find()) {
            violations.add("Password is too common, please choose a stronger password");
        }
        
        if (hasSequentialCharacters(password)) {
            violations.add("Password contains sequential characters (e.g., abc, 123)");
        }
        
        if (hasRepeatedCharacters(password)) {
            violations.add("Password contains too many repeated characters");
        }
        
        return new PasswordValidationResult(violations.isEmpty(), violations);
    }
    
    /**
     * Calculate password strength score (0-100)
     * @param password the password to score
     * @return strength score
     */
    public static int calculateStrength(String password) {
        int score = 0;
        
        if (password.length() >= 8) score += 20;
        if (password.length() >= 12) score += 20;
        if (Pattern.compile("[a-z]").matcher(password).find()) score += 15;
        if (Pattern.compile("[A-Z]").matcher(password).find()) score += 15;
        if (Pattern.compile("[0-9]").matcher(password).find()) score += 15;
        if (Pattern.compile("[^a-zA-Z0-9]").matcher(password).find()) score += 15;
        
        return Math.min(score, 100);
    }
    
    /**
     * Get strength level label
     * @param password the password to evaluate
     * @return strength level (weak, fair, good, strong)
     */
    public static String getStrengthLevel(String password) {
        int score = calculateStrength(password);
        
        if (score < 40) return "weak";
        if (score < 60) return "fair";
        if (score < 80) return "good";
        return "strong";
    }
    
    private static boolean hasSequentialCharacters(String password) {
        String lower = password.toLowerCase();
        for (int i = 0; i < lower.length() - 2; i++) {
            char c1 = lower.charAt(i);
            char c2 = lower.charAt(i + 1);
            char c3 = lower.charAt(i + 2);
            
            if (c2 == c1 + 1 && c3 == c2 + 1) return true;
            if (c2 == c1 - 1 && c3 == c2 - 1) return true;
        }
        return false;
    }
    
    private static boolean hasRepeatedCharacters(String password) {
        int count = 0;
        char last = 0;
        for (char c : password.toCharArray()) {
            if (c == last) count++;
            last = c;
        }
        return count > password.length() / 3;
    }
    
    public record PasswordValidationResult(boolean valid, List<String> violations) {}
}
