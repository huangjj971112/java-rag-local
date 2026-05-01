package com.example.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Component
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    public IngestionService(VectorStore vectorStore, RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
    }

    public void ingest() throws Exception {

        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        Resource[] resources = resolver.getResources(ragProperties.docsPath());

        if (resources.length == 0) {
            log.warn("没有找到文档: {}", ragProperties.docsPath());
            return;
        }

        List<Document> allDocs = new ArrayList<>();

        for (Resource resource : resources) {
            try {
                String fileName = Objects.requireNonNullElse(resource.getFilename(), "未知文件");
                String source = resource.getDescription();

                TikaDocumentReader reader = new TikaDocumentReader(resource);

                List<Document> docs = reader.get().stream()
                        .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
                        .map(doc -> new Document(
                                doc.getText(),
                                Map.of(
                                        "fileName", fileName,
                                        "source", source
                                )
                        ))
                        .toList();

                allDocs.addAll(docs);

                log.info("文档读取成功: {}, 段落数: {}", fileName, docs.size());

            } catch (Exception e) {
                log.warn("文档读取失败，已跳过: {}, 原因: {}",
                        resource.getFilename(),
                        e.getMessage());
            }
        }

        TokenTextSplitter splitter = new TokenTextSplitter(300, 50, 5, 10000, true);
        List<Document> splitDocs = splitter.apply(allDocs);

        // 应用层去重
        Set<String> seenHashes = new HashSet<>();
        List<Document> uniqueDocs = new ArrayList<>();

        for (Document doc : splitDocs) {
            String text = doc.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            String chunkHash = sha256(text);

            if (!seenHashes.add(chunkHash)) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("chunkHash", chunkHash);

            uniqueDocs.add(new Document(text, metadata));
        }

        log.info("切分完成: 原始 chunks={}, 去重后 chunks={}",
                splitDocs.size(),
                uniqueDocs.size());

        for (int i = 0; i < uniqueDocs.size(); i++) {
            Document doc = uniqueDocs.get(i);

            try {
                vectorStore.add(List.of(doc));

                log.info("chunk入库成功: {}/{} fileName={} hash={}",
                        i + 1,
                        uniqueDocs.size(),
                        doc.getMetadata().get("fileName"),
                        doc.getMetadata().get("chunkHash"));

                Thread.sleep(300);

            } catch (Exception e) {
                log.warn("chunk入库失败，已跳过: {}/{}, fileName={}, 原因: {}",
                        i + 1,
                        uniqueDocs.size(),
                        doc.getMetadata().get("fileName"),
                        e.getMessage());
            }
        }

        log.info("ingest done: 文档数={}, chunks={}", allDocs.size(), uniqueDocs.size());
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException("生成 hash 失败", e);
        }
    }
}