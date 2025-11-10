// src/main/java/com/example/veglens/veglensapi/dto/InfoDtos.java
package com.example.veglens.veglensapi.dto;

import java.util.List;

public class InfoDtos {

    // --- Input DTO ---
    public record InfoRequest(
        String brand,
        String productName,
        String barcode,
        String ingredientsText  // 👈 Added this field
    ) {}

    // --- Support DTO ---
    public record Citation(
        String title,
        String url,
        String quote
    ) {}

    // --- Output DTO ---
    public record InfoResponse(
        String badge,                  // "green" | "red" | "amber"
        double confidence,             // 0..1
        String summary,
        List<String> reasons,
        List<Citation> citations,
        List<String> ingredientsDetected // tokens we detected
    ) {}
}
