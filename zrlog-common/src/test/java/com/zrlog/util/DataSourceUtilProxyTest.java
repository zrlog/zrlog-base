package com.zrlog.util;

import com.hibegin.http.server.util.PathUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DataSourceUtilProxyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 30000)
    public void connectionChecksAndQueriesUseTheActiveProxyIndependentlyOfTheGlobalSelector() throws Exception {
        for (String mode : List.of("file", "environment")) {
            Path root = temporary.newFolder().toPath();
            Files.createDirectories(root.resolve("conf"));
            try (ServerSocket proxy = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
                proxy.setSoTimeout(10000);
                String url = "http://127.0.0.1:" + proxy.getLocalPort();
                Files.writeString(root.resolve("conf/proxy.properties"), "network.proxy.url="
                        + ("file".equals(mode) ? url : "http://overridden.invalid:3128") + "\n");
                CompletableFuture<List<String>> requests = new CompletableFuture<>();
                Thread worker = new Thread(() -> receiveRequests(proxy, requests));
                worker.setDaemon(true);
                worker.start();
                Path output = root.resolve("probe.log");
                ProcessBuilder builder = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", System.getProperty("java.class.path"), Probe.class.getName(), root.toString())
                        .redirectErrorStream(true).redirectOutput(output.toFile());
                for (String key : List.of("http_proxy", "HTTP_PROXY", "https_proxy", "HTTPS_PROXY", "no_proxy", "NO_PROXY")) {
                    builder.environment().remove(key);
                }
                if ("environment".equals(mode)) builder.environment().put("HTTPS_PROXY", url);
                Process process = builder.start();
                try {
                    boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                    assertTrue("Data source proxy probe did not finish: " + mode + "\n" + Files.readString(output), finished);
                    assertEquals(mode + ": " + Files.readString(output), 0, process.exitValue());
                    assertEquals(List.of("CONNECT database.invalid:443 HTTP/1.1", "CONNECT database.invalid:443 HTTP/1.1"),
                            requests.get(2, TimeUnit.SECONDS));
                } finally {
                    process.destroyForcibly();
                    proxy.close();
                    worker.join(2000);
                }
            }
        }
    }

    private static void receiveRequests(ServerSocket proxy, CompletableFuture<List<String>> result) {
        try {
            List<String> requests = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                try (var connection = proxy.accept()) {
                    connection.setSoTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                    requests.add(reader.readLine());
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    connection.getOutputStream().write(("HTTP/1.1 502 Bad Gateway\r\n"
                            + "Content-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                }
            }
            result.complete(requests);
        } catch (Exception error) {
            result.completeExceptionally(error);
        }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            PathUtil.setRootPath(args[0]);
            NetworkProxy.initialize();
            NetworkProxy.save("http://pending.invalid:3128", "");
            ProxySelector.setDefault(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) {
                    throw new AssertionError("WebAPI used the global selector instead of the active application proxy");
                }
                @Override public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) { }
            });
            Properties properties = new Properties();
            properties.setProperty("jdbcUrl", "jdbc:webapi://database.invalid:443/query");
            properties.setProperty("user", "test-user");
            properties.setProperty("password", "test-token");
            try (var source = DataSourceUtil.buildDataSource(properties)) {
                assertThrows(SQLException.class, source::testConnection);
                assertThrows(SQLException.class, () -> source.getQueryRunner().update("update example set value = ?", "test"));
            }
            // The DAO's HTTP executor lives for the process lifetime.
            System.exit(0);
        }
    }
}
