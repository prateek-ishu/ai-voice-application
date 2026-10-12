package com.voicepresentation.service;

import com.voicepresentation.model.Deck;
import com.voicepresentation.model.Slide;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Current position in the deck for one session. Not thread-safe; one per session. */
public class SlideNavigator {

    private final Deck deck;
    private int index = 0;

    public SlideNavigator(Deck deck) {
        if (deck.slides().isEmpty()) throw new IllegalArgumentException("Deck has no slides");
        this.deck = deck;
    }

    public int index() { return index; }
    public int total() { return deck.slides().size(); }
    public Slide current() { return deck.slides().get(index); }

    public boolean next() {
        if (index < total() - 1) { index++; return true; }
        return false;
    }

    public boolean previous() {
        if (index > 0) { index--; return true; }
        return false;
    }

    /** slideNumber is 1-based, as people say it. */
    public boolean goTo(int slideNumber) {
        if (slideNumber >= 1 && slideNumber <= total()) { index = slideNumber - 1; return true; }
        return false;
    }

    public String outline() {
        return IntStream.range(0, total())
                .mapToObj(i -> (i + 1) + ". " + deck.slides().get(i).title())
                .collect(Collectors.joining("\n"));
    }

    public String describeCurrent() {
        return describe(index);
    }

    /** Every slide with bullets and notes, so the model can pick the slide that answers a question. */
    public String deckContent() {
        return IntStream.range(0, total()).mapToObj(this::describe).collect(Collectors.joining("\n\n"));
    }

    private String describe(int i) {
        Slide s = deck.slides().get(i);
        String bullets = s.bullets().stream().map(b -> "- " + b).collect(Collectors.joining("\n"));
        return "Slide " + (i + 1) + " of " + total() + ": " + s.title() + "\n" + bullets
                + "\nSpeaker notes: " + s.notes();
    }
}
