package com.example.rag.rewrite;

import com.example.rag.llm.zhipu.ZhipuChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ZhipuChatService zhipuChatService;

    public String rewrite(String question) {

        try {

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", """
你是一个 RAG 检索优化助手。
请将用户问题改写为更适合知识库检索的表达。

要求：
1. 保持原意
2. 使用标准术语
3. 更适合知识库检索
4. 不要解释
"""),
                    Map.of("role", "user", "content", question)
            );

            StringBuilder rewritten = new StringBuilder();

            zhipuChatService.streamChat(messages, token -> {
                rewritten.append(token);
            });

            String result = rewritten.toString().trim();

            log.info("QueryRewrite 原问题={}", question);
            log.info("QueryRewrite 改写后={}", result);

            if (result.isBlank()) {
                return question;
            }

            return result;

        } catch (Exception e) {
            log.warn("QueryRewrite 失败", e);
            return question;
        }
    }
}