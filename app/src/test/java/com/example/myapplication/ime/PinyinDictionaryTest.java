package com.example.myapplication.ime;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PinyinDictionaryTest {
    @Test
    public void splitsContinuousPinyinIntoSyllables() {
        assertEquals(
                Arrays.asList("ni", "hao"),
                PinyinDictionary.splitSyllables(
                        "nihao", new HashSet<>(Arrays.asList("ni", "hao", "ma"))));
    }

    @Test
    public void prefersTheFewestSyllablesForAmbiguousInput() {
        assertEquals(
                Arrays.asList("xian"),
                PinyinDictionary.splitSyllables(
                        "xian", new HashSet<>(Arrays.asList("xi", "an", "xian"))));
    }

    @Test
    public void incompletePinyinDoesNotProduceAFalseSegmentation() {
        assertTrue(PinyinDictionary.splitSyllables(
                "niha", new HashSet<>(Arrays.asList("ni", "hao"))).isEmpty());
    }
}
