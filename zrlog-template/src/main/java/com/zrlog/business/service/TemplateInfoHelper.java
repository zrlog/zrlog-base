package com.zrlog.business.service;

import com.zrlog.theme.spi.BundledThemes;

import com.google.gson.Gson;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.type.TemplateType;
import com.zrlog.common.Constants;
import com.zrlog.common.resource.ZrLogResourceLoader;
import com.zrlog.common.vo.TemplatePackageJson;
import com.zrlog.common.vo.TemplateVO;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.StaticFileCacheUtils;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.zrlog.common.Constants.TEMPLATE_BASE_PATH;

public class TemplateInfoHelper {

    private static final Logger LOGGER = LoggerUtil.getLogger(TemplateInfoHelper.class);
    public static final String ADMIN_PREVIEW_IMAGE_URI = Constants.ADMIN_URI_BASE_PATH + "/template/preview-image";

    public static List<TemplateVO> getClassPathTemplates() {
        List<TemplateVO> templateVOList = new ArrayList<>();
        for (String path : BundledThemes.getInstance().paths()) {
            TemplateVO templateVO = loadTemplateVO(path);
            if (Objects.nonNull(templateVO)) {
                templateVOList.add(templateVO);
            }
        }
        return templateVOList;
    }

    public static boolean isDefaultTemplate(String templatePath) {
        return BundledThemes.getInstance().paths().contains(templatePath);
    }

    public static boolean existByTemplatePath(String templatePath) {
        return Objects.nonNull(loadTemplateVO(templatePath));
    }

    public static boolean isDefaultTemplateStartWith(String path) {
        return BundledThemes.getInstance().containsResource(path);
    }

    public static TemplateVO loadTemplateVO(String templateName) {
        if (Objects.isNull(templateName) || !templateName.startsWith(TEMPLATE_BASE_PATH)) {
            return null;
        }
        TemplateVO templateVO = getByProperties(templateName, TemplateInfoHelper.class.getResourceAsStream(templateName + "/" + TemplateType.STANDARD.getInfoFile()));
        if (Objects.nonNull(templateVO)) {
            templateVO.setClasspathTemplate(true);
        } else {
            templateVO = getByPackageJson(templateName, TemplateInfoHelper.class.getResourceAsStream(templateName + "/" + TemplateType.NODE_JS.getInfoFile()));
            if (Objects.nonNull(templateVO)) {
                templateVO.setClasspathTemplate(true);
            } else {
                try {
                    templateVO = getTemplateVO(PathUtil.getStaticFile(templateName));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (Objects.nonNull(templateVO)) {
            templateVO.setViewType(getTemplateView(templateVO));
        }
        return templateVO;
    }

    private static String getTemplateView(TemplateVO templateVO) {
        Map<String, String> viewMap = Map.of("layout/index.ejs", ".ejs",
                "layout/index.pug", ".pug", "index.ftl", ".ftl", "layout/index.njk", ".njk");
        if (templateVO.isClasspathTemplate()) {
            for (Map.Entry<String, String> entry : viewMap.entrySet()) {
                if (ZrLogResourceLoader.exists("classpath:" + templateVO.getTemplate() + "/" + entry.getKey())) {
                    return entry.getValue();
                }
            }
        } else {
            for (Map.Entry<String, String> entry : viewMap.entrySet()) {
                if (ZrLogResourceLoader.exists(PathUtil.getStaticFile(templateVO.getTemplate() + "/" + entry.getKey()).toString())) {
                    return entry.getValue();
                }
            }
        }
        return ".xx";
    }

    public static TemplateVO getTemplateVO(File file) throws IOException {
        if (!file.exists() || !file.isDirectory()) {
            return null;
        }
        File templateInfoFile = new File(file + "/" + TemplateType.STANDARD.getInfoFile());
        String templatePath = file.toString().substring(PathUtil.getStaticFile("/").toString().length()).replace("\\", "/");
        TemplateVO templateVO = null;
        if (templateInfoFile.exists()) {
            templateVO = getByProperties(templatePath, new FileInputStream(templateInfoFile));
        }
        templateInfoFile = new File(file + "/" + TemplateType.NODE_JS.getInfoFile());
        if (templateInfoFile.exists()) {
            templateVO = getByPackageJson(templatePath, new FileInputStream(templateInfoFile));
        }
        templateInfoFile = new File(file + "/" + TemplateType.NODE_JS.getConfigFile());
        if (templateInfoFile.exists()) {
            templateVO = getByConfigYml(templatePath, new FileInputStream(templateInfoFile));
        }
        if (Objects.nonNull(templateVO)) {
            templateVO.setViewType(getTemplateView(templateVO));
        }
        return templateVO;
    }


    private static TemplateVO.TemplateConfigMap byJson(InputStream inputStream) {
        String jsonStr = IOUtil.getStringInputStream(inputStream);
        return new Gson().fromJson(jsonStr, TemplateVO.TemplateConfigMap.class);
    }

    private static TemplateVO.TemplateConfigMap byYml(InputStream inputStream) {
        TemplateVO.TemplateConfigMap templateConfigMap = new TemplateVO.TemplateConfigMap();
        TemplateVO.TemplateConfigVO templateConfigVO = new TemplateVO.TemplateConfigVO();
        templateConfigVO.setPreviewValue("");
        templateConfigVO.setLabel("_config.yml");
        templateConfigVO.setType("yml");
        templateConfigVO.setHtmlElementType("textarea");
        templateConfigVO.setContentType("yml");
        templateConfigVO.setPlaceholder("");
        templateConfigVO.setValue(IOUtil.getStringInputStream(inputStream));
        templateConfigMap.put(Constants.TEMPLATE_CONFIG_STR_KEY, templateConfigVO);
        return templateConfigMap;
    }

    private static TemplateVO.TemplateConfigMap getConfigMap(String templatePath, TemplateType templateType) {
        if (isDefaultTemplate(templatePath)) {
            InputStream resourceAsStream = TemplateInfoHelper.class.getResourceAsStream(templatePath + "/" + templateType.getConfigFile());
            return getConfigMap(templateType, resourceAsStream);
        }
        File configFile = PathUtil.getStaticFile(templatePath + "/" + templateType.getConfigFile());
        //文件存在才配置
        if (configFile.exists()) {
            try {
                InputStream inputStream = new FileInputStream(configFile);
                return getConfigMap(templateType, inputStream);
            } catch (FileNotFoundException e) {
                LOGGER.warning("Read config file error " + e.getMessage());
            }
        }
        return new TemplateVO.TemplateConfigMap();
    }

    private static TemplateVO.TemplateConfigMap getConfigMap(TemplateType templateType, InputStream resourceAsStream) {
        if (Objects.nonNull(resourceAsStream) && templateType == TemplateType.STANDARD) {
            return byJson(resourceAsStream);
        } else if (Objects.nonNull(resourceAsStream) && templateType == TemplateType.NODE_JS) {
            return byYml(resourceAsStream);
        }
        return new TemplateVO.TemplateConfigMap();
    }

    private static TemplateVO getByPackageJson(String templatePath, InputStream inputStream) {
        if (Objects.isNull(inputStream)) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(inputStream)) {
            TemplateVO templateVO = new TemplateVO();
            templateVO.setTemplate(templatePath);
            templateVO.setShortTemplate(new File(templatePath).getName());
            TemplatePackageJson packageJson = new Gson().fromJson(new InputStreamReader(in), TemplatePackageJson.class);
            Object author = packageJson == null ? null : packageJson.getAuthor();
            if (Objects.nonNull(author)) {
                if (author instanceof String) {
                    templateVO.setAuthor((String) author);
                } else {
                    templateVO.setAuthor(new Gson().toJson(author));
                }
            }

            templateVO.setName(packageJson == null ? null : packageJson.getName());
            templateVO.setDigest(packageJson == null ? null : packageJson.getDescription());
            templateVO.setVersion(packageJson == null ? null : packageJson.getVersion());
            templateVO.setUrl(packageJson == null ? null : packageJson.getHomepage());
            templateVO.setTemplateType(TemplateType.NODE_JS);
            String staticResource = "source";
            templateVO.setStaticResources(List.of(staticResource.split(",")));
            return fillTemplateInfo(templateVO);
        } catch (IOException e) {
            LoggerUtil.getLogger(TemplateInfoHelper.class).log(Level.SEVERE, "", e);
            return null;
        }
    }

    private static TemplateVO getByConfigYml(String templatePath, InputStream inputStream) {
        if (Objects.isNull(inputStream)) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(inputStream)) {
            TemplateVO templateVO = new TemplateVO();
            templateVO.setTemplate(templatePath);
            templateVO.setShortTemplate(new File(templatePath).getName());
            templateVO.setAuthor("-");
            templateVO.setName("-");
            templateVO.setDigest("-");
            templateVO.setVersion("-");
            templateVO.setUrl("");
            templateVO.setTemplateType(TemplateType.NODE_JS);
            String staticResource = "source";
            templateVO.setStaticResources(List.of(staticResource.split(",")));
            return fillTemplateInfo(templateVO);
        } catch (IOException e) {
            LoggerUtil.getLogger(TemplateInfoHelper.class).log(Level.SEVERE, "", e);
            return null;
        }
    }

    private static TemplateVO getByProperties(String templatePath, InputStream inputStream) {
        if (Objects.isNull(inputStream)) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(inputStream)) {
            TemplateVO templateVO = new TemplateVO();
            templateVO.setTemplate(templatePath);
            templateVO.setShortTemplate(new File(templatePath).getName());
            Properties properties = new Properties();
            properties.load(in);
            templateVO.setAuthor(properties.getProperty("author"));
            templateVO.setTemplateType(TemplateType.STANDARD);
            templateVO.setName(properties.getProperty("name"));
            templateVO.setDigest(properties.getProperty("digest"));
            templateVO.setVersion(properties.getProperty("version"));
            templateVO.setUrl(properties.getProperty("url"));
            if (properties.get("previewImages") != null) {
                String[] images = properties.get("previewImages").toString().split(",");
                if (images.length > 0) {
                    templateVO.setPreviewImage(templatePath + "/" + images[0]);
                }
                templateVO.setPreviewImages(Arrays.asList(images));
            }
            String staticResource = properties.getProperty("staticResource");
            if (Objects.nonNull(staticResource)) {
                templateVO.setStaticResources(List.of(staticResource.split(",")));
            }
            return fillTemplateInfo(templateVO);
        } catch (IOException e) {
            LoggerUtil.getLogger(TemplateInfoHelper.class).log(Level.SEVERE, "", e);
            return null;
        }
    }

    private static TemplateVO fillTemplateInfo(TemplateVO templateVO) {
        if (templateVO.getPreviewImages() == null || templateVO.getPreviewImages().isEmpty()) {
            String defaultPreviewImage = templateVO.getTemplate() + "/images/preview.png";
            templateVO.setPreviewImage(defaultPreviewImage);
            templateVO.setPreviewImages(Collections.singletonList(defaultPreviewImage));
        }
        if (templateVO.getAdminPreviewImage() == null || templateVO.getAdminPreviewImage().isEmpty()) {
            String adminPreviewImageUrl = "";
            for (int i = 0; i < templateVO.getPreviewImages().size(); i++) {
                String image = templateVO.getPreviewImages().get(i);
                if (!image.startsWith("https://") && !image.startsWith("http://")) {
                    if (i == 0) {
                        adminPreviewImageUrl = ADMIN_PREVIEW_IMAGE_URI + "?shortTemplate=" + templateVO.getShortTemplate() + "&t=" + StaticFileCacheUtils.getInstance().getFileFlagFirstByCache(image);
                    }
                } else {
                    if (i == 0) {
                        adminPreviewImageUrl = image;
                    }
                }
            }
            templateVO.setAdminPreviewImage(adminPreviewImageUrl);
        }
        if (StringUtils.isEmpty(templateVO.getDigest())) {
            templateVO.setDigest(I18nUtil.getBackendStringFromRes("noIntroduction"));
        }
        templateVO.setConfig(getConfigMap(templateVO.getTemplate(), templateVO.getTemplateType()));
        return templateVO;
    }
}
