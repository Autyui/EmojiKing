package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.util.LruCache;

import com.example.myapplication.catalog.EmojiCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**表情网格适配器，把 EmojiCatalog.Item（表情数据）渲染成界面上的一个个格子，并管理图片的异步加载和内存缓存*/
public final class EmojiGridAdapter extends BaseAdapter {
    private final Context context;
    private final int cellHeight;
    private final int targetPixels;
    private final LruCache<String, Bitmap> thumbnails;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService decodeExecutor = Executors.newFixedThreadPool(2);
    private final Set<String> pendingKeys = new HashSet<>();
    private final Set<String> brokenKeys = new HashSet<>();
    private List<EmojiCatalog.Item> items = new ArrayList<>();
    private boolean selectionMode;
    private Set<String> selectedIds = Collections.emptySet();
    private boolean refreshScheduled;
    private boolean released;

    public EmojiGridAdapter(Context context, int cellHeightDp, int targetSizeDp) {
        this.context = context;
        this.cellHeight = dp(cellHeightDp);
        this.targetPixels = dp(targetSizeDp);
        int cacheKilobytes = (int) Math.min(
                Integer.MAX_VALUE,
                Runtime.getRuntime().maxMemory() / 1024L / 16L);
        this.thumbnails = new LruCache<String, Bitmap>(cacheKilobytes) {
// 方法作用：返回缓存位图占用的内存大小（sizeOf）。
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };
    }

// 方法作用：更新对象状态或注册回调（setItems）。
    public void setItems(List<EmojiCatalog.Item> items) {
        this.items = new ArrayList<>(items);
        notifyDataSetChanged();
    }

// 方法作用：处理 release 对应的输入并返回或更新相关结果（release）。
    public void release() {
        released = true;
        decodeExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        pendingKeys.clear();
    }

// 方法作用：更新对象状态或注册回调（setSelectionMode）。
    public void setSelectionMode(boolean enabled, Set<String> selectedIds) {
        selectionMode = enabled;
        this.selectedIds = enabled
                ? new HashSet<>(selectedIds)
                : Collections.emptySet();
        notifyDataSetChanged();
    }

// 方法作用：读取并返回持久化或运行时状态（getCount）。
    @Override
    public int getCount() {
        return items.size();
    }

// 方法作用：读取并返回持久化或运行时状态（getItem）。
    @Override
    public EmojiCatalog.Item getItem(int position) {
        return items.get(position);
    }

// 方法作用：读取并返回持久化或运行时状态（getItemId）。
    @Override
    public long getItemId(int position) {
        return items.get(position).getId().hashCode();
    }

// 方法作用：读取并返回持久化或运行时状态（getView）。
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        FrameLayout cell = convertView instanceof FrameLayout
                ? (FrameLayout) convertView
                : createCell();
        ImageView image = (ImageView) cell.getChildAt(0);
        CheckBox checkBox = (CheckBox) cell.getChildAt(1);
        EmojiCatalog.Item item = getItem(position);
        image.setContentDescription(item.getName());
        image.setImageDrawable(null);
        String cacheKey = item.getId() + ":" + item.getRelativePath() + ":" + targetPixels;
        Bitmap bitmap = thumbnails.get(cacheKey);
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        } else if (brokenKeys.contains(cacheKey)) {
            image.setImageResource(android.R.drawable.ic_menu_report_image);
            image.setContentDescription(item.getName() + "，图片不可用");
        } else {
            image.setImageResource(android.R.drawable.ic_menu_gallery);
            scheduleThumbnail(item, cacheKey);
        }
        boolean selected = selectedIds.contains(item.getId());
        checkBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        checkBox.setChecked(selected);
        checkBox.setContentDescription(item.getName() + (selected ? "，已勾选" : "，未勾选"));
        cell.setBackgroundColor(selectionMode && selected ? 0xffdbeafe : Color.TRANSPARENT);
        cell.setContentDescription(item.getName() + (selectionMode ? "，点击切换勾选" : ""));
        return cell;
    }

// 方法作用：处理 scheduleThumbnail 对应的输入并返回或更新相关结果（scheduleThumbnail）。
    private void scheduleThumbnail(EmojiCatalog.Item item, String cacheKey) {
        if (released || !pendingKeys.add(cacheKey)) {
            return;
        }
        decodeExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decodeThumbnail(
                        EmojiFileStore.getManagedFile(context, item).getAbsolutePath(),
                        targetPixels);
            } catch (Exception ignored) {
                // The main thread displays the unavailable-image state.
            }
            Bitmap result = bitmap;
            mainHandler.post(() -> completeThumbnail(cacheKey, result));
        });
    }

// 方法作用：处理 completeThumbnail 对应的输入并返回或更新相关结果（completeThumbnail）。
    private void completeThumbnail(String cacheKey, Bitmap bitmap) {
        pendingKeys.remove(cacheKey);
        if (released) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            return;
        }
        if (bitmap == null) {
            brokenKeys.add(cacheKey);
        } else {
            thumbnails.put(cacheKey, bitmap);
        }
        if (!refreshScheduled) {
            refreshScheduled = true;
            mainHandler.postDelayed(() -> {
                refreshScheduled = false;
                if (!released) {
                    notifyDataSetChanged();
                }
            }, 16L);
        }
    }

// 方法作用：创建并返回新的业务对象或界面对象（createCell）。
    private FrameLayout createCell() {
        FrameLayout cell = new FrameLayout(context);
        cell.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                cellHeight));

        ImageView image = createImageView();
        cell.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        CheckBox checkBox = new CheckBox(context);
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        checkBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
                dp(36), dp(36), Gravity.TOP | Gravity.END);
        cell.addView(checkBox, checkParams);
        return cell;
    }

// 方法作用：创建并返回新的业务对象或界面对象（createImageView）。
    private ImageView createImageView() {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(6), dp(6), dp(6), dp(6));
        image.setBackgroundColor(Color.TRANSPARENT);
        return image;
    }

// 方法作用：解码输入内容并生成可用对象（decodeThumbnail）。
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

// 方法作用：处理 dp 对应的输入并返回或更新相关结果（dp）。
    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
