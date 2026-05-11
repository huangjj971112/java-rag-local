package com.example.rag.rerank;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class RuleRerankService implements RerankService {

    @Override
    public List<Document> rerank(String question,
                                 String rewrittenQuery,
                                 List<Document> docs,
                                 int finalTopK) {

        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        List<String> keywords = extractKeywords(question + " " + rewrittenQuery);

        List<ScoredDocument> scoredDocs = docs.stream()
                .map(doc -> new ScoredDocument(doc, calcScore(doc, keywords)))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();

        for (int i = 0; i < scoredDocs.size(); i++) {
            Document doc = scoredDocs.get(i).document();

            log.info("RuleRerank[{}] score={}, fileName={}, text={}",
                    i + 1,
                    scoredDocs.get(i).score(),
                    doc.getMetadata().get("fileName"),
                    shorten(doc.getText(), 100));
        }

        return scoredDocs.stream()
                .limit(finalTopK)
                .map(ScoredDocument::document)
                .toList();
    }

    private double calcScore(Document doc, List<String> keywords) {
        String text = doc.getText();

        if (text == null || text.isBlank()) {
            return 0;
        }

        double score = 0;

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                score += 10;
            }
        }

        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index >= 0) {
                score += Math.max(0, 5 - index / 100.0);
            }
        }

        if (text.length() < 80) {
            score -= 2;
        }

        return score;
    }

    private List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String cleaned = text.replaceAll("[，。！？、,.?！；;：:\\[\\]（）()\"“”‘’]", " ");

        return Arrays.stream(cleaned.split("\\s+"))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .distinct()
                .toList();
    }

    private String shorten(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    private record ScoredDocument(Document document, double score) {
    }
}