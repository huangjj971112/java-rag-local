package com.example.rag.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalChatMemory {

    private static final String KEY_PREFIX = "rag:chat:memory:";
    private static final int MAX_MESSAGES = 20;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public List<ChatMessage> get(String sessionId) {
        String key = buildKey(sessionId);

        List<String> jsonList = stringRedisTemplate.opsForList()
                .range(key, 0, -1);

        if (jsonList == null || jsonList.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChatMessage> messages = new ArrayList<>();

        for (String json : jsonList) {
            try {
                messages.add(objectMapper.readValue(json, ChatMessage.class));
            } catch (Exception e) {
                log.warn("解析 Redis ChatMemory 失败，json={}", json, e);
            }
        }

        return messages;
    }

    public void add(String sessionId, ChatMessage message) {
        String key = buildKey(sessionId);

        try {
            String json = objectMapper.writeValueAsString(message);

            stringRedisTemplate.opsForList().rightPush(key, json);

            // 只保留最近 MAX_MESSAGES 条
            stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGES, -1);

            log.debug("写入 Redis ChatMemory: sessionId={}, role={}", sessionId, message.role());

        } catch (Exception e) {
            log.error("写入 Redis ChatMemory 失败", e);
        }
    }

    public void clear(String sessionId) {
        String key = buildKey(sessionId);
        stringRedisTemplate.delete(key);
        log.info("清空 Redis ChatMemory: sessionId={}", sessionId);
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}