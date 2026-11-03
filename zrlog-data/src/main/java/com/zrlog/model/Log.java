package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

import java.sql.Timestamp;

// Article IDs are assigned under the existing creation lock so the initial alias uses the same ID.
@Entity(table = "log", idColumn = "logId", updatedAtColumn = "last_update_date")
public class Log extends BaseEntity<Long> {
    private String alias;
    private Boolean canComment = true;
    private Integer click = 0;
    private String content;
    @Column("plain_content")
    private String plainContent;
    private String markdown;
    private String digest;
    private String keywords;
    private String thumbnail;
    private Boolean recommended = false;
    private Integer sticky = 0;
    private Timestamp releaseTime;
    private String title;
    private Integer typeId;
    private Integer userId;
    private Boolean hot;
    private Boolean rubbish;
    private Boolean privacy;
    @Column("editor_type")
    private String editorType;
    @Column("arrange_plugin")
    private String arrangePlugin;
    private String extensions;

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public Boolean getCanComment() { return canComment; }
    public void setCanComment(Boolean canComment) { this.canComment = canComment; }

    public Integer getClick() { return click; }
    public void setClick(Integer click) { this.click = click; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getPlainContent() { return plainContent; }
    public void setPlainContent(String plainContent) { this.plainContent = plainContent; }

    public String getMarkdown() { return markdown; }
    public void setMarkdown(String markdown) { this.markdown = markdown; }

    public String getDigest() { return digest; }
    public void setDigest(String digest) { this.digest = digest; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public Boolean getRecommended() { return recommended; }
    public void setRecommended(Boolean recommended) { this.recommended = recommended; }

    public Integer getSticky() { return sticky; }
    public void setSticky(Integer sticky) { this.sticky = sticky; }

    public Timestamp getReleaseTime() { return releaseTime; }
    public void setReleaseTime(Timestamp releaseTime) { this.releaseTime = releaseTime; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getTypeId() { return typeId; }
    public void setTypeId(Integer typeId) { this.typeId = typeId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public Boolean getHot() { return hot; }
    public void setHot(Boolean hot) { this.hot = hot; }

    public Boolean getRubbish() { return rubbish; }
    public void setRubbish(Boolean rubbish) { this.rubbish = rubbish; }

    public Boolean getPrivacy() { return privacy; }
    public void setPrivacy(Boolean privacy) { this.privacy = privacy; }

    public String getEditorType() { return editorType; }
    public void setEditorType(String editorType) { this.editorType = editorType; }

    public String getArrangePlugin() { return arrangePlugin; }
    public void setArrangePlugin(String arrangePlugin) { this.arrangePlugin = arrangePlugin; }

    public String getExtensions() { return extensions; }
    public void setExtensions(String extensions) { this.extensions = extensions; }
}
