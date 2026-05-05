package com.example.rag.llm.zhipu;


import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ZhipuChatService {

    private final RestClient restClient;
    private final ZhipuProperties properties;

    public ZhipuChatService(ZhipuProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    public String chat(String prompt) {
        Map<String, Object> body = Map.of(
                "model", properties.chatModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个严谨的知识库问答助手。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.2
        );

        Map response = restClient.post()
                .uri(properties.chatUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.normalizedApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        List choices = (List) response.get("choices");
        Map first = (Map) choices.get(0);
        Map message = (Map) first.get("message");

        return message.get("content").toString();
    }

    public String answer(String context, String question) {

        String prompt = """
                请你根据下面的资料回答用户问题。

                要求：
                1. 只能根据资料回答；
                2. 如果资料中没有答案，就回答：资料中没有相关信息；
                3. 回答要简洁、准确。

                【资料】
                %s

                【用户问题】
                %s
                """.formatted(context, question);

        return chat(prompt);
    }
}
