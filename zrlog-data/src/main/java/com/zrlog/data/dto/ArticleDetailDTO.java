package com.zrlog.data.dto;

import com.zrlog.common.vo.Outline;

import java.util.List;

public class ArticleDetailDTO extends ArticleBasicDTO {

    //
    private LastLogDTO lastLog;
    private NextLogDTO nextLog;
    private List<VisitorCommentDTO> comments;
    private String tocHtml;
    private List<Outline> toc;
    private String contentProtectorHtml;

    public List<Outline> getToc() {
        return toc;
    }

    public void setToc(List<Outline> toc) {
        this.toc = toc;
    }

    public LastLogDTO getLastLog() {
        return lastLog;
    }

    public void setLastLog(LastLogDTO lastLog) {
        this.lastLog = lastLog;
    }

    public NextLogDTO getNextLog() {
        return nextLog;
    }

    public void setNextLog(NextLogDTO nextLog) {
        this.nextLog = nextLog;
    }

    public List<VisitorCommentDTO> getComments() {
        return comments;
    }

    public void setComments(List<VisitorCommentDTO> comments) {
        this.comments = comments;
    }

    public String getTocHtml() {
        return tocHtml;
    }

    public void setTocHtml(String tocHtml) {
        this.tocHtml = tocHtml;
    }

    public String getContentProtectorHtml() {
        return contentProtectorHtml;
    }

    public void setContentProtectorHtml(String contentProtectorHtml) {
        this.contentProtectorHtml = contentProtectorHtml;
    }

    public static class LastLogDTO {
        private String title;
        private String alias;
        private String url;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class NextLogDTO {
        private String title;
        private String alias;
        private String url;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class TagsDTO {
        private String name;
        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
