package com.example.veglens.veglensapi.dto;


import java.util.List;

public class EnrichmentDtos {
    public record EnrichRequest(
            String barcode,
            String productName,
            String brand,
            List<String> ambiguousTokens,
            String ingredientsText
    ) {}

    public record Citation(String title, String url, String quote) {}

    public record QA(String question, String answer) {}

    public record EnrichResponse(
            String resolvedBadge,     // "green" | "red" | "amber"
            double confidence,        // 0..1
            String summary,           // concise, grounded summary
            List<QA> answers,         // optional Q&A
            List<Citation> citations, // list of sources used
            String rawSourcesHash,    // cache key
            String model              // which LLM
    ) {}
}

