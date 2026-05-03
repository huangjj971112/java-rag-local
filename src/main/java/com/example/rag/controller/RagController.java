package com.example.rag.controller;


import com.example.rag.RagProperties;
import com.example.rag.ZhipuChatClient;
import com.example.rag.ZhipuChatService;
import com.example.rag.dto.Source;
import com.example.rag.model.Ask1Source;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.ai.document.Document;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("rag")
public class RagController {

    private final ZhipuChatClient zhipuChatClient;
    private final ZhipuChatService chatService;
    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    public RagController(
            VectorStore vectorStore,
            ZhipuChatService chatService, ZhipuChatClient zhipuChatClient,
            RagProperties ragProperties
    ) {
        this.zhipuChatClient = zhipuChatClient;
        this.vectorStore = vectorStore;
        this.chatService = chatService;
        this.ragProperties = ragProperties;
    }

    @GetMapping("/ask")
    public RagAnswer ask(@RequestParam("question") String q) {

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(q)
                        .topK(ragProperties.topK())
                        .build()
        );

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
            你是一个严谨的本地知识库问答助手。
            请只根据【资料】回答【问题】。
            如果资料中没有答案，请回答：资料中没有找到相关信息。
            不要编造内容。

            【资料】
            %s

            【问题】
            %s
            """.formatted(context, q);

        String answer = zhipuChatClient.chat(prompt);

        List<Source> sources = docs.stream()
                .map(doc -> new Source(
                        String.valueOf(doc.getMetadata().get("fileName")),
                        String.valueOf(doc.getMetadata().get("source")),
                        doc.getText(),
                        0.0,
                        null
                ))
                .toList();

        return new RagAnswer(answer, sources);
    }

    public record RagAnswer(
            String answer,
            List<Source> sources
    ) {}



    @GetMapping("/askFromLocalDocx")
    public AskResponse ask1(@RequestParam("question") String question) {

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(ragProperties.topK())
                        .build()
        );

        String context = buildContext(documents, ragProperties.maxContextChars());

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

        String answer = zhipuChatClient.chat(prompt);

        List<Ask1Source> sources = documents.stream()
                .map(this::toSource)
                .toList();

        return new AskResponse(answer, sources);
    }

    private Ask1Source toSource(Document doc) {
        String fileName = String.valueOf(
                doc.getMetadata().getOrDefault("fileName", "未知文件")
        );

        String chapter = String.valueOf(
                doc.getMetadata().getOrDefault("chapter", "未知章节")
        );

        String content = doc.getText();

        Double score = doc.getScore();

        return new Ask1Source(
                fileName,
                chapter,
                content,
                score == null ? 0.0 : score
        );
    }

    private String buildContext(List<Document> docs, Integer maxContextChars) {
        int limit = maxContextChars == null ? 3000 : maxContextChars;

        StringBuilder context = new StringBuilder();

        for (Document doc : docs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            String fileName = String.valueOf(
                    doc.getMetadata().getOrDefault("fileName", "未知文件")
            );

            String chapter = String.valueOf(
                    doc.getMetadata().getOrDefault("chapter", "未知章节")
            );

            String chunk = """
                【来源文件】%s
                【所属章节】%s
                【内容】
                %s
                """.formatted(fileName, chapter, text);

            String separator = "\n\n---\n\n";

            if (context.length() + separator.length() + chunk.length() > limit) {
                int remaining = limit - context.length() - separator.length();

                if (remaining > 100) {
                    if (!context.isEmpty()) {
                        context.append(separator);
                    }
                    context.append(chunk, 0, Math.min(remaining, chunk.length()));
                }

                break;
            }

            if (!context.isEmpty()) {
                context.append(separator);
            }

            context.append(chunk);
        }

        return context.toString();
    }

    public record AskResponse(
            String answer,
            List<Ask1Source> sources
    ) {}


    @GetMapping("/askFromPgsql")
    public RagAnswer askFromPgsql(@RequestParam("question") String question) {

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(ragProperties.topK())
                        .build()
        );

        String context = buildContext(docs, ragProperties.maxContextChars());

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

        String answer = zhipuChatClient.chat(prompt);

        List<Source> sources = docs.stream()
                .map(doc -> new Source(
                        String.valueOf(doc.getMetadata().get("fileName")),
                        String.valueOf(doc.getMetadata().get("source")),
                        String.valueOf(doc.getMetadata().get("chunkHash")),
                        doc.getScore(),
                        shorten(doc.getText(), 300)
                ))
                .toList();

        return new RagAnswer(answer, sources);
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