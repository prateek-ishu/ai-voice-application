package com.voicepresentation.service;

import com.voicepresentation.model.Deck;
import com.voicepresentation.model.Slide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SlideNavigatorTest {

    private SlideNavigator nav;

    @BeforeEach
    void setUp() {
        List<Slide> slides = IntStream.rangeClosed(1, 3)
                .mapToObj(i -> new Slide(i, "S" + i, List.of(), "")).toList();
        nav = new SlideNavigator(new Deck("T", slides));
    }

    @Test
    void startsAtFirstSlide() {
        assertEquals(0, nav.index());
        assertEquals("S1", nav.current().title());
    }

    @Test
    void nextStopsAtLastSlide() {
        assertTrue(nav.next());
        assertTrue(nav.next());
        assertFalse(nav.next());
        assertEquals(2, nav.index());
    }

    @Test
    void previousStopsAtFirstSlide() {
        assertFalse(nav.previous());
        nav.next();
        assertTrue(nav.previous());
        assertEquals(0, nav.index());
    }

    @ParameterizedTest
    @CsvSource({"1,true,0", "3,true,2", "0,false,0", "4,false,0"})
    void goTo(int number, boolean ok, int expectedIndex) {
        assertEquals(ok, nav.goTo(number));
        assertEquals(expectedIndex, nav.index());
    }

    @Test
    void emptyDeckRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SlideNavigator(new Deck("T", List.of())));
    }

    @Test
    void outlineListsSlides() {
        assertEquals("1. S1\n2. S2\n3. S3", nav.outline());
    }
}
