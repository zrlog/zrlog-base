package com.zrlog.data.repository;

import com.zrlog.model.Log;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.common.util.StringUtils;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.util.ParseUtil;
import com.zrlog.util.ThreadUtils;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 存放文章数据，对应数据的 log 表。
 */
public class LogRepository extends BaseRepository<Log, Long> implements Serializable {

    public static final String TABLE_NAME = "log";
    private final DataSourceWrapper dataSource;

    @Inject
    public LogRepository(DataSourceWrapper dataSource) {
        super(Log.class, dataSource);
        this.dataSource = dataSource;
    }

    /**
     * Atomically records a read without changing the article's version or last_update_date.
     * Reads are statistics, not content revisions: update(entity) would advance both fields,
     * causing unrelated editor conflicts and invalidating scheduled publication confirmations.
     * Increment in the database without loading an entity so concurrent reads cannot lose counts.
     * Article edits use the normal tracked update, which writes only changed fields and leaves
     * this independently updated counter intact.
     *
     * @return whether the article still exists and its counter was incremented
     */
    public boolean incrementClick(Long logId) throws SQLException {
        return execute("UPDATE log SET click=click+1 WHERE logId=?", logId);
    }

    private ArticleDetailDTO getDetail(Object idOrAlias) throws SQLException {
        if (idOrAlias == null) {
            return null;
        }
        ArticleDetailDTO detail = null;
        if (idOrAlias instanceof Number || ParseUtil.isNumeric(String.valueOf(idOrAlias))) {
            String sql =
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + CommentRepository.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and rubbish=? and privacy=? and l.logId=?";
            detail = queryOne(sql, ArticleDetailDTO.class, false, false, idOrAlias);
        }
        if (Objects.isNull(detail)) {
            String sql =
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + CommentRepository.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and rubbish=? and privacy=? and l.alias=?";
            detail = queryOne(sql, ArticleDetailDTO.class, false, false, String.valueOf(idOrAlias));
        }
        if (Objects.isNull(detail)) {
            return null;
        }
        if (Objects.isNull(detail.getId())) {
            detail.setId(detail.getLogId());
        }
        return detail;
    }

    public ArticleDetailDTO findByIdOrAlias(Object idOrAlias, boolean commentsDisabled) throws SQLException {
        ExecutorService executor = ThreadUtils.newFixedThreadPool(3);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ArticleDetailDTO detail = getDetail(idOrAlias);
        if (Objects.isNull(detail)) {
            return null;
        }
        try {
            if (ResultValueConvertUtils.toBoolean(detail.getCanComment()) && !commentsDisabled) {
                futures.add(
                        CompletableFuture.runAsync(
                                () -> {
                                    try {
                                        detail.setComments(
                                                new CommentRepository(dataSource)
                                                        .visitorFindAllByLogId(
                                                                detail.getId().intValue()));
                                    } catch (SQLException e) {
                                        throw new DAOException(e);
                                    }
                                },
                                executor));
            } else {
                detail.setComments(new ArrayList<>());
            }
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    detail.setLastLog(findLastLog(Math.toIntExact(detail.getId())));
                } catch (SQLException e) {
                    throw new DAOException(e);
                }
            }, executor));
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    detail.setNextLog(findNextLog(Math.toIntExact(detail.getId())));
                } catch (SQLException e) {
                    throw new DAOException(e);
                }
            }, executor));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        return detail;
    }

    private ArticleDetailDTO.LastLogDTO findLastLog(int id) throws SQLException {
        String lastLogSql =
                "select l.alias as alias,l.title as title from " + tableName + " l where rubbish=? and " + "privacy" + "=? and l.logId<? order by logId desc limit 1";
        return queryOne(lastLogSql, ArticleDetailDTO.LastLogDTO.class, false, false, id);

    }

    private ArticleDetailDTO.NextLogDTO findNextLog(int id) throws SQLException {
        String nextLogSql =
                "select l.alias as alias,l.title as title from " + tableName + " l where rubbish=? and " + "privacy" + "=? and l.logId>? limit 1";
        return queryOne(nextLogSql, ArticleDetailDTO.NextLogDTO.class, false, false, id);
    }

    public PageData<ArticleBasicDTO> visitorFindHome(PageRequest pageRequest) {
        String sql =
                "select l.*,t.typeName,t.alias as typeAlias,u.userName,u.header as header,(select count(commentId) from " + CommentRepository.TABLE_NAME + " where logId=l.logId) commentSize from " + tableName + " l inner join user u inner join type t where l.rubbish=? and l.privacy=? and u.userId=l.userId and t.typeid=l.typeid order by l.sticky desc,l.logId desc";
        return queryPageData(sql, pageRequest, new Object[]{false, false}, ArticleBasicDTO.class);
    }

    public PageData<ArticleBasicDTO> visitorFind(PageRequest pageRequest, String keywords) {
        if (StringUtils.isEmpty(keywords)) {
            String sql =
                    "select l.*,t.typeName,t.alias as typeAlias,u.userName,u.header as header,(select count(commentId) from " + CommentRepository.TABLE_NAME + " where logId=l.logId) commentSize from " + tableName + " l inner join user u inner join type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeid=l.typeid  order by l.logId desc";
            return queryPageData(sql, pageRequest, new Object[]{false, false}, ArticleBasicDTO.class);
        }
        String sql =
                "select l.*,t.typeName,t.alias as typeAlias,(select count(commentId) from " + CommentRepository.TABLE_NAME + " "
                        + "where logId=l.logId) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type" + " t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and (l" + ".title " + "like ? or l.plain_content like ?) order by l.logId desc";
        return queryPageData(sql, pageRequest, new Object[]{false, false, "%" + keywords + "%", "%" + keywords + "%"}, ArticleBasicDTO.class);
    }

    public PageData<ArticleBasicDTO> findByTypeAlias(long page, long pageSize, String typeAlias) {
        String sql =
                "select l.*,t.typeName,t.alias  as typeAlias,(select count(commentId) from " + CommentRepository.TABLE_NAME +
                        " where logId=l.logId ) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and t" + ".alias=? order by l.logId desc";
        return queryPageData(sql, new PageRequestImpl(page, pageSize), new Object[]{false, false, typeAlias}, ArticleBasicDTO.class);
    }

    public Map<String, Long> getArchives() throws SQLException {
        List<ArticleBasicDTO> lo =
                queryList("select releaseTime from " + tableName + "  where rubbish=? and privacy=? " + "order by " + "releaseTime desc", ArticleBasicDTO.class, false, false);
        Map<String, Long> archives = new LinkedHashMap<>();
        for (ArticleBasicDTO entry : lo) {
            Object value = entry.getReleaseTime();
            if (value != null) {
                String key = ResultValueConvertUtils.formatDate(value, "yyyy_MM");
                if (archives.containsKey(key)) {
                    archives.put(key, archives.get(key) + 1);
                } else {
                    archives.put(key, 1L);
                }
            }
        }
        return archives;
    }

    public PageData<ArticleBasicDTO> findByTag(long page, long pageSize, String tag) {
        String sql =
                "select l.*,t.typeName,t.alias  as typeAlias,(select count(commentId) from " + CommentRepository.TABLE_NAME +
                        " where logId=l.logId) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and (l" + ".keywords like ? or l.keywords like ? or l.keywords like ? or l.keywords= ?) order by l" + ".logId desc";
        return queryPageData(sql, new PageRequestImpl(page, pageSize), new Object[]{false, false, tag + ",%", "%," + tag + ",%", "%," + tag, tag}, ArticleBasicDTO.class);
    }

    public PageData<ArticleBasicDTO> findByDate(long page, long pageSize, String yyyyMM) {
        if (StringUtils.isEmpty(yyyyMM)) {
            return new PageData<>();
        }
        String sql =
                "select l.*,t.typeName,t.alias as typeAlias,(select count(commentId) from " + CommentRepository.TABLE_NAME + " "
                        + "where logId=l.logId ) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and releaseTime between ? and ? order by l.logId desc";
        String start = yyyyMM.replace("_", "-") + "-01 00:00:00";
        YearMonth parse = YearMonth.parse(yyyyMM, DateTimeFormatter.ofPattern("yyyy_MM"));
        String end = parse.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 23:59:59";
        return queryPageData(sql, new PageRequestImpl(page, pageSize)
                , new Object[]{false, false, start, end}, ArticleBasicDTO.class);

    }

    public long getVisitorCount() {
        return visitorFind(new PageRequestImpl(1L, 0L), null).getTotalElements();
    }


}
