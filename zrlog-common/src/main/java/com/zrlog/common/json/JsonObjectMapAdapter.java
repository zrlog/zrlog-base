package com.zrlog.common.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonObjectMapAdapter extends TypeAdapter<Map<String, Object>> {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() {
    }.getType();

    @Override
    public void write(JsonWriter out, Map<String, Object> value) throws IOException {
        GSON.getAdapter(JsonElement.class).write(out, GSON.toJsonTree(value == null
                ? Collections.emptyMap()
                : value));
    }

    @Override
    public Map<String, Object> read(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return Collections.emptyMap();
        }
        try {
            JsonElement element = token == JsonToken.STRING
                    ? JsonParser.parseString(in.nextString())
                    : GSON.getAdapter(JsonElement.class).read(in);
            if (element == null || !element.isJsonObject()) {
                return Collections.emptyMap();
            }
            return GSON.fromJson(element, MAP_TYPE);
        } catch (JsonParseException | IllegalStateException e) {
            return Collections.emptyMap();
        }
    }
}
