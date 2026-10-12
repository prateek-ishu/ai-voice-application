package com.voicepresentation.service;

import com.voicepresentation.config.AppProperties;
import org.springframework.stereotype.Component;

/** Builds a fresh VoiceAgent (with its own slide position and history) for each session. */
@Component
public class VoiceAgentFactory {

    private final SlideService slideService;
    private final LlmService llm;
    private final AppProperties props;

    public VoiceAgentFactory(SlideService slideService, LlmService llm, AppProperties props) {
        this.slideService = slideService;
        this.llm = llm;
        this.props = props;
    }

    public VoiceAgent create() {
        return new VoiceAgent(slideService.newNavigator(), llm, props.maxHistoryTurns());
    }
}
