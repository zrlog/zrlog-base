package com.hibegin.common.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class SecurityUtilsTest {

    @Test
    public void shouldCreateMd5ForStringBytesStreamAndFile() throws Exception {
        String expected = "5d41402abc4b2a76b9719d911017c592";
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        File file = Files.createTempFile("zrlog-md5", ".txt").toFile();
        Files.write(file.toPath(), bytes);

        assertEquals(expected, SecurityUtils.md5("hello"));
        assertEquals(expected, SecurityUtils.md5(bytes));
        assertEquals(expected, SecurityUtils.md5(new ByteArrayInputStream(bytes)));
        assertEquals(expected, SecurityUtils.md5ByFile(file));
    }

    @Test
    public void shouldReturnNullForMissingMd5Inputs() {
        assertNull(SecurityUtils.md5((String) null));
        assertNull(SecurityUtils.md5((byte[]) null));
        assertNull(SecurityUtils.md5((ByteArrayInputStream) null));
    }

    @Test
    public void shouldReturnNullForBrokenStreamAndWrapMissingFile() {
        InputStream brokenStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken");
            }
        };

        assertNull(SecurityUtils.md5(brokenStream));
        assertThrows(RuntimeException.class, () -> SecurityUtils.md5ByFile(new File("/tmp/zrlog-missing-md5-file")));
    }
}
