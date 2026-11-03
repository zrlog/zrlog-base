package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.model.LogNav;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 存放导航条的信息，对应数据库的 lognav 表 */
public class LogNavRepository extends BaseRepository<LogNav, Long> {

    @Inject
    public LogNavRepository(DataSourceWrapper dataSource) {
        super(LogNav.class, dataSource);
    }

    public List<LogNavDTO> findAll() throws SQLException {
        List<LogNavDTO> result = new ArrayList<>();
        for (LogNav row : select("WHERE url IS NOT NULL AND navName IS NOT NULL ORDER BY sort")) {
            LogNavDTO item = new LogNavDTO();
            item.setId(row.getId());
            item.setNavName(row.getNavName());
            item.setUrl(row.getUrl());
            item.setSort(row.getSort() == null ? null : row.getSort().longValue());
            item.setIcon(row.getIcon());
            result.add(item);
        }
        return result;
    }
}
