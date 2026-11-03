package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.model.LogNav;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

/**
 * 存放导航条的信息，对应数据库的 lognav 表
 */
public class LogNavRepository extends BaseRepository<LogNav, Long> {

    @Inject
    public LogNavRepository(DataSourceWrapper dataSource) {
        super(LogNav.class, dataSource);
    }

    public List<LogNavDTO> findAll() throws SQLException {
        return queryList("select l.navId as id,l.navName,l.url,l.sort,l.icon from " + tableName + " l where l.url is not null and l.navName is not null order by sort", LogNavDTO.class);
    }
}
