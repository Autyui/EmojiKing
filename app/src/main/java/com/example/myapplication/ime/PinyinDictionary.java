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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final int QUERY_CACHE_SIZE = 96;
    private static volatile PinyinDictionary cachedDictionary;

    private final NavigableMap<String, List<Entry>> entriesByPinyin;
    private final Set<String> syllables;
    private final SharedPreferences usagePreferences;
    private final Map<String, Integer> usageCounts;
    private final Map<String, List<String>> queryCache =
            new LinkedHashMap<String, List<String>>(QUERY_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                    return size() > QUERY_CACHE_SIZE;
                }
            };

    PinyinDictionary(
            Map<String, List<Entry>> entriesByPinyin,
            Set<String> syllables,
            SharedPreferences usagePreferences,
            Map<String, Integer> usageCounts) {
        this.entriesByPinyin = new TreeMap<>(entriesByPinyin);
        this.syllables = new HashSet<>(syllables);
        this.usagePreferences = usagePreferences;
        this.usageCounts = new HashMap<>(usageCounts);
    }

    static PinyinDictionary load(Context context) {
        PinyinDictionary cached = cachedDictionary;
        if (cached != null) {
            return cached;
        }
        synchronized (PinyinDictionary.class) {
            if (cachedDictionary == null) {
                cachedDictionary = loadUncached(context.getApplicationContext());
            }
            return cachedDictionary;
        }
    }

    private static PinyinDictionary loadUncached(Context context) {
        Map<String, List<Entry>> index = new TreeMap<>();
        Set<String> syllableIndex = new HashSet<>();
        Set<String> commonCharacters = loadCommonCharacters(context);
        SharedPreferences preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
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
                    preferences, loadUsageCounts(preferences));
        }
        for (List<Entry> values : index.values()) {
            Collections.sort(values, PinyinDictionary::compareStaticEntries);
        }
        return new PinyinDictionary(
                index,
                syllableIndex,
                preferences,
                loadUsageCounts(preferences));
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
        List<Entry> entries = index.get(key);
        if (entries == null) {
            entries = new ArrayList<>();
            index.put(key, entries);
        }
        entries.add(new Entry(text, frequency));
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

    synchronized List<String> query(String pinyin, int limit) {
        String normalized = normalize(pinyin);
        if (normalized.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        String cacheKey = normalized + ':' + limit;
        List<String> cached = queryCache.get(cacheKey);
        if (cached != null) {
            return cached;
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
        List<String> result = new ArrayList<>(candidates);
        if (result.size() > limit) {
            result = new ArrayList<>(result.subList(0, limit));
        }
        result = Collections.unmodifiableList(result);
        queryCache.put(cacheKey, result);
        return result;
    }

    boolean isEmpty() {
        return entriesByPinyin.isEmpty();
    }

    synchronized void recordSelection(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        String key = USAGE_PREFIX + candidate;
        int count = usageCounts.containsKey(candidate) ? usageCounts.get(candidate) : 0;
        int updated = Math.min(count + 1, 100_000);
        usageCounts.put(candidate, updated);
        queryCache.clear();
        if (usagePreferences != null) {
            usagePreferences.edit().putInt(key, updated).apply();
        }
    }

    private void appendPrefixEntries(Set<String> candidates, String prefix, int limit) {
        List<Entry> best = new ArrayList<>(limit);
        for (Map.Entry<String, List<Entry>> entry
                : entriesByPinyin.tailMap(prefix, true).entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                break;
            }
            collectBest(best, candidates, entry.getValue(), limit, true);
        }
        appendRankedTexts(candidates, best, limit);
    }

    private void appendEntries(Set<String> candidates, List<Entry> entries, int limit) {
        if (entries == null) {
            return;
        }
        List<Entry> best = new ArrayList<>(limit);
        collectBest(best, candidates, entries, limit, false);
        appendRankedTexts(candidates, best, limit);
    }

    private void appendRankedTexts(Set<String> candidates, List<Entry> entries, int limit) {
        for (Entry entry : entries) {
            candidates.add(entry.text);
            if (candidates.size() >= limit) {
                return;
            }
        }
    }

    private void collectBest(
            List<Entry> best,
            Set<String> existingCandidates,
            List<Entry> entries,
            int limit,
            boolean preferShorter) {
        for (Entry entry : entries) {
            if (!existingCandidates.contains(entry.text)) {
                insertBest(best, entry, limit, preferShorter);
            }
        }
    }

    private void insertBest(List<Entry> best, Entry candidate, int limit, boolean preferShorter) {
        for (int index = 0; index < best.size(); index++) {
            Entry existing = best.get(index);
            if (existing.text.equals(candidate.text)) {
                if (compareEntries(candidate, existing, preferShorter) >= 0) {
                    return;
                }
                best.remove(index);
                break;
            }
        }
        int insertion = 0;
        while (insertion < best.size()
                && compareEntries(candidate, best.get(insertion), preferShorter) >= 0) {
            insertion++;
        }
        if (insertion < limit) {
            best.add(insertion, candidate);
            if (best.size() > limit) {
                best.remove(best.size() - 1);
            }
        }
    }

    private int compareEntries(Entry left, Entry right, boolean preferShorter) {
        int scoreOrder = Long.compare(score(right), score(left));
        if (scoreOrder != 0) {
            return scoreOrder;
        }
        if (preferShorter) {
            int lengthOrder = Integer.compare(left.text.length(), right.text.length());
            if (lengthOrder != 0) {
                return lengthOrder;
            }
        }
        return left.text.compareTo(right.text);
    }

    private static int compareStaticEntries(Entry left, Entry right) {
        int weightOrder = Long.compare(right.weight, left.weight);
        return weightOrder != 0 ? weightOrder : left.text.compareTo(right.text);
    }

    private long score(Entry entry) {
        return entry.weight
                + (long) usageCount(entry.text) * 10_000_000L;
    }

    private int usageCount(String candidate) {
        Integer count = usageCounts.get(candidate);
        return count == null ? 0 : count;
    }

    private static Map<String, Integer> loadUsageCounts(SharedPreferences preferences) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(USAGE_PREFIX) && entry.getValue() instanceof Integer) {
                counts.put(
                        entry.getKey().substring(USAGE_PREFIX.length()),
                        (Integer) entry.getValue());
            }
        }
        return counts;
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

    static final class Entry {
        private final String text;
        private final long weight;

        Entry(String text, long weight) {
            this.text = text;
            this.weight = weight;
        }
    }

}
