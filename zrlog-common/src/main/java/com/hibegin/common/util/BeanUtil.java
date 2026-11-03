package com.hibegin.common.util;

import com.google.gson.Gson;

/** 与实体相关的工具类 */
public class BeanUtil {

    public static <T> T convert(Object obj, Class<T> tClass) {
        String jsonStr = new Gson().toJson(obj);
        return new Gson().fromJson(jsonStr, tClass);
    }
}
