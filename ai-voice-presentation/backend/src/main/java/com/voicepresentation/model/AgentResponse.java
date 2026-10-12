package com.voicepresentation.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentResponse(
        AgentAction action,
        @JsonProperty("slide_index") int slideIndex,
        String transcript,
        String speech
) {
    public AgentResponse {
        if (slideIndex < 0) throw new IllegalArgumentException("slide_index must be >= 0");
        transcript = transcript == null ? "" : transcript;
        speech = speech == null ? "" : speech;
    }

    @JsonProperty("type")
    public String type() {
        return "agent_response";
    }
}
