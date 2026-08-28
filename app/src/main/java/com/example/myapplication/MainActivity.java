package com.example.myapplication;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.catalog.EmojiCatalog;
import com.example.myapplication.catalog.LocalEmojiCatalogRepository;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 类作用：定义 MainActivity，承载所在模块的主要职责。
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_IMAGES = 1001;
    private static final int REQUEST_PICK_DIRECTORY = 1002;
    private static final int REQUEST_READ_IMAGES = 1003;
    private static final int REQUEST_PICK_KEYBOARD_BACKGROUND = 1004;

    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final List<Button> busyControls = new ArrayList<>();
    private final List<Button> galleryControls = new ArrayList<>();
    private final List<Button> packControls = new ArrayList<>();

    private LinearLayout galleryRail;
    private LinearLayout packStrip;
    private GridView emojiGrid;
    private EmojiGridAdapter gridAdapter;
    private LinearLayout batchDeleteBar;
    private TextView batchDeleteCount;
    private Button batchDeleteButton;
    private TextView emptyState;
    private TextView status;
    private EmojiCatalog catalog;
    private String selectedGalleryId;
    private String selectedPackId;
    private String lastGalleryTapId;
    private long lastGalleryTapAt;
    private String lastPackTapId;
    private long lastPackTapAt;
    private boolean batchDeleteMode;
    private String batchDeletePackId;
    private final Set<String> selectedItemIds = new LinkedHashSet<>();
    private boolean importBusy;
    private boolean openDirectoryAfterPermission;

// 方法作用：按生命周期创建并初始化界面或服务状态（onCreate）。
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setContentView(buildMainLayout());
        reloadCatalog(null, null);
    }

// 方法作用：构建并更新用户界面内容（buildMainLayout）。
    private View buildMainLayout() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setBackgroundColor(surfaceColor());

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setBackgroundColor(railColor());
        rail.setPadding(dp(4), dp(6), dp(4), dp(6));
        page.addView(rail, new LinearLayout.LayoutParams(dp(72),
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView galleryLabel = compactLabel("图库");
        rail.addView(galleryLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));

        ScrollView galleryScroll = new ScrollView(this);
        galleryScroll.setFillViewport(true);
        galleryRail = new LinearLayout(this);
        galleryRail.setOrientation(LinearLayout.VERTICAL);
        galleryScroll.addView(galleryRail, matchWrap());
        rail.addView(galleryScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button addGallery = compactButton("+", "新建图库", view -> showCreateGalleryDialog());
        busyControls.add(addGallery);
        rail.addView(addGallery, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        Button settingsButton = compactButton("设置", "图库与输入法设置", view -> showSettings());
        busyControls.add(settingsButton);
        rail.addView(settingsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        page.addView(content, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        HorizontalScrollView packScroll = new HorizontalScrollView(this);
        packScroll.setHorizontalScrollBarEnabled(false);
        packStrip = new LinearLayout(this);
        packStrip.setOrientation(LinearLayout.HORIZONTAL);
        packStrip.setPadding(dp(4), dp(4), dp(4), dp(4));
        packScroll.addView(packStrip, wrapMatch());
        content.addView(packScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        batchDeleteBar = new LinearLayout(this);
        batchDeleteBar.setGravity(Gravity.CENTER_VERTICAL);
        batchDeleteBar.setPadding(dp(8), dp(2), dp(8), dp(2));
        batchDeleteCount = compactLabel("已选择 0 项");
        batchDeleteCount.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        batchDeleteBar.addView(batchDeleteCount, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        Button cancelBatchDelete = compactButton(
                "取消", "退出批量删除模式", view -> exitBatchDeleteMode());
        batchDeleteBar.addView(cancelBatchDelete, new LinearLayout.LayoutParams(dp(80), dp(48)));
        batchDeleteButton = compactButton(
                "删除", "删除已勾选表情包", view -> confirmBatchDeleteItems());
        batchDeleteBar.addView(batchDeleteButton, new LinearLayout.LayoutParams(dp(80), dp(48)));
        busyControls.add(cancelBatchDelete);
        busyControls.add(batchDeleteButton);
        batchDeleteBar.setVisibility(View.GONE);
        content.addView(batchDeleteBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout gridArea = new LinearLayout(this);
        gridArea.setOrientation(LinearLayout.VERTICAL);
        FrameLayout gridFrame = new FrameLayout(this);
        emojiGrid = new GridView(this);
        emojiGrid.setNumColumns(4);
        emojiGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        emojiGrid.setHorizontalSpacing(dp(4));
        emojiGrid.setVerticalSpacing(dp(4));
        emojiGrid.setPadding(dp(6), dp(6), dp(6), dp(6));
        emojiGrid.setClipToPadding(false);
        gridAdapter = new EmojiGridAdapter(this, 92, 76);
        emojiGrid.setAdapter(gridAdapter);
        emojiGrid.setOnItemClickListener((parent, view, position, id) -> {
            EmojiCatalog.Item item = gridAdapter.getItem(position);
            if (batchDeleteMode) {
                toggleBatchDeleteItem(item);
            } else {
                showItemActions(item);
            }
        });
        gridFrame.addView(emojiGrid, matchMatch());

        emptyState = new TextView(this);
        emptyState.setTextColor(secondaryTextColor());
        emptyState.setTextSize(15);
        emptyState.setGravity(Gravity.CENTER);
        gridFrame.addView(emptyState, matchMatch());
        emojiGrid.setEmptyView(emptyState);
        gridArea.addView(gridFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button addImages = compactButton("+", "向当前表情包添加图片", view -> openImagePicker());
        busyControls.add(addImages);
        LinearLayout importBar = new LinearLayout(this);
        importBar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        importBar.setPadding(dp(6), dp(4), dp(6), dp(4));
        importBar.addView(addImages, new LinearLayout.LayoutParams(dp(76), dp(76)));
        gridArea.addView(importBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(84)));
        content.addView(gridArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextColor(secondaryTextColor());
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setPadding(dp(10), 0, dp(10), 0);
        content.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        return page;
    }

// 方法作用：按生命周期释放线程、监听器和其他资源（onDestroy）。
    @Override
    protected void onDestroy() {
        importExecutor.shutdownNow();
        if (gridAdapter != null) {
            gridAdapter.release();
        }
        super.onDestroy();
    }

// 方法作用：在页面恢复时重新加载需要同步的状态（onResume）。
    @Override
    protected void onResume() {
        super.onResume();
        if (status != null && !importBusy) {
            reloadCatalog(selectedGalleryId, selectedPackId);
        }
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（reloadCatalog）。
    private void reloadCatalog(String preferredGalleryId, String preferredPackId) {
        clearBatchDeleteState();
        clearTapTracking();
        try {
            catalog = EmojiFileStore.getCatalog(this);
            EmojiSelectionStore.Selection saved = EmojiSelectionStore.resolve(this, catalog);
            String galleryId = containsGallery(preferredGalleryId)
                    ? preferredGalleryId
                    : saved.getGallery() == null ? null : saved.getGallery().getId();
            selectGallery(galleryId, preferredPackId == null
                    ? saved.getPack() == null ? null : saved.getPack().getId()
                    : preferredPackId);
        } catch (Exception exception) {
            catalog = null;
            selectedGalleryId = null;
            selectedPackId = null;
            galleryRail.removeAllViews();
            packStrip.removeAllViews();
            gridAdapter.setItems(Collections.emptyList());
            emptyState.setText("表情库读取失败");
            status.setText(getString(
                    R.string.catalog_load_failure,
                    readableMessage(exception)));
        }
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（clearTapTracking）。
    private void clearTapTracking() {
        lastGalleryTapId = null;
        lastGalleryTapAt = 0L;
        lastPackTapId = null;
        lastPackTapAt = 0L;
    }

// 方法作用：根据候选条件选择并返回目标项（selectGallery）。
    private void selectGallery(String galleryId, String preferredPackId) {
        if (catalog == null || catalog.getGalleries().isEmpty()) {
            return;
        }
        EmojiCatalog.Gallery gallery = findGallery(galleryId);
        if (gallery == null) {
            gallery = catalog.getGalleries().get(0);
        }
        selectedGalleryId = gallery.getId();
        List<EmojiCatalog.Pack> packs = catalog.getPacksForGallery(selectedGalleryId);
        EmojiCatalog.Pack pack = findPack(packs, preferredPackId);
        if (pack == null && !packs.isEmpty()) {
            pack = packs.get(0);
        }
        selectedPackId = pack == null ? null : pack.getId();
        EmojiSelectionStore.save(this, selectedGalleryId, selectedPackId);
        renderGalleries();
        renderPacks();
        renderGrid();
    }

// 方法作用：根据候选条件选择并返回目标项（selectPack）。
    private void selectPack(String packId) {
        if (catalog == null || !catalog.galleryContainsPack(selectedGalleryId, packId)) {
            return;
        }
        selectedPackId = packId;
        EmojiSelectionStore.save(this, selectedGalleryId, selectedPackId);
        renderPacks();
        renderGrid();
    }

// 方法作用：构建并更新用户界面内容（renderGalleries）。
    private void renderGalleries() {
        galleryRail.removeAllViews();
        galleryControls.clear();
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            Button button = compactButton(
                    gallery.getName(),
                    "选择图库" + gallery.getName(),
                    view -> handleGalleryTap(gallery));
            button.setMaxLines(2);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setTextSize(11);
            styleSelection(button, gallery.getId().equals(selectedGalleryId));
            button.setEnabled(!importBusy);
            galleryControls.add(button);
            galleryRail.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        }
    }

// 方法作用：构建并更新用户界面内容（renderPacks）。
    private void renderPacks() {
        packStrip.removeAllViews();
        packControls.clear();
        List<EmojiCatalog.Pack> packs = catalog.getPacksForGallery(selectedGalleryId);
        for (EmojiCatalog.Pack pack : packs) {
            Button button = compactButton(
                    pack.getName(),
                    "选择表情包" + pack.getName(),
                    view -> handlePackTap(pack));
            button.setMaxLines(2);
            button.setEllipsize(TextUtils.TruncateAt.END);
            button.setTextSize(12);
            styleSelection(button, pack.getId().equals(selectedPackId));
            button.setEnabled(!importBusy);
            packControls.add(button);
            packStrip.addView(button, new LinearLayout.LayoutParams(dp(76), dp(60)));
        }
        if (packs.isEmpty()) {
            TextView empty = compactLabel("暂无表情包");
            empty.setTextColor(secondaryTextColor());
            packStrip.addView(empty, new LinearLayout.LayoutParams(dp(160), dp(60)));
        }
        Button addPack = compactButton("+", "添加表情包", view -> showAddPackMenu());
        packControls.add(addPack);
        addPack.setEnabled(!importBusy);
        packStrip.addView(addPack, new LinearLayout.LayoutParams(dp(60), dp(60)));
    }

// 方法作用：构建并更新用户界面内容（renderGrid）。
    private void renderGrid() {
        EmojiCatalog.Pack pack = selectedPack();
        if (pack == null) {
            gridAdapter.setItems(Collections.emptyList());
            gridAdapter.setSelectionMode(false, Collections.emptySet());
            updateBatchDeleteBar();
            emptyState.setText("当前图库没有表情包\n点击“+ 表情包”添加");
            status.setText(getString(
                    R.string.catalog_empty_status,
                    selectedGalleryName()));
            return;
        }
        gridAdapter.setItems(pack.getItems());
        boolean selectionActive = batchDeleteMode && pack.getId().equals(batchDeletePackId);
        gridAdapter.setSelectionMode(selectionActive,
                selectionActive ? selectedItemIds : Collections.emptySet());
        updateBatchDeleteBar();
        emptyState.setText("当前表情包为空\n点击“+ 图片”导入");
        status.setText(getString(
                R.string.catalog_pack_status,
                selectedGalleryName(),
                pack.getName(),
                pack.getItems().size()));
    }

// 方法作用：显示或打开对应的交互界面（showAddPackMenu）。
    private void showAddPackMenu() {
        if (catalog == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("添加表情包")
                .setItems(new String[]{"新建多个表情包", "将已有表情包加入当前图库"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showBatchCreatePackDialog();
                            } else {
                                showLinkExistingPacksDialog();
                            }
                        })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showBatchCreatePackDialog）。
    private void showBatchCreatePackDialog() {
        EditText input = dialogInput("每行一个表情包名称", "常用\n收藏\n动图", true);
        new AlertDialog.Builder(this)
                .setTitle("新建多个表情包")
                .setView(wrapDialogInput(input))
                .setNegativeButton("取消", null)
                .setPositiveButton("下一步", (dialog, which) -> {
                    List<String> names = nonEmptyLines(input.getText().toString());
                    if (names.isEmpty()) {
                        toast("请至少输入一个名称");
                    } else {
                        showGalleryTargetsDialog(names);
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showGalleryTargetsDialog）。
    private void showGalleryTargetsDialog(List<String> packNames) {
        List<EmojiCatalog.Gallery> galleries = catalog.getGalleries();
        String[] labels = new String[galleries.size()];
        boolean[] checked = new boolean[galleries.size()];
        for (int index = 0; index < galleries.size(); index++) {
            labels[index] = galleries.get(index).getName();
            checked[index] = galleries.get(index).getId().equals(selectedGalleryId);
        }
        new AlertDialog.Builder(this)
                .setTitle("选择目标图库")
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    List<String> targetIds = new ArrayList<>();
                    for (int index = 0; index < checked.length; index++) {
                        if (checked[index]) {
                            targetIds.add(galleries.get(index).getId());
                        }
                    }
                    if (targetIds.isEmpty()) {
                        toast("请至少选择一个图库");
                        return;
                    }
                    try {
                        List<EmojiCatalog.Pack> created = EmojiFileStore.createPacks(
                                this, targetIds, packNames);
                        String preferred = created.isEmpty() ? null : created.get(0).getId();
                        reloadCatalog(selectedGalleryId, preferred);
                    } catch (Exception exception) {
                        operationFailed("创建失败", exception);
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showLinkExistingPacksDialog）。
    private void showLinkExistingPacksDialog() {
        List<EmojiCatalog.Pack> candidates = new ArrayList<>();
        for (EmojiCatalog.Pack pack : catalog.getPacks()) {
            if (!catalog.galleryContainsPack(selectedGalleryId, pack.getId())) {
                candidates.add(pack);
            }
        }
        if (candidates.isEmpty()) {
            toast("没有可加入的其他表情包");
            return;
        }
        String[] labels = new String[candidates.size()];
        boolean[] checked = new boolean[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            labels[index] = candidates.get(index).getName();
        }
        new AlertDialog.Builder(this)
                .setTitle("加入已有表情包")
                .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
                .setNegativeButton("取消", null)
                .setPositiveButton("加入", (dialog, which) -> {
                    List<String> ids = new ArrayList<>();
                    for (int index = 0; index < checked.length; index++) {
                        if (checked[index]) {
                            ids.add(candidates.get(index).getId());
                        }
                    }
                    if (ids.isEmpty()) {
                        toast("请至少选择一个表情包");
                        return;
                    }
                    try {
                        EmojiFileStore.linkPacksToGallery(this, selectedGalleryId, ids);
                        reloadCatalog(selectedGalleryId, ids.get(0));
                    } catch (Exception exception) {
                        operationFailed("加入失败", exception);
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showCreateGalleryDialog）。
    private void showCreateGalleryDialog() {
        showTextDialog("新建图库", "图库名称", "", value -> {
            try {
                EmojiCatalog.Gallery gallery = EmojiFileStore.createGallery(this, value);
                reloadCatalog(gallery.getId(), null);
            } catch (Exception exception) {
                operationFailed("新建失败", exception);
            }
        });
    }

// 方法作用：执行该操作的业务流程并处理结果（handleGalleryTap）。
    private void handleGalleryTap(EmojiCatalog.Gallery gallery) {
        if (batchDeleteMode) {
            exitBatchDeleteMode();
        }
        boolean doubleTap = isDoubleTap(gallery.getId(), true);
        selectGallery(gallery.getId(), null);
        if (doubleTap) {
            showGalleryMenu(gallery);
        }
    }

// 方法作用：执行该操作的业务流程并处理结果（handlePackTap）。
    private void handlePackTap(EmojiCatalog.Pack pack) {
        if (batchDeleteMode) {
            exitBatchDeleteMode();
        }
        boolean doubleTap = isDoubleTap(pack.getId(), false);
        selectPack(pack.getId());
        if (doubleTap) {
            showPackMenu(pack);
        }
    }

    /** 在选择栏重绘后仍保持双击判定的时间和对象一致。 */
// 方法作用：判断当前对象是否满足指定条件（isDoubleTap）。
    private boolean isDoubleTap(String itemId, boolean gallery) {
        long now = SystemClock.uptimeMillis();
        String previousId = gallery ? lastGalleryTapId : lastPackTapId;
        long previousAt = gallery ? lastGalleryTapAt : lastPackTapAt;
        boolean matched = itemId.equals(previousId)
                && now >= previousAt
                && now - previousAt <= ViewConfiguration.getDoubleTapTimeout();
        if (gallery) {
            lastGalleryTapId = matched ? null : itemId;
            lastGalleryTapAt = matched ? 0L : now;
        } else {
            lastPackTapId = matched ? null : itemId;
            lastPackTapAt = matched ? 0L : now;
        }
        return matched;
    }

// 方法作用：显示或打开对应的交互界面（showGalleryMenu）。
    private void showGalleryMenu(EmojiCatalog.Gallery gallery) {
        new AlertDialog.Builder(this)
                .setTitle(gallery.getName())
                .setItems(new String[]{"重命名" + gallery.getName(), "删除图库"}, (dialog, which) -> {
                    if (which == 0) {
                        showTextDialog("重命名图库", "图库名称", gallery.getName(), value -> {
                            try {
                                EmojiFileStore.renameGallery(this, gallery.getId(), value);
                                reloadCatalog(gallery.getId(), selectedPackId);
                            } catch (Exception exception) {
                                operationFailed("重命名失败", exception);
                            }
                        });
                    } else {
                        confirmDeleteGallery(gallery);
                    }
                })
                .show();
    }

// 方法作用：处理 confirmDeleteGallery 对应的输入并返回或更新相关结果（confirmDeleteGallery）。
    private void confirmDeleteGallery(EmojiCatalog.Gallery gallery) {
        new AlertDialog.Builder(this)
                .setTitle("删除图库")
                .setMessage("只删除图库入口，不删除共享表情包；至少保留一个图库。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        EmojiFileStore.deleteGallery(this, gallery.getId());
                        reloadCatalog(null, null);
                    } catch (Exception exception) {
                        operationFailed("删除失败", exception);
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showPackMenu）。
    private void showPackMenu(EmojiCatalog.Pack pack) {
        new AlertDialog.Builder(this)
                .setTitle(pack.getName())
                .setItems(new String[]{"重命名" + pack.getName(), "批量删除表情包", "删除该类"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showRenamePackDialog(pack);
                            } else if (which == 1) {
                                enterBatchDeleteMode(pack);
                            } else {
                                confirmDeletePack(pack);
                            }
                        })
                .show();
    }

// 方法作用：处理 enterBatchDeleteMode 对应的输入并返回或更新相关结果（enterBatchDeleteMode）。
    private void enterBatchDeleteMode(EmojiCatalog.Pack pack) {
        if (pack.getItems().isEmpty()) {
            toast("当前类没有可删除的表情包");
            return;
        }
        batchDeleteMode = true;
        batchDeletePackId = pack.getId();
        selectedItemIds.clear();
        renderGrid();
    }

// 方法作用：切换键盘模式并刷新相关界面（toggleBatchDeleteItem）。
    private void toggleBatchDeleteItem(EmojiCatalog.Item item) {
        if (!selectedItemIds.add(item.getId())) {
            selectedItemIds.remove(item.getId());
        }
        gridAdapter.setSelectionMode(true, selectedItemIds);
        updateBatchDeleteBar();
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（updateBatchDeleteBar）。
    private void updateBatchDeleteBar() {
        if (batchDeleteBar == null) {
            return;
        }
        boolean visible = batchDeleteMode;
        batchDeleteBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        batchDeleteCount.setText("已选择 " + selectedItemIds.size() + " 项");
        batchDeleteButton.setEnabled(!selectedItemIds.isEmpty() && !importBusy);
    }

// 方法作用：处理 exitBatchDeleteMode 对应的输入并返回或更新相关结果（exitBatchDeleteMode）。
    private void exitBatchDeleteMode() {
        if (!batchDeleteMode) {
            return;
        }
        clearBatchDeleteState();
        gridAdapter.setSelectionMode(false, Collections.emptySet());
        updateBatchDeleteBar();
        renderGrid();
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（clearBatchDeleteState）。
    private void clearBatchDeleteState() {
        batchDeleteMode = false;
        batchDeletePackId = null;
        selectedItemIds.clear();
        if (gridAdapter != null) {
            gridAdapter.setSelectionMode(false, Collections.emptySet());
        }
        updateBatchDeleteBar();
    }

// 方法作用：处理 confirmBatchDeleteItems 对应的输入并返回或更新相关结果（confirmBatchDeleteItems）。
    private void confirmBatchDeleteItems() {
        if (selectedItemIds.isEmpty() || batchDeletePackId == null) {
            toast("请至少选择一个表情包");
            return;
        }
        String packId = batchDeletePackId;
        List<String> itemIds = new ArrayList<>(selectedItemIds);
        new AlertDialog.Builder(this)
                .setTitle("删除已勾选表情包")
                .setMessage("只从当前类移除勾选项，不删除手机相册中的源文件，也不影响其他类。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        EmojiFileStore.deleteItems(this, packId, itemIds);
                        exitBatchDeleteMode();
                        reloadCatalog(selectedGalleryId, packId);
                    } catch (Exception exception) {
                        operationFailed("批量删除失败", exception);
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showRenamePackDialog）。
    private void showRenamePackDialog(EmojiCatalog.Pack pack) {
        showTextDialog("重命名表情包", "表情包名称", pack.getName(), value -> {
            try {
                EmojiFileStore.renamePack(this, pack.getId(), value);
                reloadCatalog(selectedGalleryId, pack.getId());
            } catch (Exception exception) {
                operationFailed("重命名失败", exception);
            }
        });
    }

// 方法作用：处理 confirmDeletePack 对应的输入并返回或更新相关结果（confirmDeletePack）。
    private void confirmDeletePack(EmojiCatalog.Pack pack) {
        new AlertDialog.Builder(this)
                .setTitle("删除该类")
                .setMessage("只删除该类及应用托管副本，不删除手机相册源文件，也不影响其他类中的同图；此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        EmojiFileStore.deletePack(this, pack.getId());
                        reloadCatalog(selectedGalleryId, null);
                    } catch (Exception exception) {
                        operationFailed("删除类失败", exception);
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showItemActions）。
    private void showItemActions(EmojiCatalog.Item item) {
        new AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setItems(new String[]{
                                item.getNote().isEmpty() ? "增加备注" : "修改备注",
                                "移除",
                                "查看"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showTextDialog("增加备注", "备注可留空", item.getNote(), value -> {
                                    try {
                                        EmojiFileStore.updateItemNote(this, item.getId(), value);
                                        reloadCatalog(selectedGalleryId, selectedPackId);
                                    } catch (Exception exception) {
                                        operationFailed("保存失败", exception);
                                    }
                                });
                            } else if (which == 1) {
                                confirmDeleteItem(item);
                            } else {
                                showItemDetails(item);
                            }
                        })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showItemDetails）。
    private void showItemDetails(EmojiCatalog.Item item) {
        try {
            LocalEmojiCatalogRepository.StoredEmoji stored = EmojiFileStore.getStoredEmoji(
                    this, item.getId());
            Bitmap bitmap = decodeDetailBitmap(stored.getFile());
            if (bitmap == null) {
                throw new IOException("图片无法解码");
            }
            ZoomableImageView preview = new ZoomableImageView(this);
            preview.setImageBitmap(bitmap);
            preview.setContentDescription(item.getName() + "详情");
            preview.setBackgroundColor(0xff111111);

            FrameLayout previewFrame = new FrameLayout(this);
            previewFrame.setPadding(dp(4), dp(4), dp(4), dp(4));
            previewFrame.addView(preview, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout controls = new LinearLayout(this);
            controls.setGravity(Gravity.CENTER);
            Button zoomOut = compactButton("-", "缩小图片", view -> preview.zoomBy(0.8f));
            Button reset = compactButton("重置", "恢复图片适合窗口大小", view -> preview.resetZoom());
            Button zoomIn = compactButton("+", "放大图片", view -> preview.zoomBy(1.25f));
            controls.addView(zoomOut, new LinearLayout.LayoutParams(dp(64), dp(48)));
            controls.addView(reset, new LinearLayout.LayoutParams(dp(84), dp(48)));
            controls.addView(zoomIn, new LinearLayout.LayoutParams(dp(64), dp(48)));

            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            TextView noteView = compactLabel(item.getNote().isEmpty() ? "无备注" : item.getNote());
            noteView.setTextColor(secondaryTextColor());
            noteView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            panel.addView(previewFrame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
            panel.addView(controls, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            panel.addView(noteView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            new AlertDialog.Builder(this)
                    .setTitle(item.getName())
                    .setView(panel)
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Exception exception) {
            operationFailed("查看失败", exception);
        }
    }

// 方法作用：解码输入内容并生成可用对象（decodeDetailBitmap）。
    private static Bitmap decodeDetailBitmap(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 2048) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

// 方法作用：处理 confirmDeleteItem 对应的输入并返回或更新相关结果（confirmDeleteItem）。
    private void confirmDeleteItem(EmojiCatalog.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("移除表情")
                .setMessage("只从当前类移除“" + item.getName() + "”，不删除手机相册源文件，也不影响其他类中的同图。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    try {
                        EmojiFileStore.deleteItem(this, item.getId());
                        reloadCatalog(selectedGalleryId, selectedPackId);
                    } catch (Exception exception) {
                        operationFailed("移除失败", exception);
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（openImagePicker）。
    private void openImagePicker() {
        if (selectedPack() == null) {
            toast("请先添加并选择一个表情包");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_PICK_IMAGES);
    }

// 方法作用：显示或打开对应的交互界面（openKeyboardBackgroundPicker）。
    private void openKeyboardBackgroundPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_KEYBOARD_BACKGROUND);
    }

// 方法作用：校验并持久化用户提供的数据（saveKeyboardBackground）。
    private void saveKeyboardBackground(Uri source) {
        setImportBusy(true, "正在保存键盘背景…");
        importExecutor.execute(() -> {
            try {
                KeyboardBackgroundStore.save(this, source);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setImportBusy(false, null);
                    toast("键盘背景已更新，重新打开输入法后生效");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    setImportBusy(false, null);
                    operationFailed("保存键盘背景失败", exception);
                });
            }
        });
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（clearKeyboardBackground）。
    private void clearKeyboardBackground() {
        try {
            boolean removed = KeyboardBackgroundStore.clear(this);
            toast(removed ? "已恢复默认键盘背景" : "当前已经是默认键盘背景");
        } catch (Exception exception) {
            operationFailed("恢复默认背景失败", exception);
        }
    }

// 方法作用：处理 requestDirectoryImport 对应的输入并返回或更新相关结果（requestDirectoryImport）。
    private void requestDirectoryImport() {
        if (selectedPack() == null) {
            toast("请先添加并选择一个表情包");
            return;
        }
        if (needsLegacyReadPermission()) {
            openDirectoryAfterPermission = true;
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_IMAGES);
        } else {
            openDirectoryPicker();
        }
    }

// 方法作用：处理 requestSharedImageAccess 对应的输入并返回或更新相关结果（requestSharedImageAccess）。
    private void requestSharedImageAccess() {
        if (needsLegacyReadPermission()) {
            openDirectoryAfterPermission = false;
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_IMAGES);
        } else {
            toast("当前系统使用文件选择器逐项授权；受保护目录无法通过普通权限开放");
        }
    }

// 方法作用：处理 needsLegacyReadPermission 对应的输入并返回或更新相关结果（needsLegacyReadPermission）。
    private boolean needsLegacyReadPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED;
    }

// 方法作用：显示或打开对应的交互界面（openDirectoryPicker）。
    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/root/primary"));
        }
        startActivityForResult(intent, REQUEST_PICK_DIRECTORY);
    }

// 方法作用：处理 onRequestPermissionsResult 对应的输入并返回或更新相关结果（onRequestPermissionsResult）。
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_READ_IMAGES) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        toast(granted
                ? "共享图片读取权限已获得"
                : "权限未获得，仍可通过系统选择器授权可见目录");
        if (openDirectoryAfterPermission) {
            openDirectoryAfterPermission = false;
            openDirectoryPicker();
        }
    }

// 方法作用：处理外部选择器返回的结果并分派后续操作（onActivityResult）。
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_IMAGES) {
            List<Uri> sources = extractUris(data);
            if (!sources.isEmpty()) {
                runImport(sources, selectedPackId);
            }
        } else if (requestCode == REQUEST_PICK_KEYBOARD_BACKGROUND
                && data.getData() != null) {
            saveKeyboardBackground(data.getData());
        } else if (requestCode == REQUEST_PICK_DIRECTORY && data.getData() != null) {
            Uri treeUri = data.getData();
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // The temporary result grant is enough for the immediate private copy.
                }
            }
            runDirectoryImport(treeUri, selectedPackId);
        }
    }

// 方法作用：执行该操作的业务流程并处理结果（runDirectoryImport）。
    private void runDirectoryImport(Uri treeUri, String packId) {
        setImportBusy(true, "正在读取授权目录…");
        importExecutor.execute(() -> {
            try {
                List<Uri> sources = EmojiFileStore.listImageDocuments(this, treeUri);
                if (sources.isEmpty()) {
                    throw new IOException("所选目录中没有可导入的图片");
                }
                showImportResult(
                        EmojiFileStore.importImages(this, sources, packId),
                        packId);
            } catch (Exception exception) {
                showImportFailure(exception);
            }
        });
    }

// 方法作用：执行该操作的业务流程并处理结果（runImport）。
    private void runImport(List<Uri> sources, String packId) {
        setImportBusy(true, "正在校验并导入图片…");
        // 导入和图片解码放到单线程执行器，避免阻塞 Android 主线程导致界面无响应。
        importExecutor.execute(() -> {
            try {
                showImportResult(EmojiFileStore.importImages(this, sources, packId), packId);
            } catch (Exception exception) {
                showImportFailure(exception);
            }
        });
    }

// 方法作用：显示或打开对应的交互界面（showImportResult）。
    private void showImportResult(EmojiFileStore.BatchImportResult result, String packId) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setImportBusy(false, null);
            reloadCatalog(selectedGalleryId, packId);
            String message = "已导入 " + result.getImportedCount()
                    + "，重复 " + result.getDuplicateCount()
                    + "，失败 " + result.getFailures().size();
            if (!result.getFailures().isEmpty()) {
                EmojiFileStore.ImportFailure failure = result.getFailures().get(0);
                message += "\n" + failure.getDisplayName() + "：" + failure.getReason();
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

// 方法作用：显示或打开对应的交互界面（showImportFailure）。
    private void showImportFailure(Exception exception) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setImportBusy(false, null);
            operationFailed("导入失败", exception);
        });
    }

// 方法作用：更新对象状态或注册回调（setImportBusy）。
    private void setImportBusy(boolean busy, String message) {
        importBusy = busy;
        for (Button button : busyControls) {
            button.setEnabled(!busy);
        }
        for (Button button : galleryControls) {
            button.setEnabled(!busy);
        }
        for (Button button : packControls) {
            button.setEnabled(!busy);
        }
        emojiGrid.setEnabled(!busy);
        if (message != null) {
            status.setText(message);
        } else if (!busy) {
            renderGrid();
        }
    }

// 方法作用：显示或打开对应的交互界面（showSettings）。
    private void showSettings() {
        new AlertDialog.Builder(this)
                .setTitle("设置与导入")
                .setItems(new String[]{
                        "选择键盘背景",
                        "恢复默认键盘背景",
                        "从授权目录导入",
                        "申请共享图片读取权限",
                        "打开系统输入法设置",
                        "弹出输入法选择器",
                        "应用内接收诊断",
                        "云端"
                }, (dialog, which) -> {
                    if (which == 0) {
                        openKeyboardBackgroundPicker();
                    } else if (which == 1) {
                        clearKeyboardBackground();
                    } else if (which == 2) {
                        requestDirectoryImport();
                    } else if (which == 3) {
                        requestSharedImageAccess();
                    } else if (which == 4) {
                        startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                    } else if (which == 5) {
                        InputMethodManager manager = (InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                        if (manager != null) {
                            manager.showInputMethodPicker();
                        }
                    } else if (which == 6) {
                        showReceiveDiagnostics();
                    } else {
                        startActivity(new Intent(this, CloudActivity.class));
                    }
                })
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showReceiveDiagnostics）。
    private void showReceiveDiagnostics() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), 0, dp(18), dp(12));
        TextView result = new TextView(this);
        result.setText("等待输入法提交图片");
        RichContentEditText input = new RichContentEditText(this);
        input.setHint("点击此处并切换到本地表情输入法");
        input.setMinLines(3);
        ImageView received = new ImageView(this);
        received.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        input.setListener(new RichContentEditText.Listener() {
// 方法作用：处理 onImageReceived 对应的输入并返回或更新相关结果（onImageReceived）。
            @Override
            public void onImageReceived(Bitmap bitmap, String mimeType) {
                received.setImageBitmap(bitmap);
                result.setText(getString(R.string.local_receive_success, mimeType));
            }

// 方法作用：处理 onImageRejected 对应的输入并返回或更新相关结果（onImageRejected）。
            @Override
            public void onImageRejected(String reason) {
                result.setText(getString(R.string.local_receive_failure, reason));
            }
        });
        panel.addView(input, matchWrap());
        panel.addView(result, matchWrap());
        panel.addView(received, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));
        new AlertDialog.Builder(this)
                .setTitle("应用内接收诊断")
                .setView(panel)
                .setPositiveButton("关闭", null)
                .show();
    }

// 方法作用：显示或打开对应的交互界面（showTextDialog）。
    private void showTextDialog(
            String title,
            String hint,
            String initial,
            TextConsumer consumer) {
        EditText input = dialogInput(hint, initial, false);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrapDialogInput(input))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) ->
                        consumer.accept(input.getText().toString()))
                .show();
    }

// 方法作用：处理 dialogInput 对应的输入并返回或更新相关结果（dialogInput）。
    private EditText dialogInput(String hint, String initial, boolean multiline) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(initial);
        input.setSelection(input.length());
        if (multiline) {
            input.setMinLines(4);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        return input;
    }

// 方法作用：创建匹配父容器尺寸的布局参数（wrapDialogInput）。
    private View wrapDialogInput(EditText input) {
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), 0, dp(20), 0);
        holder.addView(input, matchWrap());
        return holder;
    }

// 方法作用：根据候选条件选择并返回目标项（selectedGallery）。
    private EmojiCatalog.Gallery selectedGallery() {
        return findGallery(selectedGalleryId);
    }

// 方法作用：根据候选条件选择并返回目标项（selectedPack）。
    private EmojiCatalog.Pack selectedPack() {
        return catalog == null ? null : catalog.getPack(selectedPackId);
    }

// 方法作用：根据输入条件查询并返回匹配结果（findGallery）。
    private EmojiCatalog.Gallery findGallery(String galleryId) {
        if (catalog != null && galleryId != null) {
            for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
                if (galleryId.equals(gallery.getId())) {
                    return gallery;
                }
            }
        }
        return null;
    }

// 方法作用：处理 containsGallery 对应的输入并返回或更新相关结果（containsGallery）。
    private boolean containsGallery(String galleryId) {
        return findGallery(galleryId) != null;
    }

// 方法作用：根据输入条件查询并返回匹配结果（findPack）。
    private static EmojiCatalog.Pack findPack(List<EmojiCatalog.Pack> packs, String packId) {
        if (packId != null) {
            for (EmojiCatalog.Pack pack : packs) {
                if (packId.equals(pack.getId())) {
                    return pack;
                }
            }
        }
        return null;
    }

// 方法作用：根据候选条件选择并返回目标项（selectedGalleryName）。
    private String selectedGalleryName() {
        EmojiCatalog.Gallery gallery = selectedGallery();
        return gallery == null ? "无图库" : gallery.getName();
    }

// 方法作用：处理 nonEmptyLines 对应的输入并返回或更新相关结果（nonEmptyLines）。
    private static List<String> nonEmptyLines(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                result.add(line.trim());
            }
        }
        return result;
    }

// 方法作用：处理 extractUris 对应的输入并返回或更新相关结果（extractUris）。
    private static List<Uri> extractUris(Intent data) {
        List<Uri> sources = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null && !sources.contains(uri)) {
                    sources.add(uri);
                }
            }
        } else if (data.getData() != null) {
            sources.add(data.getData());
        }
        return sources;
    }

// 方法作用：根据选择状态更新控件的颜色和样式（styleSelection）。
    private void styleSelection(Button button, boolean selected) {
        button.setBackgroundColor(selected ? selectedColor() : railColor());
        button.setTextColor(selected ? selectedTextColor() : primaryTextColor());
    }

// 方法作用：处理 compactButton 对应的输入并返回或更新相关结果（compactButton）。
    private Button compactButton(String text, String description, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setOnClickListener(listener);
        return button;
    }

// 方法作用：处理 compactLabel 对应的输入并返回或更新相关结果（compactLabel）。
    private TextView compactLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(primaryTextColor());
        label.setGravity(Gravity.CENTER);
        label.setTextSize(12);
        return label;
    }

// 方法作用：处理 surfaceColor 对应的输入并返回或更新相关结果（surfaceColor）。
    private int surfaceColor() {
        return isNightMode() ? 0xff121212 : 0xfffafafa;
    }

// 方法作用：处理 railColor 对应的输入并返回或更新相关结果（railColor）。
    private int railColor() {
        return isNightMode() ? 0xff242424 : 0xfff0f1f3;
    }

// 方法作用：根据候选条件选择并返回目标项（selectedColor）。
    private int selectedColor() {
        return isNightMode() ? 0xff35506f : 0xffdbeafe;
    }

// 方法作用：根据候选条件选择并返回目标项（selectedTextColor）。
    private int selectedTextColor() {
        return isNightMode() ? 0xffffffff : 0xff12345b;
    }

// 方法作用：处理 primaryTextColor 对应的输入并返回或更新相关结果（primaryTextColor）。
    private int primaryTextColor() {
        return isNightMode() ? 0xfff2f2f2 : 0xff202124;
    }

// 方法作用：处理 secondaryTextColor 对应的输入并返回或更新相关结果（secondaryTextColor）。
    private int secondaryTextColor() {
        return isNightMode() ? 0xffb7b7b7 : 0xff686b70;
    }

// 方法作用：判断当前对象是否满足指定条件（isNightMode）。
    private boolean isNightMode() {
        int mask = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

// 方法作用：在相关数据表示之间进行转换（toast）。
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

// 方法作用：处理 operationFailed 对应的输入并返回或更新相关结果（operationFailed）。
    private void operationFailed(String prefix, Exception exception) {
        toast(prefix + "：" + readableMessage(exception));
    }

// 方法作用：从输入源读取并转换数据（readableMessage）。
    private static String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "未知错误" : message.trim();
    }

// 方法作用：创建匹配父容器尺寸的布局参数（matchWrap）。
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

// 方法作用：创建匹配父容器尺寸的布局参数（wrapMatch）。
    private LinearLayout.LayoutParams wrapMatch() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

// 方法作用：创建匹配父容器尺寸的布局参数（matchMatch）。
    private FrameLayout.LayoutParams matchMatch() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

// 方法作用：处理 dp 对应的输入并返回或更新相关结果（dp）。
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

// 类作用：定义 TextConsumer，承载所在模块的主要职责。
    private interface TextConsumer {
// 方法作用：处理 accept 对应的输入并返回或更新相关结果（accept）。
        void accept(String value);
    }
}
