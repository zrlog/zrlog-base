package com.zrlog.common.updater.docker;

import java.util.List;

/** Version 1 of the private, authenticated updater protocol. No Docker credentials reach the browser. */
public final class DockerUpdaterProtocol {
    private DockerUpdaterProtocol() { }

    public static class ErrorResponse {
        public String code;
    }

    public static class Image {
        public String repository;
        public String tag;
        public String digest;
        public List<String> platforms;
    }

    public static class Capabilities {
        public int protocolVersion;
        public boolean ready;
        public String metadataBaseUrl;
        public String manifestFilename;
        public String project;
        public String platform;
        public String currentImage;
        public String repository;
    }

    public static class Settings {
        public String repository;
        public String channel;
    }

    public static class SettingsResponse {
        public Settings settings;
        public String currentRepository;
        public List<Image> images;
    }

    public static class UpgradeRequest {
        public String requestId;
        public String channel;
        public String version;
        public String buildId;
    }

    public static class Job {
        public String id;
        public String channel;
        public String version;
        public String buildId;
        public String sourceCommit;
        public String repository;
        public String digest;
        public String platform;
        public String previousImage;
        public String phase;
        public String errorCode;
        public String message;
        public String createdAt;
        public String updatedAt;
        public boolean recoveryRequired;

        public boolean terminal() {
            return "succeeded".equals(phase) || "failed".equals(phase) || "interrupted".equals(phase);
        }
    }
}
