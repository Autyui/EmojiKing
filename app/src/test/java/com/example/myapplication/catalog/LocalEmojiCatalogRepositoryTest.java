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

// 类作用：定义 LocalEmojiCatalogRepositoryTest，承载所在模块的主要职责。
public class LocalEmojiCatalogRepositoryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

// 方法作用：处理 migrationCopiesLegacyImageAndIsIdempotent 对应的输入并返回或更新相关结果（migrationCopiesLegacyImageAndIsIdempotent）。
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

// 方法作用：替换已有图片或目录记录并保持索引一致（replacementPersistsAcrossRepositoryRestart）。
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

// 方法作用：取得集合中第一个可用元素的标识（firstImportReplacesPlaceholderAndKeepsSmallImageDisplayName）。
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

// 方法作用：判断输入是否与已有内容重复（duplicateContentDoesNotCreateAnotherRecordOrFile）。
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

// 方法作用：向界面或业务集合中添加新的元素（addedEmojiCanBeNotedAndDeletedWithoutAffectingOthers）。
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

// 方法作用：处理 nonDefaultPackSupportsCreateRenameImportAndDelete 对应的输入并返回或更新相关结果（nonDefaultPackSupportsCreateRenameImportAndDelete）。
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

// 方法作用：处理 batchCreatedPacksCanBeSharedAndUnlinkedAcrossGalleries 对应的输入并返回或更新相关结果（batchCreatedPacksCanBeSharedAndUnlinkedAcrossGalleries）。
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

// 方法作用：处理 batchUnlinkRemovesSelectedPackReferencesInOneCatalogWrite 对应的输入并返回或更新相关结果（batchUnlinkRemovesSelectedPackReferencesInOneCatalogWrite）。
    @Test
    public void batchUnlinkRemovesSelectedPackReferencesInOneCatalogWrite() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("batch-unlink-files");
        File legacy = createFile("batch-unlink-current.png", new byte[]{1});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");
        java.util.List<EmojiCatalog.Pack> created = repository.createPacks(
                java.util.Collections.singletonList(LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID),
                java.util.Arrays.asList("可移除一", "可移除二"));

        repository.unlinkPacksFromGallery(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                java.util.Arrays.asList(created.get(0).getId(), created.get(1).getId()));

        EmojiCatalog result = repository.loadCatalog();
        assertFalse(result.galleryContainsPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID, created.get(0).getId()));
        assertFalse(result.galleryContainsPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID, created.get(1).getId()));
        assertEquals(1, result.getPacksForGallery(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID).size());
    }

// 方法作用：处理 batchDeleteRemovesSelectedItemsOnlyFromTheSelectedPack 对应的输入并返回或更新相关结果（batchDeleteRemovesSelectedItemsOnlyFromTheSelectedPack）。
    @Test
    public void batchDeleteRemovesSelectedItemsOnlyFromTheSelectedPack() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("batch-delete-items-files");
        File legacy = createFile("batch-delete-current.png", new byte[]{1});
        File first = createFile("batch-delete-first.png", new byte[]{2});
        File second = createFile("batch-delete-second.png", new byte[]{3});
        File third = createFile("batch-delete-third.png", new byte[]{4});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");
        EmojiCatalog.Pack pack = repository.createPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID, "可批量删除");
        LocalEmojiCatalogRepository.ImportResult firstResult = repository.importEmoji(
                pack.getId(), first, first.getName(), "image/png");
        LocalEmojiCatalogRepository.ImportResult secondResult = repository.importEmoji(
                pack.getId(), second, second.getName(), "image/png");
        LocalEmojiCatalogRepository.ImportResult thirdResult = repository.importEmoji(
                pack.getId(), third, third.getName(), "image/png");

        repository.deleteItems(pack.getId(), java.util.Arrays.asList(
                firstResult.getEmoji().getItem().getId(),
                secondResult.getEmoji().getItem().getId()));

        EmojiCatalog.Pack updatedPack = repository.loadCatalog().getPack(pack.getId());
        assertEquals(1, updatedPack.getItems().size());
        assertEquals(thirdResult.getEmoji().getItem().getId(),
                updatedPack.getItems().get(0).getId());
        assertFalse(firstResult.getEmoji().getFile().exists());
        assertFalse(secondResult.getEmoji().getFile().exists());
        assertTrue(thirdResult.getEmoji().getFile().exists());
        assertTrue(first.isFile());
        assertTrue(second.isFile());
        assertTrue(third.isFile());
        assertEquals(2, countItems(repository.loadCatalog()));
    }

// 方法作用：处理 deletingPackDoesNotDeleteOtherPackCopyOrSourceFile 对应的输入并返回或更新相关结果（deletingPackDoesNotDeleteOtherPackCopyOrSourceFile）。
    @Test
    public void deletingPackDoesNotDeleteOtherPackCopyOrSourceFile() throws Exception {
        File filesDirectory = temporaryFolder.newFolder("delete-pack-isolation-files");
        File legacy = createFile("delete-pack-isolation-current.png", new byte[]{1});
        File firstSource = createFile("delete-pack-isolation-first.png", new byte[]{8, 8});
        File secondSource = createFile("delete-pack-isolation-second.png", new byte[]{8, 8});
        LocalEmojiCatalogRepository repository = new LocalEmojiCatalogRepository(filesDirectory);
        repository.loadDefaultEmoji(legacy, "image/png");
        EmojiCatalog.Pack firstPack = repository.createPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID, "类一");
        EmojiCatalog.Pack secondPack = repository.createPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID, "类二");
        LocalEmojiCatalogRepository.ImportResult first = repository.importEmoji(
                firstPack.getId(), firstSource, firstSource.getName(), "image/png");
        LocalEmojiCatalogRepository.ImportResult second = repository.importEmoji(
                secondPack.getId(), secondSource, secondSource.getName(), "image/png");

        repository.deletePack(firstPack.getId());

        assertFalse(first.getEmoji().getFile().exists());
        assertTrue(second.getEmoji().getFile().exists());
        assertTrue(firstSource.isFile());
        assertTrue(secondSource.isFile());
        assertTrue(repository.loadCatalog().getPack(secondPack.getId()) != null);
    }

// 方法作用：处理 sameContentCanBelongToDifferentIndependentPacks 对应的输入并返回或更新相关结果（sameContentCanBelongToDifferentIndependentPacks）。
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

// 方法作用：处理 deletingLastEmojiIsRejectedWithoutChangingCatalog 对应的输入并返回或更新相关结果（deletingLastEmojiIsRejectedWithoutChangingCatalog）。
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

// 方法作用：处理 missingManagedImageIsReported 对应的输入并返回或更新相关结果（missingManagedImageIsReported）。
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

// 方法作用：处理 catalogActivationFailureDoesNotLeaveManagedImage 对应的输入并返回或更新相关结果（catalogActivationFailureDoesNotLeaveManagedImage）。
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

// 方法作用：处理 unsupportedImageMimeIsRejectedWithoutCreatingCatalog 对应的输入并返回或更新相关结果（unsupportedImageMimeIsRejectedWithoutCreatingCatalog）。
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

// 方法作用：创建并返回新的业务对象或界面对象（createFile）。
    private File createFile(String name, byte[] contents) throws Exception {
        File file = temporaryFolder.newFile(name);
        Files.write(file.toPath(), contents);
        return file;
    }

// 方法作用：根据输入条件查询并返回匹配结果（findPack）。
    private static EmojiCatalog.Pack findPack(EmojiCatalog catalog, String packId) {
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            if (packId.equals(pack.getId())) {
                return pack;
            }
        }
        throw new AssertionError("Pack not found: " + packId);
    }

// 方法作用：统计当前集合中的元素数量（countItems）。
    private static int countItems(EmojiCatalog catalog) {
        int count = 0;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            count += pack.getItems().size();
        }
        return count;
    }

// 方法作用：统计当前集合中的元素数量（countFiles）。
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
