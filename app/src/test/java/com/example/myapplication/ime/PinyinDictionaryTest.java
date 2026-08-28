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

// 类作用：定义 PinyinDictionaryTest，承载所在模块的主要职责。
public class PinyinDictionaryTest {
// 方法作用：处理 splitsContinuousPinyinIntoSyllables 对应的输入并返回或更新相关结果（splitsContinuousPinyinIntoSyllables）。
    @Test
    public void splitsContinuousPinyinIntoSyllables() {
        assertEquals(
                Arrays.asList("ni", "hao"),
                PinyinDictionary.splitSyllables(
                        "nihao", new HashSet<>(Arrays.asList("ni", "hao", "ma"))));
    }

// 方法作用：处理 prefersTheFewestSyllablesForAmbiguousInput 对应的输入并返回或更新相关结果（prefersTheFewestSyllablesForAmbiguousInput）。
    @Test
    public void prefersTheFewestSyllablesForAmbiguousInput() {
        assertEquals(
                Arrays.asList("xian"),
                PinyinDictionary.splitSyllables(
                        "xian", new HashSet<>(Arrays.asList("xi", "an", "xian"))));
    }

// 方法作用：处理 incompletePinyinDoesNotProduceAFalseSegmentation 对应的输入并返回或更新相关结果（incompletePinyinDoesNotProduceAFalseSegmentation）。
    @Test
    public void incompletePinyinDoesNotProduceAFalseSegmentation() {
        assertTrue(PinyinDictionary.splitSyllables(
                "niha", new HashSet<>(Arrays.asList("ni", "hao"))).isEmpty());
    }

// 方法作用：处理 prefixQueryKeepsOnlyTheHighestRankedCandidates 对应的输入并返回或更新相关结果（prefixQueryKeepsOnlyTheHighestRankedCandidates）。
    @Test
    public void prefixQueryKeepsOnlyTheHighestRankedCandidates() {
        Map<String, List<PinyinDictionary.Entry>> entries = new HashMap<>();
        entries.put("na", Collections.singletonList(entry("较低", 10)));
        entries.put("ni", Collections.singletonList(entry("最高", 100)));
        entries.put("ning", Collections.singletonList(entry("次高", 90)));
        PinyinDictionary dictionary = dictionary(entries);

        assertEquals(Arrays.asList("最高", "次高"), dictionary.query("n", 2));
    }

// 方法作用：处理 learnedCandidateMovesAheadAfterCachedQuery 对应的输入并返回或更新相关结果（learnedCandidateMovesAheadAfterCachedQuery）。
    @Test
    public void learnedCandidateMovesAheadAfterCachedQuery() {
        Map<String, List<PinyinDictionary.Entry>> entries = new HashMap<>();
        entries.put("ni", Arrays.asList(entry("常用", 100), entry("学习", 1)));
        PinyinDictionary dictionary = dictionary(entries);

        assertEquals(Arrays.asList("常用", "学习"), dictionary.query("ni", 2));

        dictionary.recordSelection("学习");

        assertEquals(Arrays.asList("学习", "常用"), dictionary.query("ni", 2));
    }

// 方法作用：处理 completeSyllableDoesNotMergeSeparateLetterEntries 对应的输入并返回或更新相关结果（completeSyllableDoesNotMergeSeparateLetterEntries）。
    @Test
    public void completeSyllableDoesNotMergeSeparateLetterEntries() {
        Map<String, List<PinyinDictionary.Entry>> entries = new HashMap<>();
        entries.put("ru", Collections.singletonList(entry("如", 100)));
        entries.put("r", Collections.singletonList(entry("人", 90)));
        entries.put("u", Collections.singletonList(entry("有", 80)));
        PinyinDictionary dictionary = dictionary(entries);

        assertEquals(Collections.singletonList("如"), dictionary.query("ru", 15));
        assertEquals(Collections.singletonList("如"), dictionary.queryAll("ru"));
    }

// 方法作用：根据输入条件查询并返回匹配结果（queryCanReturnFifteenCandidates）。
    @Test
    public void queryCanReturnFifteenCandidates() {
        Map<String, List<PinyinDictionary.Entry>> entries = new HashMap<>();
        List<PinyinDictionary.Entry> candidates = new java.util.ArrayList<>();
        for (int index = 0; index < 20; index++) {
            candidates.add(entry("候选" + index, 100 - index));
        }
        entries.put("houxuan", candidates);

        PinyinDictionary dictionary = dictionary(entries);

        assertEquals(15, dictionary.query("houxuan", 15).size());
        assertEquals(20, dictionary.queryAll("houxuan").size());
        assertEquals(
                dictionary.query("houxuan", 15),
                dictionary.queryAll("houxuan").subList(0, 15));
    }

// 方法作用：处理 dictionary 对应的输入并返回或更新相关结果（dictionary）。
    private static PinyinDictionary dictionary(
            Map<String, List<PinyinDictionary.Entry>> entries) {
        return new PinyinDictionary(
                entries,
                entries.keySet(),
                null,
                Collections.emptyMap());
    }

// 方法作用：处理 entry 对应的输入并返回或更新相关结果（entry）。
    private static PinyinDictionary.Entry entry(String text, long weight) {
        return new PinyinDictionary.Entry(text, weight);
    }
}
