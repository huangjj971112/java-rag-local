package com.example.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

public class ZhipuEmbeddingModel extends AbstractEmbeddingModel {

    private final RestClient restClient;
    private final ZhipuProperties properties;

    public ZhipuEmbeddingModel(RestClient.Builder builder,
                               ZhipuProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.embeddingUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return null;
    }

    /**
     * ✅ 只实现这个方法就够了
     */
    @Override
    public float[] embed(String text) {

        Map<String, Object> body = Map.of(
                "model", properties.embeddingModel(),
                "input", List.of(text)
        );

        ZhipuResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ZhipuResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new RuntimeException("Embedding 返回为空");
        }

        return response.data().get(0).embedding();
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    // ===== DTO =====
    public record ZhipuResponse(List<Item> data) {}

    public record Item(Integer index, float[] embedding) {}
}