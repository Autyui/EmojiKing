package com.example.myapplication.ime;

/** 决定图片应提交到输入框、发起分享还是直接拒绝。 */
// 类作用：定义 ImageSendPolicy，承载所在模块的主要职责。
final class ImageSendPolicy {
// 类作用：定义 MimeMatcher，承载所在模块的主要职责。
    interface MimeMatcher {
// 方法作用：创建匹配父容器尺寸的布局参数（matches）。
        boolean matches(String actualMimeType, String acceptedMimeType);
    }

// 类作用：定义 InitialAction，承载所在模块的主要职责。
    enum InitialAction {
        COMMIT,
        SHARE
    }

// 类作用：定义 AfterCommitAction，承载所在模块的主要职责。
    enum AfterCommitAction {
        STOP,
        SHARE
    }

// 方法作用：初始化 ImageSendPolicy 对象并建立其运行所需状态。
    private ImageSendPolicy() {
    }

// 方法作用：处理 initialAction 对应的输入并返回或更新相关结果（initialAction）。
    static InitialAction initialAction(boolean mimeSupported) {
        return mimeSupported ? InitialAction.COMMIT : InitialAction.SHARE;
    }

// 方法作用：处理 afterCommit 对应的输入并返回或更新相关结果（afterCommit）。
    static AfterCommitAction afterCommit(boolean committed) {
        return committed ? AfterCommitAction.STOP : AfterCommitAction.SHARE;
    }

// 方法作用：判断当前对象是否满足指定条件（supportsMimeType）。
    static boolean supportsMimeType(
            String[] acceptedMimeTypes,
            String actualMimeType,
            MimeMatcher matcher) {
        if (acceptedMimeTypes == null || actualMimeType == null) {
            return false;
        }
        for (String acceptedMimeType : acceptedMimeTypes) {
            if (acceptedMimeType != null
                    && matcher.matches(actualMimeType, acceptedMimeType)) {
                return true;
            }
        }
        return false;
    }
}
