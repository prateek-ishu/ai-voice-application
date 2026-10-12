package com.voicepresentation.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AgentAction {
    NEXT, PREVIOUS, GOTO,
    ANSWER,   // answer a question, stay on the slide
    PRESENT,  // narrate the current slide
    NONE;

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static AgentAction fromWire(String value) {
        if (value == null) throw new IllegalArgumentException("action is required");
        return AgentAction.valueOf(value.trim().toUpperCase());
    }
}
