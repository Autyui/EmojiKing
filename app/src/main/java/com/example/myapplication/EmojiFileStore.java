package com.example.myapplication;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Stores the one image used by the compatibility prototype. */
public final class EmojiFileStore {
    private static final String DIRECTORY = "emoji";
    private static final String FILE_PREFIX = "current.";

    private EmojiFileStore() {
    }

    public static synchronized File getCurrentFile(Context context) {
        File directory = getDirectory(context);
        File[] files = directory.listFiles((dir, name) -> name.startsWith(FILE_PREFIX));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return createSample(directory);
    }

    public static synchronized Uri getCurrentUri(Context context) {
        File file = getCurrentFile(context);
        return androidx.core.content.FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file);
    }

    public static synchronized File importImage(Context context, Uri source) throws IOException {
        File directory = getDirectory(context);
        String extension = findExtension(context.getContentResolver(), source);
        File temporary = File.createTempFile("import-", "." + extension, directory);
        try (InputStream input = context.getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(temporary)) {
            if (input == null) {
                throw new IOException("Unable to open the selected image");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            temporary.delete();
            throw exception;
        }

        deleteCurrentFiles(directory);
        File destination = new File(directory, FILE_PREFIX + extension);
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("Unable to activate the imported image");
        }
        return destination;
    }

    public static String getMimeType(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }

    private static File getDirectory(Context context) {
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create image directory");
        }
        return directory;
    }

    private static void deleteCurrentFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith(FILE_PREFIX));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                throw new IllegalStateException("Cannot replace current image");
            }
        }
    }

    private static String findExtension(ContentResolver resolver, Uri source) {
        String displayName = null;
        try (android.database.Cursor cursor = resolver.query(
                source,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(0);
            }
        } catch (Exception ignored) {
            // Some providers do not expose metadata; MIME type is used below.
        }

        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot < displayName.length() - 1) {
                String extension = displayName.substring(dot + 1).toLowerCase(Locale.US);
                if (extension.matches("[a-z0-9]{1,8}")) {
                    return extension;
                }
            }
        }

        String mime = resolver.getType(source);
        if ("image/jpeg".equals(mime)) {
            return "jpg";
        }
        if ("image/webp".equals(mime)) {
            return "webp";
        }
        if ("image/gif".equals(mime)) {
            return "gif";
        }
        return "png";
    }

    private static File createSample(File directory) {
        File destination = new File(directory, FILE_PREFIX + "png");
        Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(255, 244, 196));

        Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
        face.setColor(Color.rgb(255, 205, 70));
        canvas.drawCircle(160, 160, 108, face);

        face.setColor(Color.rgb(35, 35, 35));
        canvas.drawCircle(122, 140, 13, face);
        canvas.drawCircle(198, 140, 13, face);
        face.setStyle(Paint.Style.STROKE);
        face.setStrokeWidth(12);
        canvas.drawArc(105, 112, 215, 225, 25, 130, false, face);

        try (FileOutputStream output = new FileOutputStream(destination)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create sample image", exception);
        } finally {
            bitmap.recycle();
        }
        return destination;
    }
}
