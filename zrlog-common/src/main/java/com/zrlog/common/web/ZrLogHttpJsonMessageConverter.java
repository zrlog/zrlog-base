package com.zrlog.common.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hibegin.http.server.config.GsonHttpJsonMessageConverter;
import java.time.LocalDateTime;

public class ZrLogHttpJsonMessageConverter extends GsonHttpJsonMessageConverter {

    private final Gson gson;

    public ZrLogHttpJsonMessageConverter() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter());
        this.gson = gsonBuilder.create();
    }

    @Override
    public String toJson(Object obj) {
        return gson.toJson(obj);
    }

    @Override
    public Object fromJson(String jsonStr) throws Exception {
        return super.fromJson(jsonStr);
    }
}
