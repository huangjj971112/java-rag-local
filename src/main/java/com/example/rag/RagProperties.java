package com.example.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        String docsPath,
        Integer topK,
        Integer maxContextChars
) {
}
