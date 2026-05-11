package com.example.rag.rerank;

import org.springframework.ai.document.Document;

import java.util.List;

public interface RerankService {

    List<Document> rerank(String question,
                          String rewrittenQuery,
                          List<Document> docs,
                          int finalTopK);
}