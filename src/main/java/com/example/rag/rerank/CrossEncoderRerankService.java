package com.example.rag.rerank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrossEncoderRerankService {

    private final RuleRerankService ruleRerankService;

    private final RestClient restClient = RestClient.builder().build();

    private static final String RERANK_URL =
            "http://localhost:9000/rerank";

    public List<Document> rerank(String question,
                                 String rewrittenQuery,
                                 List<Document> docs,
                                 int finalTopK) {

        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        try {

            List<String> documentTexts = docs.stream()
                    .map(Document::getText)
                    .toList();

            Map<String, Object> body = Map.of(
                    "query", rewrittenQuery,
                    "documents", documentTexts,
                    "top_k", finalTopK
            );

            Map<String, Object> response = restClient.post()
                    .uri(RERANK_URL)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            List<Map<String, Object>> results =
                    (List<Map<String, Object>>) response.get("results");

            List<Document> reranked = results.stream()
                    .sorted(Comparator.comparingDouble(
                            item -> -Double.parseDouble(
                                    String.valueOf(item.get("score"))
                            )
                    ))
                    .map(item -> {
                        int index = Integer.parseInt(
                                String.valueOf(item.get("index"))
                        );

                        return docs.get(index);
                    })
                    .toList();

            log.info("CrossEncoder重排完成: 输入={}, 输出={}",
                    docs.size(),
                    reranked.size());

            return reranked;

        } catch (Exception e) {

            log.error("CrossEncoder重排失败，降级RuleRerank", e);

            return ruleRerankService.rerank(
                    question,
                    rewrittenQuery,
                    docs,
                    finalTopK
            );
        }
    }
}