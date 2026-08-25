package com.example.myapplication;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_IMAGES = 1001;
    private static final int REQUEST_PICK_DIRECTORY = 1002;
    private static final int REQUEST_READ_IMAGES = 1003;

    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final List<Button> busyControls = new ArrayList<>();
    private final List<Button> galleryControls = new ArrayList<>();
    private final List<Button> packControls = new ArrayList<>();

    private LinearLayout galleryRail;
    private LinearLayout packStrip;
    private GridView emojiGrid;
    private EmojiGridAdapter gridAdapter;
    private TextView emptyState;
    private TextView status;
    private EmojiCatalog catalog;
    private String selectedGalleryId;
    private String selectedPackId;
    private boolean importBusy;
    private boolean openDirectoryAfterPermission;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setContentView(buildMainLayout());
        reloadCatalog(null, null);
    }

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

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(6), 0, dp(6), dp(4));
        Button addPack = compactButton("+ 表情包", "添加表情包", view -> showAddPackMenu());
        Button manageGallery = compactButton("图库", "管理当前图库", view -> showGalleryMenu());
        Button managePack = compactButton("表情包", "管理当前表情包", view -> showPackMenu());
        Button addImages = compactButton("+ 图片", "向当前表情包添加图片", view -> openImagePicker());
        busyControls.add(addPack);
        busyControls.add(manageGallery);
        busyControls.add(managePack);
        busyControls.add(addImages);
        actions.addView(addPack, weightedWrap());
        actions.addView(manageGallery, weightedWrap());
        actions.addView(managePack, weightedWrap());
        actions.addView(addImages, weightedWrap());
        content.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

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
        emojiGrid.setOnItemClickListener((parent, view, position, id) ->
                showItemActions(gridAdapter.getItem(position)));
        gridFrame.addView(emojiGrid, matchMatch());

        emptyState = new TextView(this);
        emptyState.setTextColor(secondaryTextColor());
        emptyState.setTextSize(15);
        emptyState.setGravity(Gravity.CENTER);
        gridFrame.addView(emptyState, matchMatch());
        emojiGrid.setEmptyView(emptyState);
        content.addView(gridFrame, new LinearLayout.LayoutParams(
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

    @Override
    protected void onDestroy() {
        importExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null && !importBusy) {
            reloadCatalog(selectedGalleryId, selectedPackId);
        }
    }

    private void reloadCatalog(String preferredGalleryId, String preferredPackId) {
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

    private void selectPack(String packId) {
        if (catalog == null || !catalog.galleryContainsPack(selectedGalleryId, packId)) {
            return;
        }
        selectedPackId = packId;
        EmojiSelectionStore.save(this, selectedGalleryId, selectedPackId);
        renderPacks();
        renderGrid();
    }

    private void renderGalleries() {
        galleryRail.removeAllViews();
        galleryControls.clear();
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            Button button = compactButton(
                    gallery.getName(),
                    "选择图库" + gallery.getName(),
                    view -> selectGallery(gallery.getId(), null));
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

    private void renderPacks() {
        packStrip.removeAllViews();
        packControls.clear();
        List<EmojiCatalog.Pack> packs = catalog.getPacksForGallery(selectedGalleryId);
        for (EmojiCatalog.Pack pack : packs) {
            Button button = compactButton(
                    pack.getName(),
                    "选择表情包" + pack.getName(),
                    view -> selectPack(pack.getId()));
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
    }

    private void renderGrid() {
        EmojiCatalog.Pack pack = selectedPack();
        if (pack == null) {
            gridAdapter.setItems(Collections.emptyList());
            emptyState.setText("当前图库没有表情包\n点击“+ 表情包”添加");
            status.setText(getString(
                    R.string.catalog_empty_status,
                    selectedGalleryName()));
            return;
        }
        gridAdapter.setItems(pack.getItems());
        emptyState.setText("当前表情包为空\n点击“+ 图片”导入");
        status.setText(getString(
                R.string.catalog_pack_status,
                selectedGalleryName(),
                pack.getName(),
                pack.getItems().size()));
    }

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

    private void showGalleryMenu() {
        EmojiCatalog.Gallery gallery = selectedGallery();
        if (gallery == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(gallery.getName())
                .setItems(new String[]{"重命名图库", "删除图库"}, (dialog, which) -> {
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

    private void showPackMenu() {
        EmojiCatalog.Pack pack = selectedPack();
        if (pack == null) {
            toast("当前图库没有表情包");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(pack.getName())
                .setItems(new String[]{"重命名", "从当前图库移除", "彻底删除表情包"},
                        (dialog, which) -> {
                            if (which == 0) {
                                showRenamePackDialog(pack);
                            } else if (which == 1) {
                                confirmUnlinkPack(pack);
                            } else {
                                confirmDeletePack(pack);
                            }
                        })
                .show();
    }

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

    private void confirmUnlinkPack(EmojiCatalog.Pack pack) {
        new AlertDialog.Builder(this)
                .setTitle("从图库移除")
                .setMessage("表情包会从当前图库移除，但仍可再次加入其他图库。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    try {
                        EmojiFileStore.unlinkPackFromGallery(
                                this, selectedGalleryId, pack.getId());
                        reloadCatalog(selectedGalleryId, null);
                    } catch (Exception exception) {
                        operationFailed("移除失败", exception);
                    }
                })
                .show();
    }

    private void confirmDeletePack(EmojiCatalog.Pack pack) {
        new AlertDialog.Builder(this)
                .setTitle("彻底删除表情包")
                .setMessage("将从所有图库删除该表情包及其图片。此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        EmojiFileStore.deletePack(this, pack.getId());
                        reloadCatalog(selectedGalleryId, null);
                    } catch (Exception exception) {
                        operationFailed("删除失败", exception);
                    }
                })
                .show();
    }

    private void showItemActions(EmojiCatalog.Item item) {
        String note = item.getNote().isEmpty() ? "无备注" : item.getNote();
        new AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setMessage(note)
                .setItems(new String[]{"分享测试", "编辑备注", "删除表情"},
                        (dialog, which) -> {
                            if (which == 0) {
                                shareItem(item);
                            } else if (which == 1) {
                                showTextDialog("编辑备注", "备注可留空", item.getNote(), value -> {
                                    try {
                                        EmojiFileStore.updateItemNote(this, item.getId(), value);
                                        reloadCatalog(selectedGalleryId, selectedPackId);
                                    } catch (Exception exception) {
                                        operationFailed("保存失败", exception);
                                    }
                                });
                            } else {
                                confirmDeleteItem(item);
                            }
                        })
                .show();
    }

    private void confirmDeleteItem(EmojiCatalog.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("删除表情")
                .setMessage("确定删除“" + item.getName() + "”？表情库中必须至少保留一张图片。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    try {
                        EmojiFileStore.deleteItem(this, item.getId());
                        reloadCatalog(selectedGalleryId, selectedPackId);
                    } catch (Exception exception) {
                        operationFailed("删除失败", exception);
                    }
                })
                .show();
    }

    private void shareItem(EmojiCatalog.Item item) {
        try {
            LocalEmojiCatalogRepository.StoredEmoji stored = EmojiFileStore.getStoredEmoji(
                    this, item.getId());
            ImageShareSender.Result result = ImageShareSender.send(
                    this, null, EmojiFileStore.getUri(this, stored), item.getMimeType());
            toast(result == ImageShareSender.Result.CHOOSER_STARTED
                    ? "已打开系统分享选择器"
                    : result == ImageShareSender.Result.TARGET_STARTED
                    ? "已打开目标应用分享"
                    : "没有可用的图片分享入口");
        } catch (Exception exception) {
            operationFailed("图片无法分享", exception);
        }
    }

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

    private boolean needsLegacyReadPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED;
    }

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

    private void runImport(List<Uri> sources, String packId) {
        setImportBusy(true, "正在校验并导入图片…");
        importExecutor.execute(() -> {
            try {
                showImportResult(EmojiFileStore.importImages(this, sources, packId), packId);
            } catch (Exception exception) {
                showImportFailure(exception);
            }
        });
    }

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

    private void showImportFailure(Exception exception) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setImportBusy(false, null);
            operationFailed("导入失败", exception);
        });
    }

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

    private void showSettings() {
        new AlertDialog.Builder(this)
                .setTitle("设置与导入")
                .setItems(new String[]{
                        "从授权目录导入",
                        "申请共享图片读取权限",
                        "打开系统输入法设置",
                        "弹出输入法选择器",
                        "应用内接收诊断"
                }, (dialog, which) -> {
                    if (which == 0) {
                        requestDirectoryImport();
                    } else if (which == 1) {
                        requestSharedImageAccess();
                    } else if (which == 2) {
                        startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                    } else if (which == 3) {
                        InputMethodManager manager = (InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                        if (manager != null) {
                            manager.showInputMethodPicker();
                        }
                    } else {
                        showReceiveDiagnostics();
                    }
                })
                .show();
    }

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
            @Override
            public void onImageReceived(Bitmap bitmap, String mimeType) {
                received.setImageBitmap(bitmap);
                result.setText(getString(R.string.local_receive_success, mimeType));
            }

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

    private View wrapDialogInput(EditText input) {
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(dp(20), 0, dp(20), 0);
        holder.addView(input, matchWrap());
        return holder;
    }

    private EmojiCatalog.Gallery selectedGallery() {
        return findGallery(selectedGalleryId);
    }

    private EmojiCatalog.Pack selectedPack() {
        return catalog == null ? null : catalog.getPack(selectedPackId);
    }

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

    private boolean containsGallery(String galleryId) {
        return findGallery(galleryId) != null;
    }

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

    private String selectedGalleryName() {
        EmojiCatalog.Gallery gallery = selectedGallery();
        return gallery == null ? "无图库" : gallery.getName();
    }

    private static List<String> nonEmptyLines(String text) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                result.add(line.trim());
            }
        }
        return result;
    }

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

    private void styleSelection(Button button, boolean selected) {
        button.setBackgroundColor(selected ? selectedColor() : railColor());
        button.setTextColor(selected ? selectedTextColor() : primaryTextColor());
    }

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

    private TextView compactLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(primaryTextColor());
        label.setGravity(Gravity.CENTER);
        label.setTextSize(12);
        return label;
    }

    private int surfaceColor() {
        return isNightMode() ? 0xff121212 : 0xfffafafa;
    }

    private int railColor() {
        return isNightMode() ? 0xff242424 : 0xfff0f1f3;
    }

    private int selectedColor() {
        return isNightMode() ? 0xff35506f : 0xffdbeafe;
    }

    private int selectedTextColor() {
        return isNightMode() ? 0xffffffff : 0xff12345b;
    }

    private int primaryTextColor() {
        return isNightMode() ? 0xfff2f2f2 : 0xff202124;
    }

    private int secondaryTextColor() {
        return isNightMode() ? 0xffb7b7b7 : 0xff686b70;
    }

    private boolean isNightMode() {
        int mask = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void operationFailed(String prefix, Exception exception) {
        toast(prefix + "：" + readableMessage(exception));
    }

    private static String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "未知错误" : message.trim();
    }

    private LinearLayout.LayoutParams weightedWrap() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapMatch() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams matchMatch() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface TextConsumer {
        void accept(String value);
    }
}
