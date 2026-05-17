package com.example.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OllamaRestClientConfig {

    @Bean("ollamaRestClient")
    public RestClient ollamaRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 连接超时：30秒
        factory.setConnectTimeout(30_000);

        // 读取超时：5分钟
        factory.setReadTimeout(300_000);

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}