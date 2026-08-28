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

// 类作用：定义 ImageShareSenderInstrumentedTest，承载所在模块的主要职责。
@RunWith(AndroidJUnit4.class)
public class ImageShareSenderInstrumentedTest {
// 方法作用：把当前选中的内容发送到目标输入框或分享面板（sendIntentContainsStreamClipDataAndReadGrant）。
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

// 方法作用：根据候选条件选择并返回目标项（chooserCanStartOutsideAnActivity）。
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

// 方法作用：处理 targetIntentRestrictsShareToCurrentEditorPackage 对应的输入并返回或更新相关结果（targetIntentRestrictsShareToCurrentEditorPackage）。
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
