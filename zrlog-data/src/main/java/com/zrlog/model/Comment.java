package com.zrlog.model;

import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Column;
import com.hibegin.common.dao.entity.Entity;

import java.sql.Timestamp;

@Entity(table = "comment", idColumn = "commentId", generatedId = true, createdAtColumn = "td")
public class Comment extends BaseEntity<Long> {
    private Timestamp commTime;
    private Boolean hide;
    @Column("have_read")
    private Boolean haveRead = false;
    private String userComment;
    private String userHome;
    private String userIp;
    private String userMail;
    private String userName;
    private Integer logId;
    private String postId;
    private String header;
    @Column("user_agent")
    private String userAgent;
    @Column("reply_id")
    private Integer replyId;

    public Timestamp getCommTime() { return commTime; }
    public void setCommTime(Timestamp commTime) { this.commTime = commTime; }

    public Boolean getHide() { return hide; }
    public void setHide(Boolean hide) { this.hide = hide; }

    public Boolean getHaveRead() { return haveRead; }
    public void setHaveRead(Boolean haveRead) { this.haveRead = haveRead; }

    public String getUserComment() { return userComment; }
    public void setUserComment(String userComment) { this.userComment = userComment; }

    public String getUserHome() { return userHome; }
    public void setUserHome(String userHome) { this.userHome = userHome; }

    public String getUserIp() { return userIp; }
    public void setUserIp(String userIp) { this.userIp = userIp; }

    public String getUserMail() { return userMail; }
    public void setUserMail(String userMail) { this.userMail = userMail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public Integer getLogId() { return logId; }
    public void setLogId(Integer logId) { this.logId = logId; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Integer getReplyId() { return replyId; }
    public void setReplyId(Integer replyId) { this.replyId = replyId; }
}
