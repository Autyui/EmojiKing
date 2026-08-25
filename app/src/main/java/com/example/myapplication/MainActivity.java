package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.ime.EmojiInputMethodService;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_IMAGE = 1001;
    private ImageView preview;
    private ImageView receivedPreview;
    private TextView receiveStatus;
    private TextView status;
    private Bitmap receivedBitmap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("本地图库 + 输入法兼容性原型");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("先导入或使用示例图片，再启用输入法。输入法会优先直接提交图片，不支持时打开当前应用分享。 ");
        description.setTextSize(15);
        description.setPadding(0, dp(12), 0, dp(12));
        root.addView(description, matchWrap());

        preview = new ImageView(this);
        preview.setBackgroundColor(0xffeeeeee);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        refreshPreview();

        Button importButton = new Button(this);
        importButton.setText("导入一张本地图片");
        importButton.setOnClickListener(view -> openImagePicker());
        root.addView(importButton, matchWrap());

        Button settingsButton = new Button(this);
        settingsButton.setText("打开系统输入法设置");
        settingsButton.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(settingsButton, matchWrap());

        Button pickerButton = new Button(this);
        pickerButton.setText("弹出输入法选择器");
        pickerButton.setOnClickListener(view -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.showInputMethodPicker();
            }
        });
        root.addView(pickerButton, matchWrap());

        Button shareButton = new Button(this);
        shareButton.setText("通过系统分享测试图片");
        shareButton.setOnClickListener(view -> shareCurrentImage());
        root.addView(shareButton, matchWrap());

        RichContentEditText testInput = new RichContentEditText(this);
        testInput.setHint("本地接收测试：点击并切换到本地表情输入法");
        testInput.setMinLines(2);
        testInput.setGravity(Gravity.TOP | Gravity.START);
        testInput.setListener(new RichContentEditText.Listener() {
            @Override
            public void onImageReceived(Bitmap bitmap, String mimeType) {
                if (receivedBitmap != null && receivedBitmap != bitmap) {
                    receivedBitmap.recycle();
                }
                receivedBitmap = bitmap;
                receivedPreview.setImageBitmap(bitmap);
                receivedPreview.setVisibility(ImageView.VISIBLE);
                receiveStatus.setText("本地链路通过：已接收并读取 " + mimeType);
                Toast.makeText(MainActivity.this, "图片接收成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onImageRejected(String reason) {
                receiveStatus.setText("本地链路失败：" + reason);
                Toast.makeText(MainActivity.this, reason, Toast.LENGTH_LONG).show();
            }
        });
        root.addView(testInput, matchWrap());

        receiveStatus = new TextView(this);
        receiveStatus.setText("本地接收结果：等待测试");
        receiveStatus.setTextSize(14);
        receiveStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        receiveStatus.setPadding(0, dp(10), 0, dp(8));
        root.addView(receiveStatus, matchWrap());

        receivedPreview = new ImageView(this);
        receivedPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        receivedPreview.setVisibility(ImageView.GONE);
        root.addView(receivedPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(14), 0, 0);
        root.addView(status, matchWrap());

        setContentView(scrollView);
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        receivedPreview.setImageDrawable(null);
        if (receivedBitmap != null) {
            receivedBitmap.recycle();
            receivedBitmap = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) {
            updateStatus();
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri source = data.getData();
        if (source == null) {
            return;
        }
        try {
            EmojiFileStore.importImage(this, source);
            refreshPreview();
            updateStatus();
            Toast.makeText(this, "图片已导入", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "导入失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareCurrentImage() {
        try {
            File file = EmojiFileStore.getCurrentFile(this);
            ImageShareSender.Result result = ImageShareSender.send(
                    this,
                    null,
                    EmojiFileStore.getCurrentUri(this),
                    EmojiFileStore.getMimeType(file));
            if (result == ImageShareSender.Result.TARGET_STARTED) {
                Toast.makeText(this, "已打开目标应用分享", Toast.LENGTH_SHORT).show();
            } else if (result == ImageShareSender.Result.CHOOSER_STARTED) {
                Toast.makeText(this, "已打开系统分享选择器", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "没有可用的图片分享入口，或分享启动失败", Toast.LENGTH_LONG).show();
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            Toast.makeText(this, "当前图片无法分享", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshPreview() {
        File file = EmojiFileStore.getCurrentFile(this);
        preview.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath()));
    }

    private void updateStatus() {
        File file = EmojiFileStore.getCurrentFile(this);
        boolean enabled = isInputMethodEnabled();
        status.setText("当前图片：" + file.getName() + "\n"
                + "输入法状态：" + (enabled ? "已启用，可在输入框中切换" : "未启用，请先打开设置")
                + "\n发送策略：支持时标准提交，否则定向分享");
    }

    private boolean isInputMethodEnabled() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager == null) {
            return false;
        }
        List<InputMethodInfo> methods = manager.getEnabledInputMethodList();
        for (InputMethodInfo method : methods) {
            if (getPackageName().equals(method.getPackageName())
                    && EmojiInputMethodService.class.getName().equals(method.getServiceName())) {
                return true;
            }
        }
        return false;
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
