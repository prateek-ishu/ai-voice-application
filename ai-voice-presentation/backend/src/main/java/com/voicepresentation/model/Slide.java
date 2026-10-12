package com.voicepresentation.model;

import java.util.List;

public record Slide(int id, String title, List<String> bullets, String notes) {
    public Slide {
        bullets = bullets == null ? List.of() : List.copyOf(bullets);
        notes = notes == null ? "" : notes;
    }
}
