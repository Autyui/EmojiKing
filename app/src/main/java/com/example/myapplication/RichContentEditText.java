package com.example.myapplication;

import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import java.io.IOException;
import java.io.InputStream;

/** 按 Android 标准富内容输入协议接收图片的测试控件。 */
// 类作用：定义 RichContentEditText，承载所在模块的主要职责。
public final class RichContentEditText extends AppCompatEditText {
    private static final String[] ACCEPTED_MIME_TYPES = {"image/*"};
    private static final int MAX_PREVIEW_SIZE = 1024;

// 类作用：定义 Listener，承载所在模块的主要职责。
    public interface Listener {
// 方法作用：处理 onImageReceived 对应的输入并返回或更新相关结果（onImageReceived）。
        void onImageReceived(@NonNull Bitmap bitmap, @NonNull String mimeType);

// 方法作用：处理 onImageRejected 对应的输入并返回或更新相关结果（onImageRejected）。
        void onImageRejected(@NonNull String reason);
    }

    @Nullable
    private Listener listener;

// 方法作用：初始化 RichContentEditText 对象并建立其运行所需状态。
    public RichContentEditText(@NonNull Context context) {
        super(context);
    }

// 方法作用：初始化 RichContentEditText 对象并建立其运行所需状态。
    public RichContentEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

// 方法作用：初始化 RichContentEditText 对象并建立其运行所需状态。
    public RichContentEditText(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

// 方法作用：更新对象状态或注册回调（setListener）。
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

// 方法作用：处理 onCreateInputConnection 对应的输入并返回或更新相关结果（onCreateInputConnection）。
    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo outAttrs) {
        InputConnection inputConnection = super.onCreateInputConnection(outAttrs);
        if (inputConnection == null) {
            return null;
        }
        EditorInfoCompat.setContentMimeTypes(outAttrs, ACCEPTED_MIME_TYPES);
        return InputConnectionCompat.createWrapper(
                inputConnection,
                outAttrs,
                this::onCommitContent);
    }

// 方法作用：处理 onCommitContent 对应的输入并返回或更新相关结果（onCommitContent）。
    private boolean onCommitContent(
            @NonNull InputContentInfoCompat content,
            int flags,
            @Nullable Bundle options) {
        String mimeType = findImageMimeType(content.getDescription());
        if (mimeType == null) {
            reject("收到的内容不是图片");
            return false;
        }

        boolean permissionRequested = false;
        try {
            if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                content.requestPermission();
                permissionRequested = true;
            }
            Bitmap bitmap = decodePreview(content);
            if (bitmap == null) {
                reject("图片无法解码");
                return false;
            }
            if (listener != null) {
                listener.onImageReceived(bitmap, mimeType);
            } else {
                bitmap.recycle();
            }
            return true;
        } catch (SecurityException exception) {
            reject("图片 URI 授权失败");
            return false;
        } catch (IOException exception) {
            reject("读取图片失败：" + exception.getMessage());
            return false;
        } finally {
            if (permissionRequested) {
                content.releasePermission();
            }
        }
    }

// 方法作用：根据输入条件查询并返回匹配结果（findImageMimeType）。
    @Nullable
    private String findImageMimeType(@NonNull ClipDescription description) {
        for (int index = 0; index < description.getMimeTypeCount(); index++) {
            String mimeType = description.getMimeType(index);
            if (ClipDescription.compareMimeTypes(mimeType, "image/*")) {
                return mimeType;
            }
        }
        return null;
    }

// 方法作用：解码输入内容并生成可用对象（decodePreview）。
    @Nullable
    private Bitmap decodePreview(@NonNull InputContentInfoCompat content) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContext().getContentResolver()
                .openInputStream(content.getContentUri())) {
            if (input == null) {
                throw new IOException("无法打开 URI");
            }
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > MAX_PREVIEW_SIZE
                || bounds.outHeight / options.inSampleSize > MAX_PREVIEW_SIZE) {
            options.inSampleSize *= 2;
        }
        try (InputStream input = getContext().getContentResolver()
                .openInputStream(content.getContentUri())) {
            if (input == null) {
                throw new IOException("无法重新打开 URI");
            }
            return BitmapFactory.decodeStream(input, null, options);
        }
    }

// 方法作用：处理 reject 对应的输入并返回或更新相关结果（reject）。
    private void reject(@NonNull String reason) {
        if (listener != null) {
            listener.onImageRejected(reason);
        }
    }
}
