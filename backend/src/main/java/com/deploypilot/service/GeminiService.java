package com.deploypilot.service;

import com.deploypilot.config.GeminiConfig;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private final GeminiConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    public GeminiService(GeminiConfig config) { this.config = config; }

    public boolean isConfigured() {
        return config.getKey() != null && !config.getKey().isBlank();
    }

    @SuppressWarnings("unchecked")
    public String troubleshoot(String errorContent) {
        if (!isConfigured()) {
            return "AI troubleshooting is not configured. Please set GEMINI_API_KEY environment variable on the backend.";
        }

        String prompt = buildPrompt(errorContent);

        Map<String, Object> contentPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(contentPart));
        Map<String, Object> body = Map.of("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Send the key as a header so it never appears in URLs or error messages
        headers.set("x-goog-api-key", config.getKey());

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(config.getUrl(), request, Map.class);

            if (response.getBody() != null) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    Map<String, Object> respContent = (Map<String, Object>) candidate.get("content");
                    if (respContent != null) {
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) respContent.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            return (String) parts.get(0).get("text");
                        }
                    }
                }
            }
            return "Could not parse AI response. Please try again with a shorter error message.";
        } catch (Exception e) {
            return "Error calling AI service: " + e.getMessage() + ". Please try again later.";
        }
    }

    private String buildPrompt(String errorContent) {
        return "You are a deployment troubleshooting assistant. Analyze this error and provide:\n\n" +
            "LIKELY CAUSE\nWhat the error means in simple terms\n\n" +
            "WHAT TO CHECK FIRST\nThe most common reason this happens\n\n" +
            "EXACT STEPS TO FIX\nNumbered, clear instructions\n\n" +
            "COMMANDS TO RUN\nAny terminal commands needed\n\n" +
            "EXPECTED RESULT\nWhat success looks like\n\n" +
            "NEXT STEPS IF IT STILL FAILS\nAlternative approaches\n\n" +
            "SECURITY WARNING\nIf any credentials or secrets are involved\n\n" +
            "Error:\n" + errorContent + "\n\n" +
            "Format your response using the headers above. Be concise and actionable.";
    }
}
