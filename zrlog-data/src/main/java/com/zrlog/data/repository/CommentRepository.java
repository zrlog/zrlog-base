package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.SecurityUtils;
import com.zrlog.data.dto.VisitorCommentDTO;
import com.zrlog.data.util.DateValueFormatUtils;
import com.zrlog.model.Comment;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

/**
 * 对应数据里面的 comment 表，用于存放文章对应的评论信息。
 */
public class CommentRepository extends BaseRepository<Comment, Long> {

    public static final String TABLE_NAME = "comment";

    @Inject
    public CommentRepository(DataSourceWrapper dataSource) {
        super(Comment.class, dataSource);
    }

    private static String formatCommentTime(Object value) {
        return DateValueFormatUtils.format(value, "yyyy-MM-dd HH:mm:ss");
    }

    public List<Comment> findAllByLogId(int logId) throws SQLException {
        return select("WHERE logId=?", logId);
    }

    public List<VisitorCommentDTO> visitorFindAllByLogId(int logId) throws SQLException {
        List<VisitorCommentDTO> result = new java.util.ArrayList<>();
        for (Comment row : findAllByLogId(logId)) {
            VisitorCommentDTO comment = new VisitorCommentDTO();
            comment.setId(row.getId());
            comment.setUserComment(row.getUserComment());
            comment.setHeader(row.getHeader());
            comment.setCommTime(formatCommentTime(row.getCommTime()));
            comment.setUserHome(row.getUserHome());
            comment.setUserName(row.getUserName());
            comment.setGravatarId(row.getUserMail() == null ? "" : SecurityUtils.md5(row.getUserMail()));
            result.add(comment);
        }
        return result;
    }
}
