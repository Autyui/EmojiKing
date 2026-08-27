package com.example.myapplication.ime;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.stream.JsonReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/** Loads the complete lexicon and returns candidates for a pinyin prefix. */
final class PinyinDictionary {
    private static final String ASSET_NAME = "lexicon_chat.json";
    private static final String COMMON_HANZI_ASSET_NAME = "common_hanzi.txt";
    private static final String PREFERENCES_NAME = "pinyin_usage";
    private static final String USAGE_PREFIX = "candidate_";
    private final NavigableMap<String, List<Entry>> entriesByPinyin;
    private final Set<String> syllables;
    private final SharedPreferences usagePreferences;

    private PinyinDictionary(
            Map<String, List<Entry>> entriesByPinyin,
            Set<String> syllables,
            SharedPreferences usagePreferences) {
        this.entriesByPinyin = new TreeMap<>(entriesByPinyin);
        this.syllables = new HashSet<>(syllables);
        this.usagePreferences = usagePreferences;
    }

    static PinyinDictionary load(Context context) {
        Map<String, List<Entry>> index = new TreeMap<>();
        Set<String> syllableIndex = new HashSet<>();
        Set<String> commonCharacters = loadCommonCharacters(context);
        try (InputStream input = context.getAssets().open(ASSET_NAME);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             JsonReader json = new JsonReader(reader)) {
            json.beginArray();
            while (json.hasNext()) {
                readRecord(json, index, syllableIndex, commonCharacters);
            }
            json.endArray();
        } catch (Exception exception) {
            return new PinyinDictionary(
                    Collections.emptyMap(), Collections.emptySet(),
                    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE));
        }
        Comparator<Entry> order = Comparator
                .comparingLong((Entry entry) -> entry.weight).reversed()
                .thenComparing(entry -> entry.text);
        for (List<Entry> values : index.values()) {
            values.sort(order);
        }
        return new PinyinDictionary(
                index,
                syllableIndex,
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE));
    }

    private static void readRecord(
            JsonReader json,
            Map<String, List<Entry>> index,
            Set<String> syllableIndex,
            Set<String> commonCharacters)
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
                addEntry(
                        index,
                        key,
                        token,
                        frequency * 1000 + commonCharacterCount(token, commonCharacters) * 100);
                addSingleCharacterEntries(
                        index, token, syllables, frequency, commonCharacters);
                for (String syllable : syllables) {
                    String normalized = normalize(syllable);
                    if (!normalized.isEmpty()) {
                        syllableIndex.add(normalized);
                    }
                }
            }
        }
    }

    private static void addSingleCharacterEntries(
            Map<String, List<Entry>> index,
            String token,
            List<String> syllables,
            int frequency,
            Set<String> commonCharacters) {
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
                String character = token.substring(offset, offset + charLength);
                addEntry(
                        index,
                        normalize(syllable),
                        character,
                        commonCharacters.contains(character)
                                ? 1_000_000 + frequency * 1000 : frequency * 1000);
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

    private static int commonCharacterCount(String text, Set<String> commonCharacters) {
        int count = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int length = Character.charCount(codePoint);
            if (commonCharacters.contains(text.substring(offset, offset + length))) {
                count++;
            }
            offset += length;
        }
        return count;
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

    private static Set<String> loadCommonCharacters(Context context) {
        Set<String> characters = new HashSet<>();
        try (InputStream input = context.getAssets().open(COMMON_HANZI_ASSET_NAME);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String character = line.trim();
                if (character.codePointCount(0, character.length()) == 1) {
                    characters.add(character);
                }
            }
        } catch (Exception exception) {
            return Collections.emptySet();
        }
        return characters;
    }

    List<String> query(String pinyin, int limit) {
        String normalized = normalize(pinyin);
        if (normalized.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        appendEntries(candidates, entriesByPinyin.get(normalized), limit);
        List<String> split = splitSyllables(normalized, syllables);
        if (!split.isEmpty()) {
            String fallback = composeSingleCharacters(split);
            if (fallback != null) {
                candidates.add(fallback);
            }
        }
        if (candidates.size() < limit && split.isEmpty()) {
            appendPrefixEntries(candidates, normalized, limit);
        }
        return new ArrayList<>(candidates).subList(0, Math.min(limit, candidates.size()));
    }

    void recordSelection(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        String key = USAGE_PREFIX + candidate;
        int count = usagePreferences.getInt(key, 0);
        usagePreferences.edit().putInt(key, Math.min(count + 1, 100_000)).apply();
    }

    private void appendPrefixEntries(Set<String> candidates, String prefix, int limit) {
        List<Entry> prefixEntries = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> entry
                : entriesByPinyin.tailMap(prefix, true).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                break;
            }
            prefixEntries.addAll(entry.getValue());
        }
        prefixEntries.sort(Comparator
                .comparingLong(this::score).reversed()
                .thenComparingInt(entry -> entry.text.length())
                .thenComparing(entry -> entry.text));
        appendEntries(candidates, prefixEntries, limit);
    }

    private void appendEntries(Set<String> candidates, List<Entry> entries, int limit) {
        if (entries == null) {
            return;
        }
        List<Entry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator
                .comparingLong(this::score).reversed()
                .thenComparing(entry -> entry.text));
        for (Entry entry : ordered) {
            candidates.add(entry.text);
            if (candidates.size() >= limit) {
                return;
            }
        }
    }

    private long score(Entry entry) {
        return entry.weight
                + (long) usagePreferences.getInt(USAGE_PREFIX + entry.text, 0) * 10_000_000L;
    }

    private String composeSingleCharacters(List<String> split) {
        StringBuilder result = new StringBuilder();
        for (String syllable : split) {
            List<Entry> entries = entriesByPinyin.get(syllable);
            if (entries == null) {
                return null;
            }
            String character = firstSingleCharacter(entries);
            if (character == null) {
                return null;
            }
            result.append(character);
        }
        return result.toString();
    }

    private static String firstSingleCharacter(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.text.codePointCount(0, entry.text.length()) == 1) {
                return entry.text;
            }
        }
        return null;
    }

    static List<String> splitSyllables(String pinyin, Set<String> knownSyllables) {
        String normalized = normalize(pinyin);
        if (normalized.isEmpty() || knownSyllables == null || knownSyllables.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<String>> paths = new ArrayList<>(normalized.length() + 1);
        for (int index = 0; index <= normalized.length(); index++) {
            paths.add(null);
        }
        paths.set(0, Collections.emptyList());
        for (int start = 0; start < normalized.length(); start++) {
            List<String> path = paths.get(start);
            if (path == null) {
                continue;
            }
            for (int end = start + 1; end <= normalized.length(); end++) {
                String syllable = normalized.substring(start, end);
                if (!knownSyllables.contains(syllable)) {
                    continue;
                }
                List<String> candidate = new ArrayList<>(path);
                candidate.add(syllable);
                List<String> existing = paths.get(end);
                if (existing == null || candidate.size() < existing.size()) {
                    paths.set(end, candidate);
                }
            }
        }
        List<String> result = paths.get(normalized.length());
        return result == null ? Collections.emptyList() : result;
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
        private final long weight;

        private Entry(String text, long weight) {
            this.text = text;
            this.weight = weight;
        }
    }

}
