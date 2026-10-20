package com.zrlog.common.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class TemplateVO extends BaseTemplateVO {

    private TemplateConfigMap config;
    private List<String> staticResources = new ArrayList<>();

    public TemplateConfigMap getConfig() {
        return config;
    }

    public void setConfig(TemplateConfigMap config) {
        this.config = config;
    }

    public List<String> getStaticResources() {
        return staticResources;
    }

    public void setStaticResources(List<String> staticResources) {
        this.staticResources = staticResources;
    }

    public static class TemplateConfigMap extends LinkedHashMap<String, TemplateConfigVO> {

    }

    public static class TemplateConfigVO {

        private String label;
        private String htmlElementType;
        private String placeholder;
        private String type;
        private Object value;
        private String contentType;
        private String previewValue;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getHtmlElementType() {
            return htmlElementType;
        }

        public void setHtmlElementType(String htmlElementType) {
            this.htmlElementType = htmlElementType;
        }

        public String getPlaceholder() {
            return placeholder;
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getPreviewValue() {
            return previewValue;
        }

        public void setPreviewValue(String previewValue) {
            this.previewValue = previewValue;
        }
    }

}
