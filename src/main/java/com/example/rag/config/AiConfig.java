package com.example.rag.config;

import com.example.rag.RagProperties;
import com.example.rag.llm.zhipu.ZhipuProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ZhipuProperties.class,
        RagProperties.class
})
public class AiConfig {
}