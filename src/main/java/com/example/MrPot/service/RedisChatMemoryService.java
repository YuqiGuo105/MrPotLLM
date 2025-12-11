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

    // --- Hard limits for memory size ---

    /** Max number of messages (user + assistant) to keep per session in Redis. */
    private static final int MAX_MESSAGES_PER_SESSION = 10;

    /** Max number of messages to append into the LLM prompt. */
    private static final int MAX_MESSAGES_IN_PROMPT = 8;

    // --- TTL settings ---

    private static final Duration TEMPORARY_TTL = Duration.ofMinutes(1);

    /**
     * For non-temporary sessions:
     * keep them as long as there are messages in the last 7 days,
     * and refresh TTL on each new turn (rolling TTL).
     */
    private static final Duration PERSISTENT_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Load only the latest MAX_MESSAGES_PER_SESSION messages
     * to avoid reading an excessively long list in one go.
     */
    public List<StoredMessage> loadHistory(String sessionId) {
        String key = buildKey(sessionId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0L) {
            return List.of();
        }

        long start = Math.max(0, size - MAX_MESSAGES_PER_SESSION);
        long end = size - 1;

        List<String> rawMessages = redisTemplate.opsForList().range(key, start, end);
        if (rawMessages == null || rawMessages.isEmpty()) {
            return List.of();
        }

        List<StoredMessage> messages = new ArrayList<>();
        for (String raw : rawMessages) {
            try {
                messages.add(objectMapper.readValue(raw, StoredMessage.class));
            } catch (JsonProcessingException ignored) {
                // Skip malformed entries instead of failing the whole load
            }
        }
        return messages;
    }

    /**
     * Append one full conversation turn (user + assistant),
     * then apply window trimming and TTL on the Redis list.
     */
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
                // Ignore serialization issues to avoid blocking the response
            }
        }

        // --- Window trimming: ensure each session keeps at most MAX_MESSAGES_PER_SESSION messages ---
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_MESSAGES_PER_SESSION) {
            long start = size - MAX_MESSAGES_PER_SESSION;
            long end = size - 1;
            redisTemplate.opsForList().trim(key, start, end);
        }

        // --- TTL: temporary uses a short TTL, otherwise use a rolling long TTL ---
        if (temporary) {
            redisTemplate.expire(key, TEMPORARY_TTL);
        } else {
            redisTemplate.expire(key, PERSISTENT_TTL);
        }
    }

    /**
     * Render history text for LLM:
     *  - Take only the latest MAX_MESSAGES_IN_PROMPT messages
     *  - Join them as "role: content" lines
     */
    public String renderHistory(List<StoredMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(no prior conversation)";
        }

        int size = messages.size();
        int startIdx = Math.max(0, size - MAX_MESSAGES_IN_PROMPT);
        List<StoredMessage> window = messages.subList(startIdx, size);

        String joined = window.stream()
                .map(m -> m.role() + ": " + m.content())
                .collect(Collectors.joining("\n"));

        return joined;
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    public record StoredMessage(String role, String content, long timestamp) { }
}
