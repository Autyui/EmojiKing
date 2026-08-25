package com.example.myapplication.ime;

import android.content.ClipDescription;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import com.example.myapplication.EmojiFileStore;
import com.example.myapplication.ImageShareSender;

import java.io.File;

/** Keyboard that commits supported images and otherwise opens a targeted share. */
public class EmojiInputMethodService extends InputMethodService {
    private TextView compatibilityStatus;

    @Override
    public boolean onEvaluateInputViewShown() {
        // The emoji panel remains useful when a hardware keyboard is connected.
        super.onEvaluateInputViewShown();
        return true;
    }

    @Override
    public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(12));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("本地表情输入法");
        title.setTextColor(Color.DKGRAY);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        ImageButton imageButton = new ImageButton(this);
        imageButton.setImageBitmap(android.graphics.BitmapFactory.decodeFile(
                EmojiFileStore.getCurrentFile(this).getAbsolutePath()));
        imageButton.setContentDescription("发送本地表情");
        imageButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        imageButton.setBackgroundColor(0xfff4f4f4);
        imageButton.setOnClickListener(view -> sendCurrentImage());
        root.addView(imageButton, new LinearLayout.LayoutParams(dp(96), dp(96)));

        Button shareButton = new Button(this);
        shareButton.setText("分享发送");
        shareButton.setAllCaps(false);
        shareButton.setOnClickListener(view -> shareCurrentImageManually());
        root.addView(shareButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        compatibilityStatus = new TextView(this);
        compatibilityStatus.setTextColor(Color.GRAY);
        compatibilityStatus.setTextSize(12);
        compatibilityStatus.setGravity(Gravity.CENTER);
        compatibilityStatus.setMaxLines(3);
        compatibilityStatus.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(compatibilityStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));
        updateCompatibilityStatus(getCurrentInputEditorInfo());
        return root;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        updateCompatibilityStatus(attribute);
    }

    private void sendCurrentImage() {
        InputConnection connection = getCurrentInputConnection();
        EditorInfo editorInfo = getCurrentInputEditorInfo();
        if (connection == null) {
            showResult("没有可用的输入框");
            return;
        }

        try {
            File file = EmojiFileStore.getCurrentFile(this);
            Uri uri = EmojiFileStore.getCurrentUri(this);
            String mimeType = EmojiFileStore.getMimeType(file);
            boolean mimeSupported = supportsMimeType(editorInfo, mimeType);
            ImageSendPolicy.InitialAction action = ImageSendPolicy.initialAction(mimeSupported);

            if (action == ImageSendPolicy.InitialAction.COMMIT) {
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
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showResult("当前图片不可用");
        }
    }

    private void shareCurrentImageManually() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            showResult("没有可用的输入框");
            return;
        }

        try {
            File file = EmojiFileStore.getCurrentFile(this);
            shareImage(
                    getCurrentInputEditorInfo(),
                    EmojiFileStore.getCurrentUri(this),
                    EmojiFileStore.getMimeType(file),
                    null);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            showResult("当前图片不可用");
        }
    }

    private void shareImage(
            EditorInfo editorInfo,
            Uri uri,
            String mimeType,
            String pathReason) {
        String targetPackage = editorInfo == null ? null : editorInfo.packageName;
        ImageShareSender.Result result = ImageShareSender.send(
                this,
                targetPackage,
                uri,
                mimeType);
        if (result == ImageShareSender.Result.TARGET_STARTED) {
            showShareResult(pathReason, "已打开目标应用分享");
        } else if (result == ImageShareSender.Result.CHOOSER_STARTED) {
            if (TextUtils.isEmpty(targetPackage)) {
                showShareResult(pathReason, "无法识别当前应用，已打开系统选择器");
            } else {
                showShareResult(pathReason, "目标应用不支持图片分享，已打开系统选择器");
            }
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
        if (editorInfo == null) {
            return false;
        }
        return ImageSendPolicy.supportsMimeType(
                EditorInfoCompat.getContentMimeTypes(editorInfo),
                mimeType,
                ClipDescription::compareMimeTypes);
    }

    private void updateCompatibilityStatus(EditorInfo editorInfo) {
        if (compatibilityStatus == null) {
            return;
        }
        if (editorInfo == null) {
            compatibilityStatus.setText("等待输入框连接");
            return;
        }
        String currentMimeType = EmojiFileStore.getMimeType(EmojiFileStore.getCurrentFile(this));
        if (supportsMimeType(editorInfo, currentMimeType)) {
            compatibilityStatus.setText("支持 " + currentMimeType + "；点击图片将直接提交");
        } else {
            compatibilityStatus.setText("不支持 " + currentMimeType + "；点击图片将打开当前应用分享");
        }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
