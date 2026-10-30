package com.zrlog.business.util;

import com.zrlog.util.ExecutionDeadline;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

public class PluginBundleUnpackTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void completeBundleReplacesEntriesAndPreservesUnrelatedFiles() throws Exception {
        Path archive = temporary.newFile("bundle.zip").toPath();
        Path target = temporary.newFolder("unpacked").toPath();
        Files.writeString(target.resolve("keep"), "untouched");
        byte[] content = "complete-plugin".getBytes(StandardCharsets.UTF_8);
        Files.write(archive, storedArchive("plugins/core.bin", content));
        PluginCoreUtils.unpackPlugins(archive, target, ExecutionDeadline.afterMillis(2000));
        assertArrayEquals(content, Files.readAllBytes(target.resolve("plugins/core.bin")));
        assertEquals("untouched", Files.readString(target.resolve("keep")));
        assertNoParts(target);
    }

    @Test
    public void invalidEntryCrcDoesNotReplaceExistingFile() throws Exception {
        Path archive = temporary.newFile("corrupt.zip").toPath();
        Path target = temporary.newFolder("corrupt-target").toPath();
        Files.writeString(target.resolve("core.bin"), "existing");
        byte[] bytes = storedArchive("core.bin", "replacement".getBytes(StandardCharsets.UTF_8));
        // STORED local header: 30 fixed bytes, then the UTF-8 entry name, then the payload.
        bytes[30 + "core.bin".length()] ^= 1;
        Files.write(archive, bytes);
        assertThrows(IOException.class, () -> PluginCoreUtils.unpackPlugins(archive, target, ExecutionDeadline.afterMillis(2000)));
        assertEquals("existing", Files.readString(target.resolve("core.bin")));
        assertNoParts(target);
    }

    @Test(timeout = 5000)
    public void expandingEntryCannotPublishAfterItsBudgetExpires() throws Exception {
        Path archive = temporary.newFile("large.zip").toPath();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("core.bin"));
            byte[] buffer = new byte[64 * 1024];
            for (int i = 0; i < 512; i++) zip.write(buffer);
            zip.closeEntry();
        }
        Path target = temporary.newFolder("expired-target").toPath();
        Files.writeString(target.resolve("core.bin"), "existing");
        assertThrows(HttpTimeoutException.class,
                () -> PluginCoreUtils.unpackPlugins(archive, target, ExecutionDeadline.afterMillis(1)));
        assertEquals("existing", Files.readString(target.resolve("core.bin")));
        assertNoParts(target);
    }

    @Test
    public void bundleEntryCannotEscapeItsDestination() throws Exception {
        Path archive = temporary.newFile("escape.zip").toPath();
        Path target = temporary.newFolder("escape-target").toPath();
        Files.write(archive, storedArchive("../outside.bin", new byte[]{1}));
        assertThrows(IOException.class, () -> PluginCoreUtils.unpackPlugins(archive, target, ExecutionDeadline.afterMillis(2000)));
        assertFalse(Files.exists(target.getParent().resolve("outside.bin")));
        assertNoParts(target);
    }

    private static byte[] storedArchive(String name, byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            ZipEntry entry = new ZipEntry(name);
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(data);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void assertNoParts(Path target) throws IOException {
        try (Stream<Path> files = Files.walk(target)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }
}
