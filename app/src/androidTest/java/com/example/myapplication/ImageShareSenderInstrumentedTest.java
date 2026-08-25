package com.example.myapplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImageShareSenderInstrumentedTest {
    @Test
    public void sendIntentContainsStreamClipDataAndReadGrant() {
        Context context = ApplicationProvider.getApplicationContext();
        Uri uri = Uri.parse("content://com.example.test/emoji/current.png");

        Intent intent = ImageShareSender.createSendIntent(context, uri, "image/png");

        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("image/png", intent.getType());
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertNotNull(intent.getClipData());
        assertEquals(uri, intent.getClipData().getItemAt(0).getUri());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void chooserCanStartOutsideAnActivity() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent sendIntent = ImageShareSender.createSendIntent(
                context,
                Uri.parse("content://com.example.test/emoji/current.png"),
                "image/png");

        Intent chooser = ImageShareSender.createChooserIntent(sendIntent);

        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertTrue((chooser.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void targetIntentRestrictsShareToCurrentEditorPackage() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent sendIntent = ImageShareSender.createSendIntent(
                context,
                Uri.parse("content://com.example.test/emoji/current.png"),
                "image/png");

        Intent targetIntent = ImageShareSender.createTargetIntent(
                sendIntent,
                "com.example.editor");

        assertEquals("com.example.editor", targetIntent.getPackage());
        assertEquals(Intent.ACTION_SEND, targetIntent.getAction());
    }
}
