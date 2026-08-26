package com.example.myapplication.catalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Owns the versioned catalog and its image files under application-private storage. */
public final class LocalEmojiCatalogRepository {
    public static final String DEFAULT_GALLERY_ID = "gallery-local";
    public static final String DEFAULT_PACK_ID = "pack-default";
    public static final String DEFAULT_ITEM_ID = "emoji-default";

    private static final String DEFAULT_ITEM_NAME = "默认表情";
    private static final String LIBRARY_DIRECTORY = "emoji-library";
    private static final String CATALOG_FILE = "catalog.json";

    private final File libraryDirectory;
    private final File imagesDirectory;
    private final EmojiCatalogJsonCodec codec;
    private final AtomicTextFile catalogFile;

    public LocalEmojiCatalogRepository(File applicationFilesDirectory) {
        this(new File(applicationFilesDirectory, LIBRARY_DIRECTORY),
                new EmojiCatalogJsonCodec(), null);
    }

    LocalEmojiCatalogRepository(
            File libraryDirectory,
            EmojiCatalogJsonCodec codec,
            AtomicTextFile catalogFile) {
        this.libraryDirectory = libraryDirectory;
        this.imagesDirectory = new File(libraryDirectory, "images");
        this.codec = codec;
        this.catalogFile = catalogFile == null
                ? new AtomicTextFile(new File(libraryDirectory, CATALOG_FILE))
                : catalogFile;
    }

    public synchronized StoredEmoji loadDefaultEmoji(
            File legacyImage,
            String legacyMimeType) throws IOException {
        EmojiCatalog catalog = loadCatalogOrNull();
        if (catalog == null) {
            catalog = migrateLegacyImage(legacyImage, legacyMimeType);
        }
        EmojiCatalog.Item item = findCurrentItem(catalog);
        return new StoredEmoji(item, resolveImageFile(item));
    }

    public synchronized StoredEmoji replaceDefaultEmoji(File source, String mimeType)
            throws IOException {
        return replaceDefaultEmoji(source, source == null ? null : source.getName(), mimeType);
    }

    public synchronized StoredEmoji replaceDefaultEmoji(
            File source,
            String displayName,
            String mimeType) throws IOException {
        EmojiCatalog current = loadCatalogOrNull();
        if (current == null) {
            EmojiCatalog migrated = migrateLegacyImage(source, mimeType);
            EmojiCatalog.Item item = findCurrentItem(migrated);
            return new StoredEmoji(item, resolveImageFile(item));
        }
        EmojiCatalog.Item previousItem = findCurrentItem(current);
        File previousFile = resolveImageFile(previousItem);
        String packId = findPackId(current, previousItem.getId());
        ManagedImage replacement = copyManagedImage(
                source, mimeType, packId, previousItem.getId());
        EmojiCatalog.Item replacementItem = new EmojiCatalog.Item(
                previousItem.getId(),
                normalizeDisplayName(displayName),
                previousItem.getNote(),
                mimeType,
                replacement.relativePath,
                previousItem.getSortOrder());
        commitWithNewFile(
                EmojiCatalogEditor.replaceItem(current, previousItem.getId(), replacementItem),
                replacement.file);
        deleteQuietly(previousFile);
        return new StoredEmoji(replacementItem, replacement.file);
    }

    public synchronized ImportResult importEmoji(
            String packId,
            File source,
            String displayName,
            String mimeType) throws IOException {
        EmojiCatalog current = requireCatalog();
        EmojiCatalog.Pack pack = requirePack(current, packId);
        validateSource(source, mimeType);
        String digest = sha256(source);
        StoredEmoji duplicate = findByDigest(pack, digest);
        if (duplicate != null) {
            return ImportResult.duplicate(duplicate);
        }

        EmojiCatalog.Item placeholder = findReplaceablePlaceholder(current, packId);
        String itemId = placeholder == null
                ? "emoji-" + UUID.randomUUID()
                : placeholder.getId();
        ManagedImage managed = copyManagedImage(source, mimeType, packId, itemId);
        EmojiCatalog.Item item = new EmojiCatalog.Item(
                itemId,
                normalizeDisplayName(displayName),
                "",
                mimeType,
                managed.relativePath,
                placeholder == null ? nextItemOrder(pack) : placeholder.getSortOrder());
        EmojiCatalog updated = placeholder == null
                ? EmojiCatalogEditor.addItem(current, packId, item)
                : EmojiCatalogEditor.replaceItem(current, placeholder.getId(), item);
        File replacedFile = placeholder == null ? null : resolveImageFile(placeholder);
        commitWithNewFile(updated, managed.file);
        deleteQuietly(replacedFile);
        return ImportResult.imported(new StoredEmoji(item, managed.file));
    }

    public synchronized EmojiCatalog.Gallery createGallery(String name) throws IOException {
        EmojiCatalog current = requireCatalog();
        EmojiCatalog.Gallery gallery = new EmojiCatalog.Gallery(
                "gallery-" + UUID.randomUUID(),
                name,
                nextGalleryOrder(current),
                Collections.emptyList());
        catalogFile.write(codec.encode(EmojiCatalogEditor.addGallery(current, gallery)));
        return gallery;
    }

    public synchronized void renameGallery(String galleryId, String name) throws IOException {
        EmojiCatalog current = requireCatalog();
        catalogFile.write(codec.encode(
                EmojiCatalogEditor.renameGallery(current, galleryId, name)));
    }

    public synchronized void deleteGallery(String galleryId) throws IOException {
        EmojiCatalog current = requireCatalog();
        if (current.getGalleries().size() <= 1) {
            throw new IOException("At least one gallery must remain");
        }
        catalogFile.write(codec.encode(
                EmojiCatalogEditor.removeGallery(current, galleryId)));
    }

    public synchronized EmojiCatalog.Pack createPack(String galleryId, String name)
            throws IOException {
        return createPacks(
                Collections.singletonList(galleryId),
                Collections.singletonList(name)).get(0);
    }

    public synchronized List<EmojiCatalog.Pack> createPacks(
            List<String> galleryIds,
            List<String> names) throws IOException {
        EmojiCatalog updated = requireCatalog();
        List<String> targets = uniqueRequiredIds(galleryIds, "target gallery");
        for (String galleryId : targets) {
            requireGallery(updated, galleryId);
        }
        List<String> normalizedNames = normalizeNames(names);
        List<EmojiCatalog.Pack> created = new ArrayList<>();
        int order = nextPackOrder(updated);
        for (String name : normalizedNames) {
            EmojiCatalog.Pack pack = new EmojiCatalog.Pack(
                    "pack-" + UUID.randomUUID(),
                    name,
                    order++,
                    Collections.emptyList());
            updated = EmojiCatalogEditor.addPack(updated, pack, targets);
            created.add(pack);
        }
        catalogFile.write(codec.encode(updated));
        return Collections.unmodifiableList(created);
    }

    public synchronized void linkPacksToGallery(
            String galleryId,
            List<String> packIds) throws IOException {
        EmojiCatalog updated = requireCatalog();
        requireGallery(updated, galleryId);
        for (String packId : uniqueRequiredIds(packIds, "emoji pack")) {
            requirePack(updated, packId);
            updated = EmojiCatalogEditor.linkPack(
                    updated,
                    packId,
                    Collections.singletonList(galleryId));
        }
        catalogFile.write(codec.encode(updated));
    }

    public synchronized void unlinkPackFromGallery(String galleryId, String packId)
            throws IOException {
        unlinkPacksFromGallery(galleryId, Collections.singletonList(packId));
    }

    public synchronized void unlinkPacksFromGallery(
            String galleryId, List<String> packIds) throws IOException {
        EmojiCatalog current = requireCatalog();
        requireGallery(current, galleryId);
        EmojiCatalog updated = current;
        for (String packId : uniqueRequiredIds(packIds, "emoji pack")) {
            requirePack(updated, packId);
            updated = EmojiCatalogEditor.unlinkPack(updated, galleryId, packId);
        }
        catalogFile.write(codec.encode(updated));
    }

    public synchronized void renamePack(String packId, String name) throws IOException {
        EmojiCatalog current = requireCatalog();
        catalogFile.write(codec.encode(EmojiCatalogEditor.renamePack(current, packId, name)));
    }

    public synchronized void deletePack(String packId) throws IOException {
        if (DEFAULT_PACK_ID.equals(packId)) {
            throw new IOException("The default emoji pack cannot be deleted");
        }
        EmojiCatalog current = requireCatalog();
        EmojiCatalog.Pack pack = requirePack(current, packId);
        if (countItems(current) - pack.getItems().size() <= 0) {
            throw new IOException("At least one emoji must remain in the catalog");
        }
        EmojiCatalog updated = EmojiCatalogEditor.removePack(current, packId);
        List<StagedFile> staged = stageFiles(pack.getItems());
        try {
            catalogFile.write(codec.encode(updated));
        } catch (IOException exception) {
            restoreStaged(staged, exception);
            throw exception;
        }
        discardStaged(staged);
    }

    public synchronized void updateItemNote(String itemId, String note) throws IOException {
        EmojiCatalog current = requireCatalog();
        EmojiCatalog.Item item = requireItem(current, itemId);
        EmojiCatalog.Item updatedItem = new EmojiCatalog.Item(
                item.getId(), item.getName(), note, item.getMimeType(),
                item.getRelativePath(), item.getSortOrder());
        catalogFile.write(codec.encode(
                EmojiCatalogEditor.replaceItem(current, itemId, updatedItem)));
    }

    public synchronized void deleteItem(String itemId) throws IOException {
        EmojiCatalog current = requireCatalog();
        if (countItems(current) <= 1) {
            throw new IOException("At least one emoji must remain in the catalog");
        }
        EmojiCatalog.Item item = requireItem(current, itemId);
        EmojiCatalog updated = EmojiCatalogEditor.removeItem(current, itemId);
        StagedFile staged = stageFile(resolveImageFile(item));
        try {
            catalogFile.write(codec.encode(updated));
        } catch (IOException exception) {
            restoreStaged(Collections.singletonList(staged), exception);
            throw exception;
        }
        discardStaged(Collections.singletonList(staged));
    }

    public synchronized void deleteItems(String packId, List<String> itemIds) throws IOException {
        EmojiCatalog current = requireCatalog();
        EmojiCatalog.Pack pack = requirePack(current, packId);
        List<String> ids = uniqueRequiredIds(itemIds, "emoji");
        List<EmojiCatalog.Item> selectedItems = new ArrayList<>();
        for (String itemId : ids) {
            EmojiCatalog.Item selected = null;
            for (EmojiCatalog.Item item : pack.getItems()) {
                if (itemId.equals(item.getId())) {
                    selected = item;
                    break;
                }
            }
            if (selected == null) {
                throw new IOException("Emoji does not exist in the selected emoji pack: " + itemId);
            }
            selectedItems.add(selected);
        }
        if (countItems(current) - selectedItems.size() <= 0) {
            throw new IOException("At least one emoji must remain in the catalog");
        }

        EmojiCatalog updated = EmojiCatalogEditor.removeItems(current, packId, ids);
        List<StagedFile> staged = stageFiles(selectedItems);
        try {
            catalogFile.write(codec.encode(updated));
        } catch (IOException exception) {
            restoreStaged(staged, exception);
            throw exception;
        }
        discardStaged(staged);
    }

    public synchronized EmojiCatalog loadCatalog() throws IOException {
        return requireCatalog();
    }

    public synchronized StoredEmoji getStoredEmoji(String itemId) throws IOException {
        EmojiCatalog.Item item = requireItem(requireCatalog(), itemId);
        return new StoredEmoji(item, resolveImageFile(item));
    }

    public File resolveImageFile(EmojiCatalog.Item item) throws IOException {
        File libraryRoot = libraryDirectory.getCanonicalFile();
        File imageRoot = imagesDirectory.getCanonicalFile();
        File resolved = new File(libraryRoot, item.getRelativePath()).getCanonicalFile();
        String imagePrefix = imageRoot.getPath() + File.separator;
        if (!resolved.getPath().startsWith(imagePrefix)) {
            throw new IOException("Emoji image path escapes the library");
        }
        if (!resolved.isFile()) {
            throw new IOException("Emoji image is missing");
        }
        return resolved;
    }

    private EmojiCatalog requireCatalog() throws IOException {
        EmojiCatalog catalog = loadCatalogOrNull();
        if (catalog == null) {
            throw new IOException("Emoji catalog has not been initialized");
        }
        return catalog;
    }

    private EmojiCatalog loadCatalogOrNull() throws IOException {
        String json = catalogFile.readOrNull();
        if (json == null) {
            return null;
        }
        EmojiCatalog catalog = codec.decode(json);
        validateCatalogFiles(catalog);
        return catalog;
    }

    private EmojiCatalog migrateLegacyImage(File legacyImage, String mimeType) throws IOException {
        validateSource(legacyImage, mimeType);
        ManagedImage managed = copyManagedImage(
                legacyImage, mimeType, DEFAULT_PACK_ID, DEFAULT_ITEM_ID);
        EmojiCatalog.Item item = new EmojiCatalog.Item(
                DEFAULT_ITEM_ID, DEFAULT_ITEM_NAME, "", mimeType, managed.relativePath, 0);
        EmojiCatalog.Pack pack = new EmojiCatalog.Pack(
                DEFAULT_PACK_ID, "默认表情包", 0, Collections.singletonList(item));
        EmojiCatalog.Gallery gallery = new EmojiCatalog.Gallery(
                DEFAULT_GALLERY_ID,
                "本地图库",
                0,
                Collections.singletonList(DEFAULT_PACK_ID));
        EmojiCatalog catalog = new EmojiCatalog(
                EmojiCatalog.CURRENT_VERSION,
                Collections.singletonList(gallery),
                Collections.singletonList(pack));
        commitWithNewFile(catalog, managed.file);
        return catalog;
    }

    private void commitWithNewFile(EmojiCatalog catalog, File newFile) throws IOException {
        try {
            catalogFile.write(codec.encode(catalog));
        } catch (IOException exception) {
            deleteQuietly(newFile);
            throw exception;
        }
    }

    private ManagedImage copyManagedImage(
            File source,
            String mimeType,
            String packId,
            String itemId) throws IOException {
        validateSource(source, mimeType);
        File destinationDirectory = new File(imagesDirectory, "packs" + File.separator + packId);
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw new IOException("Cannot create the managed image directory");
        }
        String fileName = itemId + "-" + UUID.randomUUID() + "." + extensionForMime(mimeType);
        File destination = new File(destinationDirectory, fileName);
        File temporary = File.createTempFile("import-", ".tmp", destinationDirectory);
        try {
            copyFile(source, temporary);
            if (!temporary.renameTo(destination)) {
                throw new IOException("Cannot activate the managed image");
            }
        } catch (IOException exception) {
            deleteQuietly(temporary);
            deleteQuietly(destination);
            throw exception;
        }
        try {
            return new ManagedImage(destination, toRelativePath(destination));
        } catch (IOException exception) {
            deleteQuietly(destination);
            throw exception;
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        }
    }

    private static void validateSource(File source, String mimeType) throws IOException {
        if (source == null || !source.isFile() || source.length() <= 0) {
            throw new IOException("Emoji source image is missing or empty");
        }
        if (mimeType == null || !mimeType.startsWith("image/") || mimeType.length() <= 6) {
            throw new IOException("Emoji source MIME type is invalid");
        }
        extensionForMime(mimeType);
    }

    private static String extensionForMime(String mimeType) throws IOException {
        String normalized = mimeType.toLowerCase(Locale.US);
        if ("image/png".equals(normalized)) {
            return "png";
        }
        if ("image/jpeg".equals(normalized)) {
            return "jpg";
        }
        if ("image/webp".equals(normalized)) {
            return "webp";
        }
        if ("image/gif".equals(normalized)) {
            return "gif";
        }
        throw new IOException("Unsupported emoji image MIME type: " + mimeType);
    }

    private String toRelativePath(File file) throws IOException {
        String root = libraryDirectory.getCanonicalPath() + File.separator;
        String path = file.getCanonicalPath();
        if (!path.startsWith(root)) {
            throw new IOException("Managed image is outside the library");
        }
        return path.substring(root.length()).replace(File.separatorChar, '/');
    }

    private StoredEmoji findByDigest(EmojiCatalog.Pack pack, String digest) throws IOException {
        for (EmojiCatalog.Item item : pack.getItems()) {
            File file = resolveImageFile(item);
            if (digest.equals(sha256(file))) {
                return new StoredEmoji(item, file);
            }
        }
        return null;
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static EmojiCatalog.Item findReplaceablePlaceholder(
            EmojiCatalog catalog,
            String packId) {
        if (!DEFAULT_PACK_ID.equals(packId) || countItems(catalog) != 1) {
            return null;
        }
        EmojiCatalog.Item item = findItemOrNull(catalog, DEFAULT_ITEM_ID);
        return item != null && DEFAULT_ITEM_NAME.equals(item.getName()) ? item : null;
    }

    private static EmojiCatalog.Item findCurrentItem(EmojiCatalog catalog) throws IOException {
        EmojiCatalog.Item first = null;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            for (EmojiCatalog.Item item : pack.getItems()) {
                if (first == null) {
                    first = item;
                }
                if (DEFAULT_ITEM_ID.equals(item.getId())) {
                    return item;
                }
            }
        }
        if (first == null) {
            throw new IOException("Emoji catalog contains no images");
        }
        return first;
    }

    private static String findPackId(EmojiCatalog catalog, String itemId) throws IOException {
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            for (EmojiCatalog.Item item : pack.getItems()) {
                if (itemId.equals(item.getId())) {
                    return pack.getId();
                }
            }
        }
        throw new IOException("Emoji does not belong to a pack");
    }

    private void validateCatalogFiles(EmojiCatalog catalog) throws IOException {
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            for (EmojiCatalog.Item item : pack.getItems()) {
                resolveImageFile(item);
            }
        }
    }

    private List<StagedFile> stageFiles(List<EmojiCatalog.Item> items) throws IOException {
        List<StagedFile> staged = new ArrayList<>();
        try {
            for (EmojiCatalog.Item item : items) {
                staged.add(stageFile(resolveImageFile(item)));
            }
            return staged;
        } catch (IOException exception) {
            restoreStaged(staged, exception);
            throw exception;
        }
    }

    private StagedFile stageFile(File original) throws IOException {
        File trash = new File(libraryDirectory, ".trash");
        if (!trash.exists() && !trash.mkdirs()) {
            throw new IOException("Cannot create deletion staging directory");
        }
        File staged = new File(trash, UUID.randomUUID() + ".deleted");
        if (!original.renameTo(staged)) {
            throw new IOException("Cannot stage emoji image for deletion");
        }
        return new StagedFile(original, staged);
    }

    private static void restoreStaged(List<StagedFile> staged, IOException originalFailure)
            throws IOException {
        for (int index = staged.size() - 1; index >= 0; index--) {
            StagedFile file = staged.get(index);
            File parent = file.original.getParentFile();
            if ((!parent.exists() && !parent.mkdirs()) || !file.staged.renameTo(file.original)) {
                throw new IOException(
                        "Catalog update failed and a deleted image could not be restored",
                        originalFailure);
            }
        }
    }

    private static void discardStaged(List<StagedFile> staged) {
        for (StagedFile file : staged) {
            deleteQuietly(file.staged);
        }
    }

    private static EmojiCatalog.Gallery requireGallery(
            EmojiCatalog catalog,
            String galleryId) throws IOException {
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            if (galleryId.equals(gallery.getId())) {
                return gallery;
            }
        }
        throw new IOException("Gallery does not exist: " + galleryId);
    }

    private static EmojiCatalog.Pack requirePack(EmojiCatalog catalog, String packId)
            throws IOException {
        EmojiCatalog.Pack pack = catalog.getPack(packId);
        if (pack == null) {
            throw new IOException("Emoji pack does not exist: " + packId);
        }
        return pack;
    }

    private static EmojiCatalog.Item requireItem(EmojiCatalog catalog, String itemId)
            throws IOException {
        EmojiCatalog.Item item = findItemOrNull(catalog, itemId);
        if (item == null) {
            throw new IOException("Emoji does not exist: " + itemId);
        }
        return item;
    }

    private static EmojiCatalog.Item findItemOrNull(EmojiCatalog catalog, String itemId) {
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            for (EmojiCatalog.Item item : pack.getItems()) {
                if (itemId.equals(item.getId())) {
                    return item;
                }
            }
        }
        return null;
    }

    private static int countItems(EmojiCatalog catalog) {
        int count = 0;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            count += pack.getItems().size();
        }
        return count;
    }

    private static int nextGalleryOrder(EmojiCatalog catalog) {
        int maximum = -1;
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            maximum = Math.max(maximum, gallery.getSortOrder());
        }
        return maximum + 1;
    }

    private static int nextPackOrder(EmojiCatalog catalog) {
        int maximum = -1;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            maximum = Math.max(maximum, pack.getSortOrder());
        }
        return maximum + 1;
    }

    private static int nextItemOrder(EmojiCatalog.Pack pack) {
        int maximum = -1;
        for (EmojiCatalog.Item item : pack.getItems()) {
            maximum = Math.max(maximum, item.getSortOrder());
        }
        return maximum + 1;
    }

    private static List<String> uniqueRequiredIds(List<String> ids, String type)
            throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new IOException("At least one " + type + " is required");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) {
                throw new IOException(type + " id is required");
            }
            if (seen.add(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private static List<String> normalizeNames(List<String> names) throws IOException {
        if (names == null || names.isEmpty()) {
            throw new IOException("At least one emoji pack name is required");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String normalized = normalizeDisplayName(name);
            if (seen.add(normalized)) {
                result.add(normalized);
            }
        }
        if (result.isEmpty()) {
            throw new IOException("At least one emoji pack name is required");
        }
        return result;
    }

    private static String normalizeDisplayName(String name) throws IOException {
        if (name == null || name.trim().isEmpty()) {
            throw new IOException("Display name is required");
        }
        String normalized = name.trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static final class ManagedImage {
        private final File file;
        private final String relativePath;

        private ManagedImage(File file, String relativePath) {
            this.file = file;
            this.relativePath = relativePath;
        }
    }

    private static final class StagedFile {
        private final File original;
        private final File staged;

        private StagedFile(File original, File staged) {
            this.original = original;
            this.staged = staged;
        }
    }

    public static final class ImportResult {
        private final boolean duplicate;
        private final StoredEmoji emoji;

        private ImportResult(boolean duplicate, StoredEmoji emoji) {
            this.duplicate = duplicate;
            this.emoji = emoji;
        }

        private static ImportResult imported(StoredEmoji emoji) {
            return new ImportResult(false, emoji);
        }

        private static ImportResult duplicate(StoredEmoji emoji) {
            return new ImportResult(true, emoji);
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public StoredEmoji getEmoji() {
            return emoji;
        }
    }

    public static final class StoredEmoji {
        private final EmojiCatalog.Item item;
        private final File file;

        private StoredEmoji(EmojiCatalog.Item item, File file) {
            this.item = item;
            this.file = file;
        }

        public EmojiCatalog.Item getItem() {
            return item;
        }

        public File getFile() {
            return file;
        }
    }
}
