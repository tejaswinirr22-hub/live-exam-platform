package com.example.examplatformapi.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Boolean exists(String key) {
        return redisTemplate.hasKey(key);
    }

    public void setAnswer(String sessionId, String questionId, String answer) {

        String key = "exam-answer:" + sessionId + ":" + questionId;

        set(
                key,
                answer,
                Duration.ofHours(24)
        );
    }

    public String getAnswer(String sessionId, String questionId) {

        String key = "exam-answer:" + sessionId + ":" + questionId;

        return get(key);
    }
}