package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.util.LruCache;

import com.example.myapplication.catalog.EmojiCatalog;

import java.util.ArrayList;
import java.util.List;

/** Fixed-size local thumbnail grid shared by the app and input method. */
public final class EmojiGridAdapter extends BaseAdapter {
    private final Context context;
    private final int cellHeight;
    private final int targetPixels;
    private final LruCache<String, Bitmap> thumbnails;
    private List<EmojiCatalog.Item> items = new ArrayList<>();

    public EmojiGridAdapter(Context context, int cellHeightDp, int targetSizeDp) {
        this.context = context;
        this.cellHeight = dp(cellHeightDp);
        this.targetPixels = dp(targetSizeDp);
        int cacheKilobytes = (int) Math.min(
                Integer.MAX_VALUE,
                Runtime.getRuntime().maxMemory() / 1024L / 16L);
        this.thumbnails = new LruCache<String, Bitmap>(cacheKilobytes) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };
    }

    public void setItems(List<EmojiCatalog.Item> items) {
        this.items = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public EmojiCatalog.Item getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView image = convertView instanceof ImageView
                ? (ImageView) convertView
                : createImageView();
        EmojiCatalog.Item item = getItem(position);
        image.setContentDescription(item.getName());
        image.setImageDrawable(null);
        try {
            String cacheKey = item.getId() + ":" + item.getRelativePath();
            Bitmap bitmap = thumbnails.get(cacheKey);
            if (bitmap == null) {
                bitmap = decodeThumbnail(
                        EmojiFileStore.getManagedFile(context, item).getAbsolutePath(),
                        targetPixels);
                if (bitmap != null) {
                    thumbnails.put(cacheKey, bitmap);
                }
            }
            if (bitmap == null) {
                image.setImageResource(android.R.drawable.ic_menu_report_image);
                image.setContentDescription(item.getName() + "，图片损坏");
            } else {
                image.setImageBitmap(bitmap);
            }
        } catch (Exception exception) {
            image.setImageResource(android.R.drawable.ic_menu_report_image);
            image.setContentDescription(item.getName() + "，图片不可用");
        }
        return image;
    }

    private ImageView createImageView() {
        ImageView image = new ImageView(context);
        image.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cellHeight));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(6), dp(6), dp(6), dp(6));
        image.setBackgroundColor(Color.TRANSPARENT);
        return image;
    }

    private static Bitmap decodeThumbnail(String path, int targetPixels) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetPixels
                && bounds.outHeight / (sample * 2) >= targetPixels) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
