package com.voicepresentation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Ollama ollama,
        Voice voice,
        String slidesPath,
        int maxHistoryTurns
) {
    public record Ollama(String baseUrl, String model, double temperature, int timeoutSeconds, String keepAlive) {}

    public record Voice(String baseUrl, int timeoutSeconds) {}
}
