package com.deploypilot.ai;

import com.deploypilot.config.GeminiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Gemini implementation of {@link AiProvider}. Applies a request timeout, caps
 * the prompt and response sizes, and turns every failure into a sanitised
 * "unavailable" result — the raw provider error is logged server-side only,
 * never returned to the user. This is the single Gemini client in the app.
 */
@Component
public class GeminiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);

    private final GeminiConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final int maxPromptChars;
    private final int maxResponseChars;

    public GeminiAiProvider(GeminiConfig config,
                            @Value("${gemini.timeout-ms:20000}") long timeoutMs,
                            @Value("${gemini.max-prompt-chars:24000}") int maxPromptChars,
                            @Value("${gemini.max-response-chars:8000}") int maxResponseChars) {
        this.config = config;
        this.maxPromptChars = maxPromptChars;
        this.maxResponseChars = maxResponseChars;
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5000)))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public boolean isConfigured() {
        return config.getKey() != null && !config.getKey().isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiResponse generate(String prompt) {
        if (!isConfigured()) {
            return AiResponse.unavailable("AI is not configured on this server.");
        }
        String bounded = prompt.length() > maxPromptChars ? prompt.substring(0, maxPromptChars) : prompt;
        try {
            Map<String, Object> body = Map.of("contents",
                List.of(Map.of("parts", List.of(Map.of("text", bounded)))));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Key travels as a header so it never appears in a URL or an error.
            headers.set("x-goog-api-key", config.getKey());
            ResponseEntity<byte[]> response = restTemplate.postForEntity(
                config.getUrl(), new HttpEntity<>(objectMapper.writeValueAsBytes(body), headers), byte[].class);
            String text = extractText(response.getBody());
            if (text == null || text.isBlank()) {
                log.warn("Gemini returned no usable text (status {})", response.getStatusCode().value());
                return AiResponse.unavailable("AI did not return a usable answer. Please try again.");
            }
            if (text.length() > maxResponseChars) {
                text = text.substring(0, maxResponseChars) + "\n[response truncated]";
            }
            return AiResponse.ok(text);
        } catch (Exception e) {
            // Log the real cause server-side; never surface provider internals to the user.
            log.warn("Gemini request failed: {}", e.getClass().getSimpleName());
            return AiResponse.unavailable("AI is temporarily unavailable. Please try again later.");
        }
    }

    private String extractText(byte[] raw) {
        if (raw == null) return null;
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (parts.isArray() && parts.size() > 0) {
                return parts.get(0).path("text").asText(null);
            }
        } catch (Exception e) {
            log.warn("Gemini response was not parseable JSON");
        }
        return null;
    }
}
