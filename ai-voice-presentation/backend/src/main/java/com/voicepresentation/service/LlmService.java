package com.voicepresentation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicepresentation.config.OllamaClient;
import com.voicepresentation.model.AgentAction;
import com.voicepresentation.model.LlmDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    static final String SYSTEM_PROMPT = """
            You are a voice presenter controlling a slide deck. The user speaks to you.
            Decide one action and what to say out loud. The full deck is given below.

            Actions:
            - "answer": the user asks a question. Answer it in speech, and set target_slide to the number of
              the slide whose content best covers the question, so that slide is shown while you answer.
              Use null only when no slide is related.
            - "goto": the user asks to see a slide: a number, first, last, the end, or a topic.
              Set target_slide to its number. The last slide is the highest number in the deck.
            - "next" / "previous": move exactly one slide forward or back. target_slide must be null.
            - "present": the user asks you to present or explain a slide. target_slide is that slide,
              or null for the current one.
            - "none": the input was unclear or not for you.

            Rules:
            - Pick target_slide by matching the question to each slide's title, bullets and speaker notes.
            - Ground answers in the deck and speaker notes. If the deck doesn't cover it, say so briefly.
            - Speech is read aloud: 1-3 short sentences, no markdown, no lists.
            - Reply with JSON only: {"action": "...", "target_slide": null, "speech": "..."}

            Examples for a deck where slide 3 is "Pricing" and slide 6 is the last slide:
            - "how much does it cost?" -> {"action": "answer", "target_slide": 3, "speech": "It costs ..."}
            - "move to the last slide" -> {"action": "goto", "target_slide": 6, "speech": ""}
            - "next please" -> {"action": "next", "target_slide": null, "speech": ""}""";

    private final OllamaClient client;
    private final ObjectMapper mapper;

    public LlmService(OllamaClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public LlmDecision decide(String userText, String deck, String current, List<Map<String, String>> history)
            throws IOException, InterruptedException {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "system", "content", "Full deck:\n" + deck + "\n\nCurrently showing:\n" + current));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userText));

        return parse(client.chat(messages, true));
    }

    /** Package-private so tests can call it directly. Never throws: bad output becomes a NONE decision. */
    LlmDecision parse(String raw) {
        String cleaned = raw.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }
        try {
            LlmDecision d = mapper.readValue(cleaned, LlmDecision.class);
            // Small models often say "next" while naming the slide they mean; trust the slide number.
            boolean step = d.action() == AgentAction.NEXT || d.action() == AgentAction.PREVIOUS;
            if (step && d.targetSlide() != null) {
                return new LlmDecision(AgentAction.GOTO, d.targetSlide(), d.speech());
            }
            return d;
        } catch (Exception e) {
            log.warn("Unparseable LLM output ({}): {}", e.getMessage(), raw.length() > 300 ? raw.substring(0, 300) : raw);
            return new LlmDecision(AgentAction.NONE, null, "Sorry, I didn't catch that. Could you say it again?");
        }
    }
}
