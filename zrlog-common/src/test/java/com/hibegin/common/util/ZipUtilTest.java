package com.hibegin.common.util;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class ZipUtilTest {

    @Test
    public void shouldZipAndUnzipFilesSafely() throws Exception {
        assertEquals(ZipUtil.class, new ZipUtil().getClass());
        File root = Files.createTempDirectory("zrlog-zip").toFile();
        File sourceDir = new File(root, "source");
        assertTrue(sourceDir.mkdirs());
        File sourceFile = new File(sourceDir, "a.txt");
        Files.write(sourceFile.toPath(), "content".getBytes(StandardCharsets.UTF_8));
        File zip = new File(root, "archive.zip");
        File target = new File(root, "target");

        ZipUtil.inZip(Collections.singletonList(sourceFile), root.getAbsolutePath() + File.separator, zip.getAbsolutePath());
        ZipUtil.unZip(zip.getAbsolutePath(), target.getAbsolutePath());

        assertEquals("content", Files.readString(new File(target, "source/a.txt").toPath()));
    }

    @Test
    public void shouldZipDirectoriesAndUnzipDirectoryEntries() throws Exception {
        File root = Files.createTempDirectory("zrlog-zip-dir").toFile();
        File sourceDir = new File(root, "source");
        File nested = new File(sourceDir, "nested");
        assertTrue(nested.mkdirs());
        Files.writeString(new File(nested, "b.txt").toPath(), "nested-content", StandardCharsets.UTF_8);
        File zip = new File(root, "archive.zip");
        File target = new File(root, "target");
        List<File> files = new ArrayList<>();
        files.add(sourceDir);

        ZipUtil.inZip(files, root.getAbsolutePath() + File.separator, zip.getAbsolutePath());
        ZipUtil.unZip(zip.getAbsolutePath(), target.getAbsolutePath());

        assertEquals("nested-content", Files.readString(new File(target, "source/nested/b.txt").toPath()));

        File zipWithDirectoryEntry = new File(root, "directory-entry.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipWithDirectoryEntry))) {
            zos.putNextEntry(new ZipEntry("empty-dir/"));
            zos.closeEntry();
        }
        ZipUtil.unZip(zipWithDirectoryEntry.getAbsolutePath(), target.getAbsolutePath());
        assertTrue(new File(target, "empty-dir").isDirectory());
    }

    @Test
    public void shouldRejectUnsafeZipEntries() throws Exception {
        File root = Files.createTempDirectory("zrlog-zip-unsafe").toFile();
        File zip = new File(root, "unsafe.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("../escape.txt"));
            zos.write("bad".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        IOException exception = assertThrows(IOException.class,
                () -> ZipUtil.unZip(zip.getAbsolutePath(), new File(root, "target").getAbsolutePath()));

        assertTrue(exception.getMessage().contains("Unsafe zip entry"));
    }
}
