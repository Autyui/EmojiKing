package com.example.myapplication.catalog;

import java.util.ArrayList;
import java.util.List;

/** 输入（旧目录 + 修改指令） → 拷贝并修改列表 → 输出（新目录） */
// 类作用：定义 EmojiCatalogEditor，承载所在模块的主要职责。
final class EmojiCatalogEditor {
    private EmojiCatalogEditor() {
    }

// 方法作用：向界面或业务集合中添加新的元素（addGallery）。
    static EmojiCatalog addGallery(EmojiCatalog catalog, EmojiCatalog.Gallery gallery) {
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>(catalog.getGalleries());
        galleries.add(gallery);
        return rebuild(catalog, galleries, catalog.getPacks());
    }

// 方法作用：修改目标图库或表情包的名称并保存变更（renameGallery）。
    static EmojiCatalog renameGallery(EmojiCatalog catalog, String galleryId, String name) {
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            if (galleryId.equals(gallery.getId())) {
                galleries.add(new EmojiCatalog.Gallery(
                        gallery.getId(), name, gallery.getSortOrder(), gallery.getPackIds()));
                changed = true;
            } else {
                galleries.add(gallery);
            }
        }
        requireChanged(changed, "Gallery does not exist: " + galleryId);
        return rebuild(catalog, galleries, catalog.getPacks());
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（removeGallery）。
    static EmojiCatalog removeGallery(EmojiCatalog catalog, String galleryId) {
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            if (galleryId.equals(gallery.getId())) {
                changed = true;
            } else {
                galleries.add(gallery);
            }
        }
        requireChanged(changed, "Gallery does not exist: " + galleryId);
        return rebuild(catalog, galleries, catalog.getPacks());
    }

// 方法作用：向界面或业务集合中添加新的元素（addPack）。
    static EmojiCatalog addPack(
            EmojiCatalog catalog,
            EmojiCatalog.Pack newPack,
            List<String> galleryIds) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>(catalog.getPacks());
        packs.add(newPack);
        EmojiCatalog withPack = rebuild(catalog, catalog.getGalleries(), packs);
        return linkPack(withPack, newPack.getId(), galleryIds);
    }

// 方法作用：建立图库与表情包之间的关联并校验引用（linkPack）。
    static EmojiCatalog linkPack(
            EmojiCatalog catalog,
            String packId,
            List<String> galleryIds) {
        if (catalog.getPack(packId) == null) {
            throw new IllegalArgumentException("Emoji pack does not exist: " + packId);
        }
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>();
        int matched = 0;
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            if (!galleryIds.contains(gallery.getId())) {
                galleries.add(gallery);
                continue;
            }
            List<String> references = new ArrayList<>(gallery.getPackIds());
            if (!references.contains(packId)) {
                references.add(packId);
            }
            galleries.add(copyGallery(gallery, references));
            matched++;
        }
        if (matched != galleryIds.size()) {
            throw new IllegalArgumentException("One or more target galleries do not exist");
        }
        return rebuild(catalog, galleries, catalog.getPacks());
    }

// 方法作用：解除图库与表情包之间的关联并保存变更（unlinkPack）。
    static EmojiCatalog unlinkPack(
            EmojiCatalog catalog,
            String galleryId,
            String packId) {
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            if (!galleryId.equals(gallery.getId())) {
                galleries.add(gallery);
                continue;
            }
            List<String> references = new ArrayList<>(gallery.getPackIds());
            changed = references.remove(packId);
            galleries.add(copyGallery(gallery, references));
        }
        requireChanged(changed, "The emoji pack is not in this gallery");
        return rebuild(catalog, galleries, catalog.getPacks());
    }

// 方法作用：修改目标图库或表情包的名称并保存变更（renamePack）。
    static EmojiCatalog renamePack(EmojiCatalog catalog, String packId, String name) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            if (packId.equals(pack.getId())) {
                packs.add(new EmojiCatalog.Pack(
                        pack.getId(), name, pack.getSortOrder(), pack.getItems()));
                changed = true;
            } else {
                packs.add(pack);
            }
        }
        requireChanged(changed, "Emoji pack does not exist: " + packId);
        return rebuild(catalog, catalog.getGalleries(), packs);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（removePack）。
    static EmojiCatalog removePack(EmojiCatalog catalog, String packId) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            if (packId.equals(pack.getId())) {
                changed = true;
            } else {
                packs.add(pack);
            }
        }
        requireChanged(changed, "Emoji pack does not exist: " + packId);
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>();
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            List<String> references = new ArrayList<>(gallery.getPackIds());
            references.remove(packId);
            galleries.add(copyGallery(gallery, references));
        }
        return rebuild(catalog, galleries, packs);
    }

// 方法作用：向界面或业务集合中添加新的元素（addItem）。
    static EmojiCatalog addItem(
            EmojiCatalog catalog,
            String packId,
            EmojiCatalog.Item newItem) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            if (packId.equals(pack.getId())) {
                List<EmojiCatalog.Item> items = new ArrayList<>(pack.getItems());
                items.add(newItem);
                packs.add(copyPack(pack, items));
                changed = true;
            } else {
                packs.add(pack);
            }
        }
        requireChanged(changed, "Emoji pack does not exist: " + packId);
        return rebuild(catalog, catalog.getGalleries(), packs);
    }

// 方法作用：替换已有图片或目录记录并保持索引一致（replaceItem）。
    static EmojiCatalog replaceItem(
            EmojiCatalog catalog,
            String itemId,
            EmojiCatalog.Item replacement) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            List<EmojiCatalog.Item> items = new ArrayList<>();
            for (EmojiCatalog.Item item : pack.getItems()) {
                if (itemId.equals(item.getId())) {
                    items.add(replacement);
                    changed = true;
                } else {
                    items.add(item);
                }
            }
            packs.add(copyPack(pack, items));
        }
        requireChanged(changed, "Emoji does not exist: " + itemId);
        return rebuild(catalog, catalog.getGalleries(), packs);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（removeItem）。
    static EmojiCatalog removeItem(EmojiCatalog catalog, String itemId) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            List<EmojiCatalog.Item> items = new ArrayList<>();
            for (EmojiCatalog.Item item : pack.getItems()) {
                if (itemId.equals(item.getId())) {
                    changed = true;
                } else {
                    items.add(item);
                }
            }
            packs.add(copyPack(pack, items));
        }
        requireChanged(changed, "Emoji does not exist: " + itemId);
        return rebuild(catalog, catalog.getGalleries(), packs);
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（removeItems）。
    static EmojiCatalog removeItems(
            EmojiCatalog catalog,
            String packId,
            List<String> itemIds) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>();
        boolean changed = false;
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            List<EmojiCatalog.Item> items = new ArrayList<>();
            for (EmojiCatalog.Item item : pack.getItems()) {
                boolean remove = packId.equals(pack.getId()) && itemIds.contains(item.getId());
                if (remove) {
                    changed = true;
                } else {
                    items.add(item);
                }
            }
            packs.add(copyPack(pack, items));
        }
        requireChanged(changed, "No selected emoji exists in the emoji pack");
        return rebuild(catalog, catalog.getGalleries(), packs);
    }

// 方法作用：处理 rebuild 对应的输入并返回或更新相关结果（rebuild）。
    private static EmojiCatalog rebuild(
            EmojiCatalog catalog,
            List<EmojiCatalog.Gallery> galleries,
            List<EmojiCatalog.Pack> packs) {
        return new EmojiCatalog(catalog.getVersion(), galleries, packs);
    }

// 方法作用：校验前置条件并在不满足时报告明确错误（requireChanged）。
    private static void requireChanged(boolean changed, String error) {
        if (!changed) {
            throw new IllegalArgumentException(error);
        }
    }

// 方法作用：在受控范围内复制输入数据（copyGallery）。
    private static EmojiCatalog.Gallery copyGallery(
            EmojiCatalog.Gallery gallery,
            List<String> packIds) {
        return new EmojiCatalog.Gallery(
                gallery.getId(), gallery.getName(), gallery.getSortOrder(), packIds);
    }

// 方法作用：在受控范围内复制输入数据（copyPack）。
    private static EmojiCatalog.Pack copyPack(
            EmojiCatalog.Pack pack,
            List<EmojiCatalog.Item> items) {
        return new EmojiCatalog.Pack(pack.getId(), pack.getName(), pack.getSortOrder(), items);
    }
}
