package com.voicepresentation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmDecision(
        AgentAction action,
        @JsonProperty("target_slide") Integer targetSlide,
        String speech
) {
    public LlmDecision {
        if (action == null) throw new IllegalArgumentException("action is required");
        speech = speech == null ? "" : speech.strip();
        if (action == AgentAction.ANSWER && speech.isEmpty()) throw new IllegalArgumentException("answer requires speech");
        if (action == AgentAction.GOTO && targetSlide == null) {
            throw new IllegalArgumentException("goto action requires target_slide");
        }
    }
}
