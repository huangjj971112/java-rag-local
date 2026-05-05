package com.example.rag.dto.response;

public record SourceVO(
        Integer ref,
        String fileName,
        String content
) {
}