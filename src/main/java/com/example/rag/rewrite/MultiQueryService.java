package com.example.rag.rewrite;

import com.example.rag.llm.zhipu.ZhipuChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiQueryService {

    private final ZhipuChatService zhipuChatClient;

    public List<String> generate(String question) {

        try {

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", """
你是一个 RAG 检索优化助手。
请针对用户问题生成 3 个不同检索表达。

要求：
1. 保持原意
2. 使用不同表达
3. 每行一个
4. 不要解释
"""),
                    Map.of("role", "user", "content", question)
            );

            StringBuilder result = new StringBuilder();

            zhipuChatClient.streamChat(messages, token -> {
                result.append(token);
            });

            String output = result.toString();

            log.info("MultiQuery 输出=\n{}", output);

            List<String> queries = Arrays.stream(output.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .limit(3)
                    .toList();

            if (!queries.isEmpty()) {
                return queries;
            }

        } catch (Exception e) {
            log.warn("MultiQuery 失败", e);
        }

        return List.of(question);
    }
}