package com.zrlog.test.support;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class MemoryRuntimeTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void resetOnlyRemovesTheReservedRuntimeAndDoesNotFollowLinks() throws Exception {
        Path project = temporary.newFolder("project").toPath();
        Path realConfig = Files.createDirectories(project.resolve("conf")).resolve("db.properties");
        Files.writeString(realConfig, "existing config");
        Path runtime = MemoryRuntime.reset(project);
        Files.writeString(runtime.resolve("stale.txt"), "stale");
        Path external = temporary.newFolder("external").toPath();
        Files.writeString(external.resolve("keep.txt"), "keep");
        Files.createSymbolicLink(runtime.resolve("linked"), external);

        assertEquals(runtime, MemoryRuntime.reset(project));
        assertFalse(Files.exists(runtime.resolve("stale.txt")));
        assertEquals("existing config", Files.readString(realConfig));
        assertEquals("keep", Files.readString(external.resolve("keep.txt")));

        Files.delete(runtime);
        Files.createSymbolicLink(runtime, external);
        assertThrows(IOException.class, () -> MemoryRuntime.reset(project));
        assertEquals("keep", Files.readString(external.resolve("keep.txt")));
    }

    @Test public void projectRootWorksFromTheRepositoryOrItsMavenModule() throws Exception {
        Path project = temporary.newFolder("project").toPath();
        Files.createDirectories(project.resolve("conf"));
        Files.writeString(project.resolve("conf/memory-install.json"), "{}");
        Path module = Files.createDirectory(project.resolve("module"));
        String previous = System.getProperty("user.dir");
        try {
            for (Path workingDirectory : new Path[]{project, module}) {
                System.setProperty("user.dir", workingDirectory.toString());
                assertEquals(project, MemoryRuntime.projectRoot());
            }
            System.setProperty("user.dir", temporary.newFolder("missing").toString());
            assertThrows(IllegalStateException.class, MemoryRuntime::projectRoot);
        } finally {
            System.setProperty("user.dir", previous);
        }
    }

    @Test public void invalidPortsFailBeforeStartingAnApplication() {
        assertEquals(7080, MemoryRuntime.resolvePort(null, 7080));
        assertEquals(17080, MemoryRuntime.resolvePort(new String[]{"--debug", "--port=17080"}, 7080));
        for (String value : new String[]{"0", "-1", "65536", "bad", ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> MemoryRuntime.resolvePort(new String[]{"--port=" + value}, 7080));
        }
    }
}
