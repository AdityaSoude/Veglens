package com.example.veglens.service;

import com.example.veglens.Integrations.WebFetchService;
import com.example.veglens.kb.IngredientKB;
import com.example.veglens.kb.IngredientKB.Entry;
import com.example.veglens.veglensapi.dto.InfoDtos.Citation;
import com.example.veglens.veglensapi.dto.InfoDtos.InfoRequest;
import com.example.veglens.veglensapi.dto.InfoDtos.InfoResponse;
import com.example.veglens.policy.PolicyOptions;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class InfoService {

  private final WebFetchService web;
  private final IngredientKB kb;

  public InfoService(WebFetchService web, IngredientKB kb) {
    this.web = web;
    this.kb = kb;
  }

  private InfoResponse classifyFromLabel(String label) {
    String full = label == null ? "" : label;
    String norm = normalizeForScan(full);

    record HitView(String severity, String canonical, String matchedKey, int index) {}
    var denyViews = new ArrayList<HitView>();
    var allowViews = new ArrayList<HitView>();
    var ambViews  = new ArrayList<HitView>();

    for (var e : kb.entries()) {
      for (String key : kb.keysFor(e)) {
        if (key == null || key.isBlank()) continue;
        var m = tolerantKeyPattern(key).matcher(norm);
        while (m.find()) {
          String status   = Optional.ofNullable(e.status_pure_veg()).orElse("ambiguous");
          String override = kb.exceptionOverride(e, full).orElse(status);
          switch (override) {
            case "deny"  -> denyViews.add(new HitView("deny",  e.canonical(), key, m.start()));
            case "allow" -> allowViews.add(new HitView("allow", e.canonical(), key, m.start()));
            default      -> ambViews.add (new HitView("ambiguous", e.canonical(), key, m.start()));
          }
        }
      }
    }

    // If any denies -> RED immediately.
    if (!denyViews.isEmpty()) {
      var reasons = new LinkedHashSet<String>();
      var tokens  = new LinkedHashSet<String>();
      for (var v : denyViews) {
        reasons.add("Non-veg ingredient: " + v.canonical());
        tokens.add(v.matchedKey);
      }
      return new InfoResponse("red", 0.90,
          "Detected non-vegetarian ingredients directly from the label.",
          new ArrayList<>(reasons), List.of(), new ArrayList<>(tokens));
    }

    // If ambiguous present and no denies → stay AMBER (no AI resolution)
    if (!ambViews.isEmpty()) {
      var reasons = new LinkedHashSet<String>();
      var tokens  = new LinkedHashSet<String>();
      for (var v : ambViews) {
        reasons.add("Ambiguous ingredient: " + v.canonical());
        tokens.add(v.matchedKey);
      }
      return new InfoResponse("amber", 0.60,
          "Ambiguous ingredients present on the label.",
          new ArrayList<>(reasons), List.of(), new ArrayList<>(tokens));
    }

    // No denies, no ambiguous → purely allow hits → GREEN
    if (!allowViews.isEmpty()) {
      var reasons = new LinkedHashSet<String>();
      var tokens  = new LinkedHashSet<String>();
      for (var v : allowViews) {
        reasons.add("Vegetarian ingredient: " + v.canonical());
        tokens.add(v.matchedKey);
      }
      return new InfoResponse("green", 0.85,
          "All ingredients are vegetarian; no non-veg signals found on the label.",
          new ArrayList<>(reasons), List.of(), new ArrayList<>(tokens));
    }

    // Nothing decisive
    return new InfoResponse("amber", 0.30,
        "No decisive ingredients found on the label.",
        List.of("No decisive ingredients found on the label."), List.of(), List.of());
  }

  private static boolean hasAmbiguousUmbrellaTerms(String t) {
    String s = normalizeForScan(t == null ? "" : t);
    return s.contains("natural flavor") || s.contains("natural flavour")
        || s.contains("enzyme") || s.contains("mono-") || s.contains("diglyceride")
        || s.contains("shortening");
  }

  private static boolean containsNonVeganHit(String t) {
    String s = normalizeForScan(t == null ? "" : t);
    return s.contains("milk") || s.contains("butter") || s.contains("ghee")
        || s.contains("cheese") || s.contains("casein") || s.contains("whey")
        || s.contains("egg") || s.contains("albumen")
        || s.contains("honey") || s.contains("beeswax") || s.contains("shellac");
  }

  private static String summarize(String badge) {
    return switch (badge) {
      case "green" -> "Vegetarian per current policy.";
      case "red"   -> "Non-vegetarian per current policy.";
      case "amber" -> "Ambiguous ingredients present.";
      default      -> "Insufficient evidence to determine status.";
    };
  }

  /** Public API with policy overlays (AI-free). */
  public InfoResponse lookup(InfoRequest req, PolicyOptions opts) {
    // 1) run the KB/web pipeline (AI-free)
    InfoResponse base = this.lookup(req);

    String badge = base.badge();         // "green" | "amber" | "red" | ...
    double conf  = base.confidence();
    var reasons  = new ArrayList<>(base.reasons());
    var cits     = new ArrayList<>(base.citations());
    var tokens   = new ArrayList<>(base.ingredientsDetected());

    String labelText = req.ingredientsText();

    // 2) Vegan overlay: flip to red if non-vegan terms present
    if (opts.diet() == PolicyOptions.Diet.VEGAN && labelText != null) {
      if (containsNonVeganHit(labelText)) {
        badge = "red";
        conf = Math.max(conf, 0.85);
        reasons.add("Diet policy: non-vegan ingredient detected (vegan mode).");
      }
    }

    // 3) Strict overlay: if still amber and umbrella terms present → red
    if ("amber".equals(badge)
        && opts.policy() == PolicyOptions.Mode.STRICT
        && labelText != null
        && hasAmbiguousUmbrellaTerms(labelText)) {
      badge = "red";
      conf = Math.max(conf, 0.65);
      reasons.add("Policy: strict — ambiguous umbrella terms treated as non-veg.");
    }

    // 4) Force binary decision when still amber (no LLM; pure policy choice)
    if ("amber".equals(badge) && opts.forceBinary()) {
      if (opts.policy() == PolicyOptions.Mode.STRICT) {
        badge = "red";
        conf = Math.max(conf, 0.60);
        reasons.add("Force-binary: strict policy resolves ambiguity as non-veg.");
      } else {
        badge = "green";
        conf = Math.max(conf, 0.60);
        reasons.add("Force-binary: lenient policy resolves ambiguity as veg.");
      }
    }

    // 5) return adjusted response
    return new InfoResponse(
        badge,
        conf,
        summarize(badge),
        reasons,
        cits,
        tokens
    );
  }

  /** Core lookup that only uses KB + WebFetchService (no AI). */
  public InfoResponse lookup(InfoRequest req) {
    // If label text provided, classify directly from label with KB only
    if (req.ingredientsText() != null && !req.ingredientsText().isBlank()) {
      return classifyFromLabel(req.ingredientsText());
    }

    // 1) OFF certification first, then OFF ingredients
    if (req.barcode() != null && !req.barcode().isBlank()) {
      var off = web.fetchIngredientsAndCertifications(req.barcode());
      if (off.isPresent()) {
        var info = off.get();

        if (info.isVegan() || info.isVegetarian()) {
          String desc = info.isVegan()
              ? "Product shows vegan certification on Open Food Facts."
              : "Product shows vegetarian certification on Open Food Facts.";
          return new InfoResponse(
              "green",
              0.95,
              desc,
              List.of(desc),
              List.of(),
              List.of("certified")
          );
        }

        if (info.ingredients() != null && !info.ingredients().isBlank()) {
          return classifyFromLabel(info.ingredients());
        }
      }
    }

    // 2) Brand pages fallback
    var urls = web.buildCandidateUrls(req.brand(), req.productName());
    web.addOpenFoodFacts(urls, req.barcode());
    var docs = web.fetchReadable(urls);

    if (docs.isEmpty()) {
      return new InfoResponse(
          "amber", 0.0,
          "No authoritative pages found to evaluate vegetarian suitability.",
          List.of("No docs returned from brand/OpenFoodFacts."),
          List.of(), List.of()
      );
    }

    record Hit(Entry entry, String matchedKey, String override, WebFetchService.Doc doc, int index) {}
    var hits = new ArrayList<Hit>();
    var detectedTokens = new LinkedHashSet<String>();

    for (var doc : docs) {
      final String full = doc.content();
      final String norm = normalizeForScan(full);

      for (var entry : kb.entries()) {
        for (String key : kb.keysFor(entry)) {
          if (key == null || key.isBlank()) continue;

          var m = tolerantKeyPattern(key).matcher(norm);
          while (m.find()) {
            String status = Optional.ofNullable(entry.status_pure_veg()).orElse("ambiguous");
            String override = kb.exceptionOverride(entry, full).orElse(status);
            hits.add(new Hit(entry, key.trim(), override, doc, m.start()));
            detectedTokens.add(key.trim());
          }
        }
      }
    }

    record HitView(String severity, String canonical, String matchedKey, WebFetchService.Doc doc, int index) {}
    var denyViews = new ArrayList<HitView>();
    var allowViews = new ArrayList<HitView>();
    var ambViews  = new ArrayList<HitView>();

    for (var h : hits) {
      switch (h.override) {
        case "deny" -> denyViews.add(new HitView("deny", h.entry.canonical(), h.matchedKey, h.doc, h.index));
        case "allow" -> allowViews.add(new HitView("allow", h.entry.canonical(), h.matchedKey, h.doc, h.index));
        default -> ambViews.add(new HitView("ambiguous", h.entry.canonical(), h.matchedKey, h.doc, h.index));
      }
    }

    if (!denyViews.isEmpty()) {
      var reasons = new LinkedHashSet<String>();
      var citations = new ArrayList<Citation>();
      var tokens = new LinkedHashSet<String>();

      for (var v : denyViews) {
        reasons.add("Non-veg ingredient detected: " + v.canonical());
        citations.add(new Citation(v.doc.title(), v.doc.url(),
            snippet(v.doc.content(), v.index, v.matchedKey.length())));
        tokens.add(v.matchedKey);
      }

      return new InfoResponse(
          "red",
          clamp(0.75 + Math.min(0.20, denyViews.size() * 0.08)),
          "Detected non-vegetarian ingredient(s) in source pages.",
          new ArrayList<>(reasons),
          dedupeCitations(citations, 10),
          new ArrayList<>(tokens)
      );
    }

    if (!allowViews.isEmpty() && ambViews.isEmpty()) {
      var reasons = new LinkedHashSet<String>();
      var citations = new ArrayList<Citation>();
      var tokens = new LinkedHashSet<String>();
      for (var v : allowViews) {
        reasons.add("Vegetarian ingredient: " + v.canonical());
        citations.add(new Citation(v.doc.title(), v.doc.url(),
            snippet(v.doc.content(), v.index, v.matchedKey.length())));
        tokens.add(v.matchedKey);
      }
      return new InfoResponse(
          "green",
          clamp(0.55 + Math.min(0.35, allowViews.size() * 0.08)),
          "Only vegetarian ingredients detected; no non-veg signals found.",
          new ArrayList<>(reasons),
          dedupeCitations(citations, 10),
          new ArrayList<>(tokens)
      );
    }

    var reasons = new LinkedHashSet<String>();
    var citations = new ArrayList<Citation>();
    var tokens = new LinkedHashSet<String>();

    if (!ambViews.isEmpty()) {
      for (var v : ambViews) {
        reasons.add("Ambiguous ingredient: " + v.canonical());
        citations.add(new Citation(v.doc.title(), v.doc.url(),
            snippet(v.doc.content(), v.index, v.matchedKey.length())));
        tokens.add(v.matchedKey);
      }
    } else {
      reasons.add("No decisive ingredients found in fetched pages.");
    }

    return new InfoResponse(
        "amber",
        !ambViews.isEmpty() ? clamp(0.35 + Math.min(0.25, ambViews.size() * 0.05)) : 0.30,
        !ambViews.isEmpty()
            ? "Ambiguous ingredients present; cannot confirm vegetarian status from sources."
            : "Insufficient evidence in sources to determine vegetarian status.",
        new ArrayList<>(reasons),
        dedupeCitations(citations, 10),
        new ArrayList<>(tokens)
    );
  }

  // ---------- helpers ----------

  private static String normalizeForScan(String text) {
    if (text == null) return "";
    String out = text
        .replace("\u00AD", "") // soft hyphen
        .replaceAll("[\\u200B-\\u200D\\u2060]", "") // zero-width chars
        .toLowerCase(Locale.ROOT);

    // UK → US spellings (helps KB keys match)
    out = out.replace("flavour", "flavor")
             .replace("colour", "color");

    // Common OCR/label noise cleanups (tweak as needed)
    out = out.replace("naural", "natural")
             .replace("mik", "milk")
             .replace("maltodextri lade", "maltodextrin made")
             .replace("com ard", "corn")
             .replace("can a", "canola")
             // strip SmartLabel footer noise often appended to ingredient blocks
             .replaceAll("smartlabel.+$", "");

    // collapse whitespace
    return out.replaceAll("\\s+", " ");
  }

  private static Pattern tolerantKeyPattern(String key) {
    String k = key.toLowerCase(Locale.ROOT).trim();
    String sep = "[\\s\\-]?";
    String token = Arrays.stream(k.split("\\s+|\\-+"))
        .filter(s -> !s.isBlank())
        .map(Pattern::quote)
        .reduce((a, b) -> a + sep + b)
        .orElse(Pattern.quote(k));
    if (k.matches(".*[a-z]$")) token = token + "s?"; // plural tolerance
    return Pattern.compile("(?<![\\p{L}\\p{N}])" + token + "(?![\\p{L}\\p{N}])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  private static String snippet(String original, int idxLowerApprox, int len) {
    if (original == null || original.isEmpty()) return "";
    int start = Math.max(0, idxLowerApprox - 80);
    int end = Math.min(original.length(), idxLowerApprox + len + 80);
    String s = original.substring(start, end).replaceAll("\\s+", " ").trim();
    if (start > 0) s = "…" + s;
    if (end < original.length()) s = s + "…";
    return s;
  }

  private static double clamp(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  private static List<Citation> dedupeCitations(List<Citation> list, int limit) {
    var seen = new LinkedHashSet<String>();
    var out = new ArrayList<Citation>();
    for (var c : list) {
      String key = (c.url() + "|" + c.quote()).toLowerCase(Locale.ROOT);
      if (seen.add(key)) {
        out.add(c);
        if (out.size() >= limit) break;
      }
    }
    return out;
  }
}
