package com.zrlog.data.dto;

import com.google.gson.annotations.JsonAdapter;
import com.zrlog.common.json.JsonObjectMapAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ArticleBasicDTO {

    private Long id;
    private Long logId;
    private String alias;
    private Boolean canComment;
    private Long click;
    private Long version;
    private String content;
    private String plain_content;
    private String markdown;
    private String digest;
    private String keywords;
    private Boolean recommended;
    private String releaseTime;
    private String fullReleaseTime;
    private String last_update_date;
    private String lastUpdateDate;
    private String fullLastUpdateDate;
    private String title;
    private Long typeId;
    private Long userId;
    private Boolean hot;
    private Boolean rubbish;
    private Boolean privacy;
    private String userName;
    private String header;
    private Long commentSize;
    private String typeAlias;
    private String typeName;
    private String arrange_plugin;
    private String typeUrl;
    private String url;
    private String thumbnail;
    private String thumbnailAlt;
    private String noSchemeUrl;
    private String commentUrl;
    private List<ArticleDetailDTO.TagsDTO> tags;
    @JsonAdapter(JsonObjectMapAdapter.class)
    private Map<String, Object> extensions;


    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public Boolean getCanComment() {
        return canComment;
    }

    public void setCanComment(Boolean canComment) {
        this.canComment = canComment;
    }

    public Long getClick() {
        return click;
    }

    public void setClick(Long click) {
        this.click = click;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPlain_content() {
        return plain_content;
    }

    public void setPlain_content(String plain_content) {
        this.plain_content = plain_content;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Boolean getRecommended() {
        return recommended;
    }

    public void setRecommended(Boolean recommended) {
        this.recommended = recommended;
    }

    public String getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(String releaseTime) {
        this.releaseTime = releaseTime;
    }

    public String getLast_update_date() {
        return last_update_date;
    }

    public void setLast_update_date(String last_update_date) {
        this.last_update_date = last_update_date;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Boolean getHot() {
        return hot;
    }

    public void setHot(Boolean hot) {
        this.hot = hot;
    }

    public Boolean getRubbish() {
        return rubbish;
    }

    public void setRubbish(Boolean rubbish) {
        this.rubbish = rubbish;
    }

    public Boolean getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Boolean privacy) {
        this.privacy = privacy;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getCommentSize() {
        return commentSize;
    }

    public void setCommentSize(Long commentSize) {
        this.commentSize = commentSize;
    }

    public String getTypeAlias() {
        return typeAlias;
    }

    public void setTypeAlias(String typeAlias) {
        this.typeAlias = typeAlias;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(String lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getArrange_plugin() {
        return arrange_plugin;
    }

    public void setArrange_plugin(String arrange_plugin) {
        this.arrange_plugin = arrange_plugin;
    }

    public String getTypeUrl() {
        return typeUrl;
    }

    public void setTypeUrl(String typeUrl) {
        this.typeUrl = typeUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getThumbnailAlt() {
        return thumbnailAlt;
    }

    public void setThumbnailAlt(String thumbnailAlt) {
        this.thumbnailAlt = thumbnailAlt;
    }

    public String getNoSchemeUrl() {
        return noSchemeUrl;
    }

    public void setNoSchemeUrl(String noSchemeUrl) {
        this.noSchemeUrl = noSchemeUrl;
    }

    public String getCommentUrl() {
        return commentUrl;
    }

    public void setCommentUrl(String commentUrl) {
        this.commentUrl = commentUrl;
    }

    public List<ArticleDetailDTO.TagsDTO> getTags() {
        return tags;
    }

    public void setTags(List<ArticleDetailDTO.TagsDTO> tags) {
        this.tags = tags;
    }

    public String getFullReleaseTime() {
        return fullReleaseTime;
    }

    public void setFullReleaseTime(String fullReleaseTime) {
        this.fullReleaseTime = fullReleaseTime;
    }

    public String getFullLastUpdateDate() {
        return fullLastUpdateDate;
    }

    public void setFullLastUpdateDate(String fullLastUpdateDate) {
        this.fullLastUpdateDate = fullLastUpdateDate;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public Map<String, Object> getExtensions() {
        return extensions == null ? Collections.emptyMap() : extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }
}
