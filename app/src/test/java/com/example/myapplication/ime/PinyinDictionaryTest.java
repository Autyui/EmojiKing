package com.example.myapplication.ime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    @Test
    public void prefixQueryKeepsOnlyTheHighestRankedCandidates() {
        Map<String, List<PinyinDictionary.Entry>> entries = new HashMap<>();
        entries.put("na", Collections.singletonList(entry("较低", 10)));
        entries.put("ni", Collections.singletonList(entry("最高", 100)));
        entries.put("ning", Collections.singletonList(entry("次高", 90)));
        PinyinDictionary dictionary = dictionary(entries);

        assertEquals(Arrays.asList("最高", "次高"), dictionary.query("n", 2));
    }

    @Test
    public void learnedCandidateMovesAheadAfterCachedQuery() {
        Map<String, List<PinyinDictionary.Entry>> entries = new HashMap<>();
        entries.put("ni", Arrays.asList(entry("常用", 100), entry("学习", 1)));
        PinyinDictionary dictionary = dictionary(entries);

        assertEquals(Arrays.asList("常用", "学习"), dictionary.query("ni", 2));

        dictionary.recordSelection("学习");

        assertEquals(Arrays.asList("学习", "常用"), dictionary.query("ni", 2));
    }

    private static PinyinDictionary dictionary(
            Map<String, List<PinyinDictionary.Entry>> entries) {
        return new PinyinDictionary(
                entries,
                entries.keySet(),
                null,
                Collections.emptyMap());
    }

    private static PinyinDictionary.Entry entry(String text, long weight) {
        return new PinyinDictionary.Entry(text, weight);
    }
}
