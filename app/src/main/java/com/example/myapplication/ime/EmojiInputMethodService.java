package com.example.myapplication.ime;

import android.content.ClipDescription;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
import com.example.myapplication.KeyboardBackgroundStore;
import com.example.myapplication.R;
import com.example.myapplication.catalog.EmojiCatalog;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chinese text keyboard and local emoji browser sharing one fixed input surface. */
// 类作用：定义 EmojiInputMethodService，承载所在模块的主要职责。
public class EmojiInputMethodService extends InputMethodService {
    private static final int CANDIDATE_LIMIT = 15;
    private static final int EXPANDED_CANDIDATE_COLUMNS = 3;
    private static final int INPUT_HEIGHT_DP = 304;
    private static final int TOOLBAR_HEIGHT_DP = 46;
    private static final int LETTER_AREA_HEIGHT_DP = 196;
    private static final int CONTROL_ROW_HEIGHT_DP = 62;

    private LinearLayout keyboardSurface;
    private LinearLayout toolbar;
    private LinearLayout toolbarBody;
    private LinearLayout contentContainer;
    private ImageView keyboardBackground;
    private Button modeSwitch;
    private Button candidateExpandButton;
    private TextView toolbarStatus;
    private View emojiPanelView;
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
    private boolean englishUppercase;
    private int textPage;
    private final StringBuilder pinyinBuffer = new StringBuilder();
    private HorizontalScrollView candidateScroll;
    private LinearLayout candidateStrip;
    private TextView composingLabel;
    private PinyinDictionary pinyinDictionary;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService dictionaryExecutor;
    private ExecutorService catalogExecutor;
    private volatile int candidateGeneration;
    private volatile int catalogGeneration;
    private volatile int backgroundGeneration;
    private volatile boolean destroyed;
    private boolean customBackgroundActive;
    private long loadedBackgroundVersion = Long.MIN_VALUE;
    private Bitmap loadedBackgroundBitmap;
    private List<String> currentCandidates = Collections.emptyList();
    private String currentCandidatePinyin = "";
    private boolean chooseFirstPending;
    private boolean candidateExpanded;

// 方法作用：按生命周期创建并初始化界面或服务状态（onCreate）。
    @Override
    public void onCreate() {
        super.onCreate();
        dictionaryExecutor = Executors.newSingleThreadExecutor();
        catalogExecutor = Executors.newSingleThreadExecutor();
        preloadPinyinDictionary();
    }

// 方法作用：处理 onEvaluateInputViewShown 对应的输入并返回或更新相关结果（onEvaluateInputViewShown）。
    @Override
    public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();
        return true;
    }

// 方法作用：处理 onEvaluateFullscreenMode 对应的输入并返回或更新相关结果（onEvaluateFullscreenMode）。
    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

// 方法作用：处理 onCreateInputView 对应的输入并返回或更新相关结果（onCreateInputView）。
    @Override
    public View onCreateInputView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(surfaceColor());
        root.setMinimumHeight(dp(INPUT_HEIGHT_DP));

        customBackgroundActive = KeyboardBackgroundStore.hasBackground(this);
        keyboardBackground = new ImageView(this);
        keyboardBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        keyboardBackground.setContentDescription("自定义键盘背景");
        root.addView(keyboardBackground, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(INPUT_HEIGHT_DP)));

        keyboardSurface = new LinearLayout(this);
        keyboardSurface.setOrientation(LinearLayout.VERTICAL);
        keyboardSurface.setMinimumHeight(dp(INPUT_HEIGHT_DP));

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(2), dp(4), dp(2));
        modeSwitch = button("☺", "切换到表情图库", view -> {
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) {
                connection.finishComposingText();
            }
            textMode = !textMode;
            englishMode = false;
            englishUppercase = false;
            textPage = 0;
            candidateExpanded = false;
            pinyinBuffer.setLength(0);
            refreshInputMode();
        });
        modeSwitch.setTextSize(21);
        toolbar.addView(modeSwitch, new LinearLayout.LayoutParams(dp(48), dp(42)));

        toolbarBody = new LinearLayout(this);
        toolbarBody.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(toolbarBody, new LinearLayout.LayoutParams(0, dp(42), 1f));
        keyboardSurface.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TOOLBAR_HEIGHT_DP)));

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        keyboardSurface.addView(contentContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(INPUT_HEIGHT_DP - TOOLBAR_HEIGHT_DP)));
        root.addView(keyboardSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(INPUT_HEIGHT_DP)));
        refreshInputMode();
        loadKeyboardBackgroundAsync();
        return root;
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（refreshInputMode）。
    private void refreshInputMode() {
        if (contentContainer == null) {
            return;
        }
        candidateGeneration++;
        chooseFirstPending = false;
        candidateExpanded = false;
        currentCandidates = Collections.emptyList();
        currentCandidatePinyin = "";
        applySurfaceTheme();
        modeSwitch.setText(textMode ? "☺" : "ABC");
        modeSwitch.setContentDescription(textMode ? "切换到表情图库" : "返回文字键盘");
        modeSwitch.setTextColor(primaryTextColor());
        modeSwitch.setBackground(borderlessKeyBackground());
        if (textMode && customBackgroundActive) {
            modeSwitch.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        } else {
            modeSwitch.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        }
        rebuildToolbar();
        contentContainer.removeAllViews();
        if (textMode) {
            contentContainer.addView(createTextPanel(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (textPage == 0) {
                renderCandidates();
            }
        } else {
            if (emojiPanelView == null) {
                emojiPanelView = createEmojiPanel();
            }
            contentContainer.addView(emojiPanelView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

// 方法作用：处理 rebuildToolbar 对应的输入并返回或更新相关结果（rebuildToolbar）。
    private void rebuildToolbar() {
        toolbarBody.removeAllViews();
        candidateScroll = null;
        candidateStrip = null;
        composingLabel = null;
        candidateExpandButton = null;

        if (textMode && textPage == 0) {
            composingLabel = label("", 13);
            composingLabel.setTextColor(secondaryTextColor());
            composingLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            composingLabel.setSingleLine(true);
            composingLabel.setMaxWidth(dp(100));
            composingLabel.setEllipsize(TextUtils.TruncateAt.END);
            composingLabel.setPadding(dp(6), 0, dp(4), 0);
            toolbarBody.addView(composingLabel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

            candidateScroll = new HorizontalScrollView(this);
            candidateScroll.setHorizontalScrollBarEnabled(false);
            candidateStrip = new LinearLayout(this);
            candidateStrip.setGravity(Gravity.CENTER_VERTICAL);
            candidateScroll.addView(candidateStrip, matchWrap());
            toolbarBody.addView(candidateScroll, new LinearLayout.LayoutParams(
                    0, dp(42), 1f));

            candidateExpandButton = button("▼", "展开全部候选词",
                    view -> setCandidateExpanded(!candidateExpanded));
            candidateExpandButton.setTextSize(17);
            candidateExpandButton.setTextColor(primaryTextColor());
            candidateExpandButton.setBackground(borderlessKeyBackground());
            if (customBackgroundActive) {
                candidateExpandButton.setShadowLayer(3f, 0f, 1f, Color.BLACK);
            }
            candidateExpandButton.setEnabled(false);
            candidateExpandButton.setVisibility(View.INVISIBLE);
            toolbarBody.addView(candidateExpandButton, new LinearLayout.LayoutParams(
                    dp(42), dp(42)));
        }

        toolbarStatus = label("", 11);
        toolbarStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        toolbarStatus.setSingleLine(true);
        toolbarStatus.setEllipsize(TextUtils.TruncateAt.END);
        toolbarStatus.setPadding(dp(4), 0, dp(6), 0);
        if (!textMode || textPage != 0) {
            toolbarBody.addView(toolbarStatus, new LinearLayout.LayoutParams(
                    0, dp(42), 1f));
        }
        updateToolbarStatus();
    }

// 方法作用：把异步加载结果应用到当前界面或状态（applySurfaceTheme）。
    private void applySurfaceTheme() {
        boolean showBackground = textMode && customBackgroundActive;
        keyboardBackground.setVisibility(showBackground ? View.VISIBLE : View.GONE);
        keyboardSurface.setBackgroundColor(showBackground ? Color.TRANSPARENT : surfaceColor());
        toolbar.setBackgroundColor(showBackground ? 0x22000000 : toolbarColor());
    }

// 方法作用：处理 preloadPinyinDictionary 对应的输入并返回或更新相关结果（preloadPinyinDictionary）。
    private void preloadPinyinDictionary() {
        dictionaryExecutor.execute(() -> {
            PinyinDictionary loaded = PinyinDictionary.load(getApplicationContext());
            mainHandler.post(() -> {
                if (destroyed) {
                    return;
                }
                pinyinDictionary = loaded;
                updateToolbarStatus();
                if (textMode && pinyinBuffer.length() > 0) {
                    renderCandidates();
                }
            });
        });
    }

// 方法作用：从文件、网络或内存加载数据（loadKeyboardBackgroundAsync）。
    private void loadKeyboardBackgroundAsync() {
        if (keyboardBackground == null || catalogExecutor == null) {
            return;
        }
        int generation = ++backgroundGeneration;
        long version = KeyboardBackgroundStore.version(this);
        if (version == loadedBackgroundVersion) {
            applySurfaceTheme();
            return;
        }
        if (version == 0L) {
            applyKeyboardBackground(generation, version, null);
            return;
        }
        catalogExecutor.execute(() -> {
            Bitmap bitmap = KeyboardBackgroundStore.load(getApplicationContext());
            mainHandler.post(() -> applyKeyboardBackground(generation, version, bitmap));
        });
    }

// 方法作用：把异步加载结果应用到当前界面或状态（applyKeyboardBackground）。
    private void applyKeyboardBackground(int generation, long version, Bitmap bitmap) {
        if (destroyed || generation != backgroundGeneration) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            return;
        }
        Bitmap previous = loadedBackgroundBitmap;
        boolean wasActive = customBackgroundActive;
        loadedBackgroundBitmap = bitmap;
        loadedBackgroundVersion = version;
        customBackgroundActive = bitmap != null;
        keyboardBackground.setImageBitmap(bitmap);
        if (customBackgroundActive) {
            keyboardBackground.setColorFilter(0x22000000);
        } else {
            keyboardBackground.clearColorFilter();
        }
        if (previous != null && previous != bitmap) {
            previous.recycle();
        }
        if (wasActive != customBackgroundActive) {
            refreshInputMode();
        } else {
            applySurfaceTheme();
        }
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（updateToolbarStatus）。
    private void updateToolbarStatus() {
        if (toolbarStatus == null) {
            return;
        }
        if (!textMode) {
            toolbarStatus.setText("表情图库");
        } else if (englishMode) {
            toolbarStatus.setText(englishUppercase ? "英文大写" : "英文");
        } else if (pinyinDictionary == null) {
            toolbarStatus.setText("中文 · 词库加载中");
        } else if (pinyinDictionary.isEmpty()) {
            toolbarStatus.setText("中文 · 词库不可用");
        } else {
            toolbarStatus.setText("中文");
        }
    }

// 方法作用：按生命周期释放线程、监听器和其他资源（onDestroy）。
    @Override
    public void onDestroy() {
        destroyed = true;
        candidateGeneration++;
        catalogGeneration++;
        backgroundGeneration++;
        if (dictionaryExecutor != null) {
            dictionaryExecutor.shutdownNow();
        }
        if (catalogExecutor != null) {
            catalogExecutor.shutdownNow();
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (gridAdapter != null) {
            gridAdapter.release();
        }
        if (keyboardBackground != null) {
            keyboardBackground.setImageDrawable(null);
        }
        if (loadedBackgroundBitmap != null) {
            loadedBackgroundBitmap.recycle();
            loadedBackgroundBitmap = null;
        }
        super.onDestroy();
    }

// 方法作用：创建并返回新的业务对象或界面对象（createEmojiPanel）。
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

        reloadCatalogAsync();
        return root;
    }

// 方法作用：创建并返回新的业务对象或界面对象（createTextPanel）。
    private View createTextPanel() {
        if (textPage != 0) {
            return createSpecialPanel();
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        LinearLayout letterArea = new LinearLayout(this);
        letterArea.setOrientation(LinearLayout.VERTICAL);
        letterArea.setPadding(dp(4), dp(2), dp(4), dp(2));
        letterArea.setBackground(unifiedLetterAreaBackground());
        addKeyRow(letterArea, "QWERTYUIOP", 0.0f, false);
        addKeyRow(letterArea, "ASDFGHJKL", 0.50f, false);
        addKeyRow(letterArea, "ZXCVBNM", 0.72f, true);
        panel.addView(letterArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(LETTER_AREA_HEIGHT_DP)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        addControlKey(controls, "符", "打开符号面板", view -> openTextPage(1), 0.9f);
        addControlKey(controls, "123", "打开数字键盘", view -> openTextPage(2), 1.0f);
        addControlKey(controls, ",", "输入逗号", view -> commitText("，"), 0.8f);
        addControlKey(controls, "空格", "选择首个候选或输入空格", view -> chooseFirstCandidate(), 2.5f);
        addControlKey(controls, "。", "输入句号", view -> commitText("。"), 0.8f);
        if (englishMode) {
            addControlKey(controls, englishUppercase ? "小写" : "大写",
                    "切换英文大小写", view -> toggleCase(), 1.0f);
        }
        addControlKey(controls, "中英", "切换中文或英文输入", view -> toggleLanguage(), 1.0f);
        addControlKey(controls, "回车", "换行", view -> sendEnter(), 1.0f);
        panel.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(CONTROL_ROW_HEIGHT_DP)));
        return panel;
    }

// 方法作用：向界面或业务集合中添加新的元素（addKeyRow）。
    private void addKeyRow(LinearLayout panel, String keys, float insetWeight, boolean withBackspace) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        if (insetWeight > 0f) {
            View inset = new View(this);
            row.addView(inset, new LinearLayout.LayoutParams(0, 1, insetWeight));
        }
        for (int index = 0; index < keys.length(); index++) {
            String baseKey = String.valueOf(keys.charAt(index));
            String key = !englishMode || !englishUppercase
                    ? baseKey.toLowerCase(java.util.Locale.ROOT) : baseKey;
            addLetterKey(row, key, baseKey);
        }
        if (withBackspace) {
            View deleteGap = new View(this);
            row.addView(deleteGap, new LinearLayout.LayoutParams(0, 1, 0.45f));
            addControlKey(row, "删", "删除拼音或前一个字符", view -> deleteLast(), 1f);
        }
        if (insetWeight > 0f && !withBackspace) {
            View inset = new View(this);
            row.addView(inset, new LinearLayout.LayoutParams(0, 1, insetWeight));
        }
        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

// 方法作用：向界面或业务集合中添加新的元素（addLetterKey）。
    private void addLetterKey(LinearLayout row, String text, String baseKey) {
        Button key = button(text, "输入字母" + text, view -> appendPinyin(baseKey));
        key.setTextSize(23);
        key.setTextColor(primaryTextColor());
        key.setBackground(borderlessKeyBackground());
        if (textMode && customBackgroundActive) {
            key.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        }
        row.addView(key, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

// 方法作用：创建并返回新的业务对象或界面对象（createSpecialPanel）。
    private View createSpecialPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout title = new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        addControlKey(title, "返回", "返回文字键盘", view -> openTextPage(0), 1f);
        TextView label = label(textPage == 1 ? "符号" : "数字", 14);
        title.addView(label, new LinearLayout.LayoutParams(0, dp(42), 3f));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        if (textPage == 1) {
            addSpecialRows(panel, new String[]{
                    "！@#$%^&*()", "+-=_/\\|~`", "（）【】{}<>", "，。！？：；‘’“”", "、·…—"
            }, 10);
        } else {
            addSpecialRows(panel, new String[]{"123", "456", "789", "0.,"}, 3);
        }
        return panel;
    }

// 方法作用：向界面或业务集合中添加新的元素（addSpecialRows）。
    private void addSpecialRows(LinearLayout panel, String[] rows, int columns) {
        for (String values : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            for (int index = 0; index < values.length(); index++) {
                String value = String.valueOf(values.charAt(index));
                addControlKey(row, value, "输入" + value, view -> commitText(value), 1f);
            }
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        }
        if (textPage == 2) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            addControlKey(row, "删", "删除一个字符", view -> deleteLast(), 1f);
            addControlKey(row, "回车", "换行", view -> sendEnter(), 1f);
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        }
    }

// 方法作用：显示或打开对应的交互界面（openTextPage）。
    private void openTextPage(int page) {
        commitPinyin();
        textPage = page;
        refreshInputMode();
    }

// 方法作用：向界面或业务集合中添加新的元素（addControlKey）。
    private void addControlKey(
            LinearLayout row,
            String text,
            String description,
            View.OnClickListener listener,
            float weight) {
        Button key = button(text, description, listener);
        key.setTextSize(15);
        key.setBackground(controlKeyBackground());
        key.setTextColor(primaryTextColor());
        if (textMode && customBackgroundActive) {
            key.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        }
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        layout.setMargins(dp(1), dp(2), dp(1), dp(2));
        row.addView(key, layout);
    }

// 方法作用：向当前拼音缓冲区追加输入并刷新候选（appendPinyin）。
    private void appendPinyin(String key) {
        if (englishMode) {
            commitText(englishUppercase ? key.toUpperCase(java.util.Locale.ROOT)
                    : key.toLowerCase(java.util.Locale.ROOT));
            return;
        }
        pinyinBuffer.append(key.toLowerCase(java.util.Locale.ROOT));
        renderCandidates();
    }

// 方法作用：切换键盘模式并刷新相关界面（toggleLanguage）。
    private void toggleLanguage() {
        commitPinyin();
        englishMode = !englishMode;
        englishUppercase = false;
        refreshInputMode();
    }

// 方法作用：切换键盘模式并刷新相关界面（toggleCase）。
    private void toggleCase() {
        englishUppercase = !englishUppercase;
        refreshInputMode();
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（deleteLast）。
    private void deleteLast() {
        if (pinyinBuffer.length() > 0) {
            pinyinBuffer.deleteCharAt(pinyinBuffer.length() - 1);
            renderCandidates();
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null && !connection.deleteSurroundingText(1, 0)) {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
    }

// 方法作用：构建并更新用户界面内容（renderCandidates）。
    private void renderCandidates() {
        if (candidateStrip == null || composingLabel == null) {
            return;
        }
        String pinyin = pinyinBuffer.toString();
        composingLabel.setText(pinyin.isEmpty() && englishMode ? "英文" : pinyin);
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.setComposingText(pinyin, 1);
        }
        int generation = ++candidateGeneration;
        currentCandidates = Collections.emptyList();
        currentCandidatePinyin = "";
        candidateStrip.removeAllViews();
        updateCandidateExpandButton(false);
        if (pinyin.isEmpty()) {
            chooseFirstPending = false;
            if (candidateExpanded) {
                setCandidateExpanded(false);
            }
            return;
        }
        if (pinyinDictionary == null) {
            showCandidateMessage("词库加载中");
            return;
        }
        showCandidateMessage("候选检索中");
        // 生成号用于丢弃过期异步结果，避免旧查询覆盖用户刚输入的新状态。
        dictionaryExecutor.execute(() -> {
            if (destroyed || generation != candidateGeneration) {
                return;
            }
            List<String> candidates = pinyinDictionary.query(pinyin, CANDIDATE_LIMIT);
            mainHandler.post(() -> applyCandidateResult(generation, pinyin, candidates));
        });
    }

// 方法作用：把异步加载结果应用到当前界面或状态（applyCandidateResult）。
    private void applyCandidateResult(int generation, String pinyin, List<String> candidates) {
        if (destroyed || generation != candidateGeneration
                || !textMode || !pinyin.equals(pinyinBuffer.toString())
                || candidateStrip == null) {
            return;
        }
        currentCandidates = candidates;
        currentCandidatePinyin = pinyin;
        renderCandidateButtons(candidates);
        updateCandidateExpandButton(!candidates.isEmpty());
        if (candidateExpanded) {
            showExpandedCandidatePanel();
        }
        if (chooseFirstPending) {
            chooseFirstPending = false;
            if (candidates.isEmpty()) {
                commitPinyin();
            } else {
                commitCandidate(candidates.get(0));
            }
        }
    }

// 方法作用：构建并更新用户界面内容（renderCandidateButtons）。
    private void renderCandidateButtons(List<String> candidates) {
        if (candidateStrip == null) {
            return;
        }
        candidateStrip.removeAllViews();
        for (String candidate : candidates) {
            Button button = button(candidate, "选择候选词" + candidate,
                    view -> commitCandidate(candidate));
            button.setTextSize(16);
            button.setTextColor(primaryTextColor());
            button.setBackground(borderlessKeyBackground());
            candidateStrip.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        }
        if (candidates.isEmpty()) {
            candidateStrip.addView(label("无候选", 12), new LinearLayout.LayoutParams(dp(70), dp(42)));
        }
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（updateCandidateExpandButton）。
    private void updateCandidateExpandButton(boolean hasCandidates) {
        if (candidateExpandButton == null) {
            return;
        }
        candidateExpandButton.setVisibility(hasCandidates ? View.VISIBLE : View.INVISIBLE);
        candidateExpandButton.setEnabled(hasCandidates);
        candidateExpandButton.setText(candidateExpanded ? "▲" : "▼");
        candidateExpandButton.setContentDescription(
                candidateExpanded ? "收起候选词" : "展开全部候选词");
    }

// 方法作用：更新对象状态或注册回调（setCandidateExpanded）。
    private void setCandidateExpanded(boolean expanded) {
        boolean canExpand = textMode && textPage == 0
                && !currentCandidates.isEmpty()
                && pinyinBuffer.toString().equals(currentCandidatePinyin);
        candidateExpanded = expanded && canExpand;
        updateCandidateExpandButton(canExpand);
        if (contentContainer == null) {
            return;
        }
        contentContainer.removeAllViews();
        contentContainer.addView(
                candidateExpanded ? createExpandedCandidatePanel() : createTextPanel(),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

// 方法作用：显示或打开对应的交互界面（showExpandedCandidatePanel）。
    private void showExpandedCandidatePanel() {
        if (!candidateExpanded || contentContainer == null) {
            return;
        }
        contentContainer.removeAllViews();
        contentContainer.addView(createExpandedCandidatePanel(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

// 方法作用：创建并返回新的业务对象或界面对象（createExpandedCandidatePanel）。
    private View createExpandedCandidatePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(4), dp(6), dp(4));
        panel.setBackground(unifiedLetterAreaBackground());
        int rowCount = (CANDIDATE_LIMIT + EXPANDED_CANDIDATE_COLUMNS - 1)
                / EXPANDED_CANDIDATE_COLUMNS;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            int start = rowIndex * EXPANDED_CANDIDATE_COLUMNS;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int column = 0; column < EXPANDED_CANDIDATE_COLUMNS; column++) {
                int index = start + column;
                if (index < currentCandidates.size()) {
                    String candidate = currentCandidates.get(index);
                    Button button = button(candidate, "选择候选词" + candidate,
                            view -> commitCandidate(candidate));
                    button.setTextSize(18);
                    button.setTextColor(primaryTextColor());
                    button.setSingleLine(true);
                    button.setEllipsize(TextUtils.TruncateAt.END);
                    button.setBackground(borderlessKeyBackground());
                    if (customBackgroundActive) {
                        button.setShadowLayer(3f, 0f, 1f, Color.BLACK);
                    }
                    row.addView(button, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                } else {
                    row.addView(new View(this), new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
                }
            }
            panel.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return panel;
    }

// 方法作用：显示或打开对应的交互界面（showCandidateMessage）。
    private void showCandidateMessage(String message) {
        candidateStrip.removeAllViews();
        updateCandidateExpandButton(false);
        candidateStrip.addView(label(message, 12),
                new LinearLayout.LayoutParams(dp(110), dp(40)));
    }

// 方法作用：根据候选条件选择并返回目标项（chooseFirstCandidate）。
    private void chooseFirstCandidate() {
        if (pinyinBuffer.length() == 0) {
            commitText(" ");
            return;
        }
        String pinyin = pinyinBuffer.toString();
        if (pinyin.equals(currentCandidatePinyin)) {
            if (currentCandidates.isEmpty()) {
                commitPinyin();
            } else {
                commitCandidate(currentCandidates.get(0));
            }
        } else {
            chooseFirstPending = true;
            renderCandidates();
        }
    }

// 方法作用：把文本或富内容提交到当前输入连接（commitCandidate）。
    private void commitCandidate(String candidate) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(candidate, 1);
        }
        if (pinyinDictionary != null) {
            pinyinDictionary.recordSelection(candidate);
        }
        chooseFirstPending = false;
        if (candidateExpanded) {
            setCandidateExpanded(false);
        }
        pinyinBuffer.setLength(0);
        renderCandidates();
    }

// 方法作用：把文本或富内容提交到当前输入连接（commitPinyin）。
    private void commitPinyin() {
        if (pinyinBuffer.length() == 0) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(pinyinBuffer.toString(), 1);
        }
        chooseFirstPending = false;
        if (candidateExpanded) {
            setCandidateExpanded(false);
        }
        pinyinBuffer.setLength(0);
        renderCandidates();
    }

// 方法作用：把文本或富内容提交到当前输入连接（commitText）。
    private void commitText(String text) {
        commitPinyin();
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
    }

// 方法作用：把当前选中的内容发送到目标输入框或分享面板（sendEnter）。
    private void sendEnter() {
        commitPinyin();
        InputConnection connection = getCurrentInputConnection();
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (connection == null) {
            return;
        }
        int options = editorInfo == null ? EditorInfo.IME_ACTION_NONE : editorInfo.imeOptions;
        int action = options & EditorInfo.IME_MASK_ACTION;
        boolean allowAction = (options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED;
        if (!allowAction || !connection.performEditorAction(action)) {
            connection.commitText("\n", 1);
        }
    }

// 方法作用：处理 onStartInput 对应的输入并返回或更新相关结果（onStartInput）。
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        if (!restarting) {
            candidateGeneration++;
            chooseFirstPending = false;
            pinyinBuffer.setLength(0);
            currentCandidates = Collections.emptyList();
            currentCandidatePinyin = "";
            if (candidateExpanded) {
                setCandidateExpanded(false);
            }
        }
        updateCompatibilityStatus(attribute);
    }

// 方法作用：处理 onStartInputView 对应的输入并返回或更新相关结果（onStartInputView）。
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        loadKeyboardBackgroundAsync();
        reloadCatalogAsync();
        updateCompatibilityStatus(info);
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（reloadCatalogAsync）。
    private void reloadCatalogAsync() {
        if (galleryRail == null || packStrip == null || gridAdapter == null) {
            return;
        }
        int generation = ++catalogGeneration;
        emptyState.setText("正在加载表情库");
        catalogExecutor.execute(() -> {
            try {
                EmojiCatalog loaded = EmojiFileStore.getCatalog(this);
                EmojiSelectionStore.Selection selection =
                        EmojiSelectionStore.resolve(this, loaded);
                mainHandler.post(() -> applyCatalogResult(generation, loaded, selection));
            } catch (Exception exception) {
                mainHandler.post(() -> applyCatalogFailure(generation));
            }
        });
    }

// 方法作用：把异步加载结果应用到当前界面或状态（applyCatalogResult）。
    private void applyCatalogResult(
            int generation,
            EmojiCatalog loaded,
            EmojiSelectionStore.Selection selection) {
        if (destroyed || generation != catalogGeneration) {
            return;
        }
        catalog = loaded;
        selectedGalleryId = selection.getGallery() == null
                ? null : selection.getGallery().getId();
        selectedPackId = selection.getPack() == null ? null : selection.getPack().getId();
        selectedItemId = firstItemId(selection.getPack());
        renderCatalog();
    }

// 方法作用：把异步加载结果应用到当前界面或状态（applyCatalogFailure）。
    private void applyCatalogFailure(int generation) {
        if (destroyed || generation != catalogGeneration) {
            return;
        }
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

// 方法作用：构建并更新用户界面内容（renderCatalog）。
    private void renderCatalog() {
        renderGalleries();
        renderPacks();
        renderItems();
        EmojiSelectionStore.save(this, selectedGalleryId, selectedPackId);
    }

// 方法作用：构建并更新用户界面内容（renderGalleries）。
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

// 方法作用：构建并更新用户界面内容（renderPacks）。
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

// 方法作用：构建并更新用户界面内容（renderItems）。
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

// 方法作用：根据候选条件选择并返回目标项（selectGallery）。
    private void selectGallery(String galleryId) {
        selectedGalleryId = galleryId;
        List<EmojiCatalog.Pack> packs = catalog.getPacksForGallery(galleryId);
        selectedPackId = packs.isEmpty() ? null : packs.get(0).getId();
        selectedItemId = packs.isEmpty() ? null : firstItemId(packs.get(0));
        renderCatalog();
    }

// 方法作用：根据候选条件选择并返回目标项（selectPack）。
    private void selectPack(String packId) {
        selectedPackId = packId;
        selectedItemId = firstItemId(selectedPack());
        renderPacks();
        renderItems();
        EmojiSelectionStore.save(this, selectedGalleryId, selectedPackId);
    }

// 方法作用：把当前选中的内容发送到目标输入框或分享面板（sendEmoji）。
    private void sendEmoji(EmojiCatalog.Item item) {
        InputConnection connection = getCurrentInputConnection();
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (connection == null) {
            showResult("没有可用的输入框");
            return;
        }
        try {
            Uri uri = EmojiFileStore.getUri(this, item);
            String mimeType = item.getMimeType();
            // 优先走标准 rich-content 提交；目标编辑器拒绝后再降级为分享流程。
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
            reloadCatalogAsync();
        }
    }

// 方法作用：创建并发起图片分享流程（shareSelectedManually）。
    private void shareSelectedManually() {
        EmojiCatalog.Item item = selectedItem();
        if (item == null) {
            showResult("当前没有可分享的表情");
            return;
        }
        try {
            shareImage(
                    getCurrentInputEditorInfo(),
                    EmojiFileStore.getUri(this, item),
                    item.getMimeType(),
                    null);
        } catch (Exception exception) {
            showResult("所选表情不可用");
        }
    }

// 方法作用：创建并发起图片分享流程（shareImage）。
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

// 方法作用：显示或打开对应的交互界面（showShareResult）。
    private void showShareResult(String pathReason, String result) {
        showResult(TextUtils.isEmpty(pathReason) ? result : pathReason + "；" + result);
    }

// 方法作用：把文本或富内容提交到当前输入连接（commitContent）。
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

// 方法作用：判断当前对象是否满足指定条件（supportsMimeType）。
    private boolean supportsMimeType(EditorInfo editorInfo, String mimeType) {
        return editorInfo != null && ImageSendPolicy.supportsMimeType(
                EditorInfoCompat.getContentMimeTypes(editorInfo),
                mimeType,
                ClipDescription::compareMimeTypes);
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（updateCompatibilityStatus）。
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

// 方法作用：根据候选条件选择并返回目标项（selectedPack）。
    private EmojiCatalog.Pack selectedPack() {
        return catalog == null ? null : catalog.getPack(selectedPackId);
    }

// 方法作用：根据候选条件选择并返回目标项（selectedItem）。
    private EmojiCatalog.Item selectedItem() {
        return findItem(selectedPack(), selectedItemId);
    }

// 方法作用：根据输入条件查询并返回匹配结果（findItem）。
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

// 方法作用：取得集合中第一个可用元素的标识（firstItemId）。
    private static String firstItemId(EmojiCatalog.Pack pack) {
        return pack == null || pack.getItems().isEmpty()
                ? null : pack.getItems().get(0).getId();
    }

// 方法作用：根据选择状态更新控件的颜色和样式（styleSelection）。
    private void styleSelection(Button button, boolean selected) {
        button.setBackgroundColor(selected ? selectedColor() : railColor());
        button.setTextColor(selected ? selectedTextColor() : primaryTextColor());
    }

// 方法作用：创建统一样式的按钮并绑定点击监听器（button）。
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

// 方法作用：创建统一样式的文本标签（label）。
    private TextView label(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(primaryTextColor());
        label.setGravity(Gravity.CENTER);
        if (textMode && customBackgroundActive) {
            label.setShadowLayer(3f, 0f, 1f, Color.BLACK);
        }
        return label;
    }

// 方法作用：更新对象状态或注册回调（setStatus）。
    private void setStatus(String message) {
        if (compatibilityStatus != null) {
            compatibilityStatus.setText(message);
        }
    }

// 方法作用：显示或打开对应的交互界面（showResult）。
    private void showResult(String message) {
        setStatus(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

// 方法作用：处理 surfaceColor 对应的输入并返回或更新相关结果（surfaceColor）。
    private int surfaceColor() {
        return isNightMode() ? 0xff202124 : 0xffd9dde3;
    }

// 方法作用：在相关数据表示之间进行转换（toolbarColor）。
    private int toolbarColor() {
        return isNightMode() ? 0xff24262a : Color.WHITE;
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
        return isNightMode() ? Color.WHITE : 0xff12345b;
    }

// 方法作用：处理 primaryTextColor 对应的输入并返回或更新相关结果（primaryTextColor）。
    private int primaryTextColor() {
        if (textMode && customBackgroundActive) {
            return Color.WHITE;
        }
        return isNightMode() ? 0xfff2f2f2 : 0xff202124;
    }

// 方法作用：处理 secondaryTextColor 对应的输入并返回或更新相关结果（secondaryTextColor）。
    private int secondaryTextColor() {
        if (textMode && customBackgroundActive) {
            return 0xddffffff;
        }
        return isNightMode() ? 0xffb7b7b7 : 0xff686b70;
    }

// 方法作用：处理 unifiedLetterAreaBackground 对应的输入并返回或更新相关结果（unifiedLetterAreaBackground）。
    private GradientDrawable unifiedLetterAreaBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(textMode && customBackgroundActive
                ? 0x11000000
                : (isNightMode() ? 0xff34363a : 0xfffafbfc));
        background.setCornerRadius(textMode && customBackgroundActive ? 0f : dp(5));
        return background;
    }

// 方法作用：处理 borderlessKeyBackground 对应的输入并返回或更新相关结果（borderlessKeyBackground）。
    private RippleDrawable borderlessKeyBackground() {
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        return new RippleDrawable(
                ColorStateList.valueOf(isNightMode() ? 0x33ffffff : 0x22000000),
                content,
                mask);
    }

// 方法作用：处理 controlKeyBackground 对应的输入并返回或更新相关结果（controlKeyBackground）。
    private RippleDrawable controlKeyBackground() {
        GradientDrawable content = new GradientDrawable();
        content.setColor(textMode && customBackgroundActive
                ? 0x26000000
                : (isNightMode() ? 0xff45484e : 0xffc7cdd5));
        content.setCornerRadius(dp(5));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(5));
        return new RippleDrawable(
                ColorStateList.valueOf(isNightMode() ? 0x33ffffff : 0x22000000),
                content,
                mask);
    }

// 方法作用：判断当前对象是否满足指定条件（isNightMode）。
    private boolean isNightMode() {
        int mask = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mask == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

// 方法作用：创建匹配父容器尺寸的布局参数（matchWrap）。
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

// 方法作用：处理 dp 对应的输入并返回或更新相关结果（dp）。
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
