package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.catalog.EmojiCatalog;

import java.util.List;

/** Persists the gallery and pack shared by the app browser and input method. */
public final class EmojiSelectionStore {
    private static final String PREFERENCES = "emoji-selection";
    private static final String GALLERY_ID = "gallery-id";
    private static final String PACK_ID = "pack-id";

    private EmojiSelectionStore() {
    }

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

    public static final class Selection {
        private final EmojiCatalog.Gallery gallery;
        private final EmojiCatalog.Pack pack;

        private Selection(EmojiCatalog.Gallery gallery, EmojiCatalog.Pack pack) {
            this.gallery = gallery;
            this.pack = pack;
        }

        public EmojiCatalog.Gallery getGallery() {
            return gallery;
        }

        public EmojiCatalog.Pack getPack() {
            return pack;
        }
    }
}
