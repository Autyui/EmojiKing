package com.example.myapplication.ime;

import android.content.Context;

import com.google.gson.stream.JsonReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Loads the complete lexicon and returns candidates for a pinyin prefix. */
final class PinyinDictionary {
    private static final String ASSET_NAME = "lexicon_chat.json";
    private final NavigableMap<String, List<Entry>> entriesByPinyin;

    private PinyinDictionary(Map<String, List<Entry>> entriesByPinyin) {
        this.entriesByPinyin = new TreeMap<>(entriesByPinyin);
    }

    static PinyinDictionary load(Context context) {
        Map<String, List<Entry>> index = new TreeMap<>();
        try (InputStream input = context.getAssets().open(ASSET_NAME);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(reader)) {
            json.beginArray();
            while (json.hasNext()) {
                readRecord(json, index);
            }
            json.endArray();
        } catch (Exception exception) {
            return new PinyinDictionary(Collections.emptyMap());
        }
        Comparator<Entry> order = Comparator
                .comparingInt((Entry entry) -> entry.frequency).reversed()
                .thenComparing(entry -> entry.text);
        for (List<Entry> values : index.values()) {
            values.sort(order);
        }
        return new PinyinDictionary(index);
    }

    private static void readRecord(JsonReader json, Map<String, List<Entry>> index)
            throws java.io.IOException {
        String token = null;
        int frequency = 0;
        List<List<String>> encodings = Collections.emptyList();
        json.beginObject();
        while (json.hasNext()) {
            String name = json.nextName();
            if ("token".equals(name)) {
                token = json.nextString();
            } else if ("textfreq".equals(name)) {
                frequency = json.nextInt();
            } else if ("encode".equals(name)) {
                encodings = readEncodings(json);
            } else {
                json.skipValue();
            }
        }
        json.endObject();
        if (token == null || token.isEmpty()) {
            return;
        }
        for (List<String> syllables : encodings) {
            String key = joinSyllables(syllables);
            if (!key.isEmpty()) {
                addEntry(index, key, token, frequency);
                addSingleCharacterEntries(index, token, syllables, frequency);
            }
        }
    }

    private static void addSingleCharacterEntries(
            Map<String, List<Entry>> index,
            String token,
            List<String> syllables,
            int frequency) {
        if (token.codePointCount(0, token.length()) != syllables.size()) {
            return;
        }
        int offset = 0;
        for (String syllable : syllables) {
            if (offset >= token.length()) {
                return;
            }
            int codePoint = token.codePointAt(offset);
            int charLength = Character.charCount(codePoint);
            if (isHan(codePoint)) {
                addEntry(
                        index,
                        normalize(syllable),
                        token.substring(offset, offset + charLength),
                        frequency);
            }
            offset += charLength;
        }
    }

    private static void addEntry(
            Map<String, List<Entry>> index,
            String key,
            String text,
            int frequency) {
        if (key == null || key.isEmpty() || text == null || text.isEmpty()) {
            return;
        }
        index.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new Entry(text, frequency));
    }

    private static boolean isHan(int codePoint) {
        return (codePoint >= 0x3400 && codePoint <= 0x4dbf)
                || (codePoint >= 0x4e00 && codePoint <= 0x9fff)
                || (codePoint >= 0x20000 && codePoint <= 0x323af);
    }

    private static List<List<String>> readEncodings(JsonReader json)
            throws java.io.IOException {
        List<List<String>> encodings = new ArrayList<>();
        json.beginArray();
        while (json.hasNext()) {
            List<String> syllables = new ArrayList<>();
            json.beginArray();
            while (json.hasNext()) {
                syllables.add(json.nextString());
            }
            json.endArray();
            encodings.add(syllables);
        }
        json.endArray();
        return encodings;
    }

    List<String> query(String pinyin, int limit) {
        String normalized = normalize(pinyin);
        if (normalized.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        List<Entry> candidates = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> entry
                : entriesByPinyin.tailMap(normalized, true).entrySet()) {
            if (!entry.getKey().startsWith(normalized)) {
                break;
            }
            candidates.addAll(entry.getValue());
        }
        candidates.sort(Comparator
                .comparingInt((Entry entry) -> entry.frequency).reversed()
                .thenComparingInt(entry -> entry.text.length())
                .thenComparing(entry -> entry.text));
        List<String> result = new ArrayList<>();
        for (Entry candidate : candidates) {
            if (!result.contains(candidate.text)) {
                result.add(candidate.text);
                if (result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String joinSyllables(List<String> syllables) {
        if (syllables == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String syllable : syllables) {
            if (syllable != null) {
                result.append(normalize(syllable));
            }
        }
        return result.toString();
    }

    private static final class Entry {
        private final String text;
        private final int frequency;

        private Entry(String text, int frequency) {
            this.text = text;
            this.frequency = frequency;
        }
    }

}
