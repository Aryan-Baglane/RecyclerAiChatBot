package com.example.EcoScan.service;

import com.example.EcoScan.dto.request.ScanRequest;
import com.example.EcoScan.dto.response.ProductDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ScanService {

    private final String apiKey;
    private final String apiUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // Pattern to find JSON within triple backticks, potentially with 'json' specifier.
    // It captures content between ``` and ```.
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.DOTALL);


    public ScanService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.url}") String apiUrl,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {

        // Validate configuration
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("Gemini API URL is not configured");
        }

        // Ensure URL starts with https://
        if (!apiUrl.startsWith("https://")) {
            throw new IllegalStateException("Gemini API URL must use HTTPS");
        }

        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public ProductDetails scanProduct(ScanRequest request) {
        try {
            // Validate input
            if (request.getBase64ImageData() == null || request.getBase64ImageData().isBlank()) {
                throw new IllegalArgumentException("Image data cannot be empty");
            }

            // Prepare request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of(
                                            "inline_data", Map.of(
                                                    "mime_type", "image/jpeg",
                                                    "data", request.getBase64ImageData()
                                            )
                                    ),
                                    Map.of(
                                            "text", buildAnalysisPrompt(request.getPrompt())
                                    )
                            ))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "topP", 0.8,
                            "topK", 40
                    )
            );

            log.debug("Calling Gemini API with URL: {}", apiUrl);

            // Make API call
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            log.debug("Gemini API response status: {}", response.getStatusCode());
            log.debug("Gemini API raw response body: {}", response.getBody());

            // Parse and return response
            return parseGeminiResponse(response.getBody());

        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to analyze product", e);
        }
    }

    private String buildAnalysisPrompt(String userPrompt) {
        // Use a more direct instruction to prevent markdown formatting
        // Though models can still output it, being explicit helps.
        return """
            Analyze this product image and provide a detailed sustainability assessment.
            Your response MUST be a valid JSON object. DO NOT include any markdown formatting (e.g., ```json or ```).
            Strictly return only the JSON.

            {
                "productName": "string",
                "confidence": number,
                "ecoTip": "string",
                "ecoScore": number,
                "biodegradability": number,
                "carbonFootprint": number,
                "sustainability": number,
                "toxicity": number,
                "categories": [
                    {
                        "title": "string",
                        "score": number,
                        "description": "string",
                        "impactDetails": ["string"]
                    }
                ],
                "alternatives": [
                    {
                        "productName": "string",
                        "features": ["string"],
                        "amazonLink": "string"
                        "ecoScore": number(1-100)
                    }
                ],
                "environmentalAlerts": ["string"],
                "environmentalBenefits": ["string"],
                "environmentalConcerns": ["string"]
            }

            Guidelines:
            1. Provide at least 2 categories.
            2. Suggest 5
                                                                                                        alternatives.
            3. All scores (ecoScore, biodegradability, carbonFootprint, sustainability, toxicity, category scores) must be integers between 0-100.
            4. `confidence` should be a float/double between 0.0 and 1.0.
            5. Provide meaningful entries for environmentalAlerts, environmentalBenefits, and environmentalConcerns if applicable, otherwise empty arrays.
            6. Additional context: %s
            """.formatted(userPrompt != null ? userPrompt : "");
    }


    private ProductDetails parseGeminiResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            log.warn("Received empty or blank JSON response from Gemini API.");
            throw new RuntimeException("Empty or blank response from Gemini API.");
        }

        String textResponse; // This will hold the "text" part from Gemini's content

        try {
            // Parse the top-level Gemini response structure
            Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("No candidates found in Gemini API response.");
            }

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) {
                throw new RuntimeException("No content found in Gemini API response candidate.");
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                throw new RuntimeException("No parts found in Gemini API response content.");
            }

            // ************************************************************
            // CRITICAL CHANGE: Trim immediately after extracting the text
            // ************************************************************
            textResponse = ((String) parts.get(0).get("text")).trim();
            if (textResponse == null || textResponse.isBlank()) { // Check after trimming too
                throw new RuntimeException("No meaningful text part found in Gemini API response.");
            }

        } catch (JsonProcessingException e) {
            log.error("Error parsing outer JSON structure from Gemini response. Raw response: '{}'", jsonResponse, e);
            throw new RuntimeException("Failed to parse outer JSON structure from Gemini API.", e);
        } catch (ClassCastException e) {
            log.error("Type casting error during Gemini response outer parsing. Raw response: '{}'", jsonResponse, e);
            throw new RuntimeException("Unexpected structure in Gemini API outer response.", e);
        } catch (Exception e) { // Catch any other unexpected exceptions for outer parsing
            log.error("An unexpected error occurred while parsing Gemini outer response. Raw response: '{}'", jsonResponse, e);
            throw new RuntimeException("An unforeseen error occurred during API outer response parsing.", e);
        }

        // --- Start of robust JSON content extraction and cleaning ---
        String cleanedJsonResponse = textResponse; // Start with the already trimmed textResponse

        // Try to find and extract content within a markdown code block
        Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(cleanedJsonResponse);
        if (matcher.find()) {
            cleanedJsonResponse = matcher.group(1).trim(); // Extract the content and trim it again
            log.debug("Extracted JSON from markdown block. Length after extraction and trim: {}", cleanedJsonResponse.length());
        } else {
            log.debug("No markdown code block found, assuming raw text is JSON. Length after initial trim: {}", cleanedJsonResponse.length());
        }

        // Aggressively remove all newline and carriage return characters from the *entire string*
        // This handles cases where newlines might be embedded in the middle due to model output formatting.
        cleanedJsonResponse = cleanedJsonResponse.replace("\n", "");
        cleanedJsonResponse = cleanedJsonResponse.replace("\r", "");
        // A final trim to catch any remaining whitespace characters that might have been at the very beginning/end
        cleanedJsonResponse = cleanedJsonResponse.trim();

        // Log with delimiters to confirm no leading/trailing problematic characters
        log.debug("Final cleaned JSON response before final parsing: >>>{}<<<", cleanedJsonResponse);

        // --- End of robust JSON content extraction and cleaning ---

        try {
            // Parse the actual JSON response from the 'cleanedJsonResponse' string
            return objectMapper.readValue(cleanedJsonResponse, ProductDetails.class);

        } catch (JsonProcessingException e) {
            // This error means `cleanedJsonResponse` is not valid JSON
            log.error("Error parsing JSON from cleaned Gemini response. Cleaned response was: '{}'", cleanedJsonResponse, e);
            throw new RuntimeException("Failed to parse JSON response from Gemini API. Check if the extracted content is valid JSON.", e);
        } catch (Exception e) {
            log.error("An unexpected error occurred during final JSON parsing. Cleaned response was: '{}'", cleanedJsonResponse, e);
            throw new RuntimeException("An unforeseen error occurred during final JSON parsing.", e);
        }
    }
}