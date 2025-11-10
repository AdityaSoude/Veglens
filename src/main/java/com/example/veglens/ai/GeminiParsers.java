package com.example.veglens.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public class GeminiParsers {
 private static final ObjectMapper om = new ObjectMapper();

public static Map<String,Object> parseAiMap(String raw) {
  Map<String,Object> out = new HashMap<>();
  try {
    String json = extractJsonObject(raw);     // <- NEW
    JsonNode n = om.readTree(json);

    put(out, "nonVegDetected", n.path("nonVegDetected").asBoolean(false));
    put(out, "offendingIngredients", readStrings(n, "offendingIngredients"));
    String decision = n.path("decision").asText("allow");
    if (!"allow".equals(decision) && !"deny".equals(decision)) decision = "allow";
    put(out, "decision", decision);
    put(out, "doubtful", n.path("doubtful").asBoolean(false));
    put(out, "suspectIngredients", readStrings(n, "suspectIngredients"));
    put(out, "doubtfulReasons", readStrings(n, "doubtfulReasons"));
    put(out, "confidence", (float) n.path("confidence").asDouble(0.5));
    put(out, "userSuggestion", n.path("userSuggestion").asText(""));
    put(out, "rationale", readStrings(n, "rationale"));
    return out;
  } catch (Exception e) {
    // Log raw once for debugging (optional)
    System.err.println("AI JSON parse failed. Raw: " + truncate(raw, 800));
    out.put("nonVegDetected", false);
    out.put("decision", "allow");
    out.put("doubtful", true);
    out.put("suspectIngredients", List.of());
    out.put("doubtfulReasons", List.of("AI response could not be parsed; defaulting to allow."));
    out.put("confidence", 0.2f);
    out.put("userSuggestion", "");
    out.put("rationale", List.of());
    return out;
  }
}

private static void put(Map<String,Object> m, String k, Object v) { m.put(k, v); }

private static String extractJsonObject(String s) {
  if (s == null) return "{}";
  // strip code fences ```json ... ``` or ``` ... ```
  String t = s.replaceAll("(?s)```(?:json)?\\s*(.*?)\\s*```", "$1").trim();
  // find first '{' and last '}' with simple balance checking
  int start = t.indexOf('{');
  int end = t.lastIndexOf('}');
  if (start >= 0 && end > start) {
    String cand = t.substring(start, end + 1);
    // normalize smart quotes and stray trailing commas
    cand = cand.replace('“','"').replace('”','"').replace('’','\'');
    cand = cand.replaceAll(",\\s*([}\\]])", "$1");
    return cand;
  }
  return t; // let Jackson throw if nothing valid
}

private static String truncate(String s, int n) {
  return (s == null || s.length() <= n) ? String.valueOf(s) : s.substring(0, n) + "…";
}


  private static List<String> readStrings(JsonNode n, String field) {
    List<String> list = new ArrayList<>();
    if (n.path(field).isArray()) n.path(field).forEach(x -> list.add(x.asText()));
    return list;
  }
}
