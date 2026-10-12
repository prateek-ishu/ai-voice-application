package com.voicepresentation.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voicepresentation.model.AgentAction;
import com.voicepresentation.model.AgentResponse;
import com.voicepresentation.service.VoiceAgent;
import com.voicepresentation.service.VoiceAgentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VoiceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketHandler.class);

    private record Session(WebSocketSession ws, VoiceAgent agent) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final VoiceAgentFactory agentFactory;
    private final ObjectMapper mapper;

    public VoiceWebSocketHandler(VoiceAgentFactory agentFactory, ObjectMapper mapper) {
        this.agentFactory = agentFactory;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) {
        // The decorator makes sends thread-safe.
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(ws, 10_000, 512 * 1024);
        sessions.put(ws.getId(), new Session(safe, agentFactory.create()));
        log.info("Session opened: {}", ws.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        sessions.remove(ws.getId());
        log.info("Session closed: {} ({})", ws.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) throws IOException {
        Session s = sessions.get(ws.getId());
        if (s == null) return;

        JsonNode data;
        try {
            data = mapper.readTree(message.getPayload());
        } catch (IOException e) {
            sendError(s, "Messages must be JSON.", null);
            return;
        }

        String type = data.path("type").asText("");
        JsonNode turn = data.get("turn");
        try {
            AgentResponse resp;
            switch (type) {
                case "text" -> {
                    String text = data.path("text").asText("").strip();
                    if (text.isEmpty()) {
                        sendStatus(s, "idle", turn);
                        return;
                    }
                    sendStatus(s, "thinking", turn);
                    resp = s.agent().handleText(text);
                }
                case "navigate" -> {
                    AgentAction action = AgentAction.fromWire(data.path("action").asText());
                    Integer slide = data.hasNonNull("slide") ? data.get("slide").asInt() : null;
                    resp = s.agent().navigate(action, slide);
                }
                default -> {
                    sendError(s, "Unknown message type: " + type, turn);
                    return;
                }
            }
            send(s, resp, turn);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("Turn failed: {}", e.getMessage());
            sendError(s, e.getMessage(), turn);
            sendStatus(s, "idle", turn);
        } catch (Exception e) {
            log.error("Turn failed", e);
            sendError(s, "That request failed on the server. Check the backend logs, then try again.", turn);
            sendStatus(s, "idle", turn);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable exception) {
        log.warn("Transport error on {}: {}", ws.getId(), exception.getMessage());
    }

    private void send(Session s, Object payload, JsonNode turn) throws IOException {
        ObjectNode json = mapper.valueToTree(payload);
        if (turn != null && !turn.isNull()) json.set("turn", turn);
        s.ws().sendMessage(new TextMessage(mapper.writeValueAsString(json)));
    }

    private void sendStatus(Session s, String state, JsonNode turn) throws IOException {
        send(s, Map.of("type", "status", "state", state), turn);
    }

    private void sendError(Session s, String msg, JsonNode turn) throws IOException {
        send(s, Map.of("type", "error", "message", msg), turn);
    }
}
