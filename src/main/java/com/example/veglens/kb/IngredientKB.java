// src/main/java/com/veglens/veglensapi/kb/IngredientKB.java
package com.example.veglens.kb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

// src/main/java/com/example/veglens/kb/IngredientKB.java
// ...existing imports...

@Component
public class IngredientKB {

    public static record Entry(
        String canonical,
        List<String> synonyms,
        List<String> codes,
        String status_pure_veg,  // allow | deny | ambiguous
        String notes,
        List<Map<String,String>> exceptions
    ) {}

    private final Map<String, Entry> byKey = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>(); // <— ADD

    public IngredientKB(ObjectMapper om) {
        try (InputStream in = new ClassPathResource("kb/ingredients-kb.json").getInputStream()) {
            List<Entry> loaded = om.readValue(in, new TypeReference<>() {});
            for (Entry e : loaded) {
                entries.add(e); // <— ADD
                byKey.put(e.canonical().toLowerCase(), e);
                if (e.synonyms()!=null) e.synonyms().forEach(s -> byKey.put(s.toLowerCase(), e));
                if (e.codes()!=null) e.codes().forEach(c -> byKey.put(c.toLowerCase(), e));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ingredients-kb.json", e);
        }
    }

    public Optional<Entry> find(String token) {
        return Optional.ofNullable(byKey.get(token.toLowerCase()));
    }

    public Optional<String> exceptionOverride(Entry e, String fullText) {
        if (e.exceptions()==null) return Optional.empty();
        for (var ex : e.exceptions()) {
            String pat = ex.get("if_matches");
            String ov  = ex.get("override");
            if (pat!=null && ov!=null && Pattern.compile("(?i)"+pat).matcher(fullText).find()) {
                return Optional.of(ov);
            }
        }
        return Optional.empty();
    }

    // === NEW: expose entries and keys for scanning ===
    public Collection<Entry> entries() { return Collections.unmodifiableList(entries); }

    public List<String> keysFor(Entry e) {
        var keys = new ArrayList<String>();
        keys.add(e.canonical());
        if (e.synonyms()!=null) keys.addAll(e.synonyms());
        if (e.codes()!=null) keys.addAll(e.codes());
        return keys;
    }
}
