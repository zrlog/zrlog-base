package com.zrlog.business.support;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.InMemoryDatabase;
import com.zrlog.util.DataSourceUtil;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class InMemoryZrLogDatabase implements AutoCloseable {

    private final DataSourceWrapper dataSource;
    private final InMemoryDatabase database;

    private InMemoryZrLogDatabase() throws Exception {
        this.dataSource = newDataSource();
        this.database = InMemoryDatabase.open(dataSource, true);
        loadSchema();
    }

    public static InMemoryZrLogDatabase open() throws Exception {
        return new InMemoryZrLogDatabase();
    }

    public DataSourceWrapper dataSource() {
        return dataSource;
    }

    public int update(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().update(sql, params);
    }

    public Object scalar(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new ScalarHandler<>(1), params);
    }

    public Map<String, Object> queryOne(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new MapHandler(), params);
    }

    public List<Map<String, Object>> queryList(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new MapListHandler(), params);
    }

    private static DataSourceWrapper newDataSource() {
        Properties properties = InMemoryDatabase.h2Properties("zrlog_base_" + UUID.randomUUID());
        return DataSourceUtil.buildDataSource(properties);
    }

    private void loadSchema() throws Exception {
        try (InputStream input = InMemoryZrLogDatabase.class.getResourceAsStream("/init-table-structure.sql")) {
            if (input == null) {
                throw new IllegalStateException("Missing init-table-structure.sql from zrlog-install-web test dependency");
            }
            database.loadMySQLSchema(input);
            dataSource.getQueryRunner().update("alter table log add column if not exists sticky integer not null default 0");
        }
    }

    @Override
    public void close() throws Exception {
        database.close();
    }
}
