package com.example.veglens.veglensapi.dto;

import java.util.List;
import java.util.Map;

public class ProductDtos {

  public record ProductDetailsResponse(
      String barcode,
      String name,
      String brand,
      String quantity,              // e.g., "500 ml", "12 oz"
      List<String> categories,      // e.g., ["Beverages","Sodas"]
      List<String> images,          // urls
      String offUrl,                // OpenFoodFacts url if any
      String brandUrl,              // best brand page we used
      Map<String,String> nutrition, // {"energy_kcal":"120","protein_g":"3",...}
      List<String> allergens,       // ["milk","soy"]
      String ingredientsText,       // raw ingredients
      List<String> certifications,  // ["Vegan", "Vegetarian", "Kosher", ...]
      String vegStatus,             // "veg" | "non_veg" | "ambiguous" | "unknown"
      double confidence,
      List<String> reasons,         // why we concluded vegStatus
      List<String> sources,         // urls we used

      // --- ↓↓↓ AI verdict fields added ↓↓↓ ---

      String aiDecision,                  // "allow" | "deny"
      boolean aiNonVegDetected,           // true if any definite non-veg found
      List<String> aiOffendingIngredients,// e.g., ["gelatin","cochineal"]

      boolean aiDoubtful,                 // true if any risky/uncertain terms found
      List<String> aiSuspectIngredients,  // e.g., ["natural flavors","enzymes"]
      List<String> aiDoubtfulReasons,     // explanation for doubtful items

      double aiConfidence,                // 0..1 from the AI
      String aiUserSuggestion,            // <=160 chars ("Contact brand..." etc.)
      List<String> aiRationale            // short bullet list of AI reasoning
  ) {}
}
