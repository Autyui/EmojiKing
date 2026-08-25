package com.example.myapplication.catalog;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalEmojiCatalogRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void migrationCopiesLegacyImageAndIsIdempotent() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("files");
        File legacy = temporaryFolder.newFile("current.png");
        Files.write(legacy.toPath(), new byte[]{1, 2, 3});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);

        LocalEmojiCatalogRepository.StoredEmoji first = repository.loadDefaultEmoji(
                legacy,
                "image/png");
        Files.write(legacy.toPath(), new byte[]{9, 9, 9});
        LocalEmojiCatalogRepository.StoredEmoji second = new LocalEmojiCatalogRepository(
                filesDirectory).loadDefaultEmoji(legacy, "image/png");

        assertTrue(legacy.isFile());
        assertNotEquals(legacy.getCanonicalFile(), first.getFile().getCanonicalFile());
        assertEquals(first.getFile().getCanonicalFile(), second.getFile().getCanonicalFile());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(second.getFile().toPath()));
        assertEquals(LocalEmojiCatalogRepository.DEFAULT_ITEM_ID, second.getItem().getId());
        assertEquals(1, countFiles(new File(filesDirectory, "emoji-library/images")));
    }

    @Test
    public void replacementPersistsAcrossRepositoryRestart() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("replace-files");
        File legacy = createFile("replace-current.png", new byte[]{1});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        File previous = repository.loadDefaultEmoji(legacy, "image/png").getFile();
        File replacement = createFile("replacement.gif", new byte[]{4, 5, 6});

        repository.replaceDefaultEmoji(replacement, "replacement.gif", "image/gif");
        LocalEmojiCatalogRepository.StoredEmoji reloaded = new LocalEmojiCatalogRepository(
                filesDirectory).loadDefaultEmoji(legacy, "image/png");

        assertEquals("image/gif", reloaded.getItem().getMimeType());
        assertEquals("replacement.gif", reloaded.getItem().getName());
        assertArrayEquals(new byte[]{4, 5, 6}, Files.readAllBytes(reloaded.getFile().toPath()));
        assertFalse(previous.exists());
        assertTrue(replacement.exists());
    }

    @Test
    public void firstImportReplacesPlaceholderAndKeepsSmallImageDisplayName() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("small-import-files");
        File legacy = createFile("small-current.png", new byte[]{1});
        File smallJpeg = createFile("338x267.jpg", new byte[]{2, 3, 4});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        File placeholder = repository.loadDefaultEmoji(legacy, "image/png").getFile();

        LocalEmojiCatalogRepository.ImportResult result = repository.importEmoji(
                LocalEmojiCatalogRepository.DEFAULT_PACK_ID,
                smallJpeg,
                "表情-338x267.jpg",
                "image/jpeg");
        LocalEmojiCatalogRepository.StoredEmoji reloaded = new LocalEmojiCatalogRepository(
                filesDirectory).loadDefaultEmoji(legacy, "image/png");

        assertFalse(result.isDuplicate());
        assertEquals("表情-338x267.jpg", reloaded.getItem().getName());
        assertEquals("image/jpeg", reloaded.getItem().getMimeType());
        assertFalse(placeholder.exists());
        assertTrue(smallJpeg.exists());
        assertEquals(1, countItems(repository.loadCatalog()));
    }

    @Test
    public void duplicateContentDoesNotCreateAnotherRecordOrFile() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("duplicate-files");
        File legacy = createFile("duplicate-current.png", new byte[]{1});
        File first = createFile("first.png", new byte[]{8, 7, 6});
        File sameContents = createFile("renamed.png", new byte[]{8, 7, 6});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");

        LocalEmojiCatalogRepository.ImportResult imported = repository.importEmoji(
                LocalEmojiCatalogRepository.DEFAULT_PACK_ID,
                first,
                first.getName(),
                "image/png");
        LocalEmojiCatalogRepository.ImportResult duplicate = repository.importEmoji(
                LocalEmojiCatalogRepository.DEFAULT_PACK_ID,
                sameContents,
                sameContents.getName(),
                "image/png");

        assertFalse(imported.isDuplicate());
        assertTrue(duplicate.isDuplicate());
        assertEquals(imported.getEmoji().getItem().getId(), duplicate.getEmoji().getItem().getId());
        assertEquals(1, countItems(repository.loadCatalog()));
        assertEquals(1, countFiles(new File(filesDirectory, "emoji-library/images")));
    }

    @Test
    public void addedEmojiCanBeNotedAndDeletedWithoutAffectingOthers() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("delete-item-files");
        File legacy = createFile("delete-current.png", new byte[]{1});
        File first = createFile("delete-first.png", new byte[]{2});
        File second = createFile("delete-second.gif", new byte[]{3});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");
        repository.importEmoji(
                LocalEmojiCatalogRepository.DEFAULT_PACK_ID,
                first,
                first.getName(),
                "image/png");
        LocalEmojiCatalogRepository.ImportResult secondResult = repository.importEmoji(
                LocalEmojiCatalogRepository.DEFAULT_PACK_ID,
                second,
                second.getName(),
                "image/gif");
        String secondId = secondResult.getEmoji().getItem().getId();
        File managedSecond = secondResult.getEmoji().getFile();

        repository.updateItemNote(secondId, "  备用表情  ");
        assertEquals("备用表情", repository.getStoredEmoji(secondId).getItem().getNote());
        repository.deleteItem(secondId);

        assertFalse(managedSecond.exists());
        assertEquals(1, countItems(repository.loadCatalog()));
        assertEquals("delete-first.png", repository.loadDefaultEmoji(
                legacy, "image/png").getItem().getName());
    }

    @Test
    public void nonDefaultPackSupportsCreateRenameImportAndDelete() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("pack-files");
        File legacy = createFile("pack-current.png", new byte[]{1});
        File source = createFile("pack-image.webp", new byte[]{4});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");

        EmojiCatalog.Pack pack = repository.createPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                "收藏");
        repository.renamePack(pack.getId(), "常用收藏");
        LocalEmojiCatalogRepository.ImportResult imported = repository.importEmoji(
                pack.getId(), source, source.getName(), "image/webp");
        File managed = imported.getEmoji().getFile();

        assertEquals("常用收藏", findPack(repository.loadCatalog(), pack.getId()).getName());
        repository.deletePack(pack.getId());

        assertFalse(managed.exists());
        assertEquals(1, repository.loadCatalog().getPacks().size());
        assertEquals(1, countItems(repository.loadCatalog()));
    }

    @Test
    public void batchCreatedPacksCanBeSharedAndUnlinkedAcrossGalleries() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("shared-pack-files");
        File legacy = createFile("shared-current.png", new byte[]{1});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");
        EmojiCatalog.Gallery second = repository.createGallery("图库二");

        java.util.List<EmojiCatalog.Pack> created = repository.createPacks(
                java.util.Arrays.asList(
                        LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                        second.getId()),
                java.util.Arrays.asList("表情包一", "表情包二"));
        EmojiCatalog shared = repository.loadCatalog();

        assertEquals(2, created.size());
        assertEquals(3, shared.getPacksForGallery(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID).size());
        assertEquals(2, shared.getPacksForGallery(second.getId()).size());
        assertEquals(created.get(0).getId(),
                shared.getPacksForGallery(second.getId()).get(0).getId());

        repository.unlinkPackFromGallery(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                created.get(0).getId());
        EmojiCatalog unlinked = repository.loadCatalog();
        assertFalse(unlinked.galleryContainsPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                created.get(0).getId()));
        assertTrue(unlinked.galleryContainsPack(second.getId(), created.get(0).getId()));
        assertEquals(3, unlinked.getPacks().size());
    }

    @Test
    public void sameContentCanBelongToDifferentIndependentPacks() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("pack-scoped-duplicate-files");
        File legacy = createFile("scoped-current.png", new byte[]{1});
        File source = createFile("same.png", new byte[]{9, 8, 7});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");
        repository.importEmoji(
                LocalEmojiCatalogRepository.DEFAULT_PACK_ID,
                source,
                source.getName(),
                "image/png");
        EmojiCatalog.Pack second = repository.createPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                "第二个包");

        LocalEmojiCatalogRepository.ImportResult result = repository.importEmoji(
                second.getId(), source, source.getName(), "image/png");

        assertFalse(result.isDuplicate());
        assertEquals(2, countItems(repository.loadCatalog()));
        assertEquals(2, countFiles(new File(filesDirectory, "emoji-library/images")));
    }

    @Test
    public void deletingLastEmojiIsRejectedWithoutChangingCatalog() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("last-item-files");
        File legacy = createFile("last-current.png", new byte[]{1});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        LocalEmojiCatalogRepository.StoredEmoji only = repository.loadDefaultEmoji(
                legacy, "image/png");

        try {
            repository.deleteItem(only.getItem().getId());
            fail("Expected last item protection");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("At least one"));
        }

        assertTrue(only.getFile().isFile());
        assertEquals(1, countItems(repository.loadCatalog()));
    }

    @Test
    public void missingManagedImageIsReported() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("missing-files");
        File legacy = createFile("missing-current.png", new byte[]{1});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        LocalEmojiCatalogRepository.StoredEmoji stored = repository.loadDefaultEmoji(
                legacy,
                "image/png");
        assertTrue(stored.getFile().delete());

        try {
            repository.loadDefaultEmoji(legacy, "image/png");
            fail("Expected missing managed image failure");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("missing"));
        }
    }

    @Test
    public void catalogActivationFailureDoesNotLeaveManagedImage() throws Exception {
        File libraryDirectory = temporaryFolder.newFolder("failed-library");
        File target = new File(libraryDirectory, "catalog.json");
        AtomicTextFile failingFile = new AtomicTextFile(target, (source, destination) -> false);
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(
                libraryDirectory,
                new EmojiCatalogJsonCodec(),
                failingFile);
        File legacy = createFile("failed-current.png", new byte[]{7});

        try {
            repository.loadDefaultEmoji(legacy, "image/png");
            fail("Expected catalog activation failure");
        } catch (IOException expected) {
            assertEquals(0, countFiles(new File(libraryDirectory, "images")));
        }
    }

    @Test
    public void unsupportedImageMimeIsRejectedWithoutCreatingCatalog() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("unsupported-files");
        File source = createFile("unsupported.bmp", new byte[]{7});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);

        try {
            repository.loadDefaultEmoji(source, "image/bmp");
            fail("Expected unsupported MIME failure");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("Unsupported"));
            assertFalse(new File(filesDirectory, "emoji-library/catalog.json").exists());
        }
    }

    private File createFile(String name, byte[] contents) throws Exception {
        File file = temporaryFolder.newFile(name);
        Files.write(file.toPath(), contents);
        return file;
    }

    private static EmojiCatalog.Pack findPack(EmojiCatalog catalog, String packId) {
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            if (packId.equals(pack.getId())) {
                return pack;
            }
        }
        throw new AssertionError("Pack not found: " + packId);
    }

    private static int countItems(EmojiCatalog catalog) {
        int count = 0;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            count += pack.getItems().size();
        }
        return count;
    }

    private static int countFiles(File directory) {
        File[] children = directory.listFiles();
        if (children == null) {
            return 0;
        }
        int count = 0;
        for (File child : children) {
            count += child.isDirectory() ? countFiles(child) : 1;
        }
        return count;
    }
}
