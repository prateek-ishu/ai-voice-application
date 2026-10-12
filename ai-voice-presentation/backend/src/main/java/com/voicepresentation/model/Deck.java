package com.voicepresentation.model;

import java.util.List;

public record Deck(String title, List<Slide> slides) {
    public Deck {
        slides = slides == null ? List.of() : List.copyOf(slides);
    }
}
