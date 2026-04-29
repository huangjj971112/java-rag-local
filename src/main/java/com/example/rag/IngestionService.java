package com.example.rag;

import com.example.rag.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class IngestionService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    public IngestionService(VectorStore vectorStore, RagProperties ragProperties) {
        this.vectorStore = vectorStore;
        this.ragProperties = ragProperties;
    }

    @Override
    public void run(String... args) throws Exception {

        String location = ragProperties.docsPath(); // classpath:/docs/**
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();
        log.info("加载文件");
        Resource[] resources = resolver.getResources(location);

        if (resources.length == 0) {
            log.warn("No files found in {}", location);
            return;
        }

        List<Document> allDocs = new ArrayList<>();

        for (Resource resource : resources) {
            try {
                TikaDocumentReader reader = new TikaDocumentReader(resource);

                List<Document> docs = reader.get().stream()
                        .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
                        .map(doc -> new Document(
                                doc.getText(),
                                Map.of(
                                        "fileName", Objects.requireNonNull(resource.getFilename()),
                                        "source", resource.getDescription()
                                )
                        ))
                        .toList();

                allDocs.addAll(docs);

                log.info("Loaded: {}", resource.getFilename());

            } catch (Exception e) {
                log.warn("Skip file: {}, reason: {}", resource.getFilename(), e.getMessage());
            }
        }

        // 切分
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocs = splitter.apply(allDocs);
        log.info("加载文件1");
        // 入库
        vectorStore.add(splitDocs);

        log.info("✅ Ingest done: {} docs → {} chunks", allDocs.size(), splitDocs.size());
    }
}