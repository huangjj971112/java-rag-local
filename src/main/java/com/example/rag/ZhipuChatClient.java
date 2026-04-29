package com.example.rag;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ZhipuChatClient {

    private final RestClient restClient;
    private final ZhipuProperties properties;

    public ZhipuChatClient(RestClient.Builder builder,
                           ZhipuProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.chatUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public String chat(String prompt) {

        Map<String, Object> body = Map.of(
                "model", properties.chatModel(),
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        ZhipuResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ZhipuResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("智谱返回为空");
        }

        return response.choices().get(0).message().content();
    }

    // ===== 返回结构 =====

    public record ZhipuResponse(List<Choice> choices) {}

    public record Choice(Message message) {}

    public record Message(String role, String content) {}
}