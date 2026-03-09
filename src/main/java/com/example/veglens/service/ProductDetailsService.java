package com.example.veglens.service;

import com.example.veglens.Integrations.WebFetchService;
import com.example.veglens.ai.AiUsageLimiter;
import com.example.veglens.ai.GeminiParsers;
import com.example.veglens.ai.GeminiPrompts;
import com.example.veglens.ai.VertexAiClient;
import com.example.veglens.policy.PolicyOptions;
import com.example.veglens.veglensapi.dto.ProductDtos.ProductDetailsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductDetailsService {

  private final VertexAiClient vertex; // AI only
  private final WebFetchService webFetchService;;
  private final AiUsageLimiter aiLimiter;

  public ProductDetailsResponse fetchProduct(String barcode, String query, PolicyOptions opts){

    // If user sent nothing usable, return a minimal shell.
    if (isBlank(barcode) && isBlank(query)) {
      return emptyResponse(null /* barcode */);
    }

    String name = null, brand = null, qty = null, ingredients = null;
    List<String> imgs = new ArrayList<>();
    List<String> certs = new ArrayList<>();
    List<String> cats = new ArrayList<>();
    Map<String,String> nutrition = new LinkedHashMap<>();
    List<String> allergens = new ArrayList<>();
    String offUrl = null, brandUrl = null;


    var off = webFetchService.fetchProductByBarcode(barcode);

if (off.isEmpty()) {
    System.out.println("Product not found for barcode: " + barcode);
    return emptyResponse(barcode);
}

var p = off.get();
    // In AI-only mode, we don't fetch external data.
    // We treat the query as both the product name and the ingredient text (best-effort).
   
        name = coalesce(name, p.name());
        brand = coalesce(brand, p.brand());
        qty = coalesce(qty, p.quantity());
        ingredients = coalesce(ingredients, p.ingredients());
        imgs.addAll(p.images() == null ? List.of() : p.images());
        certs.addAll(p.certifications() == null ? List.of() : p.certifications());
        cats.addAll(p.categories() == null ? List.of() : p.categories());
        nutrition.putAll(p.nutrition() == null ? Map.of() : p.nutrition());
        allergens.addAll(p.allergens() == null ? List.of() : p.allergens());       // allow users to paste label text

    // Run Vertex AI
    String prompt = GeminiPrompts.nonVegCheckPrompt(name, brand, ingredients, opts);
    String json;
    try {
      aiLimiter.checkLimit(); // enforce usage limits before calling AI
        json = vertex.generate(prompt);
    }    catch (IOException e) {
  // Handle network / Vertex API issues gracefully
  return new ProductDetailsResponse(
      barcode,
      name,
      null, null,
      List.of(), List.of(), null, null,
      Map.of(), List.of(), ingredients, List.of(),
      "unknown", 0.0, List.of("AI call failed: " + e.getMessage()), List.of(),
      "allow", false, List.of(), false, List.of(), List.of(), 0.0, "AI service unavailable", List.of()
  );
}
Map<String,Object> ai = GeminiParsers.parseAiMap(json);

    // Map AI → your record fields
    String aiDecision                 = (String) ai.get("decision");                       // "allow" | "deny"
    boolean aiNonVegDetected          = (Boolean) ai.get("nonVegDetected");
    List<String> aiOffending          = castList(ai.get("offendingIngredients"));
    boolean aiDoubtful                = (Boolean) ai.get("doubtful");
    List<String> aiSuspects           = castList(ai.get("suspectIngredients"));
    List<String> aiDoubtReasons       = castList(ai.get("doubtfulReasons"));
    double aiConfidence               = numberToDouble(ai.get("confidence"));
    String aiSuggestion               = (String) ai.getOrDefault("userSuggestion", "");
    List<String> aiRationale          = castList(ai.get("rationale"));

    // Derive a simple legacy vegStatus/confidence for your existing fields
    // (AI is the source of truth now)
    String vegStatus = "unknown";
    if ("deny".equals(aiDecision)) vegStatus = "non_veg";
    else if ("allow".equals(aiDecision)) vegStatus = "veg";

    double overallConfidence = aiConfidence;

    // Build the response record (non-AI fields are empty/null in AI-only mode)
    return new ProductDetailsResponse(
        barcode,
        name,             // name (best-effort from query)
        brand,                    // brand
        qty,                    // quantity
        null,               // categories
        imgs,               // images
        null,                    // offUrl
        null,                    // brandUrl
        Map.of(),                // nutrition
        List.of(),               // allergens
        ingredients,             // ingredientsText (from query)
        List.of(),               // certifications
        vegStatus,               // derived from aiDecision
        overallConfidence,       // AI confidence
        aiRationale,             // reasons (use AI bullets)
        List.of(),               // sources (none in AI-only)
        // --- AI fields ---
        aiDecision,
        aiNonVegDetected,
        aiOffending,
        aiDoubtful,
        aiSuspects,
        aiDoubtReasons,
        aiConfidence,
        aiSuggestion,
        aiRationale
    );
  }

  private static boolean isBlank(String s) { return s == null || s.isBlank(); }
  private static String safe(String s) { return s == null ? "" : s; }

  @SuppressWarnings("unchecked")
  private static List<String> castList(Object o) {
    return (o instanceof List<?> l) ? (List<String>) l : List.of();
  }

  private static double numberToDouble(Object n) {
    if (n instanceof Double d) return d;
    if (n instanceof Float f) return f.doubleValue();
    if (n instanceof Number num) return num.doubleValue();
    return 0.0;
  }

  private static ProductDetailsResponse emptyResponse(String barcode) {
    return new ProductDetailsResponse(
        barcode, null, null, null,
        List.of(), List.of(), null, null,
        Map.of(), List.of(), null, List.of(),
        "unknown", 0.0, List.of(), List.of(),
        // AI defaults
        "allow", false, List.of(), false, List.of(), List.of(), 0.0, "", List.of()
    );
  }
  private static String coalesce(String a, String b) { return (a == null || a.isBlank()) ? b : a; }
}
