package com.deploypilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiConfig {
    @Value("${gemini.api.key:}")
    private String key;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String url;

    public String getKey() { return key; }
    public String getUrl() { return url; }
}
