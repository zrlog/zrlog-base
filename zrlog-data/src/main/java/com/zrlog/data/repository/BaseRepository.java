package com.zrlog.data.repository;

import com.hibegin.common.dao.BasePageableDAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.dao.entity.BaseEntity;
import com.hibegin.common.dao.entity.Entity;
import com.hibegin.common.dao.entity.EntityRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed row writes and explicit SQL projections, without mutable query/row state. */
public abstract class BaseRepository<E extends BaseEntity<ID>, ID> extends EntityRepository<E, ID> {
    protected final String tableName;
    protected final String pk;
    private final BasePageableDAO queries;

    protected BaseRepository(Class<E> entityClass, DataSourceWrapper source) {
        super(entityClass, source);
        Entity mapping = entityClass.getAnnotation(Entity.class);
        tableName = mapping.table();
        pk = mapping.idColumn();
        queries = new BasePageableDAO(source);
    }

    public boolean execute(String sql, Object... params) throws SQLException {
        return queries.execute(sql, params);
    }

    public List<Map<String, Object>> queryListWithParams(String sql, Object... params) throws SQLException {
        return queries.queryListWithParams(sql, params);
    }

    public Map<String, Object> queryFirstWithParams(String sql, Object... params) throws SQLException {
        return queries.queryFirstWithParams(sql, params);
    }

    public Object queryFirstObj(String sql, Object... params) throws SQLException {
        return queries.queryFirstObj(sql, params);
    }

    public List<Map<String, Object>> queryList(String... columns) throws SQLException {
        return queryListWithParams("SELECT " + (columns.length == 0 ? "*" : String.join(",", columns)) + " FROM `" + tableName + "`");
    }

    /** Explicit raw projection for authorization/history code; not a writable Entity. */
    public Map<String, Object> loadById(Object id) throws SQLException {
        return queryFirstWithParams("SELECT * FROM `" + tableName + "` WHERE `" + pk + "`=?", id);
    }

    public <T> PageData<T> queryPageData(String sql, PageRequest page, Object[] params, Class<T> type) {
        return queries.queryPageData(sql, page, params, type);
    }

    public PageData<Map<String, Object>> queryPageData(String sql, PageRequest page, Object[] params) {
        return queries.queryPageData(sql, page, params);
    }

    protected static <T> List<T> doConvertList(List<Map<String, Object>> rows, Class<T> type) {
        List<T> values = new ArrayList<>();
        for (Map<String, Object> row : rows) values.add(ResultBeanUtils.convert(row, type));
        return values;
    }
}
