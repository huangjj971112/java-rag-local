package com.example.rag.dto;

public record SourceVO(
        Integer ref,
        String fileName,
        String content
) {
}