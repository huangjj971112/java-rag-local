package com.example.rag.llm.zhipu;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ZhipuChatClient {

    private final RestClient restClient;
    private final ZhipuProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZhipuChatClient(RestClient.Builder builder,
                           ZhipuProperties properties) {

        this.properties = properties;

        this.restClient = builder
                .baseUrl(properties.chatUrl())
                .defaultHeader("Authorization", "Bearer " + properties.normalizedApiKey())
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(properties.chatUrl())
                .defaultHeader("Authorization", "Bearer " + properties.normalizedApiKey())
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

    public record ZhipuResponse(List<Choice> choices) {
    }

    public record Choice(Message message) {
    }

    public record Message(String role, String content) {
    }


    public void streamChat(List<Map<String, String>> messages, Consumer<String> onToken) {
        Map<String, Object> body = Map.of(
                "model", properties.chatModel(),
                "messages", messages,
                "stream", true
        );

        Flux<String> flux = webClient.post()
                .uri(properties.chatUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + properties.apiKey().trim())
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .flatMapMany(errorBody -> {
                                    log.error("智谱状态码 = {}" , response.statusCode());
                                    log.error("智谱错误返回 = {}" , errorBody);
                                    return Flux.error(new RuntimeException("智谱接口错误：" + errorBody));
                                });
                    }

                    return response.bodyToFlux(String.class);
                });

        for (String line : flux.toIterable()) {
            try {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                String json = line.trim();

                if (json.startsWith("data:")) {
                    json = json.substring(5).trim();
                }

                if ("[DONE]".equals(json)) {
                    break;
                }

                JsonNode node = objectMapper.readTree(json);

                JsonNode contentNode = node
                        .path("choices")
                        .path(0)
                        .path("delta")
                        .path("content");

                if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                    String token = contentNode.asText();

                    if (!token.isEmpty()) {
                        onToken.accept(token);
                    }
                }

            } catch (Exception e) {
                System.out.println("解析流式响应失败: " + line);
            }
        }
    }

}
