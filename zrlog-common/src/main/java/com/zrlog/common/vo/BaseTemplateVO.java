package com.zrlog.common.vo;

import com.zrlog.business.type.TemplateType;
import java.util.List;

public class BaseTemplateVO {

    private String template;
    private String shortTemplate;
    private String url;
    private String version;
    private String name;
    private String digest;
    private boolean deleteAble;
    private List<String> previewImages;
    private List<String> tags;
    private String previewImage;
    private String author;
    private boolean configAble;
    private boolean preview;
    private String viewType;
    private TemplateType templateType;
    private boolean use;
    private String adminPreviewImage;
    private boolean classpathTemplate;

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getShortTemplate() {
        return shortTemplate;
    }

    public void setShortTemplate(String shortTemplate) {
        this.shortTemplate = shortTemplate;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public boolean isDeleteAble() {
        return deleteAble;
    }

    public void setDeleteAble(boolean deleteAble) {
        this.deleteAble = deleteAble;
    }

    public List<String> getPreviewImages() {
        return previewImages;
    }

    public void setPreviewImages(List<String> previewImages) {
        this.previewImages = previewImages;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getPreviewImage() {
        return previewImage;
    }

    public void setPreviewImage(String previewImage) {
        this.previewImage = previewImage;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean isConfigAble() {
        return configAble;
    }

    public void setConfigAble(boolean configAble) {
        this.configAble = configAble;
    }

    public boolean isPreview() {
        return preview;
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    public String getViewType() {
        return viewType;
    }

    public void setViewType(String viewType) {
        this.viewType = viewType;
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public void setTemplateType(TemplateType templateType) {
        this.templateType = templateType;
    }

    public boolean isUse() {
        return use;
    }

    public void setUse(boolean use) {
        this.use = use;
    }

    public String getAdminPreviewImage() {
        return adminPreviewImage;
    }

    public void setAdminPreviewImage(String adminPreviewImage) {
        this.adminPreviewImage = adminPreviewImage;
    }

    public boolean isClasspathTemplate() {
        return classpathTemplate;
    }

    public void setClasspathTemplate(boolean classpathTemplate) {
        this.classpathTemplate = classpathTemplate;
    }
}
