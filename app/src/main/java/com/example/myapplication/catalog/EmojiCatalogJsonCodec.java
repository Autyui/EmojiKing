package com.example.myapplication.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Structured JSON adapter with an in-memory migration from the nested v1 format. */

final class EmojiCatalogJsonCodec {
    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    EmojiCatalog decode(String json) throws IOException {
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "catalog");
            int version = requireInt(root, "version");
            if (version == 1) {
                return decodeVersionOne(root);
            }
            if (version != EmojiCatalog.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported catalog version: " + version);
            }
            return decodeVersionTwo(root);
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid emoji catalog: " + exception.getMessage(), exception);
        }
    }

    String encode(EmojiCatalog catalog) {
        JsonObject root = new JsonObject();
        root.addProperty("version", EmojiCatalog.CURRENT_VERSION);
        JsonArray galleriesJson = new JsonArray();
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            JsonObject galleryJson = new JsonObject();
            galleryJson.addProperty("id", gallery.getId());
            galleryJson.addProperty("name", gallery.getName());
            galleryJson.addProperty("sortOrder", gallery.getSortOrder());
            JsonArray packIdsJson = new JsonArray();
            for (String packId : gallery.getPackIds()) {
                packIdsJson.add(packId);
            }
            galleryJson.add("packIds", packIdsJson);
            galleriesJson.add(galleryJson);
        }
        root.add("galleries", galleriesJson);

        JsonArray packsJson = new JsonArray();
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            packsJson.add(encodePack(pack));
        }
        root.add("packs", packsJson);
        return gson.toJson(root) + "\n";
    }

// 方法作用：解码输入内容并生成可用对象（decodeVersionTwo）。
    private EmojiCatalog decodeVersionTwo(JsonObject root) throws IOException {
        JsonArray galleriesJson = requireArray(root, "galleries");
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>();
        for (int index = 0; index < galleriesJson.size(); index++) {
            JsonObject galleryJson = requireObject(
                    galleriesJson.get(index), "galleries[" + index + "]");
            JsonArray packIdsJson = requireArray(galleryJson, "packIds");
            List<String> packIds = new ArrayList<>();
            for (int packIndex = 0; packIndex < packIdsJson.size(); packIndex++) {
                JsonElement element = packIdsJson.get(packIndex);
                if (!element.isJsonPrimitive()
                        || !element.getAsJsonPrimitive().isString()) {
                    throw new IOException("packIds[" + packIndex + "] must be a string");
                }
                packIds.add(element.getAsString());
            }
            galleries.add(new EmojiCatalog.Gallery(
                    requireString(galleryJson, "id"),
                    requireString(galleryJson, "name"),
                    requireInt(galleryJson, "sortOrder"),
                    packIds));
        }

        JsonArray packsJson = requireArray(root, "packs");
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        for (int index = 0; index < packsJson.size(); index++) {
            packs.add(decodePack(requireObject(packsJson.get(index), "packs[" + index + "]")));
        }
        return new EmojiCatalog(EmojiCatalog.CURRENT_VERSION, galleries, packs);
    }

// 方法作用：解码输入内容并生成可用对象（decodeVersionOne）。
    private EmojiCatalog decodeVersionOne(JsonObject root) throws IOException {
        JsonArray galleriesJson = requireArray(root, "galleries");
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>();
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        for (int index = 0; index < galleriesJson.size(); index++) {
            JsonObject galleryJson = requireObject(
                    galleriesJson.get(index), "galleries[" + index + "]");
            JsonArray nestedPacks = requireArray(galleryJson, "packs");
            List<String> packIds = new ArrayList<>();
            for (int packIndex = 0; packIndex < nestedPacks.size(); packIndex++) {
                EmojiCatalog.Pack pack = decodePack(requireObject(
                        nestedPacks.get(packIndex),
                        "galleries[" + index + "].packs[" + packIndex + "]"));
                packs.add(pack);
                packIds.add(pack.getId());
            }
            galleries.add(new EmojiCatalog.Gallery(
                    requireString(galleryJson, "id"),
                    requireString(galleryJson, "name"),
                    requireInt(galleryJson, "sortOrder"),
                    packIds));
        }
        return new EmojiCatalog(EmojiCatalog.CURRENT_VERSION, galleries, packs);
    }

// 方法作用：将对象转换后写入目标存储（encodePack）。
    private JsonObject encodePack(EmojiCatalog.Pack pack) {
        JsonObject packJson = new JsonObject();
        packJson.addProperty("id", pack.getId());
        packJson.addProperty("name", pack.getName());
        packJson.addProperty("sortOrder", pack.getSortOrder());
        JsonArray itemsJson = new JsonArray();
        for (EmojiCatalog.Item item : pack.getItems()) {
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("id", item.getId());
            itemJson.addProperty("name", item.getName());
            itemJson.addProperty("note", item.getNote());
            itemJson.addProperty("mimeType", item.getMimeType());
            itemJson.addProperty("relativePath", item.getRelativePath());
            itemJson.addProperty("sortOrder", item.getSortOrder());
            itemsJson.add(itemJson);
        }
        packJson.add("items", itemsJson);
        return packJson;
    }

// 方法作用：解码输入内容并生成可用对象（decodePack）。
    private EmojiCatalog.Pack decodePack(JsonObject json) throws IOException {
        JsonArray itemsJson = requireArray(json, "items");
        List<EmojiCatalog.Item> items = new ArrayList<>();
        for (int index = 0; index < itemsJson.size(); index++) {
            items.add(decodeItem(requireObject(itemsJson.get(index), "items[" + index + "]")));
        }
        return new EmojiCatalog.Pack(
                requireString(json, "id"),
                requireString(json, "name"),
                requireInt(json, "sortOrder"),
                items);
    }

// 方法作用：解码输入内容并生成可用对象（decodeItem）。
    private EmojiCatalog.Item decodeItem(JsonObject json) throws IOException {
        return new EmojiCatalog.Item(
                requireString(json, "id"),
                requireString(json, "name"),
                optionalString(json, "note"),
                requireString(json, "mimeType"),
                requireString(json, "relativePath"),
                requireInt(json, "sortOrder"));
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireObject）。
    private static JsonObject requireObject(JsonElement element, String name) throws IOException {
        if (element == null || !element.isJsonObject()) {
            throw new IOException(name + " must be an object");
        }
        return element.getAsJsonObject();
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireArray）。
    private static JsonArray requireArray(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonArray()) {
            throw new IOException(name + " must be an array");
        }
        return element.getAsJsonArray();
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireString）。
    private static String requireString(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IOException(name + " must be a string");
        }
        return element.getAsString();
    }

// 方法作用：读取 JSON 中可选的字符串字段并处理缺省值（optionalString）。
    private static String optionalString(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IOException(name + " must be a string");
        }
        return element.getAsString();
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireInt）。
    private static int requireInt(JsonObject object, String name) throws IOException {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IOException(name + " must be an integer");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        String value = primitive.getAsString();
        if (!primitive.isNumber() || !value.matches("-?[0-9]+")) {
            throw new IOException(name + " must be an integer");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException(name + " is outside the integer range", exception);
        }
    }
}
