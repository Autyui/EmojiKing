package com.example.myapplication.ime;

/** Decides whether an image should be committed, shared, or rejected. */
final class ImageSendPolicy {
    interface MimeMatcher {
        boolean matches(String actualMimeType, String acceptedMimeType);
    }

    enum InitialAction {
        COMMIT,
        SHARE
    }

    enum AfterCommitAction {
        STOP,
        SHARE
    }

    private ImageSendPolicy() {
    }

    static InitialAction initialAction(boolean mimeSupported) {
        return mimeSupported ? InitialAction.COMMIT : InitialAction.SHARE;
    }

    static AfterCommitAction afterCommit(boolean committed) {
        return committed ? AfterCommitAction.STOP : AfterCommitAction.SHARE;
    }

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
