package com.example.myapplication.ime;

import android.content.ClipDescription;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import com.example.myapplication.EmojiFileStore;
import com.example.myapplication.EmojiGridAdapter;
import com.example.myapplication.EmojiSelectionStore;
import com.example.myapplication.ImageShareSender;
import com.example.myapplication.R;
import com.example.myapplication.catalog.EmojiCatalog;
import com.example.myapplication.catalog.LocalEmojiCatalogRepository;

import java.util.Collections;
import java.util.List;

/** Multi-gallery emoji panel that preserves the standard commit/share policy. */
public class EmojiInputMethodService extends InputMethodService {
    private LinearLayout galleryRail;
    private LinearLayout packStrip;
    private EmojiGridAdapter gridAdapter;
    private TextView emptyState;
    private TextView compatibilityStatus;
    private EmojiCatalog catalog;
    private String selectedGalleryId;
    private String selectedPackId;
    private String selectedItemId;

    @Override
    public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();
        return true;
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(surfaceColor());
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.setMinimumHeight(dp(300));

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setBackgroundColor(railColor());
        TextView galleryLabel = label("图库", 11);
        rail.addView(galleryLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        ScrollView galleryScroll = new ScrollView(this);
        galleryRail = new LinearLayout(this);
        galleryRail.setOrientation(LinearLayout.VERTICAL);
        galleryScroll.addView(galleryRail, matchWrap());
        rail.addView(galleryScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(rail, new LinearLayout.LayoutParams(dp(56), dp(292)));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        root.addView(panel, new LinearLayout.LayoutParams(0, dp(292), 1f));

        HorizontalScrollView packScroll = new HorizontalScrollView(this);
        packScroll.setHorizontalScrollBarEnabled(false);
        packStrip = new LinearLayout(this);
        packStrip.setOrientation(LinearLayout.HORIZONTAL);
        packScroll.addView(packStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(packScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(2));
        grid.setVerticalSpacing(dp(2));
        grid.setPadding(dp(4), dp(2), dp(4), dp(2));
        grid.setClipToPadding(false);
        gridAdapter = new EmojiGridAdapter(this, 76, 62);
        grid.setAdapter(gridAdapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            EmojiCatalog.Item item = gridAdapter.getItem(position);
            selectedItemId = item.getId();
            sendEmoji(item);
        });

        LinearLayout gridArea = new LinearLayout(this);
        gridArea.setOrientation(LinearLayout.VERTICAL);
        emptyState = label("", 13);
        emptyState.setTextColor(secondaryTextColor());
        emptyState.setGravity(Gravity.CENTER);
        grid.setEmptyView(emptyState);
        gridArea.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        gridArea.addView(emptyState, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(gridArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        compatibilityStatus = label("", 11);
        compatibilityStatus.setTextColor(secondaryTextColor());
        compatibilityStatus.setGravity(Gravity.CENTER_VERTICAL);
        compatibilityStatus.setMaxLines(2);
        compatibilityStatus.setEllipsize(TextUtils.TruncateAt.END);
        footer.addView(compatibilityStatus, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button share = button("分享", "手动分享所选表情", view -> shareSelectedManually());
        footer.addView(share, new LinearLayout.LayoutParams(dp(66), dp(48)));
        panel.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        reloadCatalog();
        return root;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        updateCompatibilityStatus(attribute);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        reloadCatalog();
        updateCompatibilityStatus(info);
    }

    private void reloadCatalog() {
        if (galleryRail == null || packStrip == null || gridAdapter == null) {
            return;
        }
        try {
            catalog = EmojiFileStore.getCatalog(this);
            EmojiSelectionStore.Selection selection = EmojiSelectionStore.resolve(this, catalog);
            selectedGalleryId = selection.getGallery() == null
                    ? null : selection.getGallery().getId();
            selectedPackId = selection.getPack() == null ? null : selection.getPack().getId();
            selectedItemId = firstItemId(selection.getPack());
            renderCatalog();
        } catch (Exception exception) {
            catalog = null;
            selectedGalleryId = null;
            selectedPackId = null;
            selectedItemId = null;
            galleryRail.removeAllViews();
            packStrip.removeAllViews();
            gridAdapter.setItems(Collections.emptyList());
            emptyState.setText("表情库读取失败");
            setStatus("表情库读取失败");
        }
    }

    private void renderCatalog() {
        renderGalleries();
        renderPacks();
        renderItems();
        EmojiSelectionStore.save(this, selectedGalleryId, selectedPackId);
    }

    private void renderGalleries() {
        galleryRail.removeAllViews();
        for (EmojiCatalog.Gallery gallery : catalog.getGalleries()) {
            Button button = button(
                    gallery.getName(),
                    "选择图库" + gallery.getName(),
                    view -> selectGallery(gallery.getId()));
            button.setTextSize(10);
            button.setMaxLines(2);
            button.setEllipsize(TextUtils.TruncateAt.END);
            styleSelection(button, gallery.getId().equals(selectedGalleryId));
            galleryRail.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }
    }

    private void renderPacks() {
        packStrip.removeAllViews();
        List<EmojiCatalog.Pack> packs = catalog.getPacksForGallery(selectedGalleryId);
        for (EmojiCatalog.Pack pack : packs) {
            Button button = button(
                    pack.getName(),
                    "选择表情包" + pack.getName(),
                    view -> selectPack(pack.getId()));
            button.setTextSize(11);
            button.setMaxLines(2);
            button.setEllipsize(TextUtils.TruncateAt.END);
            styleSelection(button, pack.getId().equals(selectedPackId));
            packStrip.addView(button, new LinearLayout.LayoutParams(dp(72), dp(54)));
        }
        if (packs.isEmpty()) {
            packStrip.addView(label("当前图库为空", 11),
                    new LinearLayout.LayoutParams(dp(150), dp(54)));
        }
    }

    private void renderItems() {
        EmojiCatalog.Pack pack = selectedPack();
        if (pack == null) {
            gridAdapter.setItems(Collections.emptyList());
            emptyState.setText("当前图库没有表情包");
            setStatus("请在应用中添加表情包");
            return;
        }
        gridAdapter.setItems(pack.getItems());
        emptyState.setText("当前表情包为空");
        if (findItem(pack, selectedItemId) == null) {
            selectedItemId = firstItemId(pack);
        }
        updateCompatibilityStatus(getCurrentInputEditorInfo());
    }

    private void selectGallery(String galleryId) {
        selectedGalleryId = galleryId;
        List<EmojiCatalog.Pack> packs = catalog.getPacksForGallery(galleryId);
        selectedPackId = packs.isEmpty() ? null : packs.get(0).getId();
        selectedItemId = packs.isEmpty() ? null : firstItemId(packs.get(0));
        renderCatalog();
    }

    private void selectPack(String packId) {
        selectedPackId = packId;
        selectedItemId = firstItemId(selectedPack());
        renderPacks();
        renderItems();
        EmojiSelectionStore.save(this, selectedGalleryId, selectedPackId);
    }

    private void sendEmoji(EmojiCatalog.Item item) {
        InputConnection connection = getCurrentInputConnection();
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (connection == null) {
            showResult("没有可用的输入框");
            return;
        }
        try {
            LocalEmojiCatalogRepository.StoredEmoji stored = EmojiFileStore.getStoredEmoji(
                    this, item.getId());
            Uri uri = EmojiFileStore.getUri(this, stored);
            String mimeType = item.getMimeType();
            if (ImageSendPolicy.initialAction(supportsMimeType(editorInfo, mimeType))
                    == ImageSendPolicy.InitialAction.COMMIT) {
                boolean committed;
                try {
                    committed = commitContent(connection, editorInfo, uri, mimeType);
                } catch (RuntimeException exception) {
                    committed = false;
                }
                if (ImageSendPolicy.afterCommit(committed)
                        == ImageSendPolicy.AfterCommitAction.STOP) {
                    showResult("标准提交已接受");
                    return;
                }
                shareImage(editorInfo, uri, mimeType, "标准提交未接受");
            } else {
                shareImage(editorInfo, uri, mimeType, "当前输入框不支持此图片");
            }
        } catch (Exception exception) {
            showResult("所选表情不可用");
            reloadCatalog();
        }
    }

    private void shareSelectedManually() {
        EmojiCatalog.Item item = selectedItem();
        if (item == null) {
            showResult("当前没有可分享的表情");
            return;
        }
        try {
            LocalEmojiCatalogRepository.StoredEmoji stored = EmojiFileStore.getStoredEmoji(
                    this, item.getId());
            shareImage(
                    getCurrentInputEditorInfo(),
                    EmojiFileStore.getUri(this, stored),
                    item.getMimeType(),
                    null);
        } catch (Exception exception) {
            showResult("所选表情不可用");
        }
    }

    private void shareImage(
            EditorInfo editorInfo,
            Uri uri,
            String mimeType,
            String pathReason) {
        String targetPackage = editorInfo == null ? null : editorInfo.packageName;
        ImageShareSender.Result result = ImageShareSender.send(
                this, targetPackage, uri, mimeType);
        if (result == ImageShareSender.Result.TARGET_STARTED) {
            showShareResult(pathReason, "已打开目标应用分享");
        } else if (result == ImageShareSender.Result.CHOOSER_STARTED) {
            showShareResult(pathReason, TextUtils.isEmpty(targetPackage)
                    ? "无法识别当前应用，已打开系统选择器"
                    : "目标应用不支持图片分享，已打开系统选择器");
        } else {
            showShareResult(pathReason, "图片分享启动失败");
        }
    }

    private void showShareResult(String pathReason, String result) {
        showResult(TextUtils.isEmpty(pathReason) ? result : pathReason + "；" + result);
    }

    private boolean commitContent(
            InputConnection connection,
            EditorInfo editorInfo,
            Uri uri,
            String mimeType) {
        ClipDescription description = new ClipDescription("本地表情", new String[]{mimeType});
        InputContentInfoCompat contentInfo = new InputContentInfoCompat(uri, description, null);
        return InputConnectionCompat.commitContent(
                connection,
                editorInfo,
                contentInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null);
    }

    private boolean supportsMimeType(EditorInfo editorInfo, String mimeType) {
        return editorInfo != null && ImageSendPolicy.supportsMimeType(
                EditorInfoCompat.getContentMimeTypes(editorInfo),
                mimeType,
                ClipDescription::compareMimeTypes);
    }

    private void updateCompatibilityStatus(EditorInfo editorInfo) {
        if (compatibilityStatus == null) {
            return;
        }
        EmojiCatalog.Item item = selectedItem();
        if (item == null) {
            setStatus("当前没有可发送的表情");
        } else if (editorInfo == null) {
            setStatus("等待输入框连接");
        } else if (supportsMimeType(editorInfo, item.getMimeType())) {
            setStatus(getString(R.string.ime_mime_supported, item.getMimeType()));
        } else {
            setStatus(getString(R.string.ime_mime_unsupported, item.getMimeType()));
        }
    }

    private EmojiCatalog.Pack selectedPack() {
        return catalog == null ? null : catalog.getPack(selectedPackId);
    }

    private EmojiCatalog.Item selectedItem() {
        return findItem(selectedPack(), selectedItemId);
    }

    private static EmojiCatalog.Item findItem(EmojiCatalog.Pack pack, String itemId) {
        if (pack != null && itemId != null) {
            for (EmojiCatalog.Item item : pack.getItems()) {
                if (itemId.equals(item.getId())) {
                    return item;
                }
            }
        }
        return null;
    }

    private static String firstItemId(EmojiCatalog.Pack pack) {
        return pack == null || pack.getItems().isEmpty()
                ? null : pack.getItems().get(0).getId();
    }

    private void styleSelection(Button button, boolean selected) {
        button.setBackgroundColor(selected ? selectedColor() : railColor());
        button.setTextColor(selected ? selectedTextColor() : primaryTextColor());
    }

    private Button button(String text, String description, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setPadding(dp(3), 0, dp(3), 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView label(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(primaryTextColor());
        label.setGravity(Gravity.CENTER);
        return label;
    }

    private void setStatus(String message) {
        if (compatibilityStatus != null) {
            compatibilityStatus.setText(message);
        }
    }

    private void showResult(String message) {
        setStatus(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int surfaceColor() {
        return isNightMode() ? 0xff121212 : Color.WHITE;
    }

    private int railColor() {
        return isNightMode() ? 0xff242424 : 0xfff0f1f3;
    }

    private int selectedColor() {
        return isNightMode() ? 0xff35506f : 0xffdbeafe;
    }

    private int selectedTextColor() {
        return isNightMode() ? Color.WHITE : 0xff12345b;
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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
