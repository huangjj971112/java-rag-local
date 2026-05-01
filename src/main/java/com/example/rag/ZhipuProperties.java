package com.example.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.zhipu")
public record ZhipuProperties(
        String apiKey,
        String embeddingUrl,
        String embeddingModel,
        String chatModel,
        String chatUrl
) {
    public String normalizedApiKey() {
        if (apiKey == null) {
            return null;
        }

        String normalized = apiKey.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            normalized = normalized.substring("Bearer ".length()).trim();
        }
        if (normalized.regionMatches(true, 0, "key=", 0, "key=".length())) {
            normalized = normalized.substring("key=".length()).trim();
        }

        return normalized;
    }

    public String embeddingUrl() {
        return embeddingUrl == null || embeddingUrl.isBlank()
                ? "https://open.bigmodel.cn/api/paas/v4/embeddings"
                : embeddingUrl;
    }

    public String embeddingModel() {
        return embeddingModel == null || embeddingModel.isBlank()
                ? "embedding-3"
                : embeddingModel;
    }

    public String chatModel() {
        return chatModel == null || chatModel.isBlank()
                ? "glm-4-flash"
                : chatModel;
    }

    public String chatUrl() {
        return chatUrl == null || chatUrl.isBlank()
                ? "https://open.bigmodel.cn/api/paas/v4/chat/completions"
                : chatUrl;
    }
}
