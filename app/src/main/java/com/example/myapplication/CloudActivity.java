package com.example.myapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.catalog.EmojiCatalog;
import com.example.myapplication.catalog.LocalEmojiCatalogRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 云端管理页面，负责维护当前机器码对应的远程图片。 */
// 类作用：定义 CloudActivity，承载所在模块的主要职责。
public final class CloudActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<CheckBox> imageChecks = new ArrayList<>();
    private final List<Button> actionButtons = new ArrayList<>();
    private List<ImageHostingClient.RemoteImage> remoteImages = Collections.emptyList();
    private LinearLayout imageList;
    private TextView machineCodeView;
    private TextView addressView;
    private TextView packView;
    private TextView statusView;
    private Button deleteButton;

// 方法作用：按生命周期创建并初始化界面或服务状态（onCreate）。
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("云端");
        setContentView(buildLayout());
        loadRemoteImages();
    }

// 方法作用：按生命周期释放线程、监听器和其他资源（onDestroy）。
    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

// 方法作用：构建并更新用户界面内容（buildLayout）。
    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(surfaceColor());

        TextView title = label("云端表情管理", 22);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        machineCodeView = label("", 12);
        machineCodeView.setTextColor(secondaryTextColor());
        root.addView(machineCodeView, matchWrap());
        addressView = label("", 12);
        addressView.setTextColor(secondaryTextColor());
        addressView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        addressView.setSingleLine(true);
        root.addView(addressView, matchWrap());
        packView = label("", 13);
        packView.setTextColor(primaryTextColor());
        root.addView(packView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button upload = button("上传当前包", view -> uploadCurrentPack());
        Button sync = button("同步云端", view -> syncToCurrentPack());
        Button configure = button("修改地址", view -> showAddressDialog());
        actionButtons.add(upload);
        actionButtons.add(sync);
        actionButtons.add(configure);
        actions.addView(upload, new LinearLayout.LayoutParams(0, dp(48), 1f));
        actions.addView(sync, new LinearLayout.LayoutParams(0, dp(48), 1f));
        actions.addView(configure, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(actions, matchWrap());

        imageList = new LinearLayout(this);
        imageList.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(imageList, matchWrap());
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        deleteButton = button("删除已选云端图片", view -> confirmDeleteSelected());
        root.addView(deleteButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        statusView = label("正在读取云端…", 12);
        statusView.setTextColor(secondaryTextColor());
        root.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        updateHeader();
        return root;
    }

// 方法作用：重新计算并刷新当前显示或缓存状态（updateHeader）。
    private void updateHeader() {
        machineCodeView.setText("机器码：" + ImageHostingClient.getMachineCode(this));
        addressView.setText("云端地址：" + ImageHostingClient.getConfiguredBaseUrl(this));
        EmojiCatalog catalog;
        try {
            catalog = EmojiFileStore.getCatalog(this);
            EmojiSelectionStore.Selection selection = EmojiSelectionStore.resolve(this, catalog);
            EmojiCatalog.Pack pack = selection.getPack();
            packView.setText(pack == null
                    ? "当前表情包：未选择"
                    : "当前表情包：" + pack.getName() + "（" + pack.getItems().size() + " 张）");
        } catch (Exception exception) {
            packView.setText("当前表情包：读取失败");
        }
    }

// 方法作用：从文件、网络或内存加载数据（loadRemoteImages）。
    private void loadRemoteImages() {
        if (ImageHostingClient.getConfiguredBaseUrl(this).trim().isEmpty()) {
            setBusy(false, "请先配置云端地址");
            return;
        }
        setBusy(true, "正在读取云端图片…");
        executor.execute(() -> {
            try {
                ImageHostingClient client = new ImageHostingClient(
                        ImageHostingClient.getConfiguredBaseUrl(this));
                List<ImageHostingClient.RemoteImage> images = client.list(
                        ImageHostingClient.getMachineCode(this));
                runOnUiThread(() -> {
                    remoteImages = images;
                    renderImages();
                    setBusy(false, "云端共有 " + images.size() + " 张图片");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> setFailure(exception));
            }
        });
    }

// 方法作用：构建并更新用户界面内容（renderImages）。
    private void renderImages() {
        imageList.removeAllViews();
        imageChecks.clear();
        if (remoteImages.isEmpty()) {
            imageList.addView(label("当前机器码没有云端图片", 14), matchWrap());
            return;
        }
        for (ImageHostingClient.RemoteImage image : remoteImages) {
            CheckBox check = new CheckBox(this);
            check.setText(image.getFileName() + "  " + formatBytes(image.getSize()));
            check.setContentDescription("选择云端图片 " + image.getFileName());
            check.setPadding(dp(4), 0, dp(4), 0);
            imageChecks.add(check);
            imageList.addView(check, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
    }

// 方法作用：处理 uploadCurrentPack 对应的输入并返回或更新相关结果（uploadCurrentPack）。
    private void uploadCurrentPack() {
        EmojiSelectionStore.Selection selection = currentSelection();
        EmojiCatalog.Pack pack = selection == null ? null : selection.getPack();
        if (pack == null) {
            toast("当前没有可上传的表情包");
            return;
        }
        String baseUrl = ImageHostingClient.getConfiguredBaseUrl(this);
        if (baseUrl.trim().isEmpty()) {
            toast("请先配置云端地址");
            return;
        }
        setBusy(true, "正在上传当前表情包…");
        executor.execute(() -> {
            int uploaded = 0;
            int failed = 0;
            try {
                ImageHostingClient client = new ImageHostingClient(baseUrl);
                String machineCode = ImageHostingClient.getMachineCode(this);
                for (EmojiCatalog.Item item : pack.getItems()) {
                    try {
                        LocalEmojiCatalogRepository.StoredEmoji stored =
                                EmojiFileStore.getStoredEmoji(this, item.getId());
                        client.upload(machineCode, stored.getFile(), item.getName(), item.getMimeType());
                        uploaded++;
                    } catch (Exception exception) {
                        failed++;
                    }
                }
                int finalUploaded = uploaded;
                int finalFailed = failed;
                runOnUiThread(() -> setBusy(false,
                        "已上传 " + finalUploaded + " 张，失败 " + finalFailed + " 张"));
            } catch (Exception exception) {
                runOnUiThread(() -> setFailure(exception));
            }
        });
    }

// 方法作用：处理 syncToCurrentPack 对应的输入并返回或更新相关结果（syncToCurrentPack）。
    private void syncToCurrentPack() {
        EmojiSelectionStore.Selection selection = currentSelection();
        EmojiCatalog.Pack pack = selection == null ? null : selection.getPack();
        if (pack == null) {
            toast("当前没有可同步的表情包");
            return;
        }
        String baseUrl = ImageHostingClient.getConfiguredBaseUrl(this);
        if (baseUrl.trim().isEmpty()) {
            toast("请先配置云端地址");
            return;
        }
        setBusy(true, "正在同步云端图片…");
        executor.execute(() -> {
            File directory = new File(getCacheDir(), "emoji-cloud-sync");
            int imported = 0;
            int duplicate = 0;
            try {
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IOException("无法创建云端同步目录");
                }
                ImageHostingClient client = new ImageHostingClient(baseUrl);
                List<ImageHostingClient.RemoteImage> images = client.list(
                        ImageHostingClient.getMachineCode(this));
                for (ImageHostingClient.RemoteImage image : images) {
                    File staged = File.createTempFile("remote-", ".image", directory);
                    try {
                        client.download(image, staged);
                        LocalEmojiCatalogRepository.ImportResult result =
                                EmojiFileStore.importFile(this, staged, image.getFileName(), pack.getId());
                        if (result.isDuplicate()) {
                            duplicate++;
                        } else {
                            imported++;
                        }
                    } finally {
                        staged.delete();
                    }
                }
                int finalImported = imported;
                int finalDuplicate = duplicate;
                runOnUiThread(() -> setBusy(false,
                        "已同步 " + finalImported + " 张，重复 " + finalDuplicate + " 张"));
            } catch (Exception exception) {
                runOnUiThread(() -> setFailure(exception));
            }
        });
    }

// 方法作用：处理 confirmDeleteSelected 对应的输入并返回或更新相关结果（confirmDeleteSelected）。
    private void confirmDeleteSelected() {
        List<ImageHostingClient.RemoteImage> selected = selectedImages();
        if (selected.isEmpty()) {
            toast("请至少选择一张云端图片");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("批量删除云端图片")
                .setMessage("将删除当前机器码下的 " + selected.size()
                        + " 张云端图片，不影响本地表情包。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteSelected(selected))
                .show();
    }

// 方法作用：删除目标数据并清理相关引用或临时文件（deleteSelected）。
    private void deleteSelected(List<ImageHostingClient.RemoteImage> selected) {
        setBusy(true, "正在批量删除云端图片…");
        executor.execute(() -> {
            int deleted = 0;
            int failed = 0;
            try {
                ImageHostingClient client = new ImageHostingClient(
                        ImageHostingClient.getConfiguredBaseUrl(this));
                String machineCode = ImageHostingClient.getMachineCode(this);
                for (ImageHostingClient.RemoteImage image : selected) {
                    try {
                        client.delete(machineCode, image);
                        deleted++;
                    } catch (Exception exception) {
                        failed++;
                    }
                }
                int finalDeleted = deleted;
                int finalFailed = failed;
                runOnUiThread(() -> {
                    setBusy(false, "已删除 " + finalDeleted + " 张，失败 " + finalFailed + " 张");
                    loadRemoteImages();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> setFailure(exception));
            }
        });
    }

// 方法作用：显示或打开对应的交互界面（showAddressDialog）。
    private void showAddressDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(ImageHostingClient.getConfiguredBaseUrl(this));
        input.setSelection(input.length());
        new AlertDialog.Builder(this)
                .setTitle("修改云端地址")
                .setMessage("例如 https://your-project.vercel.app")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    try {
                        if (!value.isEmpty()) {
                            new ImageHostingClient(value);
                        }
                        ImageHostingClient.saveConfiguredBaseUrl(this, value);
                        updateHeader();
                        loadRemoteImages();
                    } catch (IllegalArgumentException exception) {
                        toast(exception.getMessage());
                    }
                })
                .show();
    }

// 方法作用：处理 currentSelection 对应的输入并返回或更新相关结果（currentSelection）。
    @Nullable
    private EmojiSelectionStore.Selection currentSelection() {
        try {
            return EmojiSelectionStore.resolve(this, EmojiFileStore.getCatalog(this));
        } catch (Exception exception) {
            return null;
        }
    }

// 方法作用：根据候选条件选择并返回目标项（selectedImages）。
    private List<ImageHostingClient.RemoteImage> selectedImages() {
        List<ImageHostingClient.RemoteImage> selected = new ArrayList<>();
        for (int index = 0; index < imageChecks.size(); index++) {
            if (imageChecks.get(index).isChecked()) {
                selected.add(remoteImages.get(index));
            }
        }
        return selected;
    }

// 方法作用：更新对象状态或注册回调（setBusy）。
    private void setBusy(boolean value, String message) {
        deleteButton.setEnabled(!value);
        imageList.setEnabled(!value);
        for (Button button : actionButtons) {
            button.setEnabled(!value);
        }
        if (message != null) {
            statusView.setText(message);
        }
    }

// 方法作用：更新对象状态或注册回调（setFailure）。
    private void setFailure(Exception exception) {
        setBusy(false, "云端操作失败：" + readableMessage(exception));
    }

// 方法作用：创建统一样式的按钮并绑定点击监听器（button）。
    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setOnClickListener(listener);
        return button;
    }

// 方法作用：创建统一样式的文本标签（label）。
    private TextView label(String text, int size) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(primaryTextColor());
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

// 方法作用：创建匹配父容器尺寸的布局参数（matchWrap）。
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

// 方法作用：处理 surfaceColor 对应的输入并返回或更新相关结果（surfaceColor）。
    private int surfaceColor() {
        return isNightMode() ? 0xff121212 : 0xfffafafa;
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

// 方法作用：处理 dp 对应的输入并返回或更新相关结果（dp）。
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

// 方法作用：在相关数据表示之间进行转换（toast）。
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

// 方法作用：从输入源读取并转换数据（readableMessage）。
    private static String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "未知错误" : message.trim();
    }

// 方法作用：将输入值格式化为用户可读文本（formatBytes）。
    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f);
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }
}
