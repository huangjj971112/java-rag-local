package com.example.rag;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ZhipuProperties.class,
        RagProperties.class
})
public class ZhipuConfig {
}
