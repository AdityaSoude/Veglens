package com.example.veglens.service;

import com.example.veglens.kb.IngredientKB;
import com.example.veglens.veglensapi.dto.ClassificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassifierService {

    private final IngredientKB kb;


    // POST /api/v1/classify/ingredients uses this
    public ClassificationResponse classifyTextPureVeg(String text) {
        String raw = text == null ? "" : text;
        String t = raw.toLowerCase();

        // Tokenize by commas/semicolons/parentheses; keep multiword tokens intact.
        String[] tokens = t.split("[,;()]+");

        List<ClassificationResponse.Flag> denies = new ArrayList<>();
        List<ClassificationResponse.Flag> ambigs = new ArrayList<>();

        for (String tok : tokens) {
            String token = tok.trim().replaceAll("\\s+", " ");
            if (token.isEmpty()) continue;

            var hit = kb.find(token);
            if (hit.isEmpty()) continue;

            var entry = hit.get();
            // Apply exceptions (e.g., "microbial rennet" → allow)
            String finalStatus = kb.exceptionOverride(entry, t).orElse(entry.status_pure_veg());

            switch (finalStatus) {
                case "deny" -> denies.add(new ClassificationResponse.Flag(
                        token, entry.canonical(), "deny", entry.notes(), "KB"));
                case "ambiguous" -> ambigs.add(new ClassificationResponse.Flag(
                        token, entry.canonical(), "ambiguous", entry.notes(), "KB"));
                default -> { /* allow → no flag */ }
            }
        }

        if (!denies.isEmpty()) {
            // Build a compact reason listing the unique canonical offenders
            String offenders = uniqueCanonicals(denies);
            return new ClassificationResponse(
                    "red",
                    "Contains non-veg ingredient(s): " + offenders,
                    "KB",
                    merge(denies, ambigs)   // include ambigs too if present
            );
        }

        if (!ambigs.isEmpty()) {
            String items = uniqueCanonicals(ambigs);
            return new ClassificationResponse(
                    "amber",
                    "Ambiguous source ingredient(s): " + items + " — verify with manufacturer",
                    "KB",
                    ambigs
            );
        }

        return new ClassificationResponse(
                "green",
                "Matches Pure Veg (no eggs/meat/fish found)",
                "KB",
                List.of()
        );
    }

    private static String uniqueCanonicals(List<ClassificationResponse.Flag> flags) {
        return flags.stream()
                .map(ClassificationResponse.Flag::canonical)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static List<ClassificationResponse.Flag> merge(
            List<ClassificationResponse.Flag> a,
            List<ClassificationResponse.Flag> b) {
        if (b.isEmpty()) return a;
        var out = new ArrayList<ClassificationResponse.Flag>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }
}
