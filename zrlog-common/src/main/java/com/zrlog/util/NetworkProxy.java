package com.zrlog.util;

import com.hibegin.http.server.util.PathUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Startup configuration, available before any WebAPI database connection. */
public final class NetworkProxy {
    public static final String URL_KEY = "network.proxy.url";
    public static final String NO_PROXY_KEY = "network.proxy.noProxy";
    public static final String URL_ENV = "HTTP_PROXY";
    public static final String HTTPS_URL_ENV = "HTTPS_PROXY";
    public static final String NO_PROXY_ENV = "NO_PROXY";
    public static final String FILE_NAME = "proxy.properties";
    private static final String LOOPBACK = "localhost|127.*|[::1]|[::ffff:127.*";
    private static Settings effective;

    private NetworkProxy() { }

    public static synchronized Settings initialize() {
        if (effective == null) {
            Settings settings = configured();
            settings.systemProperties().forEach(System::setProperty);
            effective = settings;
        }
        return effective;
    }

    public static Settings configured() {
        return resolve(System.getenv(), readFile(configFile()));
    }

    public static Settings fileSettings() {
        return resolve(Map.of(), readFile(configFile()));
    }

    public static Path configFile() {
        return Path.of(PathUtil.getConfPath(), FILE_NAME);
    }

    private static Properties readFile(Path path) {
        Properties file = new Properties();
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                file.load(input);
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to read conf/" + FILE_NAME, exception);
            }
        }
        return file;
    }

    public static synchronized void save(String url, String noProxy) throws IOException {
        save(configFile(), url, noProxy);
    }

    /** Resolve pending file values with environment overrides, without changing the running process. */
    public static Settings preview(String url, String noProxy) {
        return resolve(System.getenv(), updatedFile(new Properties(), url, noProxy));
    }

    static void save(Path path, String url, String noProxy) throws IOException {
        Properties file = updatedFile(readFile(path), url, noProxy);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "proxy-", ".tmp");
        try {
            if (Files.exists(path) && Files.getFileStore(path).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(path));
            }
            try (OutputStream output = Files.newOutputStream(temporary)) {
                file.store(output, "ZrLog outbound HTTP proxy. Restart ZrLog after changing this file.");
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Properties updatedFile(Properties file, String url, String noProxy) {
        if (url == null || noProxy == null || url.length() > 2048 || noProxy.length() > 4096) {
            throw new IllegalArgumentException("Invalid proxy configuration");
        }
        file.setProperty(URL_KEY, url.trim());
        file.setProperty(NO_PROXY_KEY, noProxy.trim());
        resolve(Map.of(), file);
        return file;
    }

    static Settings resolve(Map<String, String> environment, Properties file) {
        Endpoint http = endpoint(value(environment, file, URL_ENV, URL_KEY), URL_ENV);
        Endpoint https = endpoint(value(environment, file, HTTPS_URL_ENV, URL_KEY), HTTPS_URL_ENV);
        String noProxy = value(environment, file, NO_PROXY_ENV, NO_PROXY_KEY);
        StringBuilder bypass = new StringBuilder(LOOPBACK);
        for (String item : noProxy.split("[,|]")) {
            String token = item.trim();
            if (token.isEmpty()) continue;
            try {
                if (!token.matches("[A-Za-z0-9.*_:\\[\\]-]+") || token.contains("..") || token.equals(".")) {
                    throw new IllegalArgumentException();
                }
                if (token.contains(":")) {
                    if (!(token.startsWith("[") && token.endsWith("]"))) token = "[" + token + "]";
                    if (URI.create("http://" + token + "/").getHost() == null) throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(NO_PROXY_ENV + " / " + NO_PROXY_KEY
                        + " must contain comma-separated hosts or domain patterns, without ports or CIDR ranges");
            }
            if (token.startsWith(".")) {
                bypass.append('|').append(token.substring(1));
                token = "*" + token;
            }
            bypass.append('|').append(token);
        }
        return new Settings(http, https, noProxy, source(environment, file, URL_ENV, URL_KEY),
                source(environment, file, HTTPS_URL_ENV, URL_KEY), source(environment, file, NO_PROXY_ENV, NO_PROXY_KEY),
                environmentKey(environment, URL_ENV), environmentKey(environment, HTTPS_URL_ENV),
                environmentKey(environment, NO_PROXY_ENV), bypass.toString());
    }

    private static Endpoint endpoint(String proxy, String variable) {
        if (proxy.isEmpty()) return new Endpoint("", "", 80);
        try {
            URI uri = URI.create(proxy);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1) {
                throw new IllegalArgumentException();
            }
            int port = uri.getPort() == -1 ? 80 : uri.getPort();
            return new Endpoint("http://" + uri.getHost() + ":" + port, uri.getHost(), port);
        } catch (IllegalArgumentException exception) {
            // Never echo raw configuration: an invalid URI may contain credentials.
            throw new IllegalArgumentException(variable + " / " + URL_KEY
                    + " must be an HTTP proxy URL such as http://proxy.example.com:3128 (without credentials)");
        }
    }

    private static String environmentKey(Map<String, String> environment, String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (environment.containsKey(lower)) return lower;
        return environment.containsKey(key) ? key : "";
    }

    private static String value(Map<String, String> environment, Properties file, String env, String key) {
        String variable = environmentKey(environment, env);
        return (variable.isEmpty() ? file.getProperty(key, "") : environment.get(variable)).trim();
    }

    private static String source(Map<String, String> environment, Properties file, String env, String key) {
        if (!environmentKey(environment, env).isEmpty()) return "environment";
        return file.containsKey(key) ? "file" : "default";
    }

    private static final class Endpoint {
        private final String url;
        private final String host;
        private final int port;
        private Endpoint(String url, String host, int port) { this.url = url; this.host = host; this.port = port; }
        private Proxy proxy() {
            if (url.isEmpty()) return Proxy.NO_PROXY;
            String address = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
            return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(address, port));
        }
    }

    public static final class Settings {
        private final Endpoint http;
        private final Endpoint https;
        private final String noProxy;
        private final String source;
        private final String httpsSource;
        private final String noProxySource;
        private final String httpVariable;
        private final String httpsVariable;
        private final String noProxyVariable;
        private final String bypass;

        private Settings(Endpoint http, Endpoint https, String noProxy, String source, String httpsSource, String noProxySource,
                         String httpVariable, String httpsVariable, String noProxyVariable, String bypass) {
            this.http = http; this.https = https; this.noProxy = noProxy;
            this.source = source; this.httpsSource = httpsSource; this.noProxySource = noProxySource;
            this.httpVariable = httpVariable; this.httpsVariable = httpsVariable; this.noProxyVariable = noProxyVariable;
            this.bypass = bypass;
        }

        public String getUrl() { return http.url; }
        public String getHttpsUrl() { return https.url; }
        public String getNoProxy() { return noProxy; }
        public String getSource() { return source; }
        public String getHttpsSource() { return httpsSource; }
        public String getNoProxySource() { return noProxySource; }
        public String getHttpVariable() { return httpVariable; }
        public String getHttpsVariable() { return httpsVariable; }
        public String getNoProxyVariable() { return noProxyVariable; }
        public boolean isEnabled() { return !http.url.isEmpty() || !https.url.isEmpty(); }

        public boolean samePolicy(Settings other) {
            return http.url.equals(other.http.url) && https.url.equals(other.https.url) && bypass.equals(other.bypass)
                    && "default".equals(source) == "default".equals(other.source)
                    && "default".equals(httpsSource) == "default".equals(other.httpsSource);
        }

        /** A private client tests pending settings without changing the global proxy selector. */
        public ProxySelector proxySelector() {
            ProxySelector inherited = ProxySelector.getDefault();
            return new ProxySelector() {
                @Override public List<Proxy> select(URI uri) {
                    if (uri == null) throw new IllegalArgumentException("URI is required");
                    boolean secure = "https".equalsIgnoreCase(uri.getScheme());
                    if (!(secure || "http".equalsIgnoreCase(uri.getScheme()))) return List.of(Proxy.NO_PROXY);
                    String destination = uri.getHost();
                    if (destination == null) return List.of(Proxy.NO_PROXY);
                    for (String pattern : bypass.split("\\|")) {
                        if (matchesHost(destination, pattern)) return List.of(Proxy.NO_PROXY);
                    }
                    if ("default".equals(secure ? httpsSource : source) && inherited != null) return inherited.select(uri);
                    return List.of((secure ? https : http).proxy());
                }
                @Override public void connectFailed(URI uri, SocketAddress address, IOException error) {
                    // Deliberately no direct fallback when a configured proxy fails.
                }
            };
        }

        private static boolean matchesHost(String destination, String pattern) {
            String host = destination.toLowerCase(Locale.ROOT);
            String token = pattern.toLowerCase(Locale.ROOT);
            if (token.equals("*")) return true;
            boolean prefix = token.startsWith("*");
            boolean suffix = token.endsWith("*");
            String literal = token.substring(prefix ? 1 : 0, token.length() - (suffix ? 1 : 0));
            if (prefix && suffix) return host.contains(literal);
            if (prefix) return host.endsWith(literal);
            if (suffix) return host.startsWith(literal);
            return host.equals(literal);
        }

        private Map<String, String> systemProperties() {
            Map<String, String> properties = new LinkedHashMap<>();
            // Preserve a protocol's legacy JVM policy when it has no application configuration.
            if (!"default".equals(source)) {
                properties.put("http.proxyHost", http.host);
                properties.put("http.proxyPort", Integer.toString(http.port));
            }
            if (!"default".equals(httpsSource)) {
                properties.put("https.proxyHost", https.host);
                properties.put("https.proxyPort", Integer.toString(https.port));
            }
            if (!"default".equals(source) || !"default".equals(httpsSource) || !"default".equals(noProxySource)) {
                properties.put("http.nonProxyHosts", bypass);
            }
            return properties;
        }

        public List<String> launchArguments() {
            List<String> arguments = new ArrayList<>();
            systemProperties().forEach((key, value) -> arguments.add("-D" + key + "=" + value));
            return arguments;
        }
    }
}
