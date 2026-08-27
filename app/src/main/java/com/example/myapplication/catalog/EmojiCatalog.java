package com.example.myapplication.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 不可变表情目录，统一保存表情包并维护图库到表情包的引用。 */
// 类作用：定义 EmojiCatalog，承载所在模块的主要职责。
public final class EmojiCatalog {
    public static final int CURRENT_VERSION = 2;

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Comparator<Gallery> GALLERY_ORDER = (left, right) -> compareOrderAndId(
            left.getSortOrder(), left.getId(), right.getSortOrder(), right.getId());
    private static final Comparator<Pack> PACK_ORDER = (left, right) -> compareOrderAndId(
            left.getSortOrder(), left.getId(), right.getSortOrder(), right.getId());
    private static final Comparator<Item> ITEM_ORDER = (left, right) -> compareOrderAndId(
            left.getSortOrder(), left.getId(), right.getSortOrder(), right.getId());

    private final int version;
    private final List<Gallery> galleries;
    private final List<Pack> packs;
    private final Map<String, Pack> packsById;

// 方法作用：初始化 EmojiCatalog 对象并建立其运行所需状态。
    public EmojiCatalog(int version, List<Gallery> galleries, List<Pack> packs) {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported catalog version: " + version);
        }
        if (galleries == null || packs == null) {
            throw new IllegalArgumentException("Catalog galleries and packs are required");
        }
        this.version = version;
        this.galleries = immutableSortedCopy(galleries, GALLERY_ORDER);
        this.packs = immutableSortedCopy(packs, PACK_ORDER);
        this.packsById = validateAndIndex(this.galleries, this.packs);
    }

// 方法作用：读取并返回持久化或运行时状态（getVersion）。
    public int getVersion() {
        return version;
    }

// 方法作用：读取并返回持久化或运行时状态（getGalleries）。
    public List<Gallery> getGalleries() {
        return galleries;
    }

// 方法作用：读取并返回持久化或运行时状态（getPacks）。
    public List<Pack> getPacks() {
        return packs;
    }

// 方法作用：读取并返回持久化或运行时状态（getPack）。
    public Pack getPack(String packId) {
        return packsById.get(packId);
    }

// 方法作用：读取并返回持久化或运行时状态（getPacksForGallery）。
    public List<Pack> getPacksForGallery(String galleryId) {
        Gallery selected = null;
        for (Gallery gallery : galleries) {
            if (gallery.getId().equals(galleryId)) {
                selected = gallery;
                break;
            }
        }
        if (selected == null) {
            return Collections.emptyList();
        }
        List<Pack> result = new ArrayList<>();
        for (String packId : selected.getPackIds()) {
            Pack pack = packsById.get(packId);
            if (pack != null) {
                result.add(pack);
            }
        }
        Collections.sort(result, PACK_ORDER);
        return Collections.unmodifiableList(result);
    }

// 方法作用：处理 galleryContainsPack 对应的输入并返回或更新相关结果（galleryContainsPack）。
    public boolean galleryContainsPack(String galleryId, String packId) {
        for (Gallery gallery : galleries) {
            if (gallery.getId().equals(galleryId)) {
                return gallery.getPackIds().contains(packId);
            }
        }
        return false;
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（validateAndIndex）。
    private static Map<String, Pack> validateAndIndex(
            List<Gallery> galleries,
            List<Pack> packs) {
        Set<String> galleryIds = new HashSet<>();
        Set<String> itemIds = new HashSet<>();
        Map<String, Pack> packsById = new HashMap<>();
        for (Pack pack : packs) {
            if (packsById.put(pack.getId(), pack) != null) {
                throw new IllegalArgumentException("Duplicate pack id: " + pack.getId());
            }
            for (Item item : pack.getItems()) {
                requireUnique(itemIds, item.getId(), "item");
            }
        }
        for (Gallery gallery : galleries) {
            requireUnique(galleryIds, gallery.getId(), "gallery");
            Set<String> references = new HashSet<>();
            for (String packId : gallery.getPackIds()) {
                if (!references.add(packId)) {
                    throw new IllegalArgumentException(
                            "Duplicate pack reference in gallery: " + packId);
                }
                if (!packsById.containsKey(packId)) {
                    throw new IllegalArgumentException("Unknown pack reference: " + packId);
                }
            }
        }
        return Collections.unmodifiableMap(packsById);
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireUnique）。
    private static void requireUnique(Set<String> ids, String id, String type) {
        if (!ids.add(id)) {
            throw new IllegalArgumentException("Duplicate " + type + " id: " + id);
        }
    }

// 方法作用：处理 immutableSortedCopy 对应的输入并返回或更新相关结果（immutableSortedCopy）。
    private static <T> List<T> immutableSortedCopy(List<T> values, Comparator<T> comparator) {
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.contains(null)) {
            throw new IllegalArgumentException("Catalog collections cannot contain null values");
        }
        Collections.sort(copy, comparator);
        return Collections.unmodifiableList(copy);
    }

// 方法作用：根据输入参数计算或比较结果（compareOrderAndId）。
    private static int compareOrderAndId(
            int leftOrder,
            String leftId,
            int rightOrder,
            String rightId) {
        if (leftOrder < rightOrder) {
            return -1;
        }
        if (leftOrder > rightOrder) {
            return 1;
        }
        return leftId.compareTo(rightId);
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireId）。
    private static String requireId(String id, String type) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid " + type + " id");
        }
        return id;
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireName）。
    private static String requireName(String name, String type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(type + " name is required");
        }
        return name.trim();
    }

// 类作用：定义 Gallery，承载所在模块的主要职责。
    public static final class Gallery {
        private final String id;
        private final String name;
        private final int sortOrder;
        private final List<String> packIds;

// 方法作用：初始化 Gallery 对象并建立其运行所需状态。
        public Gallery(String id, String name, int sortOrder, List<String> packIds) {
            this.id = requireId(id, "gallery");
            this.name = requireName(name, "Gallery");
            this.sortOrder = sortOrder;
            if (packIds == null || packIds.contains(null)) {
                throw new IllegalArgumentException("Gallery pack references are required");
            }
            List<String> references = new ArrayList<>();
            for (String packId : packIds) {
                references.add(requireId(packId, "pack reference"));
            }
            this.packIds = Collections.unmodifiableList(references);
        }

// 方法作用：读取并返回持久化或运行时状态（getId）。
        public String getId() {
            return id;
        }

// 方法作用：读取并返回持久化或运行时状态（getName）。
        public String getName() {
            return name;
        }

// 方法作用：读取并返回持久化或运行时状态（getSortOrder）。
        public int getSortOrder() {
            return sortOrder;
        }

// 方法作用：读取并返回持久化或运行时状态（getPackIds）。
        public List<String> getPackIds() {
            return packIds;
        }
    }

// 类作用：定义 Pack，承载所在模块的主要职责。
    public static final class Pack {
        private final String id;
        private final String name;
        private final int sortOrder;
        private final List<Item> items;

// 方法作用：初始化 Pack 对象并建立其运行所需状态。
        public Pack(String id, String name, int sortOrder, List<Item> items) {
            this.id = requireId(id, "pack");
            this.name = requireName(name, "Pack");
            this.sortOrder = sortOrder;
            if (items == null) {
                throw new IllegalArgumentException("Pack items are required");
            }
            this.items = immutableSortedCopy(items, ITEM_ORDER);
        }

// 方法作用：读取并返回持久化或运行时状态（getId）。
        public String getId() {
            return id;
        }

// 方法作用：读取并返回持久化或运行时状态（getName）。
        public String getName() {
            return name;
        }

// 方法作用：读取并返回持久化或运行时状态（getSortOrder）。
        public int getSortOrder() {
            return sortOrder;
        }

// 方法作用：读取并返回持久化或运行时状态（getItems）。
        public List<Item> getItems() {
            return items;
        }
    }

// 类作用：定义 Item，承载所在模块的主要职责。
    public static final class Item {
        private final String id;
        private final String name;
        private final String note;
        private final String mimeType;
        private final String relativePath;
        private final int sortOrder;

// 方法作用：初始化 Item 对象并建立其运行所需状态。
        public Item(
                String id,
                String name,
                String note,
                String mimeType,
                String relativePath,
                int sortOrder) {
            this.id = requireId(id, "item");
            this.name = requireName(name, "Item");
            this.note = note == null ? "" : note.trim();
            if (mimeType == null || !mimeType.startsWith("image/") || mimeType.length() <= 6) {
                throw new IllegalArgumentException("Invalid image MIME type");
            }
            this.mimeType = mimeType;
            this.relativePath = requireRelativePath(relativePath);
            this.sortOrder = sortOrder;
        }

// 方法作用：读取并返回持久化或运行时状态（getId）。
        public String getId() {
            return id;
        }

// 方法作用：读取并返回持久化或运行时状态（getName）。
        public String getName() {
            return name;
        }

// 方法作用：读取并返回持久化或运行时状态（getNote）。
        public String getNote() {
            return note;
        }

// 方法作用：读取并返回持久化或运行时状态（getMimeType）。
        public String getMimeType() {
            return mimeType;
        }

// 方法作用：读取并返回持久化或运行时状态（getRelativePath）。
        public String getRelativePath() {
            return relativePath;
        }

// 方法作用：读取并返回持久化或运行时状态（getSortOrder）。
        public int getSortOrder() {
            return sortOrder;
        }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireRelativePath）。
        private static String requireRelativePath(String path) {
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalArgumentException("Emoji relative path is required");
            }
            String normalized = path.trim();
            if (normalized.startsWith("/")
                    || normalized.startsWith("\\")
                    || normalized.contains("\\")
                    || normalized.contains(":")) {
                throw new IllegalArgumentException("Emoji path must remain inside the library");
            }
            String[] segments = normalized.split("/", -1);
            for (String segment : segments) {
                if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                    throw new IllegalArgumentException("Emoji path must remain inside the library");
                }
            }
            return normalized;
        }
    }
}
