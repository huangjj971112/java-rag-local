package com.example.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        ZhipuProperties.class,
        RagProperties.class
})
public class AiConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            RestClient.Builder builder,
            ZhipuProperties properties
    ) {
        return new ZhipuEmbeddingModel(builder, properties);
    }
}