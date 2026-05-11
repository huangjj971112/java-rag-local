package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        String docsPath,
        int topK,
        int finalTopK,
        int maxContextChars,
        int maxHistoryMessages,
        int maxHistoryTokens,
        boolean useLlmRerank
) {
}
