package com.example.rag.dto;

public record Source(
        String fileName,
        String source,
        String chunkHash,
        Double score,
        String content
) {}