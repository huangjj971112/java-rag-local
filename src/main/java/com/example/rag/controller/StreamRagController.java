package com.example.rag.controller;

import com.example.rag.RagProperties;
import com.example.rag.ZhipuChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rag/stream")
@CrossOrigin(origins = "http://localhost:63342")
public class StreamRagController {

    private final VectorStore vectorStore;
    private final ZhipuChatClient zhipuChatClient;
    private final RagProperties ragProperties;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public StreamRagController(
            VectorStore vectorStore,
            ZhipuChatClient zhipuChatClient,
            RagProperties ragProperties
    ) {
        this.vectorStore = vectorStore;
        this.zhipuChatClient = zhipuChatClient;
        this.ragProperties = ragProperties;
    }

    @GetMapping(value = "ask", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stream(@RequestParam("question") String question) {

        SseEmitter emitter = new SseEmitter(60_000L);

        executorService.submit(() -> {
            try {
                // 1. 检索
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(question)
                                .topK(ragProperties.topK())
                                .build()
                );

                // 2. 先发送 sources
                List<String> sources = docs.stream()
                        .map(doc -> String.valueOf(doc.getMetadata().getOrDefault("fileName", "未知文件"))
                                + "："
                                + shorten(doc.getText(), 120))
                        .toList();

                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(sources, MediaType.APPLICATION_JSON));

                // 3. 拼接上下文
                String context = buildContext(docs, ragProperties.maxContextChars());

                // 4. prompt
                String prompt = """
                    你是一个严谨的本地知识库问答助手。
                    请只根据【资料】回答【问题】。
                    如果资料中没有答案，请回答：资料中没有找到相关信息。
                    不要编造内容。

                    【资料】
                    %s

                    【问题】
                    %s
                    """.formatted(context, question);

                // 5. 真流式调用
                zhipuChatClient.streamChat(prompt, token -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(token, MediaType.valueOf("text/plain;charset=UTF-8")));
                    } catch (Exception ignored) {
                    }
                });

                // 6. done
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("[DONE]"));

                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                } catch (Exception ignored) {}

                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String buildContext(List<Document> docs, Integer maxContextChars) {
        int limit = maxContextChars == null ? 3000 : maxContextChars;

        StringBuilder context = new StringBuilder();

        for (Document doc : docs) {
            String text = doc.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            String separator = "\n\n---\n\n";

            if (context.length() + separator.length() + text.length() > limit) {
                int remaining = limit - context.length() - separator.length();

                if (remaining > 100) {
                    context.append(separator)
                            .append(text, 0, Math.min(remaining, text.length()));
                }

                break;
            }

            if (!context.isEmpty()) {
                context.append(separator);
            }

            context.append(text);
        }

        return context.toString();
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        String cleanText = text.replaceAll("\\s+", " ").trim();

        if (cleanText.length() <= maxLength) {
            return cleanText;
        }

        return cleanText.substring(0, maxLength) + "...";
    }
}