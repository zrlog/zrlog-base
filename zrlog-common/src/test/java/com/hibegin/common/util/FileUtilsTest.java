package com.hibegin.common.util;

import org.junit.Test;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class FileUtilsTest {

    @Test
    public void shouldCollectFilesWithAndWithoutSuffix() throws Exception {
        assertEquals(FileUtils.class, new FileUtils().getClass());
        File root = Files.createTempDirectory("zrlog-files").toFile();
        File nested = new File(root, "nested");
        assertTrue(nested.mkdirs());
        File md = write(new File(nested, "a.md"), "a");
        File txt = write(new File(root, "b.txt"), "b");

        List<File> all = new ArrayList<>();
        FileUtils.getAllFiles(root.getAbsolutePath(), all);
        List<String> names = all.stream().map(File::getName).sorted().collect(Collectors.toList());
        assertEquals("[a.md, b.txt]", names.toString());

        List<File> markdown = new ArrayList<>();
        FileUtils.getAllFilesBySuffix(root.getAbsolutePath(), ".md", markdown);
        assertEquals(1, markdown.size());
        assertEquals(md.getCanonicalFile(), markdown.get(0).getCanonicalFile());

        List<File> single = new ArrayList<>();
        FileUtils.getAllFilesBySuffix(txt.getAbsolutePath(), ".txt", single);
        assertEquals(txt.getCanonicalFile(), single.get(0).getCanonicalFile());

        List<File> noMatch = new ArrayList<>();
        FileUtils.getAllFilesBySuffix(txt.getAbsolutePath(), ".md", noMatch);
        assertTrue(noMatch.isEmpty());
    }

    @Test
    public void shouldCopyAndMoveFilesAndFolders() throws Exception {
        File root = Files.createTempDirectory("zrlog-copy").toFile();
        File src = write(new File(root, "src.txt"), "content");
        File copied = new File(root, "out/copied.txt");

        FileUtils.moveOrCopyFile(src.getAbsolutePath(), copied.getAbsolutePath(), false);
        assertTrue(src.exists());
        assertEquals("content", Files.readString(copied.toPath()));

        File moved = new File(root, "out/moved.txt");
        FileUtils.moveOrCopyFile(src.getAbsolutePath(), moved.getAbsolutePath(), true);
        assertFalse(src.exists());
        assertEquals("content", Files.readString(moved.toPath()));

        File folder = new File(root, "folder");
        File nested = new File(folder, "nested");
        assertTrue(nested.mkdirs());
        write(new File(nested, "a.txt"), "a");
        File target = new File(root, "folder-copy");
        FileUtils.moveOrCopyFolder(folder.getAbsolutePath(), target.getAbsolutePath(), false);
        assertEquals("a", Files.readString(new File(target, "folder/nested/a.txt").toPath()));

        File singleFile = write(new File(root, "single.txt"), "single");
        File singleTarget = new File(root, "single-target");
        FileUtils.moveOrCopyFolder(singleFile.getAbsolutePath(), singleTarget.getAbsolutePath(), false);
        assertEquals("single", Files.readString(new File(singleTarget, "single.txt").toPath()));
    }

    @Test
    public void shouldKeepAFileWhenSourceAndTargetAreTheSame() throws Exception {
        Path file = Files.createTempFile("zrlog-same-file", ".txt");
        Files.writeString(file, "content");

        FileUtils.moveOrCopyFile(file.toString(), file.toString(), true);

        assertEquals("content", Files.readString(file));
    }

    @Test
    public void shouldCopyBeforeDeletingForCrossFileStoreMoves() throws Exception {
        Path root = Files.createTempDirectory("zrlog-cross-store-move");
        Path source = Files.writeString(root.resolve("source.txt"), "content");
        Path target = root.resolve("out/target.txt");
        Files.createDirectories(target.getParent());

        FileUtils.copyThenDelete(source, target);

        assertFalse(Files.exists(source));
        assertEquals("content", Files.readString(target));
    }

    @Test
    public void shouldMoveAcrossFileStoresWhenAvailable() throws Exception {
        Path source = Files.createTempFile("zrlog-cross-file-store", ".txt");
        Files.writeString(source, "content");
        Path buildDirectory = Files.createDirectories(Path.of("target").toAbsolutePath());
        Path targetRoot = Files.createTempDirectory(buildDirectory, "cross-file-store-");
        Path target = targetRoot.resolve("target.txt");
        try {
            assumeFalse(Files.getFileStore(source).equals(Files.getFileStore(targetRoot)));

            FileUtils.moveOrCopyFile(source.toString(), target.toString(), true);

            assertFalse(Files.exists(source));
            assertEquals("content", Files.readString(target));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
            Files.deleteIfExists(targetRoot);
        }
    }

    @Test
    public void shouldPreserveTheSourceAndReportMoveFailures() throws Exception {
        Path root = Files.createTempDirectory("zrlog-failed-move");
        Path source = Files.writeString(root.resolve("source.txt"), "content");
        Path targetDirectory = Files.createDirectories(root.resolve("target"));
        Files.writeString(targetDirectory.resolve("existing.txt"), "existing");

        assertThrows(UncheckedIOException.class,
                () -> FileUtils.moveOrCopyFile(source.toString(), targetDirectory.toString(), true));
        assertEquals("content", Files.readString(source));
    }

    @Test
    public void shouldDeleteFileOrDirectory() throws Exception {
        File root = Files.createTempDirectory("zrlog-delete").toFile();
        File file = write(new File(root, "file.txt"), "content");
        File dir = new File(root, "dir");
        assertTrue(dir.mkdirs());
        write(new File(dir, "nested.txt"), "nested");

        assertTrue(FileUtils.deleteFile(file.getAbsolutePath()));
        assertFalse(file.exists());
        assertTrue(FileUtils.deleteFile(dir.getAbsolutePath()));
        assertFalse(dir.exists());
    }

    @Test
    public void shouldTrimOldestFilesWhenDiskSpaceIsExceeded() throws Exception {
        File root = Files.createTempDirectory("zrlog-resize").toFile();
        File oldFile = write(new File(root, "old.log"), "12345");
        File newFile = write(new File(root, "new.log"), "123456");
        assertTrue(oldFile.setLastModified(1_000));
        assertTrue(newFile.setLastModified(2_000));

        FileUtils.tryResizeDiskSpace(root.getAbsolutePath(), 0, 6);

        assertFalse(oldFile.exists());
        assertTrue(newFile.exists());
    }

    @Test
    public void shouldKeepFilesWhenDiskSpaceIsEnough() throws Exception {
        File root = Files.createTempDirectory("zrlog-resize-enough").toFile();
        File file = write(new File(root, "keep.log"), "12345");

        FileUtils.tryResizeDiskSpace(root.getAbsolutePath(), 0, 100);

        assertTrue(file.exists());
    }

    @Test
    public void shouldReturnFileExtension() {
        assertEquals("gz", FileUtils.getFileExt("/tmp/archive.tar.gz"));
        assertEquals("", FileUtils.getFileExt("/tmp/README"));
    }

    private static File write(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs());
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
