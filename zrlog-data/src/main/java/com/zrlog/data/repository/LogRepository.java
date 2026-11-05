package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.common.dao.dto.UnPageRequestImpl;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.cache.vo.HotLogBasicInfoEntry;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.model.Log;
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

/** 存放文章数据，对应数据的 log 表。 */
public class LogRepository extends BaseRepository<Log, Long> implements Serializable {

    public static final String TABLE_NAME = "log";
    private static final String VISITOR_FROM =
            " from log l inner join user u on u.userId=l.userId"
                    + " inner join type t on t.typeId=l.typeId"
                    + " where l.rubbish=? and l.privacy=?";
    private final DataSourceWrapper dataSource;

    @Inject
    public LogRepository(DataSourceWrapper dataSource) {
        super(Log.class, dataSource);
        this.dataSource = dataSource;
    }

    /**
     * Atomically records a read without changing the article's version or last_update_date. Reads
     * are statistics, not content revisions: update(entity) would advance both fields, causing
     * unrelated editor conflicts and invalidating scheduled publication confirmations. Increment in
     * the database without loading an entity so concurrent reads cannot lose counts. Article edits
     * use the normal tracked update, which writes only changed fields and leaves this independently
     * updated counter intact.
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
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from "
                            + CommentRepository.TABLE_NAME
                            + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from "
                            + tableName
                            + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and rubbish=? and privacy=? and l.logId=?";
            detail = queryOne(sql, ArticleDetailDTO.class, false, false, idOrAlias);
        }
        if (Objects.isNull(detail)) {
            String sql =
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from "
                            + CommentRepository.TABLE_NAME
                            + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from "
                            + tableName
                            + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and rubbish=? and privacy=? and l.alias=?";
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

    public ArticleDetailDTO findByIdOrAlias(Object idOrAlias, boolean commentsDisabled)
            throws SQLException {
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
            futures.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    detail.setLastLog(findLastLog(Math.toIntExact(detail.getId())));
                                } catch (SQLException e) {
                                    throw new DAOException(e);
                                }
                            },
                            executor));
            futures.add(
                    CompletableFuture.runAsync(
                            () -> {
                                try {
                                    detail.setNextLog(findNextLog(Math.toIntExact(detail.getId())));
                                } catch (SQLException e) {
                                    throw new DAOException(e);
                                }
                            },
                            executor));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
        return detail;
    }

    private ArticleDetailDTO.LastLogDTO findLastLog(int id) throws SQLException {
        String lastLogSql =
                "select l.alias as alias,l.title as title from "
                        + tableName
                        + " l where rubbish=? and "
                        + "privacy"
                        + "=? and l.logId<? order by logId desc limit 1";
        return queryOne(lastLogSql, ArticleDetailDTO.LastLogDTO.class, false, false, id);
    }

    private ArticleDetailDTO.NextLogDTO findNextLog(int id) throws SQLException {
        String nextLogSql =
                "select l.alias as alias,l.title as title from "
                        + tableName
                        + " l where rubbish=? and "
                        + "privacy"
                        + "=? and l.logId>? limit 1";
        return queryOne(nextLogSql, ArticleDetailDTO.NextLogDTO.class, false, false, id);
    }

    public PageData<ArticleBasicDTO> visitorFindHome(PageRequest pageRequest) {
        return queryVisitorPage(pageRequest, "", new Object[] {false, false}, true);
    }

    public PageData<ArticleBasicDTO> visitorFind(PageRequest pageRequest, String keywords) {
        if (StringUtils.isEmpty(keywords)) {
            return queryVisitorPage(pageRequest, "", new Object[] {false, false}, false);
        }
        return queryVisitorPage(
                pageRequest,
                " and (l.title like ? or l.plain_content like ?)",
                new Object[] {false, false, "%" + keywords + "%", "%" + keywords + "%"},
                false);
    }

    public PageData<ArticleBasicDTO> findByTypeAlias(long page, long pageSize, String typeAlias) {
        return queryVisitorPage(
                new PageRequestImpl(page, pageSize),
                " and t.alias=?",
                new Object[] {false, false, typeAlias},
                false);
    }

    public Map<String, Long> getArchives() throws SQLException {
        List<ArticleBasicDTO> lo =
                queryList(
                        "select releaseTime from "
                                + tableName
                                + "  where rubbish=? and privacy=? "
                                + "order by "
                                + "releaseTime desc",
                        ArticleBasicDTO.class,
                        false,
                        false);
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
        return queryVisitorPage(
                new PageRequestImpl(page, pageSize),
                " and (l.keywords like ? or l.keywords like ? or l.keywords like ? or l.keywords=?)",
                new Object[] {false, false, tag + ",%", "%," + tag + ",%", "%," + tag, tag},
                false);
    }

    public PageData<ArticleBasicDTO> findByDate(long page, long pageSize, String yyyyMM) {
        if (StringUtils.isEmpty(yyyyMM)) {
            return new PageData<>();
        }
        String start = yyyyMM.replace("_", "-") + "-01 00:00:00";
        YearMonth parse = YearMonth.parse(yyyyMM, DateTimeFormatter.ofPattern("yyyy_MM"));
        String end =
                parse.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        + " 23:59:59";
        return queryVisitorPage(
                new PageRequestImpl(page, pageSize),
                " and l.releaseTime between ? and ?",
                new Object[] {false, false, start, end},
                false);
    }

    public long getVisitorCount() {
        return countVisitorArticles(VISITOR_FROM, new Object[] {false, false});
    }

    public List<HotLogBasicInfoEntry> findRecentBasic(long limit) {
        return queryRecentBasic("", new Object[] {false, false}, limit);
    }

    public List<HotLogBasicInfoEntry> findRecentBasicByTypeAlias(String typeAlias, long limit) {
        return queryRecentBasic(" and t.alias=?", new Object[] {false, false, typeAlias}, limit);
    }

    private List<HotLogBasicInfoEntry> queryRecentBasic(
            String filter, Object[] params, long limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<Object> arguments = new ArrayList<>(Arrays.asList(params));
        arguments.add(limit);
        try {
            // Sidebar/cache consumers need links, not full article pages or their total count.
            return queryList(
                    "select l.logId,l.title,l.alias"
                            + VISITOR_FROM
                            + filter
                            + " order by l.logId desc limit ?",
                    HotLogBasicInfoEntry.class,
                    arguments.toArray());
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    private long countVisitorArticles(String from, Object[] params) {
        try {
            // Retain the joins: articles with a missing author/category were never public rows.
            return ((Number) queryFirstObj("select count(*)" + from, params)).longValue();
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    private PageData<ArticleBasicDTO> queryVisitorPage(
            PageRequest page, String filter, Object[] params, boolean sticky) {
        String from = VISITOR_FROM + filter;
        PageData<ArticleBasicDTO> data = new PageData<>();
        if (page.getSize() <= 0) {
            data.setTotalElements(countVisitorArticles(from, params));
        } else {
            ExecutorService executor = ThreadUtils.newFixedThreadPool(2);
            try {
                CompletableFuture<Long> total =
                        CompletableFuture.supplyAsync(
                                () -> countVisitorArticles(from, params), executor);
                CompletableFuture<List<ArticleBasicDTO>> rows =
                        CompletableFuture.supplyAsync(
                                () -> queryVisitorRows(page, from, params, sticky), executor);
                CompletableFuture.allOf(total, rows).join();
                data.setTotalElements(total.join());
                data.setRows(rows.join());
            } finally {
                executor.shutdown();
            }
        }
        data.setPage(page.getPage());
        data.setSize(
                page instanceof UnPageRequestImpl
                        ? Math.max(data.getRows().size(), 1000000L)
                        : page.getSize());
        List<String> sorts = new ArrayList<>();
        if (page.getSorts() != null) {
            page.getSorts().forEach(sort -> sorts.add(sort.toParamString()));
        }
        data.setSort(sorts);
        return data;
    }

    private List<ArticleBasicDTO> queryVisitorRows(
            PageRequest page, String from, Object[] params, boolean sticky) {
        String order = sticky ? "l.sticky desc,l.logId desc" : "l.logId desc";
        // Select page IDs and joined metadata before reading bodies or counting comments. LIMIT
        // keeps this derived table from being merged into the outer query by MySQL/SQLite.
        String ids =
                "select l.logId,t.typeName,t.alias as typeAlias,u.userName,u.header"
                        + from
                        + " order by "
                        + order;
        List<Object> arguments = new ArrayList<>(Arrays.asList(params));
        if (page instanceof PageRequestImpl) {
            ids += " limit ?,?";
            arguments.add(page.getOffset());
            arguments.add(page.getSize());
        }
        String sql =
                "select l.*,p.typeName,p.typeAlias,p.userName,p.header,"
                        + "(select count(commentId) from comment where logId=l.logId) commentSize"
                        + " from ("
                        + ids
                        + ") p inner join log l on l.logId=p.logId order by "
                        + order;
        try {
            return queryList(sql, ArticleBasicDTO.class, arguments.toArray());
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }
}
