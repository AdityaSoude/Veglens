package com.example.veglens.Integrations;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


@Component
public class WebFetchService {
    private final ObjectMapper om = new ObjectMapper();

    public Optional<String> fetchIngredientsByNameOFF(String query) {
  try {
    String url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=" +
        java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8) +
        "&search_simple=1&json=1&page_size=1";
    var json = httpGet(url);
    var root = om.readTree(json);
    var arr = root.path("products");
    if (arr.isArray() && arr.size() > 0) {
      var p = arr.get(0);
      String ingredients = p.path("ingredients_text").asText(null);
      return Optional.ofNullable(ingredients);
    }
  } catch (Exception ignore) {}
  return Optional.empty();
}


    /** Utility: returns true if a string is null or empty after trimming. */
private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
}

/** Utility: returns the first non-empty text value among given field names. */
private static String pick(JsonNode node, String... keys) {
    for (String k : keys) {
        JsonNode v = node.path(k);
        if (!v.isMissingNode() && !v.isNull()) {
            String t = v.asText("");
            if (!t.isBlank()) return t;
        }
    }
    return null;
}

/** Utility: adds a key/value pair if the value is not blank. */
private static void putIfPresent(Map<String,String> map, String key, String value) {
    if (!isBlank(value)) map.put(key, value);
}

    /** Detect common certifications/claims from OFF product JSON. */
private static List<String> detectCertifications(JsonNode p) {
    var out = new LinkedHashSet<String>();

    // 1) OFF "labels_tags" (e.g., ["en:vegan","en:vegetarian","en:kosher","en:halal"])
    for (JsonNode t : p.path("labels_tags")) {
        String v = t.asText("").toLowerCase();
        if (v.contains("vegan")) out.add("Vegan");
        if (v.contains("vegetarian")) out.add("Vegetarian");
        if (v.contains("kosher")) out.add("Kosher");
        if (v.contains("halal")) out.add("Halal");
        if (v.contains("gluten-free") || v.contains("gluten_free")) out.add("Gluten-Free");
        if (v.contains("organic")) out.add("Organic");
    }

    // 2) OFF "ingredients_analysis_tags" (e.g., ["en:vegan","en:vegetarian","en:palm-oil-free"])
    for (JsonNode t : p.path("ingredients_analysis_tags")) {
        String v = t.asText("").toLowerCase();
        if (v.endsWith(":vegan")) out.add("Vegan");
        if (v.endsWith(":vegetarian")) out.add("Vegetarian");
        if (v.contains("gluten-free")) out.add("Gluten-Free");
        if (v.contains("palm-oil-free")) out.add("Palm-oil-free");
    }

    // 3) Safety net: some datasets put claims under "labels" (plain string)
    String labelsFreeText = p.path("labels").asText("").toLowerCase();
    if (!labelsFreeText.isBlank()) {
        if (labelsFreeText.contains("vegan")) out.add("Vegan");
        if (labelsFreeText.contains("vegetarian")) out.add("Vegetarian");
        if (labelsFreeText.contains("kosher")) out.add("Kosher");
        if (labelsFreeText.contains("halal")) out.add("Halal");
        if (labelsFreeText.contains("gluten")) out.add("Gluten-Free");
        if (labelsFreeText.contains("organic")) out.add("Organic");
    }

    return new ArrayList<>(out);
}

/** Extract a small, consistent nutrition map from OFF nutriments. */
private static Map<String, String> extractNutrition(JsonNode p) {
    var out = new LinkedHashMap<String, String>();
    JsonNode n = p.path("nutriments");

    // Energy: prefer kcal; if only kJ, convert approx (kcal ≈ kJ * 0.239)
    String kcal = pick(n, "energy-kcal_100g", "energy-kcal", "energy-kcal_serving");
    if (isBlank(kcal)) {
        String kj = pick(n, "energy-kj_100g", "energy-kj", "energy-kj_serving");
        if (!isBlank(kj)) {
            try {
                double v = Double.parseDouble(kj);
                kcal = String.valueOf(Math.round(v * 0.239));
            } catch (NumberFormatException ignored) {}
        }
    }
    if (!isBlank(kcal)) out.put("energy_kcal", kcal);

    putIfPresent(out, "protein_g",      pick(n, "proteins_100g", "proteins", "proteins_serving"));
    putIfPresent(out, "fat_g",          pick(n, "fat_100g", "fat", "fat_serving"));
    putIfPresent(out, "carb_g",         pick(n, "carbohydrates_100g", "carbohydrates", "carbohydrates_serving"));
    putIfPresent(out, "sugar_g",        pick(n, "sugars_100g", "sugars", "sugars_serving"));
    // Salt may be reported as sodium; convert sodium (g) → salt (g) ≈ sodium * 2.5
    String salt = pick(n, "salt_100g", "salt", "salt_serving");
    if (isBlank(salt)) {
        String sodium = pick(n, "sodium_100g", "sodium", "sodium_serving");
        if (!isBlank(sodium)) {
            try {
                double s = Double.parseDouble(sodium);
                salt = String.format(Locale.ROOT, "%.2f", (s * 2.5));
            } catch (NumberFormatException ignored) {}
        }
    }
    putIfPresent(out, "salt_g", salt);

    return out;
}


    private static List<String> splitCsv(String s) {
    if (s == null || s.isBlank()) return List.of();

    // Remove brackets and quotes if present, then split by comma
    return Arrays.stream(s.replaceAll("[\\[\\]\"]", "").split(","))
        .map(String::trim)
        .filter(x -> !x.isEmpty())
        .toList();
}
    public record OffProduct(
  String name, String brand, String quantity, String ingredients,
  List<String> images, List<String> categories, List<String> certifications,
  Map<String,String> nutrition, List<String> allergens, String url, String reason
) {}

public Optional<OffProduct> fetchProductByBarcode(String barcode) {
  try {
    // OFF v2 product endpoint
    var url = "https://world.openfoodfacts.org/api/v2/product/" + barcode + ".json";
    var json = httpGet(url); // your existing GET helper that returns String
    // parse minimal fields (use your ObjectMapper)
    var root = om.readTree(json);
    if (root.path("status").asInt(0)!=1) return Optional.empty();
    var p = root.path("product");

    String name = p.path("product_name").asText(null);
    String brand = p.path("brands").asText(null);
    String qty = p.path("quantity").asText(null);
    String ingredients = p.path("ingredients_text").asText(null);
    List<String> imgs = new ArrayList<>();
    var img = p.path("image_url").asText(null);
    if (img != null) imgs.add(img);

    List<String> cats = splitCsv(p.path("categories_tags").toString()); // or parse array
    List<String> certs = detectCertifications(p);
    Map<String,String> nutrition = extractNutrition(p);
    List<String> allergens = splitCsv(p.path("allergens_tags").toString());

    String offUrl = "https://world.openfoodfacts.org/product/" + barcode;

    return Optional.of(new OffProduct(
        name, brand, qty, ingredients, imgs, cats, certs, nutrition, allergens, offUrl,
        "OFF product match"
    ));
  } catch (Exception e) {
    return Optional.empty();
  }
}

// helpers splitCsv, detectCertifications, extractNutrition -> implement trivially or reuse yours


    public record Doc(String url, String title, String content) {}

    /** Build naive brand-site URLs (unchanged). */
    public List<String> buildCandidateUrls(String brand, String productName) {
        var urls = new LinkedList<String>();
        if (brand != null && !brand.isBlank()) {
            var b = brand.toLowerCase().replaceAll("[^a-z0-9]+", "").trim();
            urls.add("https://www." + b + ".com");
            urls.add("https://www." + b + ".com/faq");
            if (productName != null && !productName.isBlank()) {
                String q = URLEncoder.encode(productName, StandardCharsets.UTF_8);
                urls.add("https://www." + b + ".com/search?q=" + q);
            }
        }
        return urls;
    }

    /** Add the Open Food Facts product page (HTML). */
    public void addOpenFoodFacts(List<String> urls, String barcode) {
        if (barcode != null && !barcode.isBlank()) {
            urls.add("https://world.openfoodfacts.org/product/" + barcode);
        }
    }

    /** 🔹 Fetch only ingredients text from Open Food Facts by barcode. */
    public Optional<String> fetchIngredients(String barcode) {
        if (barcode == null || barcode.isBlank()) return Optional.empty();
        String url = "https://world.openfoodfacts.org/product/" + barcode;

        try {
            Document d = Jsoup.connect(url)
                    .timeout((int) Duration.ofSeconds(7).toMillis())
                    // IMPORTANT: avoid "Bot" to prevent OFF gating
                    .userAgent("VegLens/0.1 (+contact@example.com)")
                    .header("Accept-Language", "en")
                    .get();

            // Remove Nutri-Score help/promotional blocks and lines mentioning fish/meat etc.
            String html = d.html()
                    .replaceAll("(?is)<section[^>]*id=\"panel_nutriscore\".*?</section>", "")
                    .replaceAll("(?is)<div[^>]*id=\"panel_nutriscore\".*?</div>", "")
                    .replaceAll("(?is)<div[^>]*id=\"panel_nutrition_score\".*?</div>", "")
                    .replaceAll("(?is)Discover the new Nutri-Score!.*?(?=<|$)", "")
                    .replaceAll("(?i)Better score for some fatty fish and oils rich in good fats", "")
                    .replaceAll("(?i)Better score for whole products rich in fiber", "")
                    .replaceAll("(?i)Worse score for products containing a lot of salt or sugar", "")
                    .replaceAll("(?i)Worse score for red meat \\(compared to poultry\\)", "");
            d = Jsoup.parse(html);

            // Try most common selectors on OFF pages
            String ingredients = d.select("#ingredients_list").text();
            if (ingredients == null || ingredients.isBlank())
                ingredients = d.select("#panel_ingredients_content").text();
            if (ingredients == null || ingredients.isBlank())
                ingredients = d.select("div#ingredients").text();
            if (ingredients == null || ingredients.isBlank())
                ingredients = d.select("meta[property=og:description]").attr("content");

            if (ingredients != null && !ingredients.isBlank()) {
                ingredients = ingredients.replaceAll("\\s+", " ").trim();
                return Optional.of(ingredients);
            }
        } catch (Exception e) {
            // swallow
        }

        return Optional.empty();
    }

    /** Keep your old generic fetchReadable in case you need it elsewhere. */
    public List<Doc> fetchReadable(List<String> urls) {
        var out = new ArrayList<Doc>();
        for (String u : urls) {
            try {
                Document d = Jsoup.connect(u)
                        .timeout((int) Duration.ofSeconds(7).toMillis())
                        .userAgent("VegLens/0.1 (+contact@example.com)")
                        .get();

                // 🔹 Remove Nutri-Score help/promotional blocks and lines mentioning fish/meat etc.
                String html = d.html()
                        .replaceAll("(?is)<section[^>]*id=\"panel_nutriscore\".*?</section>", "")
                        .replaceAll("(?is)<div[^>]*id=\"panel_nutriscore\".*?</div>", "")
                        .replaceAll("(?is)<div[^>]*id=\"panel_nutrition_score\".*?</div>", "")
                        .replaceAll("(?is)Discover the new Nutri-Score!.*?(?=<|$)", "")
                        .replaceAll("(?i)Better score for some fatty fish and oils rich in good fats", "")
                        .replaceAll("(?i)Better score for whole products rich in fiber", "")
                        .replaceAll("(?i)Worse score for products containing a lot of salt or sugar", "")
                        .replaceAll("(?i)Worse score for red meat \\(compared to poultry\\)", "");
                d = Jsoup.parse(html);

                String text = d.select("article, main, .content, .entry, body").text();
                String title = d.title();
                if (text != null && text.length() > 200) {
                    out.add(new Doc(u, title == null ? "" : title, text));
                }
            } catch (Exception ignore) {}
            if (out.size() >= 6) break;
        }
        return out;
    }

    public record OFFInfo(String ingredients, boolean isVegan, boolean isVegetarian) {}

    public Optional<OFFInfo> fetchIngredientsAndCertifications(String barcode) {
    if (barcode == null || barcode.isBlank()) return Optional.empty();
    String url = "https://world.openfoodfacts.org/product/" + barcode;

    try {
        Document d = Jsoup.connect(url)
                .timeout((int) Duration.ofSeconds(10).toMillis())
                .userAgent("VegLens/0.1 (+contact@example.com)")
                .header("Accept-Language", "en")
                .get();

        // ---- Remove Nutri-Score promo/help and the specific fish/meat lines
        String html = d.html()
                .replaceAll("(?is)<section[^>]*id=\"panel_nutriscore\".*?</section>", "")
                .replaceAll("(?is)<div[^>]*id=\"panel_nutriscore\".*?</div>", "")
                .replaceAll("(?is)<div[^>]*id=\"panel_nutrition_score\".*?</div>", "")
                .replaceAll("(?is)Discover the new Nutri-Score!.*?(?=<|$)", "")
                .replaceAll("(?i)Better score for some fatty fish and oils rich in good fats", "")
                .replaceAll("(?i)Better score for whole products rich in fiber", "")
                .replaceAll("(?i)Worse score for products containing a lot of salt or sugar", "")
                .replaceAll("(?i)Worse score for red meat \\(compared to poultry\\)", "");
        d = Jsoup.parse(html);

        // ======================
        // 1) CERTIFICATIONS FIRST
        // ======================

        // 1a) Exact table row: <tr><th>Labels, certifications, awards</th><td>...</td></tr>
        // Use a robust regex (no stray parens) and then read the <td>
        var labelsCell = d.selectFirst("tr:has(th:matchesOwn((?i)labels.*certifications.*awards)) td");

        StringBuilder labelsBuf = new StringBuilder();
        if (labelsCell != null) {
            labelsBuf.append(labelsCell.text()).append(' ');
            labelsCell.select("a").eachText().forEach(t -> labelsBuf.append(t).append(' '));
            labelsCell.select("img[alt]").eachAttr("alt").forEach(a -> labelsBuf.append(a).append(' '));
        }

        // 1b) Attribute cards/labels panels used by some layouts/locales
        String attributesPanels = d.select(
                "#attributes, #attributes_grid .attribute, [id*=attribute] .attribute, " +
                "#labels, #labels_content, #panel_labels_content, #certifications, #awards, section#labels_content"
        ).text();

        // 1c) Any label links or badge ALTs across the page (e.g., /label/vegan, Vegan Action badge)
        String labelLinks = String.join(" ",
                d.select("a[href*=\"/label/\"]").eachText());
        String labelAlts  = String.join(" ",
                d.select("img[alt]").eachAttr("alt"));

        String labelsAll = (labelsBuf + " " + attributesPanels + " " + labelLinks + " " + labelAlts)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        // Tokenize to catch discrete tags like "Vegan Action"
        Set<String> labelTokens = new HashSet<>();
        for (String part : labelsAll.split("[,;/\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) labelTokens.add(t);
        }

        boolean mentionsNonVegan = labelsAll.contains("non-vegan") || labelsAll.contains("not vegan");
        boolean mentionsNonVegetarian = labelsAll.contains("non-vegetarian") || labelsAll.contains("not vegetarian");

        boolean vegan = !mentionsNonVegan && containsAny(labelTokens,
                "vegan", "certified vegan", "vegan society", "the vegan society",
                "vegan-friendly", "vegan friendly", "vegan action");

        boolean vegetarian = !mentionsNonVegetarian && containsAny(labelTokens,
                "vegetarian", "certified vegetarian", "vegetarian society",
                "lacto-vegetarian", "ovo-vegetarian");

        // If we have a clear certification, return immediately (ingredients not needed)
        if (vegan || vegetarian) {
            return Optional.of(new OFFInfo("", vegan, vegetarian));
        }

        // ==========================
        // 2) FALLBACK: INGREDIENTS
        // ==========================
        String ingredients = d.select("#ingredients_list").text();
        if (ingredients == null || ingredients.isBlank())
            ingredients = d.select("#panel_ingredients_content").text();
        if (ingredients == null || ingredients.isBlank())
            ingredients = d.select("div#ingredients").text();
        if (ingredients == null || ingredients.isBlank())
            ingredients = d.select("meta[property=og:description]").attr("content");
        if (ingredients == null) ingredients = "";
        ingredients = ingredients.replaceAll("\\s+", " ").trim();

        return Optional.of(new OFFInfo(ingredients, false, false));

    } catch (Exception e) {
        return Optional.empty();
    }
}


    // small helpers (inside WebFetchService)
    private static boolean containsAny(Set<String> haystack, String... needles) {
        for (String n : needles) {
            String needle = n.toLowerCase(Locale.ROOT).trim();
            for (String h : haystack) {
                if (h.equals(needle) || h.contains(needle)) return true;
            }
        }
        return false;
    }



    /**
 * Simple HTTP GET helper used for OpenFoodFacts fetches.
 * Uses Java's built-in HttpClient and returns the body as a String.
 */
private String httpGet(String url) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .timeout(Duration.ofSeconds(15))
        .header("User-Agent", "VegLens/1.0 (+https://veglens.app)")
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return response.body();
    } else {
        throw new IOException("HTTP GET failed: " + response.statusCode() + " for " + url);
    }
}

}
