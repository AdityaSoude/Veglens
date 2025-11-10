package com.example.veglens.veglensapi.dto;


import java.util.List;

public record ClassificationResponse(
        String badge,          // "green" | "amber" | "red"
        String reason,         // short summary
        String source,         // "KB" | "Rules" | "AI"
        List<Flag> flags       // which tokens triggered red/amber (empty for green)
) {
 public static record Flag(
         String token,        // as it appeared in the text, e.g., "mono and diglycerides"
         String canonical,    // normalized name from KB, e.g., "mono and diglycerides"
         String status,       // "deny" | "ambiguous"
         String notes,        // explanatory note from KB (or rules)
         String source        // "KB" | "Rules"
 ) {}
}


