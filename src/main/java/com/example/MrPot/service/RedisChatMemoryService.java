package com.example.MrPot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedisChatMemoryService {

    private static final String KEY_PREFIX = "chat:memory:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<StoredMessage> loadHistory(String sessionId) {
        String key = buildKey(sessionId);
        List<String> rawMessages = redisTemplate.opsForList().range(key, 0, -1);
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }

        List<StoredMessage> messages = new ArrayList<>();
        for (String raw : rawMessages) {
            try {
                messages.add(objectMapper.readValue(raw, StoredMessage.class));
            } catch (JsonProcessingException ignored) {
                // skip broken entries
            }
        }
        return messages;
    }

    public void appendTurn(String sessionId, String userMessage, String assistantMessage, boolean temporary) {
        String key = buildKey(sessionId);
        List<StoredMessage> turn = List.of(
                new StoredMessage("user", userMessage, Instant.now().toEpochMilli()),
                new StoredMessage("assistant", assistantMessage, Instant.now().toEpochMilli())
        );
        for (StoredMessage message : turn) {
            try {
                redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException ignored) {
                // ignore serialization issues to avoid blocking response
            }
        }

        if (temporary) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
    }

    public String renderHistory(List<StoredMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(no prior conversation)";
        }
        return messages.stream()
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    public record StoredMessage(String role, String content, long timestamp) { }
}
