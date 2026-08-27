package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Starts a targeted image share, falling back to the system chooser when needed. */
// 类作用：定义 ImageShareSender，承载所在模块的主要职责。
public final class ImageShareSender {
    private static final String CLIP_LABEL = "Aut image";
    private static final String CHOOSER_TITLE = "发送图片";

// 类作用：定义 Result，承载所在模块的主要职责。
    public enum Result {
        TARGET_STARTED,
        CHOOSER_STARTED,
        FAILED
    }

// 类作用：定义 Destination，承载所在模块的主要职责。
    enum Destination {
        TARGET,
        CHOOSER,
        NONE
    }

// 方法作用：初始化 ImageShareSender 对象并建立其运行所需状态。
    private ImageShareSender() {
    }

// 方法作用：把当前选中的内容发送到目标输入框或分享面板（send）。
    @NonNull
    public static Result send(
            @NonNull Context context,
            @Nullable String targetPackage,
            @Nullable Uri contentUri,
            @Nullable String mimeType) {
        if (contentUri == null || mimeType == null || mimeType.trim().isEmpty()) {
            return Result.FAILED;
        }

        try {
            Intent sendIntent = createSendIntent(context, contentUri, mimeType);
            String normalizedPackage = normalizePackage(targetPackage);
            Intent targetIntent = normalizedPackage == null
                    ? null
                    : createTargetIntent(sendIntent, normalizedPackage);
            boolean targetAvailable = targetIntent != null && canResolve(context, targetIntent);
            boolean chooserAvailable = targetAvailable || canResolve(context, sendIntent);
            Destination destination = selectDestination(
                    normalizedPackage,
                    targetAvailable,
                    chooserAvailable);

            if (destination == Destination.TARGET && targetIntent != null) {
                try {
                    context.startActivity(targetIntent);
                    return Result.TARGET_STARTED;
                } catch (ActivityNotFoundException exception) {
                    destination = canResolve(context, sendIntent)
                            ? Destination.CHOOSER
                            : Destination.NONE;
                }
            }

            if (destination == Destination.CHOOSER) {
                context.startActivity(createChooserIntent(sendIntent));
                return Result.CHOOSER_STARTED;
            }
            return Result.FAILED;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException exception) {
            return Result.FAILED;
        }
    }

// 方法作用：创建并返回新的业务对象或界面对象（createSendIntent）。
    @NonNull
    static Intent createSendIntent(
            @NonNull Context context,
            @NonNull Uri contentUri,
            @NonNull String mimeType) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType(mimeType);
        sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        sendIntent.setClipData(ClipData.newUri(
                context.getContentResolver(),
                CLIP_LABEL,
                contentUri));
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return sendIntent;
    }

// 方法作用：创建并返回新的业务对象或界面对象（createChooserIntent）。
    @NonNull
    static Intent createChooserIntent(@NonNull Intent sendIntent) {
        Intent chooser = Intent.createChooser(sendIntent, CHOOSER_TITLE);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooser;
    }

// 方法作用：创建并返回新的业务对象或界面对象（createTargetIntent）。
    @NonNull
    static Intent createTargetIntent(
            @NonNull Intent sendIntent,
            @NonNull String targetPackage) {
        return new Intent(sendIntent).setPackage(targetPackage);
    }

// 方法作用：根据候选条件选择并返回目标项（selectDestination）。
    @NonNull
    static Destination selectDestination(
            @Nullable String targetPackage,
            boolean targetAvailable,
            boolean chooserAvailable) {
        if (normalizePackage(targetPackage) != null && targetAvailable) {
            return Destination.TARGET;
        }
        if (chooserAvailable) {
            return Destination.CHOOSER;
        }
        return Destination.NONE;
    }

// 方法作用：判断当前对象是否满足指定条件（canResolve）。
    private static boolean canResolve(@NonNull Context context, @NonNull Intent intent) {
        return context.getPackageManager().resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

// 方法作用：清理并规范化输入值（normalizePackage）。
    @Nullable
    private static String normalizePackage(@Nullable String targetPackage) {
        if (targetPackage == null) {
            return null;
        }
        String trimmed = targetPackage.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
