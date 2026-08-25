package com.example.myapplication.ime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageSendPolicyTest {
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

    @Test
    public void acceptedCommitStopsWithoutShare() {
        assertEquals(
                ImageSendPolicy.AfterCommitAction.STOP,
                ImageSendPolicy.afterCommit(true));
    }

    @Test
    public void rejectedCommitUsesShare() {
        assertEquals(
                ImageSendPolicy.AfterCommitAction.SHARE,
                ImageSendPolicy.afterCommit(false));
    }
}
