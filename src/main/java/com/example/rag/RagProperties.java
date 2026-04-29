package com.example.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix ="app.rag")
public record RagProperties(
        String docsPath,
        Integer topK
) {
}
