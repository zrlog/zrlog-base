package com.zrlog.util;

import com.hibegin.http.server.util.PathUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NetworkProxyEnvironmentTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    public static void main(String[] args) throws Exception {
        PathUtil.setRootPath(args[0]);
        NetworkProxy.Settings active = NetworkProxy.initialize();
        assertEquals("http://plain.example:8080", active.getUrl());
        assertEquals("http://secure.example:3128", active.getHttpsUrl());
        assertEquals("plain.example", System.getProperty("http.proxyHost"));
        assertEquals("secure.example", System.getProperty("https.proxyHost"));
        NetworkProxy.save("http://edited.example:8081", ".edited.example");
        assertEquals("http://edited.example:8081", NetworkProxy.fileSettings().getUrl());
        assertEquals(".edited.example", NetworkProxy.fileSettings().getNoProxy());
        assertTrue(active.samePolicy(NetworkProxy.configured()));
        assertTrue(active.samePolicy(NetworkProxy.preview("http://unsaved.example:8082", "")));
        assertSame(active, NetworkProxy.initialize());
    }

    @Test
    public void standardProcessEnvironmentWinsWhileTheFileRemainsEditable() throws Exception {
        Path root = temporary.newFolder().toPath();
        Files.createDirectories(root.resolve("conf"));
        Files.writeString(root.resolve("conf/proxy.properties"),
                "network.proxy.url=http://file.example:80\nnetwork.proxy.noProxy=.file.example\n");
        Path output = root.resolve("probe.log");
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), getClass().getName(), root.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile());
        for (String key : new String[]{"http_proxy", "HTTP_PROXY", "https_proxy", "HTTPS_PROXY", "no_proxy", "NO_PROXY"}) {
            builder.environment().remove(key);
        }
        builder.environment().put("HTTP_PROXY", "http://upper.example:3128");
        builder.environment().put("http_proxy", "http://plain.example:8080");
        builder.environment().put("HTTPS_PROXY", "http://secure.example:3128");
        builder.environment().put("NO_PROXY", ".internal.example");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(Files.readString(output), 0, process.exitValue());
        } finally {
            process.destroyForcibly();
        }
    }
}
