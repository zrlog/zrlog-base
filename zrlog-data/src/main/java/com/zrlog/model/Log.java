package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.OrderBy;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Constants;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import com.zrlog.util.ParseUtil;
import com.zrlog.util.ThreadUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 存放文章数据，对应数据的 log 表。
 */
public class Log extends BasePageableDAO implements Serializable {

    public static final String TABLE_NAME = "log";

    public Log() {
        this.tableName = TABLE_NAME;
        this.pk = "logId";
    }

    private ArticleDetailDTO getDetail(Object idOrAlias) throws SQLException {
        if (idOrAlias == null) {
            return null;
        }
        ArticleDetailDTO detail = null;
        if (idOrAlias instanceof Number || ParseUtil.isNumeric(String.valueOf(idOrAlias))) {
            String sql =
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + Comment.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and rubbish=? and privacy=? and l.logId=?";
            Map<String, Object> log = queryFirstWithParams(sql, false, false, idOrAlias);
            if (log != null) {
                detail = ResultBeanUtils.convert(log, ArticleDetailDTO.class);
            }
        }
        if (Objects.isNull(detail)) {
            String sql =
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + Comment.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and rubbish=? and privacy=? and l.alias=?";
            Map<String, Object> log = queryFirstWithParams(sql, false, false, String.valueOf(idOrAlias));
            if (Objects.nonNull(log)) {
                detail = ResultBeanUtils.convert(log, ArticleDetailDTO.class);
            }
        }
        if (Objects.isNull(detail)) {
            return null;
        }
        if (Objects.isNull(detail.getId())) {
            detail.setId(detail.getLogId());
        }
        return detail;
    }

    public ArticleDetailDTO findByIdOrAlias(Object idOrAlias) throws SQLException {
        ExecutorService executor = ThreadUtils.newFixedThreadPool(3);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ArticleDetailDTO detail = getDetail(idOrAlias);
        if (Objects.isNull(detail)) {
            return null;
        }
        try {
            if (ResultValueConvertUtils.toBoolean(detail.getCanComment()) && !Constants.zrLogConfig.getCacheService().getPublicWebSiteInfo().getDisable_comment_status()) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        detail.setComments(new Comment().visitorFindAllByLogId(detail.getId().intValue()));
                    } catch (SQLException e) {
                        throw new DAOException(e);
                    }
                }, executor));
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

    /**
     * 这个用于Admin 进行查询不检查
     */
    public ArticleBasicDTO adminFindByIdOrAlias(Object idOrAlias) throws SQLException {
        if (idOrAlias == null) {
            return null;
        }
        if (idOrAlias instanceof Number || ParseUtil.isNumeric(String.valueOf(idOrAlias))) {
            String sql =
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + Comment.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and l.logId=?";
            Map<String, Object> log = queryFirstWithParams(sql, idOrAlias);
            if (log != null) {
                return ResultBeanUtils.convert(log, ArticleBasicDTO.class);
            }
        }
        String sql =
                "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + Comment.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and l.alias=?";
        Map<String, Object> log = queryFirstWithParams(sql, String.valueOf(idOrAlias));
        if (Objects.isNull(log)) {
            return null;
        }
        return ResultBeanUtils.convert(log, ArticleBasicDTO.class);
    }

    private ArticleDetailDTO.LastLogDTO findLastLog(int id) throws SQLException {
        String lastLogSql =
                "select l.alias as alias,l.title as title from " + tableName + " l where rubbish=? and " + "privacy" + "=? and l.logId<? order by logId desc limit 1";
        return BeanUtil.convert(queryFirstWithParams(lastLogSql, false, false, id), ArticleDetailDTO.LastLogDTO.class);

    }

    private ArticleDetailDTO.NextLogDTO findNextLog(int id) throws SQLException {
        String nextLogSql =
                "select l.alias as alias,l.title as title from " + tableName + " l where rubbish=? and " + "privacy" + "=? and l.logId>? limit 1";
        return BeanUtil.convert(queryFirstWithParams(nextLogSql, false, false, id), ArticleDetailDTO.NextLogDTO.class);
    }

    public long findMaxId() throws SQLException {
        Number number = (Number) queryFirstObj("select max(logId) max from " + tableName);
        if (Objects.isNull(number)) {
            return 0;
        }
        return number.longValue();

    }

    public PageData<ArticleBasicDTO> visitorFindHome(PageRequest pageRequest) {
        String sql =
                "select l.*,t.typeName,t.alias as typeAlias,u.userName,u.header as header,(select count(commentId) from " + Comment.TABLE_NAME + " where logId=l.logId) commentSize from " + tableName + " l inner join user u inner join type t where l.rubbish=? and l.privacy=? and u.userId=l.userId and t.typeid=l.typeid order by l.sticky desc,l.logId desc";
        return queryPageData(sql, pageRequest, new Object[]{false, false}, ArticleBasicDTO.class);
    }

    public PageData<ArticleBasicDTO> visitorFind(PageRequest pageRequest, String keywords) {
        if (StringUtils.isEmpty(keywords)) {
            String sql =
                    "select l.*,t.typeName,t.alias as typeAlias,u.userName,u.header as header,(select count(commentId) from " + Comment.TABLE_NAME + " where logId=l.logId) commentSize from " + tableName + " l inner join user u inner join type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeid=l.typeid  order by l.logId desc";
            return queryPageData(sql, pageRequest, new Object[]{false, false}, ArticleBasicDTO.class);
        }
        String sql =
                "select l.*,t.typeName,t.alias as typeAlias,(select count(commentId) from " + Comment.TABLE_NAME + " "
                        + "where logId=l.logId) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type" + " t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and (l" + ".title " + "like ? or l.plain_content like ?) order by l.logId desc";
        return queryPageData(sql, pageRequest, new Object[]{false, false, "%" + keywords + "%", "%" + keywords + "%"}, ArticleBasicDTO.class);
    }

    /**
     * 管理员查询文章
     */
    public PageData<ArticleBasicDTO> adminFind(PageRequest pageRequest, String keywords, String typeAlias) {
        return adminFind(pageRequest, keywords, typeAlias, "");
    }

    /**
     * 管理员查询文章
     */
    public PageData<ArticleBasicDTO> adminFind(PageRequest pageRequest, String keywords, String typeAlias, String status) {
        String searchKeywords = "";
        List<Object> searchParam = new ArrayList<>();
        if (StringUtils.isNotEmpty(keywords)) {
            searchKeywords = " and (l.title like ? or l.plain_content like ? or l.keywords like ? or l.alias like ?)";
            searchParam.add("%" + keywords + "%");
            searchParam.add("%" + keywords + "%");
            searchParam.add("%" + keywords + "%");
            searchParam.add("%" + keywords + "%");
        }
        if (StringUtils.isNotEmpty(typeAlias)) {
            searchKeywords += " and t.alias = ?";
            searchParam.add(typeAlias);
        }
        if (StringUtils.isNotEmpty(status)) {
            switch (status) {
                case "published":
                    searchKeywords += " and l.rubbish = ? and l.privacy = ?";
                    searchParam.add(false);
                    searchParam.add(false);
                    break;
                case "private":
                    searchKeywords += " and l.privacy = ?";
                    searchParam.add(true);
                    break;
                case "draft":
                    searchKeywords += " and l.rubbish = ?";
                    searchParam.add(true);
                    break;
            }
        }
        String pageSort = getPageSort(pageRequest);
        String sql =
                "select l.*,t.typeName,l.logId as id,l.last_update_date as lastUpdateDate,t" +
                        ".alias as typeAlias,u.userName,u.header as header,(select count(commentId) from " + Comment.TABLE_NAME + " " +
                        "where " + "logId=l.logId ) commentSize from " + tableName + " l inner join user u inner " + "join type t where u" + ".userId=l.userId" + searchKeywords +
                        " and t.typeid=l.typeid and l.typeid is not null order " + "by " + pageSort;
        return queryPageData(
                sql, pageRequest,
                searchParam.toArray(), ArticleBasicDTO.class);
    }

    private static final Map<String, String> sortKeyMap = new HashMap<>();

    static {
        sortKeyMap.put("typeName", "l.typeId");
        sortKeyMap.put("releaseTime", "l.releaseTime");
        sortKeyMap.put("commentSize", "commentSize");
        sortKeyMap.put("privacy", "l.privacy");
        sortKeyMap.put("click", "l.click");
        sortKeyMap.put("lastUpdateDate", "l.last_update_date");
    }


    private static String getPageSort(PageRequest pageRequest) {
        List<OrderBy> orders = pageRequest.getSorts();
        if (orders == null || orders.isEmpty()) {
            return "l.logId desc";
        }
        StringBuilder orderSort = new StringBuilder();
        for (OrderBy orderBy : orders) {
            orderSort.append(sortKeyMap.getOrDefault(orderBy.getSortKey(), "l.logId"));
            orderSort.append(" ").append(orderBy.getDirection().name().toLowerCase());
        }
        return orderSort.toString();
    }

    public PageData<ArticleBasicDTO> findByTypeAlias(long page, long pageSize, String typeAlias) {
        String sql =
                "select l.*,t.typeName,t.alias  as typeAlias,(select count(commentId) from " + Comment.TABLE_NAME +
                        " where logId=l.logId ) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and t" + ".alias=? order by l.logId desc";
        return queryPageData(sql, new PageRequestImpl(page, pageSize), new Object[]{false, false, typeAlias}, ArticleBasicDTO.class);
    }

    public Map<String, Long> getArchives() throws SQLException {
        List<Map<String, Object>> lo =
                queryListWithParams("select releaseTime from " + tableName + "  where rubbish=? and privacy=? " + "order by " + "releaseTime desc", false, false);
        Map<String, Long> archives = new LinkedHashMap<>();
        for (Map<String, Object> entry : lo) {
            Object value = entry.get("releaseTime");
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

    public Map<String, Long> getAdminArticleData() throws SQLException {
        List<Map<String, Object>> lo = queryListWithParams("select releaseTime from " + tableName + " order by releaseTime desc");
        Map<String, Long> archives = new LinkedHashMap<>();
        for (Map<String, Object> entry : lo) {
            Object value = entry.get("releaseTime");
            if (Objects.isNull(value)) {
                continue;
            }
            String key = ResultValueConvertUtils.formatDate(value, "yyyy-MM-dd");
            if (archives.containsKey(key)) {
                archives.put(key, archives.get(key) + 1);
            } else {
                archives.put(key, 1L);
            }
        }
        return archives;
    }


    public PageData<ArticleBasicDTO> findByTag(long page, long pageSize, String tag) {
        String sql =
                "select l.*,t.typeName,t.alias  as typeAlias,(select count(commentId) from " + Comment.TABLE_NAME +
                        " where logId=l.logId) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and (l" + ".keywords like ? or l.keywords like ? or l.keywords like ? or l.keywords= ?) order by l" + ".logId desc";
        return queryPageData(sql, new PageRequestImpl(page, pageSize), new Object[]{false, false, tag + ",%", "%," + tag + ",%", "%," + tag, tag}, ArticleBasicDTO.class);
    }

    public PageData<ArticleBasicDTO> findByDate(long page, long pageSize, String yyyyMM) {
        if (StringUtils.isEmpty(yyyyMM)) {
            return new PageData<>();
        }
        String sql =
                "select l.*,t.typeName,t.alias as typeAlias,(select count(commentId) from " + Comment.TABLE_NAME + " "
                        + "where logId=l.logId ) commentSize,u.userName,u.header as header from " + tableName + " l inner join user u," + "type t where rubbish=? and privacy=? and u.userId=l.userId and t.typeId=l.typeId and releaseTime between ? and ? order by l.logId desc";
        String start = yyyyMM.replace("_", "-") + "-01 00:00:00";
        YearMonth parse = YearMonth.parse(yyyyMM, DateTimeFormatter.ofPattern("yyyy_MM"));
        String end = parse.atEndOfMonth().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 23:59:59";
        return queryPageData(sql, new PageRequestImpl(page, pageSize)
                , new Object[]{false, false, start, end}, ArticleBasicDTO.class);

    }

    public BigDecimal sumClick() throws SQLException {
        Number sum = (Number) queryFirstObj("select sum(click) from " + tableName);
        return sum == null ? new BigDecimal(0) : new BigDecimal(sum.longValue());
    }

    public long getVisitorCount() {
        return visitorFind(new PageRequestImpl(1L, 0L), null).getTotalElements();
    }

    public long countByTypeId(Integer typeId) {
        try {
            return ((Number) queryFirstObj("select count(1) as count from " + tableName + " where typeId=?", typeId)).longValue();
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    public long getAdminCount() {
        return adminFind(new PageRequestImpl(1L, 0L), null, null).getTotalElements();
    }
}
