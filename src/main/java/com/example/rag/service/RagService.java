package com.example.rag.service;

import com.example.rag.chat.ChatMessage;
import com.example.rag.chat.LocalChatMemory;
import com.example.rag.config.RagProperties;
import com.example.rag.dto.response.SourceVO;
import com.example.rag.llm.zhipu.ZhipuChatClient;
import com.example.rag.rerank.LlmRerankService;
import com.example.rag.rerank.RuleRerankService;
import com.example.rag.rewrite.MultiQueryService;
import com.example.rag.rewrite.QueryRewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final ZhipuChatClient zhipuChatClient;
    private final LocalChatMemory localChatMemory;

    private final QueryRewriteService queryRewriteService;
    private final MultiQueryService multiQueryService;
    private final RuleRerankService ruleRerankService;
    private final LlmRerankService llmRerankService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public void streamAsk(String question, String sessionId, SseEmitter emitter) {
        executorService.submit(() -> {
            try {
                // 1. 查询历史
                List<ChatMessage> history = localChatMemory.get(sessionId);

                List<ChatMessage> limitedHistory = limitHistoryByTokens(
                        history,
                        ragProperties.maxHistoryMessages(),
                        ragProperties.maxHistoryTokens()
                );

                // 2. Query Rewrite
                String rewrittenQuery = queryRewriteService.rewrite(question);

                emitter.send(SseEmitter.event()
                        .name("rewrite")
                        .data(rewrittenQuery));

                // 3. Multi Query
                List<String> queries = multiQueryService.generate(rewrittenQuery);
                log.info("MultiQuery queries={}", queries);

                // 4. 多路召回
                List<Document> recallDocs = new ArrayList<>();

                for (String q : queries) {
                    List<Document> docs = vectorStore.similaritySearch(
                            SearchRequest.builder()
                                    .query(q)
                                    .topK(ragProperties.topK())
                                    .build()
                    );

                    recallDocs.addAll(docs);
                }

                log.info("MultiQuery 去重前 recallDocs={}", recallDocs.size());

                // 5. 去重
                recallDocs = deduplicateDocs(recallDocs);

                log.info("MultiQuery 去重后 recallDocs={}", recallDocs.size());

                // 6. Rerank
                List<Document> docs;

                if (ragProperties.useLlmRerank()) {
                    docs = llmRerankService.rerank(
                            question,
                            rewrittenQuery,
                            recallDocs,
                            ragProperties.finalTopK()
                    );
                } else {
                    docs = ruleRerankService.rerank(
                            question,
                            rewrittenQuery,
                            recallDocs,
                            ragProperties.finalTopK()
                    );
                }

                log.info("召回数量={}, 重排后数量={}", recallDocs.size(), docs.size());

                // 7. 返回 sources
                List<SourceVO> sources = buildSources(docs);

                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(sources, MediaType.APPLICATION_JSON));

                // 8. 构造 context
                String context = buildContextWithRefs(docs, ragProperties.maxContextChars());

                // 9. 构造 messages
                List<Map<String, String>> messages = buildMessages(
                        limitedHistory,
                        context,
                        question
                );

                // 10. 打印入参
                logRequestMessages(sessionId, history, limitedHistory, messages);

                // 11. 先保存用户问题
                localChatMemory.add(sessionId, new ChatMessage("user", question));

                // 12. 流式调用并收集回答
                StringBuilder answerBuilder = new StringBuilder();

                zhipuChatClient.streamChat(messages, token -> {
                    try {
                        answerBuilder.append(token);

                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(token, MediaType.valueOf("text/plain;charset=UTF-8")));
                    } catch (Exception e) {
                        log.error("SSE 发送 token 失败", e);
                    }
                });

                // 13. 保存 assistant 回答
                String answer = answerBuilder.toString();

                if (!answer.isBlank()) {
                    localChatMemory.add(sessionId, new ChatMessage("assistant", answer));
                }

                log.info("本轮回答已写入 memory，当前会话消息数量={}",
                        localChatMemory.get(sessionId).size());

                // 14. done
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("[DONE]"));

                emitter.complete();

            } catch (Exception e) {
                log.error("RAG 流式问答失败", e);

                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage(), MediaType.TEXT_PLAIN));
                } catch (Exception ignored) {
                }

                emitter.complete();
            }
        });
    }

    public void clearMemory(String sessionId) {
        localChatMemory.clear(sessionId);
    }

    private List<Map<String, String>> buildMessages(List<ChatMessage> limitedHistory,
                                                    String context,
                                                    String question) {
        List<Map<String, String>> messages = new ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", """
                        你是一个严谨的本地知识库问答助手。
                        请结合历史对话和检索资料回答问题。
                        如果资料中没有答案，请回答：资料中没有找到相关信息。
                        不要编造内容。
                        回答中必须使用资料引用编号，例如：[1]、[2]。
                        """
        ));

        for (ChatMessage msg : limitedHistory) {
            messages.add(Map.of(
                    "role", msg.role(),
                    "content", msg.content()
            ));
        }

        messages.add(Map.of(
                "role", "user",
                "content", """
                        【检索资料】
                        %s
                        
                        【当前问题】
                        %s
                        """.formatted(context, question)
        ));

        return messages;
    }

    private List<SourceVO> buildSources(List<Document> docs) {
        List<SourceVO> sources = new ArrayList<>();

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);

            String fileName = String.valueOf(
                    doc.getMetadata().getOrDefault("fileName", "未知文件")
            );

            sources.add(new SourceVO(
                    i + 1,
                    fileName,
                    shorten(doc.getText(), 200)
            ));
        }

        return sources;
    }

    private String buildContextWithRefs(List<Document> docs, Integer maxContextChars) {
        int limit = maxContextChars == null ? 3000 : maxContextChars;

        StringBuilder context = new StringBuilder();

        for (int i = 0; i < docs.size(); i++) {
            if (context.length() >= limit) {
                break;
            }

            Document doc = docs.get(i);
            String text = doc.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            text = text.length() > 500 ? text.substring(0, 500) : text;

            String chunk = "[" + (i + 1) + "] " + text;
            String separator = context.isEmpty() ? "" : "\n\n---\n\n";

            if (context.length() + separator.length() + chunk.length() > limit) {
                int remaining = limit - context.length() - separator.length();

                if (remaining > 50) {
                    context.append(separator)
                            .append(chunk, 0, Math.min(remaining, chunk.length()));
                }

                break;
            }

            context.append(separator).append(chunk);
        }

        return context.toString();
    }

    private List<Document> deduplicateDocs(List<Document> docs) {
        Map<String, Document> unique = new LinkedHashMap<>();

        for (Document doc : docs) {
            String chunkHash = String.valueOf(
                    doc.getMetadata().get("chunkHash")
            );

            unique.putIfAbsent(chunkHash, doc);
        }

        return new ArrayList<>(unique.values());
    }

    private List<ChatMessage> limitHistoryByTokens(List<ChatMessage> history,
                                                   int maxMessages,
                                                   int maxTokens) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> result = new ArrayList<>();

        int totalTokens = 0;
        int count = 0;

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);

            if (msg == null || msg.content() == null || msg.content().isBlank()) {
                continue;
            }

            int msgTokens = estimateTokens(msg.content());

            if (count >= maxMessages) {
                break;
            }

            if (totalTokens + msgTokens > maxTokens) {
                break;
            }

            result.add(msg);
            totalTokens += msgTokens;
            count++;
        }

        Collections.reverse(result);

        return result;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int tokens = 0;

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                tokens += 2;
            } else if (Character.isWhitespace(c)) {
                tokens += 0;
            } else {
                tokens += 1;
            }
        }

        return tokens;
    }

    private boolean isChinese(char c) {
        return c >= '\u4e00' && c <= '\u9fff';
    }

    private void logRequestMessages(String sessionId,
                                    List<ChatMessage> history,
                                    List<ChatMessage> limitedHistory,
                                    List<Map<String, String>> messages) {
        log.info("本次会话 sessionId={}", sessionId);

        int historyTokens = limitedHistory.stream()
                .mapToInt(m -> estimateTokens(m.content()))
                .sum();

        log.info("历史消息数量={}, 实际携带历史消息数量={}, 历史携带估算token={}",
                history.size(),
                limitedHistory.size(),
                historyTokens);

        log.info("最终发送给智谱 messages 数量={}", messages.size());

        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> msg = messages.get(i);

            log.info("messages[{}].role={}", i, msg.get("role"));
            log.info("messages[{}].content={}", i, shorten(msg.get("content"), 300));
        }
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