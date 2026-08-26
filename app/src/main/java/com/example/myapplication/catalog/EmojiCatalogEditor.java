package com.example.myapplication.catalog;

import java.util.ArrayList;
import java.util.List;

/** Rebuilds immutable catalog snapshots for gallery references, packs and items. */
final class EmojiCatalogEditor {
    private EmojiCatalogEditor() {
    }

    static EmojiCatalog addGallery(EmojiCatalog catalog, EmojiCatalog.Gallery gallery) {
        List<EmojiCatalog.Gallery> galleries = new ArrayList<>(catalog.getGalleries());
        galleries.add(gallery);
        return rebuild(catalog, galleries, catalog.getPacks());
    }

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

    static EmojiCatalog addPack(
            EmojiCatalog catalog,
            EmojiCatalog.Pack newPack,
            List<String> galleryIds) {
        List<EmojiCatalog.Pack> packs = new ArrayList<>(catalog.getPacks());
        packs.add(newPack);
        EmojiCatalog withPack = rebuild(catalog, catalog.getGalleries(), packs);
        return linkPack(withPack, newPack.getId(), galleryIds);
    }

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

    private static EmojiCatalog rebuild(
            EmojiCatalog catalog,
            List<EmojiCatalog.Gallery> galleries,
            List<EmojiCatalog.Pack> packs) {
        return new EmojiCatalog(catalog.getVersion(), galleries, packs);
    }

    private static void requireChanged(boolean changed, String error) {
        if (!changed) {
            throw new IllegalArgumentException(error);
        }
    }

    private static EmojiCatalog.Gallery copyGallery(
            EmojiCatalog.Gallery gallery,
            List<String> packIds) {
        return new EmojiCatalog.Gallery(
                gallery.getId(), gallery.getName(), gallery.getSortOrder(), packIds);
    }

    private static EmojiCatalog.Pack copyPack(
            EmojiCatalog.Pack pack,
            List<EmojiCatalog.Item> items) {
        return new EmojiCatalog.Pack(pack.getId(), pack.getName(), pack.getSortOrder(), items);
    }
}
