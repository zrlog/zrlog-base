package com.zrlog.business.template.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.ZipUtil;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.Constants;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.StaticFileCacheUtils;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.logging.Logger;

public class TemplateDownloadUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(TemplateDownloadUtils.class);

    public static void installByUrl(String downloadUrl) throws IOException, URISyntaxException, InterruptedException {
        if (StringUtils.isEmpty(downloadUrl)) {
            return;
        }
        HttpFileHandle fileHandle = (HttpFileHandle) HttpUtil.getInstance().sendGetRequest(downloadUrl + "?t=" + System.currentTimeMillis(), new HttpFileHandle(PathUtil.getStaticFile(Constants.TEMPLATE_BASE_PATH).toString()), new HashMap<>());
        if (!fileHandle.getT().exists()) {
            return;
        }
        String templateName = downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1).replace(".zip", "");
        LOGGER.info("Download template [" + templateName + "] success");
        installByZipFile(fileHandle.getT(), Constants.TEMPLATE_BASE_PATH + templateName);
        //delete zip file
        fileHandle.getT().delete();
    }

    public static void installByZipFile(File zipFile, String templatePath) throws IOException {
        ZipUtil.unZip(zipFile.toString(), PathUtil.getStaticFile(templatePath).toString());
        StaticFileCacheUtils.getInstance().refreshCacheFileMap();
        LOGGER.info("Install template [" + new File(templatePath).getName() + "] success");

    }

    private static File getTemplateZipFile(String templatePath) {
        if (EnvKit.isFaaSMode()) {
            File file = PathUtil.safeAppendFilePath(ZrLogUtil.getFaaSRoot() + "/static/", templatePath + ".zip");
            if (file.exists()) {
                return file;
            }
        }
        return PathUtil.getStaticFile(templatePath + ".zip");
    }

    public static boolean exists(String templatePath) {
        File file = PathUtil.getStaticFile(templatePath);
        return file.exists();
    }

    public static void installByTemplateName(String templatePath, boolean forceUpdate) throws IOException, URISyntaxException, InterruptedException {
        if (!forceUpdate && exists(templatePath)) {
            return;
        }
        File zipFile = getTemplateZipFile(templatePath);
        if (zipFile.exists()) {
            installByZipFile(zipFile, templatePath);
            return;
        }
        File file = PathUtil.getStaticFile(templatePath);
        installByUrl(BlogBuildInfoUtil.getResourceDownloadUrl() + "/attachment/template/" + file.getName() + ".zip");
    }
}
