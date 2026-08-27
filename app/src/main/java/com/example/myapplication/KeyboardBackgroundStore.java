package com.example.myapplication;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Owns the private, size-bounded image used behind the text keyboard. */
// 类作用：定义 KeyboardBackgroundStore，承载所在模块的主要职责。
public final class KeyboardBackgroundStore {
    private static final long MAX_SOURCE_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static final int MAX_SAVED_DIMENSION = 1600;
    private static final String DIRECTORY = "keyboard-theme";
    private static final String FILE_NAME = "background.jpg";

// 方法作用：初始化 KeyboardBackgroundStore 对象并建立其运行所需状态。
    private KeyboardBackgroundStore() {
    }

// 方法作用：校验并持久化用户提供的数据（save）。
    public static synchronized void save(Context context, Uri source) throws IOException {
        if (source == null) {
            throw new IOException("No keyboard background was selected");
        }
        File directory = directory(context);
        File stagedSource = File.createTempFile("background-source-", ".image", directory);
        File pending = new File(directory, FILE_NAME + ".new");
        try {
            copySource(context.getContentResolver(), source, stagedSource);
            Bitmap prepared = decodePrepared(stagedSource);
            try {
                writePending(prepared, pending);
            } finally {
                prepared.recycle();
            }
            activate(directory, pending);
        } finally {
            stagedSource.delete();
            pending.delete();
        }
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（clear）。
    public static synchronized boolean clear(Context context) throws IOException {
        File directory = directory(context);
        File target = new File(directory, FILE_NAME);
        File pending = new File(directory, FILE_NAME + ".new");
        File backup = new File(directory, FILE_NAME + ".bak");
        deleteIfPresent(pending, "pending keyboard background");
        deleteIfPresent(backup, "keyboard background backup");
        if (!target.exists()) {
            return false;
        }
        if (!target.delete()) {
            throw new IOException("Cannot remove the keyboard background");
        }
        return true;
    }

// 方法作用：判断当前对象是否满足指定条件（hasBackground）。
    public static boolean hasBackground(Context context) {
        return backgroundFile(context).isFile();
    }

// 方法作用：处理 version 对应的输入并返回或更新相关结果（version）。
    public static long version(Context context) {
        File file = backgroundFile(context);
        return file.isFile() ? file.lastModified() * 31L + file.length() : 0L;
    }

// 方法作用：从文件、网络或内存加载数据（load）。
    public static Bitmap load(Context context) {
        File file = backgroundFile(context);
        if (!file.isFile()) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

// 方法作用：根据输入参数计算或比较结果（calculateSampleSize）。
    static int calculateSampleSize(int width, int height, int maximumDimension) {
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / (sample * 2) >= maximumDimension) {
            sample *= 2;
        }
        return sample;
    }

// 方法作用：在受控范围内复制输入数据（copySource）。
    private static void copySource(ContentResolver resolver, Uri source, File destination)
            throws IOException {
        long total = 0L;
        try (InputStream input = resolver.openInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) {
                throw new IOException("The selected image cannot be opened");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_SOURCE_BYTES) {
                    throw new IOException("Keyboard background exceeds 20 MiB");
                }
                output.write(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        }
        if (total == 0L) {
            throw new IOException("The selected image is empty");
        }
    }

// 方法作用：解码输入内容并生成可用对象（decodePrepared）。
    private static Bitmap decodePrepared(File source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        // 先读取尺寸而不分配像素内存，提前拒绝超大图片以控制内存峰值。
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("The selected file is not a readable image");
        }
        long pixels = (long) bounds.outWidth * (long) bounds.outHeight;
        if (pixels > MAX_SOURCE_PIXELS) {
            throw new IOException("Keyboard background has too many pixels");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(
                bounds.outWidth, bounds.outHeight, MAX_SAVED_DIMENSION);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (decoded == null) {
            throw new IOException("The selected image cannot be decoded");
        }

        int largest = Math.max(decoded.getWidth(), decoded.getHeight());
        if (largest <= MAX_SAVED_DIMENSION) {
            return decoded;
        }
        float scale = MAX_SAVED_DIMENSION / (float) largest;
        Bitmap scaled = Bitmap.createScaledBitmap(
                decoded,
                Math.max(1, Math.round(decoded.getWidth() * scale)),
                Math.max(1, Math.round(decoded.getHeight() * scale)),
                true);
        if (scaled != decoded) {
            decoded.recycle();
        }
        return scaled;
    }

// 方法作用：将对象转换后写入目标存储（writePending）。
    private static void writePending(Bitmap bitmap, File pending) throws IOException {
        deleteIfPresent(pending, "pending keyboard background");
        try (FileOutputStream output = new FileOutputStream(pending)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw new IOException("Cannot encode the keyboard background");
            }
            output.flush();
            output.getFD().sync();
        } catch (IOException exception) {
            pending.delete();
            throw exception;
        }
    }

// 方法作用：处理 activate 对应的输入并返回或更新相关结果（activate）。
    private static void activate(File directory, File pending) throws IOException {
        File target = new File(directory, FILE_NAME);
        File backup = new File(directory, FILE_NAME + ".bak");
        deleteIfPresent(backup, "keyboard background backup");
        boolean hadTarget = target.isFile();
        if (hadTarget && !target.renameTo(backup)) {
            throw new IOException("Cannot back up the current keyboard background");
        }
        if (!pending.renameTo(target)) {
            if (hadTarget && !backup.renameTo(target)) {
                throw new IOException("Cannot restore the previous keyboard background");
            }
            throw new IOException("Cannot activate the keyboard background");
        }
        backup.delete();
    }

// 方法作用：处理 backgroundFile 对应的输入并返回或更新相关结果（backgroundFile）。
    private static File backgroundFile(Context context) {
        return new File(directory(context), FILE_NAME);
    }

// 方法作用：处理 directory 对应的输入并返回或更新相关结果（directory）。
    private static File directory(Context context) {
        File directory = new File(context.getApplicationContext().getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create keyboard theme directory");
        }
        return directory;
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（deleteIfPresent）。
    private static void deleteIfPresent(File file, String description) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Cannot remove " + description);
        }
    }
}
