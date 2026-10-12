package com.voicepresentation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicepresentation.model.AgentAction;
import com.voicepresentation.model.AgentResponse;
import com.voicepresentation.model.Deck;
import com.voicepresentation.model.LlmDecision;
import com.voicepresentation.model.Slide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class VoiceAgentTest {

    private static final Deck DECK = new Deck("T", IntStream.rangeClosed(1, 4)
            .mapToObj(i -> new Slide(i, "S" + i, List.of(), "Notes " + i)).toList());

    /** LlmService that returns a fixed decision instead of calling Ollama. */
    private static VoiceAgent agentReturning(LlmDecision decision) {
        LlmService llm = new LlmService(null, new ObjectMapper()) {
            @Override
            public LlmDecision decide(String userText, String deck, String current, List<Map<String, String>> history) {
                return decision;
            }
        };
        return new VoiceAgent(new SlideNavigator(DECK), llm, 8);
    }

    @Test
    void answerShowsTheSlideThatCoversTheQuestion() throws Exception {
        AgentResponse r = agentReturning(new LlmDecision(AgentAction.ANSWER, 3, "Because of X."))
                .handleText("why X?");
        assertEquals(2, r.slideIndex());
        assertEquals("Because of X.", r.speech());
    }

    @Test
    void answerWithInvalidSlideStaysPut() throws Exception {
        AgentResponse r = agentReturning(new LlmDecision(AgentAction.ANSWER, 99, "Answer."))
                .handleText("question?");
        assertEquals(0, r.slideIndex());
        assertEquals("Answer.", r.speech());
    }

    @Test
    void gotoNarratesTheNewSlideFromItsNotes() throws Exception {
        AgentResponse r = agentReturning(new LlmDecision(AgentAction.GOTO, 4, ""))
                .handleText("show me the summary");
        assertEquals(3, r.slideIndex());
        assertEquals("Slide 4: S4. Notes 4", r.speech());
    }

    @Test
    void presentWithTargetMovesAndNarrates() throws Exception {
        AgentResponse r = agentReturning(new LlmDecision(AgentAction.PRESENT, 2, ""))
                .handleText("explain the second topic");
        assertEquals(1, r.slideIndex());
        assertEquals("Slide 2: S2. Notes 2", r.speech());
    }

    @Test
    void answerRequiresSpeech() {
        assertThrows(IllegalArgumentException.class, () -> new LlmDecision(AgentAction.ANSWER, 2, " "));
    }
}
