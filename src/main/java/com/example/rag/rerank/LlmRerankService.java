package com.example.rag.rerank;

import com.example.rag.llm.zhipu.ZhipuChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmRerankService implements RerankService {

    private final ZhipuChatClient zhipuChatClient;

    // fallback
    private final RuleRerankService ruleRerankService;

    @Override
    public List<Document> rerank(String question,
                                 String rewrittenQuery,
                                 List<Document> docs,
                                 int finalTopK) {

        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        try {

            // 1. 构造候选文档
            StringBuilder candidates = new StringBuilder();

            for (int i = 0; i < docs.size(); i++) {

                String text = shorten(
                        docs.get(i).getText(),
                        200
                );

                candidates.append("[")
                        .append(i)
                        .append("] ")
                        .append(text)
                        .append("\n\n");
            }

            // 2. prompt
            String prompt = """
                    你是一个文档相关性排序助手。
                    请根据问题，从候选文档中选出最相关的 %d 条。

                    要求：
                    1. 只返回编号，例如：[2,5,1]
                    2. 按相关性从高到低排序
                    3. 不要解释
                    4. 不要返回不存在的编号

                    【问题】
                    %s

                    【候选文档】
                    %s
                    """.formatted(
                    finalTopK,
                    question,
                    candidates
            );

            List<Map<String, String>> messages = List.of(
                    Map.of(
                            "role",
                            "user",
                            "content",
                            prompt
                    )
            );

            StringBuilder result = new StringBuilder();

            zhipuChatClient.streamChat(messages, token -> {
                result.append(token);
            });

            String output = result.toString().trim();

            log.info("LLM Rerank 输出={}", output);

            // 3. 解析编号
            List<Integer> indices = parseIndices(output);

            List<Document> reranked = new ArrayList<>();

            for (Integer idx : indices) {

                if (idx >= 0 && idx < docs.size()) {
                    reranked.add(docs.get(idx));
                }

                if (reranked.size() >= finalTopK) {
                    break;
                }
            }

            if (!reranked.isEmpty()) {
                return reranked;
            }

        } catch (Exception e) {

            log.warn("LLM rerank 失败，降级规则 rerank", e);
        }

        // fallback
        return ruleRerankService.rerank(
                question,
                rewrittenQuery,
                docs,
                finalTopK
        );
    }

    private List<Integer> parseIndices(String text) {

        List<Integer> result = new ArrayList<>();

        Matcher matcher = Pattern
                .compile("\\d+")
                .matcher(text);

        while (matcher.find()) {
            result.add(
                    Integer.parseInt(
                            matcher.group()
                    )
            );
        }

        return result;
    }

    private String shorten(String text, int maxLength) {

        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}