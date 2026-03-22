package com.aman.notification_system.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.aman.notification_system.exception.RateLimitException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${notification.rate-limit.max-requests}")
    private int maxRequests;

    @Value("${notification.rate-limit.window-seconds}")
    private int windowSeconds;

    public void checkRateLimit(String userId) {
        String key = "rate_limit:user:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        log.debug("Rate limit check for user {}: {}/{}", userId, count, maxRequests);

        if (count > maxRequests) {
            throw new RateLimitException(userId);
        }
    }
}