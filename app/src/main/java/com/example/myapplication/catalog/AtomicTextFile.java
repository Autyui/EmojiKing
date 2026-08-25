package com.example.myapplication.catalog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small recoverable UTF-8 file store for the catalog manifest. */
final class AtomicTextFile {
    interface FileMover {
        boolean move(File source, File destination);
    }

    private final File target;
    private final File pending;
    private final File backup;
    private final FileMover mover;

    AtomicTextFile(File target) {
        this(target, File::renameTo);
    }

    AtomicTextFile(File target, FileMover mover) {
        this.target = target;
        this.pending = new File(target.getParentFile(), target.getName() + ".new");
        this.backup = new File(target.getParentFile(), target.getName() + ".bak");
        this.mover = mover;
    }

    String readOrNull() throws IOException {
        recoverIfNeeded();
        if (!target.isFile()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(target);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    void write(String contents) throws IOException {
        File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create catalog directory");
        }
        deleteIfPresent(pending, "stale pending catalog");
        try (FileOutputStream output = new FileOutputStream(pending)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        } catch (IOException exception) {
            pending.delete();
            throw exception;
        }

        boolean hadTarget = target.isFile();
        if (hadTarget) {
            deleteIfPresent(backup, "stale catalog backup");
            if (!mover.move(target, backup)) {
                pending.delete();
                throw new IOException("Cannot back up the current catalog");
            }
        }

        if (!mover.move(pending, target)) {
            pending.delete();
            if (hadTarget && !mover.move(backup, target)) {
                throw new IOException("Catalog update failed and backup restoration failed");
            }
            throw new IOException("Cannot activate the new catalog");
        }
        // The new target is already authoritative; stale backup cleanup is best-effort.
        backup.delete();
    }

    private void recoverIfNeeded() throws IOException {
        if (!target.exists() && backup.isFile() && !mover.move(backup, target)) {
            throw new IOException("Cannot restore the catalog backup");
        }
        if (target.isFile() && backup.exists()) {
            backup.delete();
        }
        if (pending.exists()) {
            pending.delete();
        }
    }

    private static void deleteIfPresent(File file, String description) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Cannot delete " + description);
        }
    }
}
