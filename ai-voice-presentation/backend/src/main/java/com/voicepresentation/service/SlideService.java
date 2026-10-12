package com.voicepresentation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicepresentation.config.AppProperties;
import com.voicepresentation.model.Deck;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads the deck once. Each WebSocket session gets its own SlideNavigator for its position. */
@Service
public class SlideService {

    private final Deck deck;

    public SlideService(AppProperties props, ResourceLoader loader, ObjectMapper mapper) {
        try (InputStream in = loader.getResource(props.slidesPath()).getInputStream()) {
            this.deck = mapper.readValue(in, Deck.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read slides from " + props.slidesPath(), e);
        }
        if (deck.slides().isEmpty()) {
            throw new IllegalStateException("Deck has no slides: " + props.slidesPath());
        }
    }

    public Deck getDeck() {
        return deck;
    }

    public SlideNavigator newNavigator() {
        return new SlideNavigator(deck);
    }
}
