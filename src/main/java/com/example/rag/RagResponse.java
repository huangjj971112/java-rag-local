package com.example.rag;

import java.util.List;

public record RagResponse(
        String question,
        String answer,
        List<String> sources
) {
}