package com.aman.notification_system.exception;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String userId) {
        super("Rate limit exceeded for user: " + userId);
    }
}