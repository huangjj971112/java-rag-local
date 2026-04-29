package com.example.rag;


import ai.z.openapi.service.chat.ChatService;
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
                        doc.getText()
                ))
                .toList();

        return new RagAnswer(answer, sources);
    }


    @GetMapping("/ask1")
    public AskResponse ask1(@RequestParam("question") String question) {

        List<Document> documents = vectorStore.similaritySearch(question)
                .stream()
                .limit(3)
                .toList();

        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

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

        List<Source> sources = documents.stream()
                .map(doc -> new Source(
                        String.valueOf(doc.getMetadata().get("fileName")),
                        String.valueOf(doc.getMetadata().get("source")),
                        doc.getText()
                ))
                .toList();

        return new AskResponse(answer, sources);
    }

    public record RagAnswer(
            String answer,
            List<Source> sources
    ) {}

    public record Source(
            String fileName,
            String source,
            String content
    ) {}


    public record AskResponse(
            String answer,
            List<Source> sources
    ) {}


}