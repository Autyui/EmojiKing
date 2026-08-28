package com.example.myapplication.catalog;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

// 类作用：定义 AtomicTextFileTest，承载所在模块的主要职责。
public class AtomicTextFileTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

// 方法作用：将对象转换后写入目标存储（writePersistsUtf8Content）。
    @Test
    public void writePersistsUtf8Content() throws Exception {
        File target = new File(temporaryFolder.newFolder("catalog"), "catalog.json");
        AtomicTextFile file = new AtomicTextFile(target);

        file.write("本地表情\n");

        assertEquals("本地表情\n", file.readOrNull());
    }

// 方法作用：处理 failedActivationRestoresPreviousCatalog 对应的输入并返回或更新相关结果（failedActivationRestoresPreviousCatalog）。
    @Test
    public void failedActivationRestoresPreviousCatalog() throws Exception {
        File target = temporaryFolder.newFile("catalog.json");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        AtomicTextFile file = new AtomicTextFile(target, (source, destination) -> {
            if (source.getName().endsWith(".new")) {
                return false;
            }
            return source.renameTo(destination);
        });

        try {
            file.write("new");
            fail("Expected activation failure");
        } catch (IOException expected) {
            assertEquals("old", file.readOrNull());
            assertFalse(new File(target.getParentFile(), "catalog.json.bak").exists());
        }
    }

// 方法作用：从输入源读取并转换数据（readRestoresBackupLeftByInterruptedUpdate）。
    @Test
    public void readRestoresBackupLeftByInterruptedUpdate() throws Exception {
        File directory = temporaryFolder.newFolder("recovery");
        File target = new File(directory, "catalog.json");
        File backup = new File(directory, "catalog.json.bak");
        Files.write(backup.toPath(), "recoverable".getBytes(StandardCharsets.UTF_8));

        String recovered = new AtomicTextFile(target).readOrNull();

        assertEquals("recoverable", recovered);
        assertTrue(target.isFile());
        assertFalse(backup.exists());
    }
}
