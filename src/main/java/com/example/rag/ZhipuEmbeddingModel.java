package com.example.rag;

import com.example.rag.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ZhipuEmbeddingModel extends AbstractEmbeddingModel {

    private final RestClient restClient;
    private final ZhipuProperties properties;

    public ZhipuEmbeddingModel(RestClient.Builder builder,
                               ZhipuProperties properties) {
        this.properties = properties;

        String apiKey = properties.normalizedApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("ZHIPU_API_KEY 未读取到，请检查环境变量或 application.yml");
        }

        apiKey = apiKey.trim();

        System.out.println("ZHIPU API KEY 已读取，长度: " + apiKey.length());

        this.restClient = builder
                .baseUrl(properties.embeddingUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {

        List<String> inputs = request.getInstructions();

        List<org.springframework.ai.embedding.Embedding> embeddings = new ArrayList<>();

        for (int i = 0; i < inputs.size(); i++) {
            float[] vector = embed(inputs.get(i));

            embeddings.add(new org.springframework.ai.embedding.Embedding(
                    vector,
                    i
            ));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(String text) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding 文本不能为空");
        }

        if (text.length() > 2000) {
            text = text.substring(0, 2000);
        }

        Map<String, Object> body = Map.of(
                "model", properties.embeddingModel(),
                "input", List.of(text)
        );

        int retry = 0;

        while (retry < 3) {
            try {
                String raw = restClient.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);

                if (raw == null || raw.isBlank()) {
                    throw new RuntimeException("Embedding 返回为空");
                }

                System.out.println("embedding raw length: " + raw.length());

                ZhipuResponse response = JsonUtils.fromJson(raw, ZhipuResponse.class);

                if (response == null || response.data() == null || response.data().isEmpty()) {
                    throw new RuntimeException("Embedding 返回 data 为空");
                }

                float[] embedding = response.data().get(0).embedding();

                if (embedding == null || embedding.length == 0) {
                    throw new RuntimeException("Embedding 向量为空");
                }

                System.out.println("embedding dimension: " + embedding.length);

                return embedding;

            } catch (Exception e) {
                retry++;
                System.err.println("embedding失败，第" + retry + "次重试: " + e.getMessage());

                if (retry >= 3) {
                    throw new RuntimeException("Embedding 最终失败，请检查 API Key / 请求头 / 模型配置", e);
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Embedding 重试被中断", interruptedException);
                }
            }
        }

        throw new RuntimeException("Embedding 未知失败");
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZhipuResponse(List<Item> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Integer index, float[] embedding) {}
}
