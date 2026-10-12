package com.voicepresentation.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calls Ollama's native chat API: POST {baseUrl}/api/chat */
@Component
public class OllamaClient {

    private final AppProperties.Ollama cfg;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OllamaClient(AppProperties props, ObjectMapper mapper) {
        this.cfg = props.ollama();
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * Sends a chat and returns the assistant's text.
     * jsonMode = true asks Ollama to constrain the output to valid JSON.
     */
    public String chat(List<Map<String, String>> messages, boolean jsonMode) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("keep_alive", cfg.keepAlive());
        body.put("options", Map.of("temperature", cfg.temperature()));
        if (jsonMode) body.put("format", "json");

        HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(cfg.baseUrl()) + "/api/chat"))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            throw new IOException("Couldn't reach Ollama at " + cfg.baseUrl()
                    + ". Make sure Ollama is running (open the Ollama app, or run `ollama serve`).", e);
        }

        if (res.statusCode() == 404) {
            throw new IOException("Ollama doesn't have the model '" + cfg.model()
                    + "'. Download it with: ollama pull " + cfg.model());
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("Ollama returned HTTP " + res.statusCode() + ": " + res.body());
        }

        JsonNode json = mapper.readTree(res.body());
        return json.path("message").path("content").asText("");
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
