package com.example.rag.llm.zhipu;

import com.example.rag.RagProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ZhipuProperties.class,
        RagProperties.class
})
public class ZhipuConfig {
}
