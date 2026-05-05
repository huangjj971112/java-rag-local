package com.example.rag.dto.response;

import java.util.List;

public record RagResponse(
        String question,
        String answer,
        List<String> sources
) {
}