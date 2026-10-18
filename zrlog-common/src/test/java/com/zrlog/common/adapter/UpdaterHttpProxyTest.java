package com.zrlog.common.adapter;

import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.http.server.util.PathUtil;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.updater.docker.DockerImageUpdater;
import com.zrlog.updater.spi.DockerConnection;
import com.zrlog.updater.spi.UpdateContext;
import com.zrlog.util.NetworkProxy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class UpdaterHttpProxyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String TOKEN = "test_token_123456789012345678901234";

    @Test(timeout = 45000)
    public void dockerControlIsDirectAndForwardsTheActivePolicyWhileOtherRequestsKeepTheirProxy() throws Exception {
        for (String mode : List.of("environment", "file", "jvm")) verifyRouting(mode);
    }

    private void verifyRouting(String mode) throws Exception {
        Path root = temporary.newFolder().toPath();
        Path hosts = root.resolve("hosts");
        Files.writeString(hosts, "127.0.0.1 updater.internal.test\n");
        AtomicInteger controlRequests = new AtomicInteger();
        AtomicInteger proxiedRequests = new AtomicInteger();
        AtomicBoolean leaked = new AtomicBoolean();
        AtomicBoolean forwarded = new AtomicBoolean();
        HttpServer updater = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HttpServer proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String proxyUrl = "http://127.0.0.1:" + proxy.getAddress().getPort();
        updater.createContext("/v1/capabilities", exchange -> {
            controlRequests.incrementAndGet();
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            boolean valid = ("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    && userAgent != null && userAgent.contains(" ZrLog/");
            String expected = "jvm".equals(mode) ? null : proxyUrl;
            forwarded.set(java.util.Objects.equals(expected, exchange.getRequestHeaders().getFirst("X-ZrLog-HTTP-Proxy"))
                    && java.util.Objects.equals("environment".equals(mode) ? "" : expected,
                    exchange.getRequestHeaders().getFirst("X-ZrLog-HTTPS-Proxy"))
                    && java.util.Objects.equals("jvm".equals(mode) ? null : "." + mode + ".example",
                    exchange.getRequestHeaders().getFirst("X-ZrLog-No-Proxy")));
            byte[] body = ("{\"protocolVersion\":1,\"ready\":true,\"metadataBaseUrl\":\"https://dl.example/\","
                    + "\"manifestFilename\":\"docker.json\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid ? 200 : 403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        updater.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://metadata.external.test/unexpected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        proxy.createContext("/", exchange -> {
            proxiedRequests.incrementAndGet();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if ((auth != null && auth.contains(TOKEN)) || exchange.getRequestHeaders().containsKey("X-ZrLog-HTTP-Proxy"))
                leaked.set(true);
            byte[] body = "proxied metadata".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        updater.start();
        proxy.start();
        try {
            Files.createDirectories(root.resolve("conf"));
            if ("file".equals(mode)) Files.writeString(root.resolve("conf/proxy.properties"),
                    "network.proxy.url=" + proxyUrl + "\nnetwork.proxy.noProxy=.file.example\n");
            List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Djdk.net.hosts.file=" + hosts));
            if ("jvm".equals(mode)) command.addAll(List.of("-Dhttp.proxyHost=127.0.0.1",
                    "-Dhttp.proxyPort=" + proxy.getAddress().getPort()));
            command.addAll(List.of("-cp", System.getProperty("java.class.path"), Probe.class.getName(), root.toString()));
            Path output = root.resolve("probe.log");
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
            for (String key : List.of("http_proxy", "HTTP_PROXY", "https_proxy", "HTTPS_PROXY", "no_proxy", "NO_PROXY"))
                builder.environment().remove(key);
            if ("environment".equals(mode)) {
                builder.environment().put("HTTP_PROXY", proxyUrl);
                builder.environment().put("HTTPS_PROXY", "");
                builder.environment().put("NO_PROXY", ".environment.example");
            }
            builder.environment().put("ZRLOG_UPDATER_URL", "http://updater.internal.test:" + updater.getAddress().getPort());
            Process process = builder.start();
            try {
                assertTrue("Proxy probe did not finish: " + mode, process.waitFor(10, TimeUnit.SECONDS));
                assertEquals(mode + ": " + Files.readString(output), 0, process.exitValue());
            } finally { process.destroyForcibly(); }
            assertEquals(1, controlRequests.get());
            assertEquals(4, proxiedRequests.get());
            assertTrue("Active proxy settings were not forwarded: " + mode, forwarded.get());
            assertFalse("Control credentials or proxy configuration leaked: " + mode, leaked.get());
        } finally {
            updater.stop(0);
            proxy.stop(0);
        }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            PathUtil.setRootPath(args[0]);
            try {
                NetworkProxy.initialize();
                NetworkProxy.save("http://pending.invalid:3128", ".pending.example");
                UpdateContext.Builder context = new UpdateContext.Builder();
                context.docker = new DockerConnection(System.getenv("ZRLOG_UPDATER_URL"), TOKEN, "https://dl.example/", "docker.json");
                context.http = UpdaterHosts.HTTP;
                URI control = URI.create(System.getenv("ZRLOG_UPDATER_URL") + "/v1/capabilities");
                assertEquals(Proxy.Type.HTTP, ProxySelector.getDefault().select(control).get(0).type());
                assertTrue(new DockerImageUpdater(context.build()).capabilities().ready);
                assertEquals(302, context.http.send(HttpRequest.newBuilder(control.resolve("/redirect"))
                        .timeout(Duration.ofSeconds(2)).header("Authorization", "Bearer " + TOKEN).build()).statusCode());
                assertEquals("proxied metadata", HttpUtil.getInstance().sendRequest(HttpRequest.newBuilder(control)
                        .timeout(Duration.ofSeconds(2)).build()).body());
                URI external = URI.create("http://metadata.external.test/manifest.json");
                assertEquals("proxied metadata", context.http.send(HttpRequest.newBuilder(external)
                        .timeout(Duration.ofSeconds(2)).POST(HttpRequest.BodyPublishers.ofString("signed request"))
                        .header("X-Amz-Date", "20261018T000000Z").build()).body());
                assertEquals("proxied metadata", context.http.read(external));
                Path download = Path.of(args[0], "download.json");
                assertEquals(200, context.http.download(external, download.toFile()));
                assertEquals("proxied metadata", Files.readString(download));
                assertEquals(Proxy.Type.HTTP, ProxySelector.getDefault().select(control).get(0).type());
            } finally { HttpUtil.getInstance().closeHttpClient(); }
        }
    }
}
