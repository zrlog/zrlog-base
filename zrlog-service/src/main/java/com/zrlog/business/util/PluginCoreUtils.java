package com.zrlog.business.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpHandle;
import com.hibegin.common.util.http.handle.HttpStringHandle;
import com.zrlog.util.BlogBuildInfoUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.logging.Logger;

public class PluginCoreUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCoreUtils.class);
    private static File getPluginFileName(String pluginsFolder) {
        return getPluginFileName(pluginsFolder, EnvKit.isNativeImage(), BlogBuildInfoUtil.getFileArch());
    }

    static File getPluginFileName(String pluginsFolder, boolean nativeImage, String fileArch) {
        if (!nativeImage) {
            return new File(pluginsFolder + "/plugin-core.jar");
        }
        if (fileArch.contains("Window")) {
            return new File(pluginsFolder + "/plugin-core-" + fileArch + ".exe");
        }
        return new File(pluginsFolder + "/plugin-core-" + fileArch + ".bin");
    }


    public static File tryDownloadPluginCoreFile(String pluginsFolder) {
        return tryDownloadPluginCoreFile(pluginsFolder, EnvKit.isNativeImage(), BlogBuildInfoUtil.getFileArch());
    }

    static synchronized File tryDownloadPluginCoreFile(String pluginsFolder, boolean nativeImage, String fileArch) {
        File pluginCoreFile = getPluginFileName(pluginsFolder, nativeImage, fileArch);
        Path pluginCorePath = pluginCoreFile.toPath();
        Path downloadPath = null;
        boolean usablePluginCore = false;
        try {
            Path pluginsPath = pluginCorePath.getParent();
            Files.createDirectories(pluginsPath);
            deleteStaleDownloadFiles(pluginsPath, pluginCoreFile.getName());
            if (isUsablePluginCore(pluginCoreFile, nativeImage)) {
                usablePluginCore = true;
                return pluginCoreFile;
            }
            String downloadPrefix = pluginCoreFile.getName() + "." + ProcessHandle.current().pid() + ".";
            downloadPath = Files.createTempFile(pluginsPath, downloadPrefix, ".part");
            downloadPath.toFile().deleteOnExit();
            long cacheBuster = System.currentTimeMillis();
            String artifactUrl = BlogBuildInfoUtil.getResourceDownloadUrl() + "/plugin/core/"
                    + pluginCoreFile.getName();
            String withoutCacheDownloadUrl = artifactUrl + "?_t=" + cacheBuster;
            LOGGER.info(pluginCoreFile.getName() + " is missing or invalid; downloading from "
                    + withoutCacheDownloadUrl);
            Map<String, String> map = new HashMap<>();
            map.put("Cache-Control", "no-cache");
            String expectedNativeMd5 = nativeImage
                    ? downloadNativeMd5(artifactUrl + ".md5?_t=" + cacheBuster,
                    pluginCoreFile.getName(), map) : null;
            PluginCoreDownloadHandle downloadHandle = new PluginCoreDownloadHandle(downloadPath);
            HttpUtil.getInstance().sendGetRequest(withoutCacheDownloadUrl, new HashMap<>(), downloadHandle, map);
            downloadHandle.requireComplete();
            PluginCoreArtifactValidator.validateDownload(downloadPath, nativeImage, pluginCoreFile.getName(),
                    expectedNativeMd5);
            if (nativeImage) {
                publishNativeMd5(pluginCorePath, expectedNativeMd5);
            }
            publishDownloadedPluginCore(downloadPath, pluginCorePath);
            downloadPath = null;
            usablePluginCore = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("download plugin core error, " + e.getMessage(), e);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("download plugin core error, " + e.getMessage(), e);
        } finally {
            deleteDownloadFile(downloadPath);
            if (usablePluginCore) {
                if (pluginCoreFile.getName().endsWith(".bin")) {
                    CmdUtil.sendCmd("chmod", "a+x", pluginCoreFile.toString());
                }
            }
        }
        return pluginCoreFile;
    }

    private static boolean isUsablePluginCore(File pluginCoreFile, boolean nativeImage) {
        if (!pluginCoreFile.isFile() || pluginCoreFile.length() <= 0) {
            return false;
        }
        try {
            PluginCoreArtifactValidator.validateExisting(pluginCoreFile.toPath(), nativeImage);
            return true;
        } catch (IOException | SecurityException e) {
            LOGGER.warning(pluginCoreFile.getName() + " is invalid and will be downloaded again: "
                    + e.getMessage());
            return false;
        }
    }

    private static String downloadNativeMd5(String checksumUrl, String fileName,
                                            Map<String, String> headers)
            throws IOException, InterruptedException, URISyntaxException {
        HttpStringHandle checksumHandle = new HttpStringHandle();
        HttpUtil.getInstance().sendGetRequest(checksumUrl, new HashMap<>(), checksumHandle, headers);
        if (checksumHandle.getStatusCode() != 200) {
            throw new IOException("plugin core checksum download returned HTTP "
                    + checksumHandle.getStatusCode());
        }
        return PluginCoreArtifactValidator.parseNativeMd5(checksumHandle.getT(), fileName);
    }

    private static void publishNativeMd5(Path nativePath, String expectedMd5) throws IOException {
        Path checksumPath = PluginCoreArtifactValidator.nativeMd5Path(nativePath);
        Path temporaryChecksum = Files.createTempFile(checksumPath.getParent(),
                checksumPath.getFileName() + "." + ProcessHandle.current().pid() + ".", ".part");
        try {
            Files.writeString(temporaryChecksum, expectedMd5 + "  " + nativePath.getFileName() + "\n");
            Files.move(temporaryChecksum, checksumPath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryChecksum);
        }
    }

    private static void deleteStaleDownloadFiles(Path pluginsPath, String pluginCoreFileName) throws IOException {
        String prefix = pluginCoreFileName + ".";
        try (java.util.stream.Stream<Path> paths = Files.list(pluginsPath)) {
            Path[] staleDownloads = paths.filter(path -> {
                String fileName = path.getFileName().toString();
                return Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && fileName.startsWith(prefix) && fileName.endsWith(".part");
            }).toArray(Path[]::new);
            for (Path staleDownload : staleDownloads) {
                if (!belongsToLiveProcess(staleDownload, prefix)) {
                    Files.deleteIfExists(staleDownload);
                }
            }
        }
    }

    private static boolean belongsToLiveProcess(Path downloadPath, String prefix) {
        String fileName = downloadPath.getFileName().toString();
        int pidStart = prefix.length();
        if (fileName.startsWith("md5.", pidStart)) {
            pidStart += "md5.".length();
        }
        int pidEnd = fileName.indexOf('.', pidStart);
        if (pidEnd < 0) {
            return false;
        }
        try {
            long pid = Long.parseLong(fileName.substring(pidStart, pidEnd));
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void publishDownloadedPluginCore(Path downloadPath, Path pluginCorePath) throws IOException {
        Files.move(downloadPath, pluginCorePath, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteDownloadFile(Path downloadPath) {
        if (downloadPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(downloadPath);
        } catch (IOException e) {
            LOGGER.warning("Unable to remove incomplete plugin-core download " + downloadPath.getFileName());
        }
    }

    private static class PluginCoreDownloadHandle extends HttpHandle<File> {

        private final Path downloadPath;
        private int statusCode;
        private IOException failure;

        private PluginCoreDownloadHandle(Path downloadPath) {
            this.downloadPath = downloadPath;
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public boolean handle(HttpRequest request, HttpResponse<InputStream> response) {
            statusCode = response.statusCode();
            try (InputStream inputStream = response.body()) {
                if (statusCode != 200) {
                    return false;
                }
                long copied = Files.copy(inputStream, downloadPath, StandardCopyOption.REPLACE_EXISTING);
                OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
                if (contentLength.isPresent() && copied != contentLength.getAsLong()) {
                    failure = new IOException("incomplete plugin core download: expected "
                            + contentLength.getAsLong() + " bytes but received " + copied);
                    return false;
                }
                setT(downloadPath.toFile());
            } catch (IOException e) {
                failure = e;
            }
            return false;
        }

        private void requireComplete() throws IOException {
            if (statusCode != 200) {
                throw new IOException("plugin core download returned HTTP " + statusCode);
            }
            if (failure != null) {
                throw failure;
            }
            if (getT() == null) {
                throw new IOException("plugin core download did not produce a file");
            }
        }
    }
}
