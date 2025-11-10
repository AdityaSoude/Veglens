package com.example.veglens.ai;

import com.example.veglens.policy.PolicyOptions;

public class GeminiPrompts {
  public static String nonVegCheckPrompt(String productName, String brand, String ingredientsRaw, PolicyOptions opts) {
    String policy = opts.policy().name().toLowerCase(); // lenient|strict
    String diet   = opts.diet().name().toLowerCase();   // vegetarian|vegan
    return """
  You are VegLens. Analyze ingredient list for NON-VEGETARIAN risks.

  STRICT OUTPUT CONTRACT:
  - Return **ONLY** JSON matching the schema below.
  - No markdown, no backticks, no preface or explanations.
  - Use true/false booleans and double-quoted JSON strings.

  INPUT
  - product_name: %s
  - brand: %s
  - ingredients_raw: %s
  - diet: %s
  - policy: %s

  DECISION RULES
  - decision must be "allow" or "deny" (never "ambiguous").
  - vegetarian: deny if flesh/stock/gelatin/animal rennet/egg/honey/shellac/bone-char etc. but not dairy
  - Uncertain umbrella terms (e.g., "natural flavors","enzymes","mono- and diglycerides","stearic acid"):
    set doubtful=true + list in suspectIngredients; still choose allow/deny
      • strict: if any doubtful terms exist, prefer deny unless clearly plant/microbial.
      • lenient: if only doubtful terms exist and no clear non-veg, prefer allow.

  JSON SCHEMA (return exactly this shape):
  {
    "nonVegDetected": true|false,
    "offendingIngredients": ["lowercase canonical names"],
    "decision": "allow" | "deny",
    "doubtful": true|false,
    "suspectIngredients": ["strings as seen/canonicalized"],
    "doubtfulReasons": ["short bullet points"],
    "confidence": 0.0-1.0,
    "userSuggestion": "<=160 chars actionable next step",
    "rationale": ["short bullet points"]
  }
  """.formatted(nz(productName), nz(brand), nz(ingredientsRaw), diet, policy);

  }
  private static String nz(String v) { return v == null ? "" : v; }
}
