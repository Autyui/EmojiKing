package com.example.myapplication;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import com.example.myapplication.catalog.EmojiCatalog;
import com.example.myapplication.catalog.LocalEmojiCatalogRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 把 Android 文档 URI 转换为应用私有表情目录中的文件。 */
// 类作用：定义 EmojiFileStore，承载所在模块的主要职责。
public final class EmojiFileStore {
    public static final long MAX_IMAGE_BYTES = 20L * 1024L * 1024L;
    public static final long MAX_IMAGE_PIXELS = 40_000_000L;

    private static final int MAX_TREE_DOCUMENTS = 2_000;
    private static final String DIRECTORY = "emoji";
    private static final String FILE_PREFIX = "current.";
    private static LocalEmojiCatalogRepository sharedRepository;
    private static String sharedRepositoryRoot;

// 方法作用：初始化 EmojiFileStore 对象并建立其运行所需状态。
    private EmojiFileStore() {
    }

// 方法作用：读取并返回持久化或运行时状态（getCurrentFile）。
    public static synchronized File getCurrentFile(Context context) {
        return getCurrentEmoji(context).getFile();
    }

// 方法作用：读取并返回持久化或运行时状态（getCurrentEmoji）。
    public static synchronized LocalEmojiCatalogRepository.StoredEmoji getCurrentEmoji(
            Context context) {
        File legacyFile = getLegacyCurrentFile(context);
        try {
            return repository(context).loadDefaultEmoji(
                    legacyFile,
                    getMimeType(legacyFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load the emoji catalog", exception);
        }
    }

// 方法作用：读取并返回持久化或运行时状态（getCatalog）。
    public static synchronized EmojiCatalog getCatalog(Context context) throws IOException {
        ensureCatalog(context);
        return repository(context).loadCatalog();
    }

// 方法作用：读取并返回持久化或运行时状态（getStoredEmoji）。
    public static synchronized LocalEmojiCatalogRepository.StoredEmoji getStoredEmoji(
            Context context,
            String itemId) throws IOException {
        ensureCatalog(context);
        return repository(context).getStoredEmoji(itemId);
    }

// 方法作用：读取并返回持久化或运行时状态（getManagedFile）。
    public static File getManagedFile(Context context, EmojiCatalog.Item item) throws IOException {
        return repository(context).resolveImageFile(item);
    }

// 方法作用：读取并返回持久化或运行时状态（getLegacyCurrentFile）。
    private static File getLegacyCurrentFile(Context context) {
        File directory = getDirectory(context);
        File[] files = directory.listFiles((dir, name) -> name.startsWith(FILE_PREFIX));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return createSample(directory);
    }

// 方法作用：读取并返回持久化或运行时状态（getCurrentUri）。
    public static synchronized Uri getCurrentUri(Context context) {
        return getUri(context, getCurrentEmoji(context));
    }

// 方法作用：读取并返回持久化或运行时状态（getCurrentUri）。
    public static Uri getCurrentUri(
            Context context,
            LocalEmojiCatalogRepository.StoredEmoji emoji) {
        return getUri(context, emoji);
    }

// 方法作用：读取并返回持久化或运行时状态（getUri）。
    public static Uri getUri(
            Context context,
            LocalEmojiCatalogRepository.StoredEmoji emoji) {
        return androidx.core.content.FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                emoji.getFile());
    }

// 方法作用：读取并返回持久化或运行时状态（getUri）。
    public static Uri getUri(Context context, EmojiCatalog.Item item) throws IOException {
        return androidx.core.content.FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                getManagedFile(context, item));
    }

    /** 保留单图导入入口，兼容既有调用流程。 */
// 方法作用：校验并导入外部数据（importImage）。
    public static synchronized File importImage(Context context, Uri source) throws IOException {
        List<Uri> sources = new ArrayList<>();
        sources.add(source);
        BatchImportResult result = importImages(
                context,
                sources,
                LocalEmojiCatalogRepository.DEFAULT_PACK_ID);
        if (result.getImportedCount() == 0) {
            String reason = result.getFailures().isEmpty()
                    ? "The selected image is already in the catalog"
                    : result.getFailures().get(0).getReason();
            throw new IOException(reason);
        }
        return result.getLastImported().getFile();
    }

// 方法作用：校验并导入外部数据（importImages）。
    public static synchronized BatchImportResult importImages(
            Context context,
            List<Uri> sources,
            String packId) throws IOException {
        if (sources == null || sources.isEmpty()) {
            throw new IOException("No image documents were selected");
        }
        ensureCatalog(context);
        LocalEmojiCatalogRepository repository = repository(context);
        int imported = 0;
        int duplicate = 0;
        LocalEmojiCatalogRepository.StoredEmoji lastImported = null;
        List<ImportFailure> failures = new ArrayList<>();
        // 每张图片独立处理；单张失败只记录原因，不回滚已经成功导入的图片。
        for (Uri source : sources) {
            String displayName = queryDisplayName(context.getContentResolver(), source);
            try {
                PreparedImage prepared = prepareImage(context, source, displayName);
                try {
                    LocalEmojiCatalogRepository.ImportResult result = repository.importEmoji(
                            packId,
                            prepared.file,
                            prepared.displayName,
                            prepared.mimeType);
                    if (result.isDuplicate()) {
                        duplicate++;
                    } else {
                        imported++;
                        lastImported = result.getEmoji();
                    }
                } finally {
                    prepared.file.delete();
                }
            } catch (IOException | RuntimeException exception) {
                failures.add(new ImportFailure(displayName, readableReason(exception)));
            }
        }
        return new BatchImportResult(imported, duplicate, failures, lastImported);
    }

// 方法作用：校验并导入外部数据（importFile）。
    public static synchronized LocalEmojiCatalogRepository.ImportResult importFile(
            Context context,
            File source,
            String displayName,
            String packId) throws IOException {
        if (source == null || !source.isFile() || source.length() <= 0) {
            throw new IOException("待导入图片不存在");
        }
        if (source.length() > MAX_IMAGE_BYTES) {
            throw new IOException("图片超过 20 MiB 限制");
        }
        ensureCatalog(context);
        ImageInfo info = inspectImage(source);
        return repository(context).importEmoji(
                packId,
                source,
                normalizeDisplayName(displayName, info.mimeType),
                info.mimeType);
    }

// 方法作用：处理 listImageDocuments 对应的输入并返回或更新相关结果（listImageDocuments）。
    public static List<Uri> listImageDocuments(Context context, Uri treeUri) throws IOException {
        if (treeUri == null) {
            throw new IOException("The selected directory authorization is invalid");
        }
        List<Uri> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (RuntimeException exception) {
            throw new IOException("The selected directory authorization is invalid", exception);
        }
        collectImageDocuments(context.getContentResolver(), treeUri, rootId, result, visited);
        return result;
    }

// 方法作用：创建并返回新的业务对象或界面对象（createPack）。
    public static synchronized EmojiCatalog.Pack createPack(Context context, String name)
            throws IOException {
        ensureCatalog(context);
        return repository(context).createPack(
                LocalEmojiCatalogRepository.DEFAULT_GALLERY_ID,
                name);
    }

// 方法作用：创建并返回新的业务对象或界面对象（createPacks）。
    public static synchronized List<EmojiCatalog.Pack> createPacks(
            Context context,
            List<String> galleryIds,
            List<String> names) throws IOException {
        ensureCatalog(context);
        return repository(context).createPacks(galleryIds, names);
    }

// 方法作用：创建并返回新的业务对象或界面对象（createGallery）。
    public static synchronized EmojiCatalog.Gallery createGallery(
            Context context,
            String name) throws IOException {
        ensureCatalog(context);
        return repository(context).createGallery(name);
    }

// 方法作用：修改目标图库或表情包的名称并保存变更（renameGallery）。
    public static synchronized void renameGallery(
            Context context,
            String galleryId,
            String name) throws IOException {
        repository(context).renameGallery(galleryId, name);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（deleteGallery）。
    public static synchronized void deleteGallery(Context context, String galleryId)
            throws IOException {
        repository(context).deleteGallery(galleryId);
    }

// 方法作用：建立图库与表情包之间的关联并校验引用（linkPacksToGallery）。
    public static synchronized void linkPacksToGallery(
            Context context,
            String galleryId,
            List<String> packIds) throws IOException {
        repository(context).linkPacksToGallery(galleryId, packIds);
    }

// 方法作用：解除图库与表情包之间的关联并保存变更（unlinkPackFromGallery）。
    public static synchronized void unlinkPackFromGallery(
            Context context,
            String galleryId,
            String packId) throws IOException {
        repository(context).unlinkPackFromGallery(galleryId, packId);
    }

// 方法作用：解除图库与表情包之间的关联并保存变更（unlinkPacksFromGallery）。
    public static synchronized void unlinkPacksFromGallery(
            Context context,
            String galleryId,
            List<String> packIds) throws IOException {
        repository(context).unlinkPacksFromGallery(galleryId, packIds);
    }

// 方法作用：修改目标图库或表情包的名称并保存变更（renamePack）。
    public static synchronized void renamePack(Context context, String packId, String name)
            throws IOException {
        repository(context).renamePack(packId, name);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（deletePack）。
    public static synchronized void deletePack(Context context, String packId) throws IOException {
        repository(context).deletePack(packId);
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（updateItemNote）。
    public static synchronized void updateItemNote(
            Context context,
            String itemId,
            String note) throws IOException {
        repository(context).updateItemNote(itemId, note);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（deleteItem）。
    public static synchronized void deleteItem(Context context, String itemId) throws IOException {
        repository(context).deleteItem(itemId);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（deleteItems）。
    public static synchronized void deleteItems(
            Context context,
            String packId,
            List<String> itemIds) throws IOException {
        repository(context).deleteItems(packId, itemIds);
    }

// 方法作用：读取并返回持久化或运行时状态（getMimeType）。
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

// 方法作用：校验前置条件并在不满足时报告明确错误（ensureCatalog）。
    private static void ensureCatalog(Context context) {
        getCurrentEmoji(context);
    }

// 方法作用：复制、校验并准备待导入的图片文件（prepareImage）。
    private static PreparedImage prepareImage(
            Context context,
            Uri source,
            String displayName) throws IOException {
        if (source == null) {
            throw new IOException("The image URI is missing");
        }
        ContentResolver resolver = context.getContentResolver();
        long declaredSize = querySize(resolver, source);
        if (declaredSize > MAX_IMAGE_BYTES) {
            throw new IOException("Image exceeds the 20 MiB limit");
        }

        File stagingDirectory = new File(context.getCacheDir(), "emoji-import");
        if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
            throw new IOException("Cannot create the import staging directory");
        }
        File staged = File.createTempFile("document-", ".image", stagingDirectory);
        try {
            copyDocument(resolver, source, staged);
            ImageInfo info = inspectImage(staged);
            String normalizedName = normalizeDisplayName(displayName, info.mimeType);
            return new PreparedImage(staged, normalizedName, info.mimeType);
        } catch (IOException | RuntimeException exception) {
            staged.delete();
            throw exception;
        }
    }

// 方法作用：在受控范围内复制输入数据（copyDocument）。
    private static void copyDocument(ContentResolver resolver, Uri source, File destination)
            throws IOException {
        long total = 0;
        try (InputStream input = resolver.openInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) {
                throw new IOException("The selected document cannot be opened");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException("Image exceeds the 20 MiB limit");
                }
                output.write(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        }
        if (total == 0) {
            throw new IOException("The selected image is empty");
        }
    }

// 方法作用：读取图片尺寸和 MIME 类型等元数据（inspectImage）。
    private static ImageInfo inspectImage(File file) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        String mimeType = normalizeDecodedMime(bounds.outMimeType);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || mimeType == null) {
            throw new IOException("The selected document is not a decodable image");
        }
        long pixels = (long) bounds.outWidth * (long) bounds.outHeight;
        if (pixels > MAX_IMAGE_PIXELS) {
            throw new IOException("Image exceeds the 40 megapixel limit");
        }
        return new ImageInfo(bounds.outWidth, bounds.outHeight, mimeType);
    }

// 方法作用：清理并规范化输入值（normalizeDecodedMime）。
    private static String normalizeDecodedMime(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String normalized = mimeType.toLowerCase(Locale.US);
        if ("image/png".equals(normalized) || "image/x-png".equals(normalized)) {
            return "image/png";
        }
        if ("image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        if ("image/webp".equals(normalized)) {
            return "image/webp";
        }
        if ("image/gif".equals(normalized)) {
            return "image/gif";
        }
        return null;
    }

// 方法作用：清理并规范化输入值（normalizeDisplayName）。
    private static String normalizeDisplayName(String displayName, String mimeType) {
        String normalized = displayName == null ? "" : displayName.trim();
        normalized = normalized.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        if (normalized.isEmpty()) {
            normalized = "image." + extensionForMime(mimeType);
        }
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

// 方法作用：处理 extensionForMime 对应的输入并返回或更新相关结果（extensionForMime）。
    private static String extensionForMime(String mimeType) {
        if ("image/jpeg".equals(mimeType)) {
            return "jpg";
        }
        if ("image/webp".equals(mimeType)) {
            return "webp";
        }
        if ("image/gif".equals(mimeType)) {
            return "gif";
        }
        return "png";
    }

// 方法作用：根据输入条件查询并返回匹配结果（queryDisplayName）。
    private static String queryDisplayName(ContentResolver resolver, Uri source) {
        if (source == null) {
            return "未知文件";
        }
        try (Cursor cursor = resolver.query(
                source,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && !cursor.isNull(index)) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Providers may omit metadata even while the document itself remains readable.
        }
        return "未知文件";
    }

// 方法作用：根据输入条件查询并返回匹配结果（querySize）。
    private static long querySize(ContentResolver resolver, Uri source) {
        if (source == null) {
            return -1;
        }
        try (Cursor cursor = resolver.query(
                source,
                new String[]{OpenableColumns.SIZE},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index);
                }
            }
        } catch (RuntimeException ignored) {
            // The streaming byte limit remains authoritative when SIZE is unavailable.
        }
        return -1;
    }

// 方法作用：递归遍历授权目录并收集图片 URI（collectImageDocuments）。
    private static void collectImageDocuments(
            ContentResolver resolver,
            Uri treeUri,
            String parentDocumentId,
            List<Uri> result,
            Set<String> visited) throws IOException {
        if (!visited.add(parentDocumentId)) {
            return;
        }
        if (visited.size() > MAX_TREE_DOCUMENTS) {
            throw new IOException("The selected directory contains too many documents");
        }
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                parentDocumentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                throw new IOException("The selected directory is no longer accessible");
            }
            int idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE);
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(idIndex);
                String mimeType = cursor.getString(mimeIndex);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    collectImageDocuments(
                            resolver, treeUri, documentId, result, visited);
                } else if (mimeType != null && mimeType.startsWith("image/")) {
                    result.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId));
                }
            }
        } catch (SecurityException exception) {
            throw new IOException("The selected directory authorization has expired", exception);
        } catch (RuntimeException exception) {
            throw new IOException("The selected directory cannot be read", exception);
        }
    }

// 方法作用：从输入源读取并转换数据（readableReason）。
    private static String readableReason(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? "Unknown import error"
                : message.trim();
    }

// 方法作用：读取并返回持久化或运行时状态（getDirectory）。
    private static File getDirectory(Context context) {
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create image directory");
        }
        return directory;
    }

// 方法作用：按应用文件目录取得可复用的目录仓库实例（repository）。
    private static synchronized LocalEmojiCatalogRepository repository(Context context) {
        String root = context.getApplicationContext().getFilesDir().getAbsolutePath();
        if (sharedRepository == null || !root.equals(sharedRepositoryRoot)) {
            sharedRepository = new LocalEmojiCatalogRepository(new File(root));
            sharedRepositoryRoot = root;
        }
        return sharedRepository;
    }

// 方法作用：创建并返回新的业务对象或界面对象（createSample）。
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

// 类作用：定义 PreparedImage，承载所在模块的主要职责。
    private static final class PreparedImage {
        private final File file;
        private final String displayName;
        private final String mimeType;

// 方法作用：初始化 PreparedImage 对象并建立其运行所需状态。
        private PreparedImage(File file, String displayName, String mimeType) {
            this.file = file;
            this.displayName = displayName;
            this.mimeType = mimeType;
        }
    }

// 类作用：定义 ImageInfo，承载所在模块的主要职责。
    private static final class ImageInfo {
        private final int width;
        private final int height;
        private final String mimeType;

// 方法作用：初始化 ImageInfo 对象并建立其运行所需状态。
        private ImageInfo(int width, int height, String mimeType) {
            this.width = width;
            this.height = height;
            this.mimeType = mimeType;
        }
    }

// 类作用：定义 ImportFailure，承载所在模块的主要职责。
    public static final class ImportFailure {
        private final String displayName;
        private final String reason;

// 方法作用：初始化 ImportFailure 对象并建立其运行所需状态。
        private ImportFailure(String displayName, String reason) {
            this.displayName = displayName;
            this.reason = reason;
        }

// 方法作用：读取并返回持久化或运行时状态（getDisplayName）。
        public String getDisplayName() {
            return displayName;
        }

// 方法作用：读取并返回持久化或运行时状态（getReason）。
        public String getReason() {
            return reason;
        }
    }

// 类作用：定义 BatchImportResult，承载所在模块的主要职责。
    public static final class BatchImportResult {
        private final int importedCount;
        private final int duplicateCount;
        private final List<ImportFailure> failures;
        private final LocalEmojiCatalogRepository.StoredEmoji lastImported;

// 方法作用：初始化 BatchImportResult 对象并建立其运行所需状态。
        private BatchImportResult(
                int importedCount,
                int duplicateCount,
                List<ImportFailure> failures,
                LocalEmojiCatalogRepository.StoredEmoji lastImported) {
            this.importedCount = importedCount;
            this.duplicateCount = duplicateCount;
            this.failures = new ArrayList<>(failures);
            this.lastImported = lastImported;
        }

// 方法作用：读取并返回持久化或运行时状态（getImportedCount）。
        public int getImportedCount() {
            return importedCount;
        }

// 方法作用：读取并返回持久化或运行时状态（getDuplicateCount）。
        public int getDuplicateCount() {
            return duplicateCount;
        }

// 方法作用：读取并返回持久化或运行时状态（getFailures）。
        public List<ImportFailure> getFailures() {
            return new ArrayList<>(failures);
        }

// 方法作用：读取并返回持久化或运行时状态（getLastImported）。
        public LocalEmojiCatalogRepository.StoredEmoji getLastImported() {
            return lastImported;
        }
    }
}
