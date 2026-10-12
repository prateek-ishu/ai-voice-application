package com.voicepresentation.service;

import com.voicepresentation.model.AgentAction;
import com.voicepresentation.model.AgentResponse;
import com.voicepresentation.model.LlmDecision;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceAgent {

    public record QuickIntent(AgentAction action, Integer target) {}

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("first", 1), Map.entry("second", 2),
            Map.entry("third", 3), Map.entry("fourth", 4), Map.entry("fifth", 5), Map.entry("sixth", 6),
            Map.entry("seventh", 7), Map.entry("eighth", 8), Map.entry("ninth", 9), Map.entry("tenth", 10),
            Map.entry("last", -1), Map.entry("final", -1));

    // Optional lead-in such as "go to", "move to the", "please jump to", "take me to", "show me".
    private static final String VERB =
            "(?:please )?(?:(?:go|move|jump|skip|switch|navigate|take me|bring me)(?: to)? |show(?: me)? |open )?(?:the )?";
    private static final Pattern NEXT = Pattern.compile(VERB + "(next|next slide|next one|forward|continue|move on)( please)?");
    private static final Pattern PREV = Pattern.compile(VERB + "(previous|previous slide|previous one|back)( please)?");
    private static final Pattern RESTART = Pattern.compile("(start over|restart|" + VERB + "beginning|" + VERB + "start)");
    private static final Pattern END = Pattern.compile(VERB + "end( of the deck| of the presentation)?( please)?");
    // "slide 3", "go to slide three", "slide number 4"
    private static final Pattern GOTO = Pattern.compile(VERB + "slide (?:number )?(\\d+|\\w+)( please)?");
    // "move to last slide", "go to the third slide", "first slide"
    private static final Pattern GOTO_ORDINAL = Pattern.compile(VERB + "(\\w+) slide( please)?");

    private final SlideNavigator slides;
    private final LlmService llm;
    private final int maxHistoryTurns;
    private final List<Map<String, String>> history = new ArrayList<>();

    public VoiceAgent(SlideNavigator slides, LlmService llm, int maxHistoryTurns) {
        this.slides = slides;
        this.llm = llm;
        this.maxHistoryTurns = maxHistoryTurns;
    }

    public static Optional<QuickIntent> quickIntent(String text) {
        String t = text.toLowerCase().replaceAll("[^\\w\\s]", "").strip();
        if (NEXT.matcher(t).matches()) return Optional.of(new QuickIntent(AgentAction.NEXT, null));
        if (PREV.matcher(t).matches()) return Optional.of(new QuickIntent(AgentAction.PREVIOUS, null));
        if (RESTART.matcher(t).matches()) return Optional.of(new QuickIntent(AgentAction.GOTO, 1));
        if (END.matcher(t).matches()) return Optional.of(new QuickIntent(AgentAction.GOTO, -1));
        for (Pattern p : new Pattern[] {GOTO, GOTO_ORDINAL}) {
            Matcher m = p.matcher(t);
            if (m.matches()) {
                Integer num = slideNumber(m.group(1));
                if (num != null) return Optional.of(new QuickIntent(AgentAction.GOTO, num));
            }
        }
        return Optional.empty();
    }

    private static Integer slideNumber(String token) {
        if (token.chars().allMatch(Character::isDigit)) return Integer.valueOf(token);
        return NUMBER_WORDS.get(token);
    }

    public AgentResponse handleText(String text) throws IOException, InterruptedException {
        Optional<QuickIntent> quick = quickIntent(text);
        if (quick.isPresent()) {
            Integer target = quick.get().target();
            if (target != null && target == -1) target = slides.total();
            return apply(new LlmDecision(quick.get().action(), target, ""), text);
        }
        LlmDecision d = llm.decide(text, slides.deckContent(), slides.describeCurrent(), List.copyOf(history));
        return apply(d, text);
    }

    public AgentResponse navigate(AgentAction action, Integer target) {
        move(action, target);
        return new AgentResponse(action, slides.index(), "", "");
    }

    private boolean move(AgentAction action, Integer target) {
        return switch (action) {
            case NEXT -> slides.next();
            case PREVIOUS -> slides.previous();
            case GOTO -> target != null && slides.goTo(target);
            default -> true;
        };
    }

    private AgentResponse apply(LlmDecision d, String userText) {
        String speech = switch (d.action()) {
            case NEXT, PREVIOUS, GOTO -> {
                if (move(d.action(), d.targetSlide())) yield narration();
                yield switch (d.action()) {
                    case NEXT -> "That's the last slide.";
                    case PREVIOUS -> "We're already on the first slide.";
                    default -> "There are only " + slides.total() + " slides.";
                };
            }
            case PRESENT -> {
                if (d.targetSlide() != null) slides.goTo(d.targetSlide());
                yield narration();
            }
            case ANSWER -> {
                // Show the slide that covers the question while answering it. An invalid number is ignored.
                if (d.targetSlide() != null) slides.goTo(d.targetSlide());
                yield d.speech();
            }
            case NONE -> d.speech().isBlank() ? "Sorry, I didn't catch that. Could you say it again?" : d.speech();
        };
        return respond(d.action(), speech, userText);
    }

    /** Spoken from the slide's own notes, so it always matches what's on screen. */
    private String narration() {
        return "Slide " + (slides.index() + 1) + ": " + slides.current().title() + ". " + slides.current().notes();
    }

    private AgentResponse respond(AgentAction action, String speech, String userText) {
        if (!userText.isEmpty()) remember(userText, speech);
        // The browser reads `speech` aloud with its built-in speech synthesis.
        return new AgentResponse(action, slides.index(), userText, speech);
    }

    private void remember(String userText, String speech) {
        history.add(Map.of("role", "user", "content", userText));
        history.add(Map.of("role", "assistant", "content", speech));
        while (history.size() > 2 * maxHistoryTurns) history.remove(0);
    }
}
