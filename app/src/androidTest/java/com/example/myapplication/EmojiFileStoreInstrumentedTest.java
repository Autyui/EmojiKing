package com.example.myapplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.myapplication.catalog.EmojiCatalog;
import com.example.myapplication.catalog.LocalEmojiCatalogRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class EmojiFileStoreInstrumentedTest {
    @Test
    public void smallJpegImportsAndPartialFailureKeepsSuccessfulImage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        EmojiFileStore.getCurrentEmoji(context);
        EmojiCatalog.Pack pack = EmojiFileStore.createPack(
                context,
                "small-image-test-" + System.nanoTime());
        EmojiCatalog.Gallery secondGallery = EmojiFileStore.createGallery(
                context,
                "shared-gallery-test-" + System.nanoTime());
        File valid = createJpeg(context, "small-338x267.jpg", 338, 267);
        File corrupt = createCorruptImage(context, "corrupt.jpg");

        try {
            Uri validUri = uriFor(context, valid);
            Uri corruptUri = uriFor(context, corrupt);
            EmojiFileStore.BatchImportResult result = EmojiFileStore.importImages(
                    context,
                    Arrays.asList(validUri, corruptUri),
                    pack.getId());

            assertEquals(1, result.getImportedCount());
            assertEquals(0, result.getDuplicateCount());
            assertEquals(1, result.getFailures().size());
            assertEquals("small-338x267.jpg", result.getLastImported().getItem().getName());
            assertEquals("image/jpeg", result.getLastImported().getItem().getMimeType());
            assertTrue(result.getLastImported().getFile().isFile());

            EmojiFileStore.linkPacksToGallery(
                    context,
                    secondGallery.getId(),
                    Arrays.asList(pack.getId()));
            EmojiSelectionStore.save(context, secondGallery.getId(), pack.getId());
            EmojiSelectionStore.Selection selection = EmojiSelectionStore.resolve(
                    context,
                    EmojiFileStore.getCatalog(context));
            assertEquals(secondGallery.getId(), selection.getGallery().getId());
            assertEquals(pack.getId(), selection.getPack().getId());
            assertEquals(1, selection.getPack().getItems().size());
        } finally {
            EmojiFileStore.deletePack(context, pack.getId());
            EmojiFileStore.deleteGallery(context, secondGallery.getId());
            EmojiSelectionStore.save(
                    context,
                    LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                    LocalEmojiCatalogRepository.DEFAULT_PACK_ID);
            valid.delete();
            corrupt.delete();
        }
    }

    @Test
    public void invalidDirectoryAuthorizationIsReported() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            EmojiFileStore.listImageDocuments(context, Uri.parse("content://invalid/not-a-tree"));
            fail("Expected invalid tree authorization failure");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("invalid"));
        }
    }

    private static File createJpeg(Context context, String name, int width, int height)
            throws Exception {
        File directory = cacheEmojiDirectory(context);
        File file = new File(directory, name);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(31, 145, 211));
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output));
        } finally {
            bitmap.recycle();
        }
        return file;
    }

    private static File createCorruptImage(Context context, String name) throws Exception {
        File file = new File(cacheEmojiDirectory(context), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[]{1, 2, 3, 4});
        }
        return file;
    }

    private static File cacheEmojiDirectory(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), "emoji");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Cannot create test cache directory");
        }
        return directory;
    }

    private static Uri uriFor(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file);
    }
}
