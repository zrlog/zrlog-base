package com.zrlog.blog.web.template;

import java.io.Serializable;
import java.util.List;

/** 分页 */
public class PagerVO implements Serializable {

    private List<PageEntry> pageList;
    private String pageStartUrl;
    private String pageEndUrl;
    private Boolean startPage;
    private Boolean endPage;

    public List<PageEntry> getPageList() {
        return pageList;
    }

    public void setPageList(List<PageEntry> pageList) {
        this.pageList = pageList;
    }

    public String getPageStartUrl() {
        return pageStartUrl;
    }

    public void setPageStartUrl(String pageStartUrl) {
        this.pageStartUrl = pageStartUrl;
    }

    public String getPageEndUrl() {
        return pageEndUrl;
    }

    public void setPageEndUrl(String pageEndUrl) {
        this.pageEndUrl = pageEndUrl;
    }

    public Boolean getStartPage() {
        return startPage;
    }

    public void setStartPage(Boolean startPage) {
        this.startPage = startPage;
    }

    public Boolean getEndPage() {
        return endPage;
    }

    public void setEndPage(Boolean endPage) {
        this.endPage = endPage;
    }

    public static class PageEntry implements Serializable {
        private String url;
        private Boolean current;
        private String desc;
        private Long number;
        private Boolean prev;
        private Boolean next;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Boolean getCurrent() {
            return current;
        }

        public void setCurrent(Boolean current) {
            this.current = current;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public Long getNumber() {
            return number;
        }

        public void setNumber(Long number) {
            this.number = number;
        }

        public Boolean getPrev() {
            return prev;
        }

        public void setPrev(Boolean prev) {
            this.prev = prev;
        }

        public Boolean getNext() {
            return next;
        }

        public void setNext(Boolean next) {
            this.next = next;
        }
    }
}
