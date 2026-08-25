package com.example.myapplication.catalog;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EmojiCatalogJsonCodecTest {
    private final EmojiCatalogJsonCodec codec = new EmojiCatalogJsonCodec();

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

    @Test
    public void duplicateItemIdIsRejected() throws Exception {
        String duplicateItems = item("same-item", "A", 0, "images/a.png") + ","
                + item("same-item", "B", 1, "images/b.png");

        assertInvalid("{\"version\":1,\"galleries\":["
                + gallery("gallery-a", "A", 0, pack("pack-a", "A", 0, duplicateItems))
                + "]}", "Duplicate item id");
    }

    @Test
    public void unknownVersionIsRejected() throws Exception {
        assertInvalid("{\"version\":3,\"galleries\":[]}", "Unsupported catalog version");
    }

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

    @Test
    public void pathTraversalIsRejected() throws Exception {
        assertInvalid("{\"version\":1,\"galleries\":["
                + gallery("gallery-a", "A", 0, pack("pack-a", "A", 0,
                item("item-a", "A", 0, "images/../outside.png")))
                + "]}", "path must remain inside");
    }

    @Test
    public void malformedJsonIsRejected() throws Exception {
        assertInvalid("{not-json", "Invalid emoji catalog");
    }

    private void assertInvalid(String json, String expectedMessage) throws Exception {
        try {
            codec.decode(json);
            fail("Expected invalid catalog");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

    private static String gallery(String id, String name, int order, String packs) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"sortOrder\":" + order + ",\"packs\":[" + packs + "]}";
    }

    private static String pack(String id, String name, int order, String items) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"sortOrder\":" + order + ",\"items\":[" + items + "]}";
    }

    private static String item(String id, String name, int order, String path) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"note\":\"\",\"mimeType\":\"image/png\","
                + "\"relativePath\":\"" + path + "\",\"sortOrder\":" + order + "}";
    }
}
