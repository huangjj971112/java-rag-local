package com.example.rag.controller;

import com.example.rag.RagProperties;
import com.example.rag.chat.ChatMemory;
import com.example.rag.chat.ChatMessage;
import com.example.rag.dto.SourceVO;
import com.example.rag.ZhipuChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/rag/stream")
@RequiredArgsConstructor
public class StreamRagController {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;
    private final ZhipuChatClient zhipuChatClient;
    private final ChatMemory chatMemory;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @GetMapping(value = "/ask", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestParam("question") String question,
                             @RequestParam(value = "sessionId", defaultValue = "default") String sessionId) {

        SseEmitter emitter = new SseEmitter(60_000L);

        executorService.submit(() -> {
            try {
                // 1. 查询历史
                List<ChatMessage> history = chatMemory.get(sessionId);

                List<ChatMessage> limitedHistory = limitHistoryByTokens(
                        history,
                        ragProperties.maxHistoryMessages(),
                        ragProperties.maxHistoryTokens()
                );
                // 2. RAG 检索
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(question)
                                .topK(ragProperties.topK())
                                .build()
                );

                // 3. 先返回 sources
                List<SourceVO> sources = buildSources(docs);

                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(sources, MediaType.APPLICATION_JSON));

                // 4. 构造带引用编号的上下文
                String context = buildContextWithRefs(docs, ragProperties.maxContextChars());

                // 5. 构造 messages
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

                // 6. 打印入参，确认多轮是否生效
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

                // 7. 注意：先保存当前用户问题
                chatMemory.add(sessionId, new ChatMessage("user", question));

                // 8. 流式调用，并收集完整回答
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

                // 9. 保存 assistant 回答
                String answer = answerBuilder.toString();
                if (!answer.isBlank()) {
                    chatMemory.add(sessionId, new ChatMessage("assistant", answer));
                }

                log.info("本轮回答已写入 memory，当前会话消息数量={}", chatMemory.get(sessionId).size());

                // 10. done
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

        return emitter;
    }

    @DeleteMapping("/memory")
    public String clearMemory(@RequestParam(value = "sessionId", defaultValue = "default") String sessionId) {
        chatMemory.clear(sessionId);
        return "已清空会话：" + sessionId;
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

    private List<ChatMessage> limitHistoryByTokens(List<ChatMessage> history,
                                                   int maxMessages,
                                                   int maxTokens) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> result = new ArrayList<>();

        int totalTokens = 0;
        int count = 0;

        // 从后往前取，优先保留最近对话
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