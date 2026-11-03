package com.zrlog.data.repository;

import com.zrlog.model.Comment;


import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.SecurityUtils;
import com.zrlog.data.dto.CommentDTO;
import com.zrlog.data.dto.VisitorCommentDTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.util.DateValueFormatUtils;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 对应数据里面的 comment 表，用于存放文章对应的评论信息。
 */
public class CommentRepository extends BaseRepository<Comment, Long> {

    public static final String TABLE_NAME = "comment";
    private final DataSourceWrapper dataSource;

    @Inject
    public CommentRepository(DataSourceWrapper dataSource) {
        super(Comment.class, dataSource);
        this.dataSource = dataSource;
    }

    private static String formatCommentTime(Object value) {
        return DateValueFormatUtils.format(value, "yyyy-MM-dd HH:mm:ss");
    }

    public PageData<CommentDTO> find(PageRequest page) {
        return find(page, null);
    }

    public PageData<CommentDTO> find(PageRequest page, Integer privateOwnerId) {
        String sql = "select commentId as id,userComment,header,commTime,userMail,userHome,userIp,userName,hide,logId from " + tableName + (privateOwnerId == null ? "" : " where logId in (select logId from log where privacy=false or userId=?)") + " order by commTime desc";
        PageData<CommentDTO> commentDTOPageData = queryPageData(sql, page, privateOwnerId == null ? new Object[0] : new Object[]{privateOwnerId}, CommentDTO.class);
        commentDTOPageData.getRows().forEach(e -> {
            e.setCommTime(formatCommentTime(e.getCommTime()));
        });
        return commentDTOPageData;
    }

    public Long count() throws SQLException {
        String sql = "select count(1) from " + tableName;
        return ((Number) queryFirstObj(sql)).longValue();
    }

    public Long countToDayComment() throws SQLException {
        String sql = "select count(1) from " + tableName + " where commTime>?";
        return ((Number) queryFirstObj(sql, new SimpleDateFormat("yyyy-MM-dd").format(new Date()))).longValue();
    }

    public List<Comment> findHaveReadIsFalse() throws SQLException {
        return select("WHERE have_read=? ORDER BY commTime DESC", false);
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

    public void doRead(long id) {
        try {
            execute("UPDATE comment SET have_read=?,updatedAt=?,version=version+1 WHERE commentId=? AND have_read=?",
                    true, new java.sql.Timestamp(System.currentTimeMillis()).toString(), id, false);
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }
}
