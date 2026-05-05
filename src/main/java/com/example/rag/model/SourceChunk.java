package com.example.rag;

public record SourceChunk(
        String id,
        String content,
        double[] vector
) {
}