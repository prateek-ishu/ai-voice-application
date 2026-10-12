package com.voicepresentation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicepresentation.model.AgentAction;
import com.voicepresentation.model.AgentResponse;
import com.voicepresentation.model.LlmDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AgentResponseTest {

    private final LlmService llm = new LlmService(null, new ObjectMapper());

    @Test
    void gotoRequiresTarget() {
        assertThrows(IllegalArgumentException.class, () -> new LlmDecision(AgentAction.GOTO, null, "ok"));
    }

    @Test
    void responseRejectsNegativeIndex() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResponse(AgentAction.NEXT, -1, "", "x"));
    }

    @Test
    void parseHandlesFencedJson() {
        LlmDecision d = llm.parse("```json\n{\"action\": \"answer\", \"target_slide\": null, \"speech\": \"Yes.\"}\n```");
        assertEquals(AgentAction.ANSWER, d.action());
        assertEquals("Yes.", d.speech());
    }

    @Test
    void parseTreatsStepWithTargetAsGoto() {
        LlmDecision d = llm.parse("{\"action\": \"next\", \"target_slide\": 8, \"speech\": \"Final slide.\"}");
        assertEquals(AgentAction.GOTO, d.action());
        assertEquals(8, d.targetSlide());
    }

    @Test
    void parseFallsBackOnGarbage() {
        assertEquals(AgentAction.NONE, llm.parse("not json").action());
    }

    @Test
    void responseSerializesWithSnakeCaseFields() throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                new AgentResponse(AgentAction.GOTO, 2, "go to slide 3", "Here it is."));
        assertTrue(json.contains("\"type\":\"agent_response\""));
        assertTrue(json.contains("\"action\":\"goto\""));
        assertTrue(json.contains("\"slide_index\":2"));
    }

    static Stream<Arguments> intents() {
        return Stream.of(
                Arguments.of("Next slide.", new VoiceAgent.QuickIntent(AgentAction.NEXT, null)),
                Arguments.of("go back", new VoiceAgent.QuickIntent(AgentAction.PREVIOUS, null)),
                Arguments.of("Go to slide 3", new VoiceAgent.QuickIntent(AgentAction.GOTO, 3)),
                Arguments.of("slide four", new VoiceAgent.QuickIntent(AgentAction.GOTO, 4)),
                Arguments.of("start over", new VoiceAgent.QuickIntent(AgentAction.GOTO, 1)),
                Arguments.of("Move to last slide", new VoiceAgent.QuickIntent(AgentAction.GOTO, -1)),
                Arguments.of("go to the final slide please", new VoiceAgent.QuickIntent(AgentAction.GOTO, -1)),
                Arguments.of("take me to the end", new VoiceAgent.QuickIntent(AgentAction.GOTO, -1)),
                Arguments.of("jump to the third slide", new VoiceAgent.QuickIntent(AgentAction.GOTO, 3)),
                Arguments.of("first slide", new VoiceAgent.QuickIntent(AgentAction.GOTO, 1)),
                Arguments.of("move to the next slide", new VoiceAgent.QuickIntent(AgentAction.NEXT, null)),
                Arguments.of("go to previous slide", new VoiceAgent.QuickIntent(AgentAction.PREVIOUS, null)),
                Arguments.of("show me this slide", null),
                Arguments.of("what is on the next slide", null),
                Arguments.of("What does the agent do with speaker notes?", null));
    }

    @ParameterizedTest
    @MethodSource("intents")
    void quickIntent(String text, VoiceAgent.QuickIntent expected) {
        assertEquals(Optional.ofNullable(expected), VoiceAgent.quickIntent(text));
    }
}
