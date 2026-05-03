package com.example.rag.chat;
public record ChatMessage(
        String role,   // user / assistant
        String content
) {}
