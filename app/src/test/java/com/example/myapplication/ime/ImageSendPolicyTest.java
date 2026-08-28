package com.example.myapplication.ime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

// 类作用：定义 ImageSendPolicyTest，承载所在模块的主要职责。
public class ImageSendPolicyTest {
// 方法作用：处理 supportedMimeAttemptsStandardCommit 对应的输入并返回或更新相关结果（supportedMimeAttemptsStandardCommit）。
    @Test
    public void supportedMimeAttemptsStandardCommit() {
        boolean supported = ImageSendPolicy.supportsMimeType(
                new String[]{"image/*"},
                "image/png",
                (actual, accepted) -> "image/png".equals(actual)
                        && "image/*".equals(accepted));

        assertEquals(
                ImageSendPolicy.InitialAction.COMMIT,
                ImageSendPolicy.initialAction(supported));
    }

// 方法作用：处理 emptyMimeDeclarationUsesShare 对应的输入并返回或更新相关结果（emptyMimeDeclarationUsesShare）。
    @Test
    public void emptyMimeDeclarationUsesShare() {
        boolean supported = ImageSendPolicy.supportsMimeType(
                new String[0],
                "image/png",
                (actual, accepted) -> true);

        assertEquals(
                ImageSendPolicy.InitialAction.SHARE,
                ImageSendPolicy.initialAction(supported));
    }

// 方法作用：处理 mismatchedMimeDeclarationUsesShare 对应的输入并返回或更新相关结果（mismatchedMimeDeclarationUsesShare）。
    @Test
    public void mismatchedMimeDeclarationUsesShare() {
        boolean supported = ImageSendPolicy.supportsMimeType(
                new String[]{"image/jpeg"},
                "image/png",
                (actual, accepted) -> actual.equals(accepted));

        assertEquals(
                ImageSendPolicy.InitialAction.SHARE,
                ImageSendPolicy.initialAction(supported));
    }

// 方法作用：处理 acceptedCommitStopsWithoutShare 对应的输入并返回或更新相关结果（acceptedCommitStopsWithoutShare）。
    @Test
    public void acceptedCommitStopsWithoutShare() {
        assertEquals(
                ImageSendPolicy.AfterCommitAction.STOP,
                ImageSendPolicy.afterCommit(true));
    }

// 方法作用：处理 rejectedCommitUsesShare 对应的输入并返回或更新相关结果（rejectedCommitUsesShare）。
    @Test
    public void rejectedCommitUsesShare() {
        assertEquals(
                ImageSendPolicy.AfterCommitAction.SHARE,
                ImageSendPolicy.afterCommit(false));
    }
}
