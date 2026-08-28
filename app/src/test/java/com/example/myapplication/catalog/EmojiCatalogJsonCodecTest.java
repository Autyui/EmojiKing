package com.example.myapplication.catalog;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// 类作用：定义 EmojiCatalogJsonCodecTest，承载所在模块的主要职责。
public class EmojiCatalogJsonCodecTest {
    private final EmojiCatalogJsonCodec codec = new EmojiCatalogJsonCodec();

// 方法作用：解码输入内容并生成可用对象（decodeSortsGalleriesPacksAndItems）。
    @Test
    public void decodeSortsGalleriesPacksAndItems() throws Exception {
        String sortedItems = item("item-z", "Z", 9, "images/z.png") + ","
                + item("item-a", "A", 2, "images/a.png");
        EmojiCatalog catalog = codec.decode("{"
                + "\"version\":1,"
                + "\"galleries\":["
                + gallery("gallery-b", "B", 20, pack("pack-b", "B", 5,
                item("item-b", "B", 8, "images/b.png"))) + ","
                + gallery("gallery-a", "A", 10, pack("pack-a", "A", 1, sortedItems))
                + "]}");

        assertEquals("gallery-a", catalog.getGalleries().get(0).getId());
        assertEquals("pack-a", catalog.getPacksForGallery("gallery-a").get(0).getId());
        assertEquals(
                "item-a",
                catalog.getPacksForGallery("gallery-a").get(0).getItems().get(0).getId());
        assertTrue(codec.encode(catalog).contains("\"version\": 2"));
        assertTrue(codec.encode(catalog).contains("\"packIds\""));
    }

// 方法作用：判断输入是否与已有内容重复（duplicateItemIdIsRejected）。
    @Test
    public void duplicateItemIdIsRejected() throws Exception {
        String duplicateItems = item("same-item", "A", 0, "images/a.png") + ","
                + item("same-item", "B", 1, "images/b.png");

        assertInvalid("{\"version\":1,\"galleries\":["
                + gallery("gallery-a", "A", 0, pack("pack-a", "A", 0, duplicateItems))
                + "]}", "Duplicate item id");
    }

// 方法作用：处理 unknownVersionIsRejected 对应的输入并返回或更新相关结果（unknownVersionIsRejected）。
    @Test
    public void unknownVersionIsRejected() throws Exception {
        assertInvalid("{\"version\":3,\"galleries\":[]}", "Unsupported catalog version");
    }

// 方法作用：处理 versionTwoAllowsOnePackInMultipleGalleries 对应的输入并返回或更新相关结果（versionTwoAllowsOnePackInMultipleGalleries）。
    @Test
    public void versionTwoAllowsOnePackInMultipleGalleries() throws Exception {
        String json = "{\"version\":2,\"galleries\":["
                + "{\"id\":\"gallery-a\",\"name\":\"A\",\"sortOrder\":0,"
                + "\"packIds\":[\"pack-shared\"]},"
                + "{\"id\":\"gallery-b\",\"name\":\"B\",\"sortOrder\":1,"
                + "\"packIds\":[\"pack-shared\"]}],"
                + "\"packs\":[" + pack("pack-shared", "Shared", 0,
                item("item-shared", "Shared", 0, "images/shared.png")) + "]}";

        EmojiCatalog catalog = codec.decode(json);

        assertEquals(1, catalog.getPacks().size());
        assertEquals("pack-shared", catalog.getPacksForGallery("gallery-a").get(0).getId());
        assertEquals("pack-shared", catalog.getPacksForGallery("gallery-b").get(0).getId());
    }

// 方法作用：处理 pathTraversalIsRejected 对应的输入并返回或更新相关结果（pathTraversalIsRejected）。
    @Test
    public void pathTraversalIsRejected() throws Exception {
        assertInvalid("{\"version\":1,\"galleries\":["
                + gallery("gallery-a", "A", 0, pack("pack-a", "A", 0,
                item("item-a", "A", 0, "images/../outside.png")))
                + "]}", "path must remain inside");
    }

// 方法作用：处理 malformedJsonIsRejected 对应的输入并返回或更新相关结果（malformedJsonIsRejected）。
    @Test
    public void malformedJsonIsRejected() throws Exception {
        assertInvalid("{not-json", "Invalid emoji catalog");
    }

// 方法作用：处理 assertInvalid 对应的输入并返回或更新相关结果（assertInvalid）。
    private void assertInvalid(String json, String expectedMessage) throws Exception {
        try {
            codec.decode(json);
            fail("Expected invalid catalog");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

// 方法作用：处理 gallery 对应的输入并返回或更新相关结果（gallery）。
    private static String gallery(String id, String name, int order, String packs) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"sortOrder\":" + order + ",\"packs\":[" + packs + "]}";
    }

// 方法作用：处理 pack 对应的输入并返回或更新相关结果（pack）。
    private static String pack(String id, String name, int order, String items) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"sortOrder\":" + order + ",\"items\":[" + items + "]}";
    }

// 方法作用：处理 item 对应的输入并返回或更新相关结果（item）。
    private static String item(String id, String name, int order, String path) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"note\":\"\",\"mimeType\":\"image/png\","
                + "\"relativePath\":\"" + path + "\",\"sortOrder\":" + order + "}";
    }
}
