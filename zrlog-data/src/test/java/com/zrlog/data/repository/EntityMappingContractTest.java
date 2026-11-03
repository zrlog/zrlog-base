package com.zrlog.data.repository;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hibegin.common.dao.entity.EntityReflection;
import com.hibegin.common.dao.entity.EntityRepository;
import com.zrlog.model.*;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Schema and Native Image registrations are consumer contracts of common-dao's mapping.
 */
public class EntityMappingContractTest {
    private static final Class<?>[] ENTITIES = {Log.class, Comment.class, Link.class, LogNav.class,
            Plugin.class, Tag.class, Type.class, User.class, WebSite.class, LogVersion.class,
            LogExtensionIndex.class, UserPasskey.class, UserPasskeyChallenge.class, OAuthClient.class,
            OAuthGrant.class, OAuthCredential.class, UserAccessToken.class};

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void everyEntityCanSelectItsCompleteProjectionFromTheInstalledSchema() throws Exception {
        for (ZrLogTestDatabase.DatabaseType backend : ZrLogTestDatabase.DatabaseType.values()) {
            try (ZrLogTestDatabase db = ZrLogTestDatabase.open(backend)) {
                for (Class<?> type : ENTITIES) {
                    assertTrue(type.getName(), new EntityRepository(type, db.dataSource())
                            .findMatching(Map.of(), 1).isEmpty());
                }
            }
        }
    }

    @Test
    public void nativeArtifactRegistersEveryPersistedFieldAndEntityConstructor() throws Exception {
        Map<String, JsonObject> registered = new HashMap<>();
        try (InputStream input = getClass().getResourceAsStream(
                "/META-INF/native-image/com.hibegin/zrlog-data/reflect-config.json")) {
            assertNotNull("Missing Native Image persistence registrations", input);
            for (JsonElement entry : JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject item = entry.getAsJsonObject();
                registered.put(item.get("name").getAsString(), item);
            }
        }
        for (JsonElement entry : JsonParser.parseString(EntityReflection.configuration(ENTITIES)).getAsJsonArray()) {
            JsonObject expected = entry.getAsJsonObject();
            String name = expected.get("name").getAsString();
            JsonObject actual = registered.get(name);
            assertNotNull(name, actual);
            for (JsonElement field : expected.getAsJsonArray("fields")) {
                assertTrue(name + ": " + field, actual.getAsJsonArray("fields").contains(field));
            }
            if (expected.has("methods")) {
                for (JsonElement method : expected.getAsJsonArray("methods")) {
                    assertTrue(name + ": " + method, actual.getAsJsonArray("methods").contains(method));
                }
            }
        }
    }
}
