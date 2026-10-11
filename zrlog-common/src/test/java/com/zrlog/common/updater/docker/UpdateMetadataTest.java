package com.zrlog.common.updater.docker;

import com.google.gson.Gson;
import com.zrlog.common.vo.Version;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateMetadataTest {
    @Test
    public void usesConfiguredOriginPathAndFilename() {
        assertEquals("https://mirror.example/packages/preview/docker.json",
                UpdateMetadata.uri("https://mirror.example/packages", "docker.json", "preview").toString());
        for (String origin : new String[]{"http://dl.example", "//dl.example", "file:///tmp", "https://user@dl.example",
                "https://dl.example/?next=evil", "https://dl.example/a/../b", "https://dl.example/#x"}) {
            assertThrows(IllegalArgumentException.class, () -> UpdateMetadata.uri(origin, "docker.json", "release"));
        }
        assertThrows(IllegalArgumentException.class, () -> UpdateMetadata.uri("https://dl.example", "../evil.json", "release"));
        assertThrows(IllegalArgumentException.class, () -> UpdateMetadata.uri("https://dl.example", "docker.json", "../preview"));
    }

    @Test
    public void validatesIdentityChannelAndAmbiguousPlatforms() {
        String image = "{\"repository\":\"zrlog/zrlog\",\"tag\":\"4.0.0\",\"digest\":\"sha256:" + "a".repeat(64)
                + "\",\"platforms\":[\"linux/arm64\"]}";
        String json = "{\"schemaVersion\":1,\"packageType\":\"docker-image\",\"channel\":\"release\","
                + "\"version\":\"4.0.0\",\"buildId\":\"1234567\",\"sourceCommit\":\"1234567" + "a".repeat(33)
                + "\",\"releaseDate\":\"2026-10-11 12:00:00Z\",\"images\":[" + image + "]}";
        Version version = new Gson().fromJson(json, Version.class);
        UpdateMetadata.validateDocker(version, "release");
        assertThrows(IllegalArgumentException.class, () -> UpdateMetadata.validateDocker(version, "preview"));
        version.setImages(java.util.List.of(version.getImages().get(0), version.getImages().get(0)));
        assertThrows(IllegalArgumentException.class, () -> UpdateMetadata.validateDocker(version, "release"));
    }
}
