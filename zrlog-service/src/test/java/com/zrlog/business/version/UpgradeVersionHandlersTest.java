package com.zrlog.business.version;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import org.apache.commons.dbutils.QueryRunner;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class UpgradeVersionHandlersTest {

    private static Map<String, Object> row(Long logId, String content) {
        Map<String, Object> row = new HashMap<>();
        row.put("logid", logId);
        row.put("content", content);
        return row;
    }

    @Test
    public void shouldInitializeThumbnailDuringV5Upgrade() throws Exception {
        RecordingDao dao = new RecordingDao(row(7L, "<p>Hello</p>"));

        new FastV5UpgradeVersionHandler().doUpgrade(dao);

        assertEquals("select logid,content from log", dao.querySql);
        assertEquals("update log set thumbnail = ? where logid = ?", dao.executedSql.get(0));
        assertEquals("", dao.executedParams.get(0)[0]);
        assertEquals(7L, dao.executedParams.get(0)[1]);
    }

    @Test
    public void shouldBackfillSearchContentDuringV6Upgrade() throws Exception {
        RecordingDao dao = new RecordingDao(row(8L, "<h1>Hello</h1><p>ZrLog&nbsp;Search</p>"));

        new FastV6UpgradeVersionHandler().doUpgrade(dao);

        assertEquals("select logid,content from log", dao.querySql);
        assertEquals("update log set search_content = ? where logid = ?", dao.executedSql.get(0));
        assertEquals("Hello ZrLog Search", dao.executedParams.get(0)[0]);
        assertEquals(8L, dao.executedParams.get(0)[1]);
    }

    private static class RecordingDao extends DAO {

        private final List<Map<String, Object>> rows;
        private final List<String> executedSql = new ArrayList<>();
        private final List<Object[]> executedParams = new ArrayList<>();
        private String querySql;

        RecordingDao(Map<String, Object> row) {
            super(dataSource());
            this.rows = List.of(row);
        }

        private static DataSourceWrapper dataSource() {
            return (DataSourceWrapper) Proxy.newProxyInstance(
                    UpgradeVersionHandlersTest.class.getClassLoader(),
                    new Class[]{DataSourceWrapper.class},
                    (proxy, method, args) -> {
                        if ("getQueryRunner".equals(method.getName())) {
                            return new QueryRunner();
                        }
                        if ("toString".equals(method.getName())) {
                            return "DataSourceWrapperProxy";
                        }
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class) {
                            return false;
                        }
                        if (returnType == int.class) {
                            return 0;
                        }
                        if (returnType == long.class) {
                            return 0L;
                        }
                        return null;
                    });
        }

        @Override
        public List<Map<String, Object>> queryListWithParams(String sql, Object... params) {
            querySql = sql;
            return rows;
        }

        @Override
        public boolean execute(String sql, Object... params) throws SQLException {
            executedSql.add(sql);
            executedParams.add(params);
            return true;
        }
    }

    private static class FastV5UpgradeVersionHandler extends V5UpgradeVersionHandler {

        @Override
        protected void beforeUpgrade() {
        }
    }

    private static class FastV6UpgradeVersionHandler extends V6UpgradeVersionHandler {

        @Override
        protected void beforeUpgrade() {
        }
    }
}
