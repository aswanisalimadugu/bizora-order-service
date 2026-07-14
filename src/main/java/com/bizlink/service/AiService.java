package com.bizlink.service;

import com.bizlink.exception.ValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
public class AiService {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Random random = new Random();

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String model;

    @Value("${ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    public String generate(String type, Map<String, Object> params) {
        if ("product_fill".equals(type)) {
            return toJson(productFill(params));
        }
        if ("category_suggestions".equals(type)) {
            return toJson(Map.of("suggestions", categorySuggestions(params)));
        }

        String prompt = buildPrompt(type, params);
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return callOpenAi(prompt);
            } catch (Exception e) {
                log.warn("AI provider call failed, using template fallback: {}", e.getMessage());
            }
        }
        return templateFallback(type, params);
    }

    public Map<String, Object> processCommand(String prompt, Map<String, Object> context) {
        if (prompt == null || prompt.isBlank()) {
            throw new ValidationException("Prompt is required");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return parseCommandJson(callOpenAi(buildCommandPrompt(prompt, context)));
            } catch (Exception e) {
                log.warn("AI command parse failed, using rules: {}", e.getMessage());
            }
        }
        return ruleBasedCommand(prompt.trim(), context == null ? Map.of() : context);
    }

    public Map<String, Object> productFill(Map<String, Object> params) {
        String name = str(params.get("name"));
        if (name.isBlank()) {
            throw new ValidationException("Product name is required");
        }

        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String json = callOpenAi(buildProductFillPrompt(params));
                return parseProductFillJson(json, params);
            } catch (Exception e) {
                log.warn("AI product_fill failed, using template: {}", e.getMessage());
            }
        }
        return templateProductFill(params);
    }

    public List<String> categorySuggestions(Map<String, Object> params) {
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String raw = callOpenAi(buildCategorySuggestionsPrompt(params));
                return parseCategoryList(raw);
            } catch (Exception e) {
                log.warn("AI category_suggestions failed, using template: {}", e.getMessage());
            }
        }
        return templateCategorySuggestions(params);
    }

    private String buildPrompt(String type, Map<String, Object> params) {
        String name = str(params.get("name"));
        String category = str(params.get("category"));
        return switch (type) {
            case "product_description" -> "Write a short, appetising 2-sentence marketing description for a product named \""
                    + name + "\"" + (category.isBlank() ? "" : " in the " + category + " category")
                    + ". Keep it under 240 characters. No hashtags.";
            case "business_bio" -> "Write a warm, professional 2-3 sentence business bio for \"" + name + "\""
                    + (category.isBlank() ? "" : ", a " + category + " business")
                    + (str(params.get("city")).isBlank() ? "" : " based in " + str(params.get("city")))
                    + ". Make it inviting to customers. Under 300 characters.";
            default -> throw new ValidationException("Unknown AI generation type: " + type);
        };
    }

    private String buildProductFillPrompt(Map<String, Object> params) {
        String name = str(params.get("name"));
        String business = str(params.get("businessName"));
        String existing = str(params.get("existingCategories"));
        return """
                You are helping a local business owner add a product to their catalog.
                Product name (DO NOT change): %s
                Business: %s
                Existing categories: %s

                Return ONLY valid JSON with these keys:
                - "description": string, 2 appetising sentences under 240 chars
                - "suggestedPrice": number in INR (realistic for India)
                - "suggestedCategory": string, one category name (reuse existing if suitable, else suggest new)

                No markdown, no extra text.
                """.formatted(name, business.isBlank() ? "local shop" : business,
                existing.isBlank() ? "none" : existing);
    }

    private String buildCategorySuggestionsPrompt(Map<String, Object> params) {
        String business = str(params.get("businessName"));
        String desc = str(params.get("businessDescription"));
        String existing = str(params.get("existingCategories"));
        return """
                Suggest 5 product category names for this business.
                Business: %s
                Description: %s
                Already have: %s

                Return ONLY a JSON array of 5 short category name strings. No duplicates with existing.
                Example: ["Starters","Main Course","Beverages","Desserts","Combos"]
                """.formatted(
                business.isBlank() ? "local shop" : business,
                desc.isBlank() ? "general retail" : desc,
                existing.isBlank() ? "none" : existing);
    }

    private Map<String, Object> parseProductFillJson(String json, Map<String, Object> params) throws Exception {
        String cleaned = json.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        JsonNode node = mapper.readTree(cleaned);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", node.path("description").asText(
                templateProductFill(params).get("description").toString()));
        result.put("suggestedPrice", node.path("suggestedPrice").asDouble(
                ((Number) templateProductFill(params).get("suggestedPrice")).doubleValue()));
        result.put("suggestedCategory", node.path("suggestedCategory").asText(
                templateProductFill(params).get("suggestedCategory").toString()));
        return result;
    }

    private List<String> parseCategoryList(String raw) throws Exception {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        if (cleaned.startsWith("[")) {
            return mapper.readValue(cleaned, new TypeReference<List<String>>() {});
        }
        List<String> list = new ArrayList<>();
        for (String part : cleaned.split("[,\n]")) {
            String s = part.replaceAll("^[\"'\\s\\[\\]]+", "").replaceAll("[\"'\\s\\[\\]]+$", "").trim();
            if (!s.isBlank()) list.add(s);
        }
        return list.isEmpty() ? templateCategorySuggestions(Map.of()) : list;
    }

    private Map<String, Object> templateProductFill(Map<String, Object> params) {
        String name = str(params.get("name"));
        String category = str(params.get("category"));
        if (category.isBlank()) category = guessCategory(name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", templateFallback("product_description", Map.of("name", name, "category", category)));
        result.put("suggestedPrice", guessPrice(name));
        result.put("suggestedCategory", category);
        return result;
    }

    private List<String> templateCategorySuggestions(Map<String, Object> params) {
        String desc = str(params.get("businessDescription")).toLowerCase();
        String existing = str(params.get("existingCategories")).toLowerCase();

        List<String> pool;
        if (desc.contains("food") || desc.contains("restaurant") || desc.contains("cafe") || desc.contains("bakery")) {
            pool = List.of("Starters", "Main Course", "Beverages", "Desserts", "Combos", "Snacks");
        } else if (desc.contains("salon") || desc.contains("spa") || desc.contains("beauty")) {
            pool = List.of("Hair Care", "Skin Care", "Packages", "Grooming", "Add-ons");
        } else if (desc.contains("cloth") || desc.contains("boutique") || desc.contains("fashion")) {
            pool = List.of("New Arrivals", "Ethnic Wear", "Casual", "Accessories", "Sale");
        } else {
            pool = List.of("Popular", "New Arrivals", "Best Sellers", "Offers", "Premium");
        }

        List<String> out = new ArrayList<>();
        for (String s : pool) {
            if (!existing.contains(s.toLowerCase()) && out.size() < 5) out.add(s);
        }
        return out;
    }

    private double guessPrice(String name) {
        String n = name.toLowerCase();
        if (n.contains("combo") || n.contains("platter") || n.contains("family")) return 499;
        if (n.contains("premium") || n.contains("special")) return 349;
        if (n.contains("small") || n.contains("mini")) return 99;
        return new double[]{149, 199, 249, 299}[random.nextInt(4)];
    }

    private String guessCategory(String name) {
        String n = name.toLowerCase();
        if (n.matches(".*(coffee|tea|juice|shake|drink|lassi|water).*")) return "Beverages";
        if (n.matches(".*(cake|sweet|dessert|ice cream|brownie).*")) return "Desserts";
        if (n.matches(".*(rice|biryani|curry|thali|meal|noodles|pizza|burger).*")) return "Main Course";
        if (n.matches(".*(samosa|pakora|starter|snack|chips).*")) return "Starters";
        return "Popular";
    }

    private String callOpenAi(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a concise assistant for local Indian businesses. Respond exactly as requested."),
                        Map.of("role", "user", "content", prompt)),
                "temperature", 0.7,
                "max_tokens", 400);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText().trim();
    }

    private String templateFallback(String type, Map<String, Object> params) {
        String name = str(params.get("name"));
        String category = str(params.get("category"));
        String city = str(params.get("city"));

        if ("product_description".equals(type)) {
            String[] openers = {
                    "Treat yourself to our %s — crafted with care and made to impress.",
                    "Say hello to %s, a customer favourite you'll keep coming back for.",
                    "Our %s brings together quality and value in every single bite.",
                    "Discover %s — thoughtfully prepared and always fresh.",
            };
            String[] closers = {
                    " Perfect for any occasion.",
                    " Order now and taste the difference.",
                    " Freshly made, just for you.",
                    category.isBlank() ? " A must-try!" : " One of the best in " + category + ".",
            };
            return String.format(pick(openers), name) + pick(closers);
        }

        String[] bios = {
                "Welcome to %s%s. We're passionate about delivering quality and a warm experience to every customer%s.",
                "%s%s is your trusted local destination. We take pride in great service and products you'll love%s.",
                "At %s%s, every detail matters. Come experience the care and quality we're known for%s.",
        };
        String catPart = category.isBlank() ? "" : ", a leading " + category + " business";
        String cityPart = city.isBlank() ? "" : " in " + city;
        return String.format(pick(bios), name, catPart, cityPart);
    }

    private String buildCommandPrompt(String prompt, Map<String, Object> context) {
        return """
                You are BizLink AI — assistant for local business owners in India.
                Understand English, Telugu, and Hinglish. Parse the user command and return ONLY valid JSON:

                {
                  "reply": "short friendly message to show the user",
                  "action": {
                    "type": "navigate|add_product|add_category|apply_bio|setup_profile|suggest_categories|show_text|help|none",
                    "path": "/dashboard/... (only for navigate)",
                    "name": "product name (add_product)",
                    "price": 199 (number, add_product, optional),
                    "description": "text (add_product or apply_bio or show_text or setup_profile)",
                    "categoryName": "category name (add_product or add_category)",
                    "categories": ["Cat1","Cat2"] (suggest_categories only),
                    "businessName": "suggested business name (setup_profile)",
                    "phone": "10-digit phone (setup_profile)",
                    "whatsappNumber": "whatsapp number (setup_profile)",
                    "address": "street/area (setup_profile)",
                    "city": "city name (setup_profile)",
                    "state": "state (setup_profile)",
                    "businessHours": "hours string (setup_profile)",
                    "autoCreate": true (setup_profile when user wants profile created)
                  }
                }

                Current page: %s
                Business: %s
                Owner name: %s
                Has business profile: %s
                Existing categories: %s

                Action rules:
                - navigate: open/go/show orders, products, categories, profile, qr, customers, settings, dashboard, subscription, payments
                - add_product: user wants to add/create a product (extract name, optional price in INR)
                - add_category: user wants to create/add a single category (extract categoryName)
                - setup_profile: user wants business profile created/filled — extract phone, whatsapp, city, business type, suggest businessName, address, description
                - apply_bio: user wants only business bio/about text written or updated
                - suggest_categories: user wants category ideas for their catalog
                - show_text: write product marketing copy without adding it
                - help: user asks what you can do
                - none: general chat answer in reply only

                User command: %s
                """.formatted(
                str(context.get("page")),
                str(context.get("businessName")).isBlank() ? "not set" : str(context.get("businessName")),
                str(context.get("userName")).isBlank() ? "owner" : str(context.get("userName")),
                Boolean.TRUE.equals(context.get("hasBusiness")) ? "yes" : "no",
                str(context.get("existingCategories")).isBlank() ? "none" : str(context.get("existingCategories")),
                prompt);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCommandJson(String json) throws Exception {
        String cleaned = json.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        JsonNode root = mapper.readTree(cleaned);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", root.path("reply").asText("Done."));
        JsonNode actionNode = root.path("action");
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", actionNode.path("type").asText("none"));
        if (actionNode.has("path")) action.put("path", actionNode.path("path").asText());
        if (actionNode.has("name")) action.put("name", actionNode.path("name").asText());
        if (actionNode.has("price") && !actionNode.path("price").isNull()) {
            action.put("price", actionNode.path("price").asDouble());
        }
        if (actionNode.has("description")) action.put("description", actionNode.path("description").asText());
        if (actionNode.has("categoryName")) action.put("categoryName", actionNode.path("categoryName").asText());
        if (actionNode.has("businessName")) action.put("businessName", actionNode.path("businessName").asText());
        if (actionNode.has("phone")) action.put("phone", actionNode.path("phone").asText());
        if (actionNode.has("whatsappNumber")) action.put("whatsappNumber", actionNode.path("whatsappNumber").asText());
        if (actionNode.has("address")) action.put("address", actionNode.path("address").asText());
        if (actionNode.has("city")) action.put("city", actionNode.path("city").asText());
        if (actionNode.has("state")) action.put("state", actionNode.path("state").asText());
        if (actionNode.has("pincode")) action.put("pincode", actionNode.path("pincode").asText());
        if (actionNode.has("businessHours")) action.put("businessHours", actionNode.path("businessHours").asText());
        if (actionNode.has("autoCreate")) action.put("autoCreate", actionNode.path("autoCreate").asBoolean(false));
        if (actionNode.has("products") && actionNode.path("products").isArray()) {
            List<Map<String, Object>> products = new ArrayList<>();
            for (JsonNode item : actionNode.path("products")) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", item.path("name").asText(""));
                if (item.has("price") && !item.path("price").isNull()) {
                    p.put("price", item.path("price").asDouble());
                }
                if (item.has("description")) p.put("description", item.path("description").asText());
                if (item.has("categoryName")) p.put("categoryName", item.path("categoryName").asText());
                products.add(p);
            }
            action.put("products", products);
        }
        if (actionNode.has("categories") && actionNode.path("categories").isArray()) {
            List<String> cats = new ArrayList<>();
            for (JsonNode n : actionNode.path("categories")) {
                if (!n.asText("").isBlank()) cats.add(n.asText());
            }
            action.put("categories", cats);
        }
        result.put("action", action);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ruleBasedCommand(String prompt, Map<String, Object> context) {
        prompt = normalizeVoicePrompt(prompt);
        String lower = prompt.toLowerCase();
        String page = str(context.get("page"));
        String businessName = str(context.get("businessName"));

        // Help
        if (lower.matches(".*\\b(help|what can you do|em cheyagalavu|enti cheyagalavu)\\b.*")) {
            return commandResult(
                    "I can open pages, add products, create categories, write bios, suggest categories, and write product copy. Try: \"Create category Biryani\" or \"Add product Idli 40\".",
                    Map.of("type", "help"));
        }

        // Navigate
        String navPath = detectNavigation(lower);
        if (navPath != null) {
            return commandResult("Opening " + friendlyPage(navPath) + "…", Map.of("type", "navigate", "path", navPath));
        }

        // Create category — extract actual name (e.g. "add category biryani" → "Biryani")
        String categoryName = resolveCategoryName(prompt, lower);
        if (categoryName != null && shouldCreateCategory(lower, prompt, page)) {
            return commandResult(
                    "Creating category **" + categoryName + "**…",
                    Map.of("type", "add_category", "categoryName", categoryName));
        }

        // Add product(s) — parse names (e.g. "add products dosa, idli and bonda")
        List<ProductParse> products = resolveProducts(prompt, lower, page);
        if (!products.isEmpty() && shouldCreateProducts(lower, prompt, page)) {
            String categoryHint = inferProductCategory(lower);
            if (products.size() == 1) {
                ProductParse p = products.get(0);
                Map<String, Object> fillParams = new LinkedHashMap<>();
                fillParams.put("name", p.name);
                fillParams.put("businessName", businessName);
                fillParams.put("existingCategories", str(context.get("existingCategories")));
                Map<String, Object> fill = productFill(fillParams);
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("type", "add_product");
                action.put("name", p.name);
                if (p.price != null) action.put("price", p.price);
                else action.put("price", fill.get("suggestedPrice"));
                action.put("description", fill.get("description"));
                String cat = categoryHint.isBlank() ? str(fill.get("suggestedCategory")) : categoryHint;
                action.put("categoryName", cat);
                String pricePart = p.price != null ? " at ₹" + p.price.intValue()
                        : " at ₹" + ((Number) fill.get("suggestedPrice")).intValue();
                return commandResult("Adding **" + p.name + "**" + pricePart + "…", action);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (ProductParse p : products) {
                Map<String, Object> fillParams = new LinkedHashMap<>();
                fillParams.put("name", p.name);
                fillParams.put("businessName", businessName);
                fillParams.put("existingCategories", str(context.get("existingCategories")));
                Map<String, Object> fill = productFill(fillParams);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", p.name);
                item.put("price", p.price != null ? p.price : fill.get("suggestedPrice"));
                item.put("description", fill.get("description"));
                item.put("categoryName", categoryHint.isBlank() ? fill.get("suggestedCategory") : categoryHint);
                items.add(item);
                labels.add(p.name);
            }
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("type", "add_products");
            action.put("products", items);
            if (!categoryHint.isBlank()) action.put("categoryName", categoryHint);
            return commandResult(
                    "Adding **" + items.size() + " products**: " + String.join(", ", labels) + "…",
                    action);
        }

        // Category suggestions
        if (lower.matches(".*\\b(suggest categories|category ideas|categories suggest|category lu suggest)\\b.*")) {
            List<String> suggestions = categorySuggestions(context);
            return commandResult(
                    "Here are category ideas for your business:",
                    Map.of("type", "suggest_categories", "categories", suggestions));
        }

        // Setup / fill business profile
        Map<String, Object> profileSetup = parseSetupProfile(prompt, lower, businessName, context);
        if (profileSetup != null) {
            return profileSetup;
        }

        // Business bio (description only)
        if (lower.matches(".*\\b(bio|about us|about section|profile description|bio rayi|write bio)\\b.*")
                && !lower.contains("business profile") && !lower.contains("phone") && !lower.contains("number")) {
            String name = businessName.isBlank() ? extractQuotedOrTail(prompt, "bio") : businessName;
            if (name.isBlank()) name = "My Business";
            String bio = templateFallback("business_bio", Map.of("name", name, "city", str(context.get("city"))));
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("type", "apply_bio");
            action.put("description", bio);
            return commandResult("Here's a bio for your business page. Applying it now…", action);
        }

        // Product description (show text only)
        if (lower.matches(".*\\b(describe|write description|product description|marketing copy)\\b.*")) {
            String productName = extractProductNameFromDescribe(prompt, lower);
            if (!productName.isBlank()) {
                String desc = templateFallback("product_description", Map.of("name", productName));
                return commandResult(desc, Map.of("type", "show_text", "description", desc));
            }
        }

        // Page-aware defaults — only short single-field hints, not full profile requests
        if ("/dashboard/profile".equals(page) && prompt.length() < 80
                && !lower.contains("number") && !lower.contains("phone") && !lower.contains("whatsapp")
                && !lower.contains("address") && !lower.contains("tiffin") && !lower.contains("restaurant")) {
            String bio = templateFallback("business_bio", Map.of("name", businessName.isBlank() ? "My Business" : businessName));
            return commandResult("Updated bio draft for your profile:", Map.of("type", "apply_bio", "description", bio));
        }

        return commandResult(
                "Try: \"Add products dosa, idli and bonda\" or \"Add product Masala Dosa 80\".",
                Map.of("type", "none"));
    }

    private Map<String, Object> parseSetupProfile(String prompt, String lower, String businessName, Map<String, Object> context) {
        boolean wantsProfile = lower.contains("business profile")
                || lower.matches(".*\\b(setup|create|add|fill|make).{0,30}\\b(business|profile|store|shop)\\b.*")
                || lower.matches(".*\\b(tiffin center|tiffin centre|restaurant|cafe|bakery|salon|boutique|grocery|kirana)\\b.*")
                || ((lower.contains("number is") || lower.contains("whatsapp") || lower.contains("phone"))
                && (lower.contains("profile") || lower.contains("business") || lower.contains("address")
                || lower.contains("suggest") || "/dashboard/profile".equals(str(context.get("page")))));

        if (!wantsProfile) return null;

        String phone = extractPhone(prompt);
        String whatsapp = extractWhatsApp(prompt, lower, phone);
        String city = extractCity(prompt, lower);
        String state = inferState(city);
        String businessType = extractBusinessType(lower);
        String suggestedName = businessName.isBlank()
                ? suggestBusinessName(businessType, city, str(context.get("userName")))
                : businessName;
        String address = extractAddress(prompt, lower, city);
        String description = templateFallback("business_bio", Map.of("name", suggestedName, "city", city));
        String hours = businessType.contains("tiffin") ? "Mon–Sat: 6 AM – 10 PM" : "Mon–Sat: 9 AM – 9 PM";

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "setup_profile");
        action.put("businessName", suggestedName);
        action.put("description", description);
        if (phone != null) action.put("phone", phone);
        if (whatsapp != null) action.put("whatsappNumber", whatsapp);
        if (!address.isBlank()) action.put("address", address);
        if (!city.isBlank()) action.put("city", city);
        if (!state.isBlank()) action.put("state", state);
        action.put("businessHours", hours);
        action.put("autoCreate", true);

        StringBuilder reply = new StringBuilder("Setting up **").append(suggestedName).append("**");
        if (phone != null) reply.append(" with phone ").append(phone);
        if (!city.isBlank()) reply.append(" in ").append(city);
        reply.append(". Creating your profile…");
        return commandResult(reply.toString(), action);
    }

    private String extractPhone(String prompt) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([6-9]\\d{9})\\b").matcher(prompt);
        return m.find() ? m.group(1) : null;
    }

    private String extractWhatsApp(String prompt, String lower, String phone) {
        if (phone != null && (lower.contains("both") || lower.contains("whatsapp and") || lower.contains("same number"))) {
            return phone;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)whatsapp\\s*(?:number|no\\.?|is)?\\s*[:\\-]?\\s*([6-9]\\d{9})")
                .matcher(prompt);
        if (m.find()) return m.group(1);
        return phone;
    }

    private String extractCity(String prompt, String lower) {
        if (lower.contains("hyderabad") || lower.matches(".*\\bhyd\\b.*") || lower.contains(" address hyd")) {
            return "Hyderabad";
        }
        if (lower.contains("bangalore") || lower.contains("bengaluru")) return "Bangalore";
        if (lower.contains("chennai")) return "Chennai";
        if (lower.contains("mumbai")) return "Mumbai";
        if (lower.contains("delhi")) return "Delhi";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:city|in|at|address)\\s+(?:is\\s+)?([A-Za-z\\s]{3,30})")
                .matcher(prompt);
        if (m.find()) {
            String c = m.group(1).trim();
            if (c.length() <= 30 && !c.toLowerCase().contains("number")) return capitalizeWords(c);
        }
        return "";
    }

    private String inferState(String city) {
        if (city == null || city.isBlank()) return "";
        String c = city.toLowerCase();
        if (c.contains("hyderabad")) return "Telangana";
        if (c.contains("bangalore") || c.contains("bengaluru")) return "Karnataka";
        if (c.contains("chennai")) return "Tamil Nadu";
        if (c.contains("mumbai")) return "Maharashtra";
        if (c.contains("delhi")) return "Delhi";
        return "";
    }

    private String extractBusinessType(String lower) {
        if (lower.contains("tiffin")) return "tiffin center";
        if (lower.contains("restaurant")) return "restaurant";
        if (lower.contains("cafe") || lower.contains("coffee")) return "cafe";
        if (lower.contains("bakery")) return "bakery";
        if (lower.contains("salon")) return "salon";
        if (lower.contains("boutique")) return "boutique";
        if (lower.contains("grocery") || lower.contains("kirana")) return "grocery store";
        return "local business";
    }

    private String suggestBusinessName(String businessType, String city, String userName) {
        String first = "My";
        if (userName != null && !userName.isBlank()) {
            first = userName.trim().split("\\s+")[0];
            if (!first.isEmpty()) {
                first = Character.toUpperCase(first.charAt(0)) + (first.length() > 1 ? first.substring(1) : "");
            }
        }
        return switch (businessType) {
            case "tiffin center" -> first + "'s Tiffin Center";
            case "restaurant" -> first + "'s Restaurant";
            case "cafe" -> first + "'s Cafe";
            case "bakery" -> first + "'s Bakery";
            case "salon" -> first + "'s Salon";
            default -> city.isBlank() ? first + "'s Store" : first + "'s " + city + " Store";
        };
    }

    private String extractAddress(String prompt, String lower, String city) {
        if (!city.isBlank()) {
            return "Main Road, " + city;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)address\\s+(?:is\\s+)?(.+?)(?:\\s+and\\s+|\\s*$)")
                .matcher(prompt);
        if (m.find()) return capitalizeWords(m.group(1).trim());
        return "";
    }

    private boolean shouldCreateCategory(String lower, String prompt, String page) {
        if (lower.matches(".*\\b(open|show|go to|navigate|suggest|help)\\b.*")) return false;
        if (lower.contains("category") || lower.contains("categories")) {
            return lower.matches(".*\\b(create|add|new|insert|make|create chey|add chey)\\b.*")
                    || lower.contains("name is") || lower.contains("named") || lower.contains("called");
        }
        return "/dashboard/categories".equals(page) && !prompt.trim().contains(" ");
    }

    private String resolveCategoryName(String prompt, String lower) {
        String fromCommand = parseAddCategory(prompt, lower);
        if (fromCommand != null && !fromCommand.isBlank()) return fromCommand;

        java.util.regex.Matcher named = java.util.regex.Pattern
                .compile("(?i)^categories?\\s+(?:for\\s+)?(.+)$")
                .matcher(prompt.trim());
        if (named.find()) {
            String name = cleanCategoryName(named.group(1));
            if (!name.isBlank()) return name;
        }

        if (!prompt.trim().contains(" ") && !lower.matches(".*\\b(open|suggest|help|show|go)\\b.*")) {
            return capitalizeWords(prompt.trim());
        }
        return null;
    }

    private String parseAddCategory(String prompt, String lower) {
        boolean mentionsCategory = lower.contains("category") || lower.contains("categories");
        boolean wantsCreate = lower.matches(".*\\b(create|add|new|insert|make|create chey|add chey)\\b.*")
                || lower.contains("name is") || lower.contains("named") || lower.contains("called");

        if (!mentionsCategory || !wantsCreate) {
            return null;
        }

        java.util.regex.Pattern[] patterns = {
                java.util.regex.Pattern.compile(
                        "(?i)(?:please\\s+)?(?:create|add|new|insert|make)\\s+(?:a\\s+)?categories?\\s+(?:name\\s+is|named|called|with\\s+name)?\\s*[:\\-]?\\s*(.+)",
                        java.util.regex.Pattern.DOTALL),
                java.util.regex.Pattern.compile("(?i)categor(?:y|ies)\\s+name\\s+is\\s+(.+)", java.util.regex.Pattern.DOTALL),
                java.util.regex.Pattern.compile(
                        "(?i)(?:please\\s+)?(?:create|add|new|insert|make)\\s+(?:a\\s+)?categories?\\s+(.+)",
                        java.util.regex.Pattern.DOTALL),
                java.util.regex.Pattern.compile("(?i)categor(?:y|ies)\\s+(?:create|add|new)\\s+(?:chey\\s+)?(.+)", java.util.regex.Pattern.DOTALL),
        };

        for (java.util.regex.Pattern pattern : patterns) {
            java.util.regex.Matcher m = pattern.matcher(prompt.trim());
            if (m.find()) {
                String name = cleanCategoryName(m.group(1));
                if (!name.isBlank()) return name;
            }
        }
        return null;
    }

    private String cleanCategoryName(String raw) {
        if (raw == null) return "";
        String name = raw.trim()
                .replaceAll("(?i)^(?:please\\s+)?(?:create|add|new|insert|make)\\s+(?:a\\s+)?(?:categories?\\s+)?(?:named|called|name\\s+is\\s+)?", "")
                .replaceAll("(?i)^categories?\\s+(?:named|called|name\\s+is\\s+)", "")
                .replaceAll("(?i)\\s+(category|categories)\\s*$", "")
                .replaceAll("^[\"']|[\"']$", "")
                .trim();
        if (name.length() > 60) name = name.substring(0, 60).trim();
        return capitalizeWords(name);
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isBlank()) return text == null ? "" : text;
        String[] parts = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1));
            }
        }
        return sb.toString();
    }

    private Map<String, Object> commandResult(String reply, Map<String, Object> action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("action", action);
        return result;
    }

    private String detectNavigation(String lower) {
        record Route(String... keys) {
            boolean matches(String s) {
                for (String k : keys) if (s.contains(k)) return true;
                return false;
            }
        }
        List<Route> routes = List.of(
                new Route("dashboard", "home", "overview"),
                new Route("business profile", "profile", "business page"),
                new Route("categories", "category"),
                new Route("products", "product list", "catalog"),
                new Route("qr", "qr code"),
                new Route("orders", "order"),
                new Route("customers", "customer"),
                new Route("subscription", "plan", "billing"),
                new Route("payments", "payment", "transactions"),
                new Route("settings", "setting"));
        String[] paths = {
                "/dashboard", "/dashboard/profile", "/dashboard/categories", "/dashboard/products",
                "/dashboard/qr", "/dashboard/orders", "/dashboard/customers",
                "/dashboard/subscription", "/dashboard/payments", "/dashboard/settings"
        };
        boolean wantsNav = lower.matches(".*\\b(open|go to|show|navigate|take me|chupinchu|open chey|vellu|page)\\b.*")
                || lower.matches("^(orders|products|categories|profile|qr|customers|settings|dashboard|payments|subscription)\\b.*");
        if (!wantsNav) return null;
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).matches(lower)) return paths[i];
        }
        return null;
    }

    private String friendlyPage(String path) {
        return switch (path) {
            case "/dashboard" -> "Dashboard";
            case "/dashboard/profile" -> "Business Profile";
            case "/dashboard/categories" -> "Categories";
            case "/dashboard/products" -> "Products";
            case "/dashboard/qr" -> "QR Code";
            case "/dashboard/orders" -> "Orders";
            case "/dashboard/customers" -> "Customers";
            case "/dashboard/subscription" -> "Subscription";
            case "/dashboard/payments" -> "Payments";
            case "/dashboard/settings" -> "Settings";
            default -> "page";
        };
    }

    private record ProductParse(String name, Double price) {}

    private static final String PRODUCT_VERB = "(?:add|create|new|insert|make|plant|add chey|create chey)";

    private String normalizeVoicePrompt(String prompt) {
        if (prompt == null) return "";
        return prompt
                .replaceAll("(?i)\\bplant\\s+products?\\b", "add products")
                .replaceAll("(?i)\\bplans\\s+products?\\b", "add products")
                .trim();
    }

    private boolean shouldCreateProducts(String lower, String prompt, String page) {
        if (lower.matches(".*\\b(open|show|go to|navigate|suggest|help|describe)\\b.*")) return false;
        if (lower.contains("product") && lower.matches(".*\\b(create|add|new|insert|make|plant|add chey|create chey)\\b.*")) {
            return true;
        }
        return "/dashboard/products".equals(page) && !prompt.trim().contains(" ");
    }

    private String inferProductCategory(String lower) {
        if (lower.contains("tiffin")) return "Tiffins";
        if (lower.contains("beverage") || lower.contains("drink") || lower.contains("juice")) return "Beverages";
        if (lower.contains("dessert") || lower.contains("sweet")) return "Desserts";
        if (lower.contains("snack")) return "Snacks";
        return "";
    }

    private List<ProductParse> resolveProducts(String prompt, String lower, String page) {
        List<String> multi = parseProductNameList(prompt, lower);
        if (multi.size() >= 2) {
            return multi.stream().map(n -> new ProductParse(n, null)).toList();
        }
        ProductParse single = parseSingleProduct(prompt, lower);
        if (single != null) return List.of(single);
        if (multi.size() == 1) return List.of(new ProductParse(multi.get(0), null));
        if ("/dashboard/products".equals(page) && !prompt.trim().contains(" ")
                && !lower.matches(".*\\b(open|suggest|help|show|go)\\b.*")) {
            return List.of(new ProductParse(capitalizeWords(prompt.trim()), null));
        }
        return List.of();
    }

    private List<String> parseProductNameList(String prompt, String lower) {
        if (!lower.matches(".*\\b" + PRODUCT_VERB + "\\b.*\\bproducts?\\b.*")
                && !lower.matches(".*\\bproducts?\\b.*\\b(add|create|new|plant)\\b.*")) {
            return List.of();
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:please\\s+)?" + PRODUCT_VERB + "\\s+(?:a\\s+|some\\s+)?products?\\s+(.+)",
                        java.util.regex.Pattern.DOTALL)
                .matcher(prompt.trim());
        if (!m.find()) {
            m = java.util.regex.Pattern.compile("(?i)products?\\s+(?:add|create|new|plant)\\s+(.+)", java.util.regex.Pattern.DOTALL)
                    .matcher(prompt.trim());
            if (!m.find()) return List.of();
        }

        String tail = m.group(1).trim();
        tail = tail.replaceAll("(?i)\\s+(?:for|at|price)\\s+\\d+.*$", "").trim();
        return splitProductNames(tail);
    }

    private List<String> splitProductNames(String tail) {
        tail = stripProductTailPrefix(tail);
        if (tail.isBlank()) return List.of();

        if (tail.contains(",") || tail.toLowerCase().contains(" and ")) {
            String[] parts = tail.split("(?i)\\s*,\\s*|\\s+and\\s+|\\s*&\\s*");
            return collectProductNames(parts);
        }

        String[] words = tail.split("\\s+");
        if (words.length >= 2 && words.length <= 12) {
            List<String> names = new ArrayList<>();
            for (String word : words) {
                if (word.matches("(?i)(like|tiffins?|snacks?|items?|food|dishes?|meals?|plant|plans?)")) {
                    continue;
                }
                String name = cleanProductName(word);
                if (!name.isBlank() && name.length() <= 30) {
                    names.add(name);
                }
            }
            if (names.size() >= 2) return names;
        }

        String single = cleanProductName(tail);
        if (!single.isBlank() && !looksLikeCommandPhrase(single)) {
            return List.of(single);
        }
        return List.of();
    }

    private List<String> collectProductNames(String[] parts) {
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            String name = cleanProductName(part);
            if (!name.isBlank() && !looksLikeCommandPhrase(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private String stripProductTailPrefix(String tail) {
        String t = tail.trim();
        t = t.replaceAll("(?i)^(?:like\\s+(?:tiffins?|snacks?|items?|food|dishes?|meals?|breakfast)\\s*)+", "");
        while (t.toLowerCase().startsWith("like ")) {
            t = t.substring(5).trim();
        }
        return t;
    }

    private ProductParse parseSingleProduct(String prompt, String lower) {
        if (!lower.matches(".*\\b" + PRODUCT_VERB + "\\b.*\\bproducts?\\b.*")
                && !lower.matches(".*\\bproducts?\\b.*\\b(add|create|new|plant)\\b.*")) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:please\\s+)?" + PRODUCT_VERB + "\\s+(?:a\\s+)?products?\\s+(.+)",
                        java.util.regex.Pattern.DOTALL)
                .matcher(prompt.trim());
        if (!m.find()) {
            m = java.util.regex.Pattern.compile("(?i)products?\\s+(?:add|create|new|plant)\\s+(.+)", java.util.regex.Pattern.DOTALL)
                    .matcher(prompt.trim());
            if (!m.find()) return null;
        }

        String tail = m.group(1).trim();
        List<String> names = splitProductNames(tail);
        if (names.size() != 1) {
            return null;
        }

        Double price = null;
        java.util.regex.Matcher priceMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:rs|rupees|₹|inr)?\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(tail);
        if (priceMatcher.find()) {
            price = Double.parseDouble(priceMatcher.group(1));
        }

        return new ProductParse(names.get(0), price);
    }

    private boolean looksLikeCommandPhrase(String name) {
        String n = name.toLowerCase();
        return n.contains("product") || n.startsWith("like ") || n.length() > 45
                || n.split("\\s+").length > 4;
    }

    private String cleanProductName(String raw) {
        if (raw == null) return "";
        String name = raw.trim()
                .replaceAll("(?i)^(?:please\\s+)?(?:add|create|new|plant|like|a|an|the|some)\\s+", "")
                .replaceAll("(?i)^(?:add|create|new|plant)\\s+(?:a\\s+)?products?\\s+", "")
                .replaceAll("^[\"']|[\"']$", "")
                .trim();
        if (name.length() > 50) return "";
        return capitalizeWords(name);
    }

    private ProductParse parseAddProduct(String prompt, String lower) {
        return parseSingleProduct(prompt, lower);
    }

    private String extractQuotedOrTail(String prompt, String keyword) {
        java.util.regex.Matcher q = java.util.regex.Pattern.compile("[\"']([^\"']+)[\"']").matcher(prompt);
        if (q.find()) return q.group(1).trim();
        int idx = prompt.toLowerCase().indexOf(keyword);
        if (idx >= 0) {
            String tail = prompt.substring(idx + keyword.length()).replaceAll("^[\\s:for-]+", "").trim();
            if (!tail.isBlank()) return tail;
        }
        return "";
    }

    private String extractProductNameFromDescribe(String prompt, String lower) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)(?:describe|write description for|product description for)\\s+(.+)")
                .matcher(prompt.trim());
        if (m.find()) return m.group(1).trim();
        return extractQuotedOrTail(prompt, "describe");
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new ValidationException("Failed to serialize AI response");
        }
    }

    private String pick(String[] options) {
        return options[random.nextInt(options.length)];
    }

    private String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
