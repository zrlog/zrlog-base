package com.zrlog.business.template.util;

import com.zrlog.theme.spi.BundledThemes;

import com.hibegin.common.util.IOUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BlogResourceUtils {

    private static final BlogResourceUtils instance = new BlogResourceUtils();

    private final List<String> resources;

    public static BlogResourceUtils getInstance() {
        return instance;
    }

    private BlogResourceUtils() {
        InputStream resourceAsStream = BlogResourceUtils.class.getResourceAsStream("/resource.txt");
        if (Objects.nonNull(resourceAsStream)) {
            this.resources = Arrays.stream(IOUtil.getStringInputStream(resourceAsStream).split("\n")).collect(Collectors.toList());
        } else {
            this.resources = new ArrayList<>();
        }
        for (String resource : BundledThemes.getInstance().resources()) {
            if (!resources.contains(resource)) resources.add(resource);
        }
    }

    public List<String> getResources() {
        return resources.stream().map(String::trim).collect(Collectors.toList());
    }

    public List<String> searchResources(String prefix) {
        return resources.stream().filter(e -> e.startsWith(prefix)).collect(Collectors.toList());
    }

    public boolean existsResource(String path) {
        return resources.contains(path);
    }
}
