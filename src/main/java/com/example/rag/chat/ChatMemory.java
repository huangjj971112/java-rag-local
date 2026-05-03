package com.example.rag.chat;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ChatMemory {

    // 👉 简单版：一个用户一条对话（你现在够用）
    private final Map<String, List<ChatMessage>> store = new HashMap<>();

    public List<ChatMessage> get(String sessionId) {
        return store.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public void add(String sessionId, ChatMessage message) {
        get(sessionId).add(message);
    }

    public void clear(String sessionId) {
        store.remove(sessionId);
    }
}
