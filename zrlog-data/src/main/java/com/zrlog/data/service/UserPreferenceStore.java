package com.zrlog.data.service;

import com.google.gson.*;
import com.zrlog.common.exception.ArgsException;
import com.zrlog.model.User;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Account JSON persistence shared by personal settings and browser session issuance. */
public class UserPreferenceStore {
    private static final Gson GSON = new Gson();
    private static final long UPDATE_RETRY_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    public static final long MIN_SESSION_MINUTES = 6;
    public static final long MAX_SESSION_MINUTES = 99999;

    public JsonObject read(int userId) throws SQLException { return parseStored(raw(userId)); }

    public long sessionTimeoutMinutes(int userId, long siteDefault) throws SQLException {
        JsonElement section = read(userId).get("session");
        if (section != null && section.isJsonObject()
                && java.util.Set.of("timeoutMinutes").containsAll(section.getAsJsonObject().keySet())) {
            JsonElement value = section.getAsJsonObject().get("timeoutMinutes");
            try {
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    long minutes = value.getAsBigDecimal().longValueExact();
                    if (minutes >= MIN_SESSION_MINUTES && minutes <= MAX_SESSION_MINUTES) return minutes;
                }
            } catch (ArithmeticException | NumberFormatException ignored) { }
        }
        return siteDefault;
    }

    public String raw(int userId) throws SQLException {
        Object value = new User().queryFirstObj("select preferences from user where userId=?", userId);
        return value == null ? null : value.toString();
    }

    public JsonObject parseStored(String raw) {
        if (raw == null || raw.isBlank()) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (JsonParseException e) { return new JsonObject(); }
    }

    public void mutate(int userId, Consumer<JsonObject> change) throws SQLException {
        long deadline = System.nanoTime() + UPDATE_RETRY_TIMEOUT_NANOS;
        long backoffMillis = 1;
        do {
            String previous = raw(userId);
            JsonObject root = parseStored(previous);
            change.accept(root);
            String updated = GSON.toJson(root);
            if (updated.getBytes(StandardCharsets.UTF_8).length > 60000) throw new ArgsException("preferences");
            if (updated.equals(previous)) return;
            boolean saved = previous == null
                    ? new User().execute("update user set preferences=? where userId=? and preferences is null", updated, userId)
                    : new User().execute("update user set preferences=? where userId=? and preferences=?", updated, userId, previous);
            if (saved) return;
            // A burst of writes can exhaust a small attempt count before the other writer finishes.
            // Back off, then read and merge again; the conditional write also protects other instances.
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            long delay = TimeUnit.MILLISECONDS.toNanos(ThreadLocalRandom.current().nextLong(backoffMillis, backoffMillis * 2 + 1));
            try { TimeUnit.NANOSECONDS.sleep(Math.min(delay, remaining)); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while updating account preferences", e);
            }
            backoffMillis = Math.min(backoffMillis * 2, 32);
        } while (System.nanoTime() - deadline < 0);
        throw new SQLException("Concurrent account preference update; please retry");
    }

}
