package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.catalog.EmojiCatalog;

import java.util.List;

/** 持久化应用浏览页与输入法共享的图库和表情包选择。 */
// 类作用：定义 EmojiSelectionStore，承载所在模块的主要职责。
public final class EmojiSelectionStore {
    private static final String PREFERENCES = "emoji-selection";
    private static final String GALLERY_ID = "gallery-id";
    private static final String PACK_ID = "pack-id";

// 方法作用：初始化 EmojiSelectionStore 对象并建立其运行所需状态。
    private EmojiSelectionStore() {
    }

// 方法作用：处理 resolve 对应的输入并返回或更新相关结果（resolve）。
    public static Selection resolve(Context context, EmojiCatalog catalog) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, 0);
        String requestedGallery = preferences.getString(GALLERY_ID, null);
        EmojiCatalog.Gallery gallery = findGallery(catalog, requestedGallery);
        if (gallery == null && !catalog.getGalleries().isEmpty()) {
            gallery = catalog.getGalleries().get(0);
        }
        if (gallery == null) {
            return new Selection(null, null);
        }

        List<EmojiCatalog.Pack> packs = catalog.getPacksForGallery(gallery.getId());
        String requestedPack = preferences.getString(PACK_ID, null);
        EmojiCatalog.Pack pack = findPack(packs, requestedPack);
        if (pack == null && !packs.isEmpty()) {
            pack = packs.get(0);
        }
        return new Selection(gallery, pack);
    }

// 方法作用：校验并持久化用户提供的数据（save）。
    public static void save(Context context, String galleryId, String packId) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFERENCES, 0).edit();
        if (galleryId == null) {
            editor.remove(GALLERY_ID);
        } else {
            editor.putString(GALLERY_ID, galleryId);
        }
        if (packId == null) {
            editor.remove(PACK_ID);
        } else {
            editor.putString(PACK_ID, packId);
        }
        editor.apply();
    }

// 方法作用：根据输入条件查询并返回匹配结果（findGallery）。
    private static EmojiCatalog.Gallery findGallery(EmojiCatalog catalog, String galleryId) {
        if (galleryId != null) {
            for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
                if (galleryId.equals(gallery.getId())) {
                    return gallery;
                }
            }
        }
        return null;
    }

// 方法作用：根据输入条件查询并返回匹配结果（findPack）。
    private static EmojiCatalog.Pack findPack(
            List<EmojiCatalog.Pack> packs,
            String packId) {
        if (packId != null) {
            for (EmojiCatalog.Pack pack : packs) {
                if (packId.equals(pack.getId())) {
                    return pack;
                }
            }
        }
        return null;
    }

// 类作用：定义 Selection，承载所在模块的主要职责。
    public static final class Selection {
        private final EmojiCatalog.Gallery gallery;
        private final EmojiCatalog.Pack pack;

// 方法作用：初始化 Selection 对象并建立其运行所需状态。
        private Selection(EmojiCatalog.Gallery gallery, EmojiCatalog.Pack pack) {
            this.gallery = gallery;
            this.pack = pack;
        }

// 方法作用：读取并返回持久化或运行时状态（getGallery）。
        public EmojiCatalog.Gallery getGallery() {
            return gallery;
        }

// 方法作用：读取并返回持久化或运行时状态（getPack）。
        public EmojiCatalog.Pack getPack() {
            return pack;
        }
    }
}
