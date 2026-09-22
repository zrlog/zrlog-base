package com.zrlog.common.updater;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NativeImageUpdaterTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void restartsRelativeExecutableWithoutPathLookupOrMovingOntoItself() throws Exception {
        File root = temporary.newFolder("application");
        Path executable = new File(root, "zrlog").toPath();
        File relative = Path.of("").toAbsolutePath().relativize(executable).toFile();
        NativeImageUpdater updater = new NativeImageUpdater(new String[]{"--port=18080"}, relative);
        assertEquals(executable.toFile(), updater.execFile());
        runUpgrade(updater, root, 0);
        assertEquals(List.of("--port=18080"), Files.readAllLines(new File(root, "arguments").toPath()));
    }

    @Test
    public void preservesRenamedExecutableAndLiteralArgumentsInQuotedPaths() throws Exception {
        File root = temporary.newFolder("app's directory");
        NativeImageUpdater updater = new NativeImageUpdater(
                new String[]{"--contextPath=/it's a blog; $(false)", "--port=18081"},
                new File(root, "custom zrlog"));
        runUpgrade(updater, root, 0);
        assertEquals(List.of("--contextPath=/it's a blog; $(false)", "--port=18081"),
                Files.readAllLines(new File(root, "arguments").toPath()));
        assertTrue(updater.execFile().canExecute());
        assertFalse(new File(root, "zrlog").exists());
    }

    @Test
    public void waitsForOldProcessToExitBeforeReplacingExecutable() throws Exception {
        Assume.assumeTrue(new File("/bin/sh").isFile());
        File root = temporary.newFolder("waiting");
        File staging = stage(root, 0);
        File executable = new File(root, "zrlog");
        Files.writeString(executable.toPath(), "old executable");
        Process old = new ProcessBuilder("sleep", "30").start();
        Process upgrade = null;
        try {
            NativeImageUpdater updater = new NativeImageUpdater(new String[0], executable);
            upgrade = launch(updater, root, staging, old.pid());
            assertFalse(upgrade.waitFor(1200, TimeUnit.MILLISECONDS));
            assertEquals("old executable", Files.readString(executable.toPath()));
            old.destroy();
            assertTrue(old.waitFor(5, TimeUnit.SECONDS));
            assertTrue(upgrade.waitFor(5, TimeUnit.SECONDS));
            assertEquals(Files.readString(new File(root, "upgrade.log").toPath()), 0, upgrade.exitValue());
            assertTrue(new File(root, "arguments").isFile());
        } finally {
            old.destroyForcibly();
            if (upgrade != null) upgrade.destroyForcibly();
        }
    }

    @Test
    public void preservesStartupFailureOutputAndExitCode() throws Exception {
        File root = temporary.newFolder("failure");
        NativeImageUpdater updater = new NativeImageUpdater(new String[0], new File(root, "zrlog"));
        runUpgrade(updater, root, 23);
        assertTrue(Files.readString(new File(root, "upgrade.log").toPath()).contains("new process output"));
    }

    private void runUpgrade(NativeImageUpdater updater, File root, int exitCode) throws Exception {
        Assume.assumeTrue(new File("/bin/sh").isFile());
        // Use a reaped PID so the test cannot overwrite files before the old process exits.
        Process old = new ProcessBuilder("sh", "-c", "exit 0").start();
        assertTrue(old.waitFor(5, TimeUnit.SECONDS));
        Process upgrade = launch(updater, root, stage(root, exitCode), old.pid());
        try {
            assertTrue(upgrade.waitFor(5, TimeUnit.SECONDS));
            assertEquals(Files.readString(new File(root, "upgrade.log").toPath()), exitCode, upgrade.exitValue());
            assertFalse(new File(root, "update temp").exists());
        } finally {
            upgrade.destroyForcibly();
        }
    }

    private File stage(File root, int exitCode) throws Exception {
        File staging = new File(root, "update temp");
        Files.createDirectories(staging.toPath());
        Files.writeString(new File(staging, "zrlog").toPath(),
                "#!/bin/sh\nprintf '%s\\n' \"$@\" > arguments\necho 'new process output' >&2\nexit " + exitCode + "\n");
        return staging;
    }

    private Process launch(NativeImageUpdater updater, File root, File staging, long oldPid) throws Exception {
        File script = new File(root, "upgrade script.sh");
        Files.writeString(script.toPath(), updater.buildExec(root, staging, oldPid));
        return new ProcessBuilder("nohup", "sh", script.toString())
                .redirectInput(new File("/dev/null"))
                .redirectErrorStream(true)
                .redirectOutput(new File(root, "upgrade.log"))
                .start();
    }
}
