package com.zrlog.common.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Test;

public class ResourceScannerTest {

    @Test
    public void shouldRecursivelyListLocalFiles() throws Exception {
        File root = Files.createTempDirectory("zrlog-resource-scan").toFile();
        File child = new File(root, "scripts");
        File nested = new File(child, "nested");
        assertTrue(nested.mkdirs());
        File first = new File(child, "001.sql");
        File second = new File(nested, "002.sql");
        Files.writeString(first.toPath(), "select 1", StandardCharsets.UTF_8);
        Files.writeString(second.toPath(), "select 2", StandardCharsets.UTF_8);

        List<String> files = new ResourceScanner(root.getAbsolutePath()).listFiles("scripts");

        assertEquals(2, files.size());
        assertTrue(files.contains(first.toString()));
        assertTrue(files.contains(second.toString()));
        assertTrue(new ResourceScanner(root.getAbsolutePath()).listFiles("missing").isEmpty());
    }

    @Test
    public void shouldSupportClasspathResourcePrefix() {
        List<String> files = new ResourceScanner("classpath:/").listFiles("missing");

        assertTrue(files.isEmpty());
    }
}
