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
    private static final int CANDIDATE_LIMIT = 8;

    private LinearLayout contentContainer;
    private LinearLayout galleryRail;
    private LinearLayout packStrip;
    private EmojiGridAdapter gridAdapter;
    private TextView emptyState;
    private TextView compatibilityStatus;
    private EmojiCatalog catalog;
    private String selectedGalleryId;
    private String selectedPackId;
    private String selectedItemId;
    private boolean textMode = true;
    private boolean englishMode;
    private final StringBuilder pinyinBuffer = new StringBuilder();
    private LinearLayout candidateStrip;
    private TextView composingLabel;
    private PinyinDictionary pinyinDictionary;

    @Override
    public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();
        return true;
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(surfaceColor());
        root.setPadding(dp(4), dp(4), dp(4), dp(4));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("EmojiKing", 13);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button switchMode = button("", "切换输入模式", view -> {
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) {
                connection.finishComposingText();
            }
            textMode = !textMode;
            englishMode = false;
            pinyinBuffer.setLength(0);
            refreshInputMode();
        });
        switchMode.setTag("modeSwitch");
        header.addView(switchMode, new LinearLayout.LayoutParams(dp(78), dp(42)));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        refreshInputMode();
        return root;
    }

    private void refreshInputMode() {
        if (contentContainer == null) {
            return;
        }
        View switchMode = ((ViewGroup) contentContainer.getParent()).getChildAt(0);
        if (switchMode instanceof ViewGroup) {
            View button = ((ViewGroup) switchMode).findViewWithTag("modeSwitch");
            if (button instanceof Button) {
                ((Button) button).setText(textMode ? "表情" : "文字");
            }
        }
        contentContainer.removeAllViews();
        if (textMode) {
            contentContainer.addView(createTextPanel(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            contentContainer.addView(createEmojiPanel(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private View createEmojiPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);

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
        root.addView(rail, new LinearLayout.LayoutParams(
                dp(56), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        root.addView(panel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

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

    private View createTextPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        HorizontalScrollView candidateScroll = new HorizontalScrollView(this);
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidateStrip = new LinearLayout(this);
        candidateStrip.setGravity(Gravity.CENTER_VERTICAL);
        candidateScroll.addView(candidateStrip, matchWrap());
        panel.addView(candidateScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        composingLabel = label("", 12);
        composingLabel.setTextColor(secondaryTextColor());
        composingLabel.setGravity(Gravity.CENTER_VERTICAL);
        composingLabel.setPadding(dp(10), 0, dp(10), 0);
        panel.addView(composingLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        addKeyRow(panel, "QWERTYUIOP", 0.0f, false);
        addKeyRow(panel, "ASDFGHJKL", 0.05f, false);
        addKeyRow(panel, "ZXCVBNM", 0.10f, true);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        addControlKey(controls, "符", "输入符号", view -> commitText("，"), 0.9f);
        addControlKey(controls, "123", "切换数字输入", view -> commitText("123"), 1.0f);
        addControlKey(controls, ",", "输入逗号", view -> commitText("，"), 0.8f);
        addControlKey(controls, "空格", "选择首个候选或输入空格", view -> chooseFirstCandidate(), 2.5f);
        addControlKey(controls, "。", "输入句号", view -> commitText("。"), 0.8f);
        addControlKey(controls, "中英", "切换中文或英文输入", view -> toggleLanguage(), 1.0f);
        addControlKey(controls, "回车", "换行", view -> sendEnter(), 1.0f);
        panel.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        renderCandidates();
        return panel;
    }

    private void addKeyRow(LinearLayout panel, String keys, float insetWeight, boolean withBackspace) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        if (insetWeight > 0f) {
            View inset = new View(this);
            row.addView(inset, new LinearLayout.LayoutParams(0, 1, insetWeight));
        }
        for (int index = 0; index < keys.length(); index++) {
            String key = String.valueOf(keys.charAt(index));
            addControlKey(row, key, "输入字母" + key, view -> appendPinyin(key), 1f);
        }
        if (withBackspace) {
            addControlKey(row, "删", "删除拼音或前一个字符", view -> deleteLast(), 1f);
        }
        if (insetWeight > 0f) {
            View inset = new View(this);
            row.addView(inset, new LinearLayout.LayoutParams(0, 1, insetWeight));
        }
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
    }

    private void addControlKey(
            LinearLayout row,
            String text,
            String description,
            View.OnClickListener listener,
            float weight) {
        Button key = button(text, description, listener);
        key.setTextSize(15);
        key.setBackgroundColor(railColor());
        key.setTextColor(primaryTextColor());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(48), weight);
        layout.setMargins(dp(1), dp(1), dp(1), dp(1));
        row.addView(key, layout);
    }

    private void appendPinyin(String key) {
        if (englishMode) {
            commitText(key);
            return;
        }
        pinyinBuffer.append(key.toLowerCase(java.util.Locale.ROOT));
        renderCandidates();
    }

    private void toggleLanguage() {
        commitPinyin();
        englishMode = !englishMode;
        if (composingLabel != null) {
            composingLabel.setText(englishMode ? "英文" : "中文");
        }
    }

    private void deleteLast() {
        if (pinyinBuffer.length() > 0) {
            pinyinBuffer.deleteCharAt(pinyinBuffer.length() - 1);
            renderCandidates();
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.deleteSurroundingText(1, 0);
        }
    }

    private void renderCandidates() {
        if (candidateStrip == null || composingLabel == null) {
            return;
        }
        candidateStrip.removeAllViews();
        String pinyin = pinyinBuffer.toString();
        composingLabel.setText(pinyin.isEmpty() && englishMode ? "英文" : pinyin);
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.setComposingText(pinyin, 1);
        }
        if (pinyin.isEmpty()) {
            return;
        }
        if (pinyinDictionary == null) {
            pinyinDictionary = PinyinDictionary.load(this);
        }
        List<String> candidates = pinyinDictionary.query(pinyin, CANDIDATE_LIMIT);
        for (String candidate : candidates) {
            Button button = button(candidate, "选择候选词" + candidate,
                    view -> commitCandidate(candidate));
            button.setTextSize(16);
            candidateStrip.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        }
        if (candidates.isEmpty()) {
            candidateStrip.addView(label("无候选", 12), new LinearLayout.LayoutParams(dp(70), dp(42)));
        }
    }

    private void chooseFirstCandidate() {
        if (pinyinBuffer.length() == 0) {
            commitText(" ");
            return;
        }
        if (pinyinDictionary == null) {
            pinyinDictionary = PinyinDictionary.load(this);
        }
        List<String> candidates = pinyinDictionary.query(pinyinBuffer.toString(), 1);
        if (candidates.isEmpty()) {
            commitPinyin();
        } else {
            commitCandidate(candidates.get(0));
        }
    }

    private void commitCandidate(String candidate) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(candidate, 1);
        }
        pinyinBuffer.setLength(0);
        renderCandidates();
    }

    private void commitPinyin() {
        if (pinyinBuffer.length() == 0) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(pinyinBuffer.toString(), 1);
        }
        pinyinBuffer.setLength(0);
        renderCandidates();
    }

    private void commitText(String text) {
        commitPinyin();
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
    }

    private void sendEnter() {
        commitPinyin();
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.sendKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER));
        }
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
