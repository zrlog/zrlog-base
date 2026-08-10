package com.zrlog.util;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.Pid;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.NativeImageUtils;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.business.version.UpgradeVersionHandlerHelpers;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.*;
import com.zrlog.common.cache.vo.Archive;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.cache.vo.HotLogBasicInfoEntry;
import com.zrlog.common.cache.vo.HotTypeLogInfo;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.aws.AwsLambdaUpdateCodeRequest;
import com.zrlog.common.vo.*;
import com.zrlog.common.json.JsonObjectMapAdapter;
import org.apache.commons.dbutils.BasicRowProcessor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ZrLogBaseNativeImageUtils {

    public static class MyBasicRowProcessor extends BasicRowProcessor {
        public static Map<String, Object> createMap() {
            return createCaseInsensitiveHashMap(2);
        }
    }

    private static String getWindowsExecutablePath() {
        try {
            Process process = Runtime.getRuntime().exec("cmd /c wmic process where processid=" + Pid.get() + " get ExecutablePath");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            reader.readLine(); // Skip the header line
            String executablePath = reader.readLine(); // The second line is the executable path
            if (StringUtils.isEmpty(executablePath)) {
                return "zrlog.exe";
            }
            return new File(executablePath.trim()).getName();
        } catch (Exception e) {
            System.exit(1);
            return "zrlog.exe";
        }
    }

    public static String getExecFile() {
        if (EnvKit.isFaaSMode()) {
            return ZrLogUtil.getFaaSRoot() + "/zrlog";
        }
        if (BlogBuildInfoUtil.getFileArch().contains("Windows")) {
            return getWindowsExecutablePath();
        }
        String envBinFile = System.getenv("_");
        if (StringUtils.isEmpty(envBinFile)) {
            return "zrlog";
        }
        String execFile = envBinFile.replace("./", "");
        if (!execFile.startsWith(Constants.getZrLogHome())) {
            execFile = new File(Constants.getZrLogHome() + "/" + execFile).toString();
        }
        return execFile;
    }

    public static void reg() {
        List<String> resourceFiles = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            String filePath = "/conf/update-sql/" + i + ".sql";
            InputStream inputStream = ZrLogBaseNativeImageUtils.class.getResourceAsStream(filePath);
            if (Objects.nonNull(inputStream)) {
                resourceFiles.add(filePath);
            }
        }
        resourceFiles.add("/i18n/backend_en_US.properties");
        resourceFiles.add("/i18n/backend_zh_CN.properties");
        resourceFiles.add("/conf/error/403.html");
        resourceFiles.add("/conf/error/404.html");
        resourceFiles.add("/conf/error/500.html");
        resourceFiles.add(BlogBuildInfoUtil.BUILD_PROPERTIES_FILE_PATH);

        NativeImageUtils.doResourceLoadByResourceNames(resourceFiles);
        //
        NativeImageUtils.gsonNativeAgentByClazz(gsonClasses());
        //freemarker
        regWithGetMethod(TypeDTO.class,
                LinkDTO.class, LogNavDTO.class, TagDTO.class, PluginDTO.class,
                BaseDataInitVO.class, BaseDataInitVO.Statistics.class,
                HotLogBasicInfoEntry.class, PageData.class, HotTypeLogInfo.class,
                Version.class, Archive.class, Outline.class,
                PublicWebSiteInfo.class, UserBasicDTO.class, SocialPreviewDTO.class,
                UpgradeProgressEvent.Data.class, AwsLambdaUpdateCodeRequest.class);

        //register version update
        for (int i = 1; i <= UpgradeVersionHandler.SQL_VERSION; i++) {
            UpgradeVersionHandlerHelpers.getUpgradeVersionHandler(i);
        }
    }

    static List<Class<?>> gsonClasses() {
        return Arrays.asList(LockVO.class, I18nVO.class,
                BaseTemplateVO.class,
                TemplatePackageJson.class,
                TemplateVO.class,
                TemplateVO.TemplateConfigMap.class,
                TemplateVO.TemplateConfigVO.class,
                JsonObjectMapAdapter.class,
                MyBasicRowProcessor.class,
                MyBasicRowProcessor.createMap().getClass(),
                LinkedHashMap.class);
    }

    public static void regWithGetMethod(Class<?>... objects) {
        NativeImageUtils.gsonNativeAgentByClazz(List.of(objects));
        for (Class<?> o : objects) {
            NativeImageUtils.regGetMethodByClassName(o);
        }
    }
}
