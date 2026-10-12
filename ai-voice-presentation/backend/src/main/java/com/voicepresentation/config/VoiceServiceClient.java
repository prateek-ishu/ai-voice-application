package com.voicepresentation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Calls the Python voice service (voice-service/): POST /stt and POST /tts. */
@Component
public class VoiceServiceClient {

    private final AppProperties.Voice cfg;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public VoiceServiceClient(AppProperties props, ObjectMapper mapper) {
        this.cfg = props.voice();
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** Sends browser audio (webm/opus) and returns the recognised text. */
    public String transcribe(byte[] audio) throws IOException, InterruptedException {
        String boundary = "----vp" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"speech.webm\"\r\n"
                + "Content-Type: audio/webm\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(audio);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = request("/stt")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> res = send(req, HttpResponse.BodyHandlers.ofString());
        check(res.statusCode(), res.body());
        return mapper.readTree(res.body()).path("text").asText("").strip();
    }

    /** Returns WAV audio of the text being spoken. */
    public byte[] synthesize(String text) throws IOException, InterruptedException {
        HttpRequest req = request("/tts")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("text", text))))
                .build();
        HttpResponse<byte[]> res = send(req, HttpResponse.BodyHandlers.ofByteArray());
        check(res.statusCode(), new String(res.body(), StandardCharsets.UTF_8));
        return res.body();
    }

    private HttpRequest.Builder request(String path) {
        String base = cfg.baseUrl().endsWith("/") ? cfg.baseUrl().substring(0, cfg.baseUrl().length() - 1) : cfg.baseUrl();
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(cfg.timeoutSeconds()));
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        try {
            return http.send(req, handler);
        } catch (ConnectException e) {
            throw new IOException("Couldn't reach the voice service at " + cfg.baseUrl()
                    + ". Start it from the voice-service folder: uvicorn app.main:app --port 8001", e);
        }
    }

    private static void check(int status, String body) throws IOException {
        if (status / 100 != 2) {
            String snippet = body.length() > 300 ? body.substring(0, 300) : body;
            throw new IOException("Voice service returned HTTP " + status + ": " + snippet);
        }
    }
}
