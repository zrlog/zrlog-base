package com.zrlog.test.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;

/** Filesystem and command-line support for local memory applications, never an installer. */
public final class MemoryRuntime {

    private MemoryRuntime() { }

    public static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : new Path[]{current, current.getParent()}) {
            if (candidate != null && Files.isRegularFile(candidate.resolve("conf/memory-install.json"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot locate conf/memory-install.json from " + current);
    }

    public static java.util.Properties readDatabaseProperties(Path runtimeRoot) throws IOException {
        java.util.Properties properties = new java.util.Properties();
        try (var input = Files.newInputStream(runtimeRoot.resolve("conf/db.properties"))) {
            properties.load(input);
        }
        return properties;
    }

    /** Only the reserved child directory is cleared; symbolic links are never traversed. */
    public static Path reset(Path projectRoot) throws IOException {
        Path root = projectRoot.toRealPath().resolve(".zrlog-memory");
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Refuse to reset symbolic-link memory root: " + root);
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(root);
        return root;
    }

    public static int resolvePort(String[] args, int defaultPort) {
        int port = defaultPort;
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith("--port=")) {
                    port = Integer.parseInt(arg.substring("--port=".length()));
                    break;
                }
            }
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
        return port;
    }
}
