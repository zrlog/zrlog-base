package com.zrlog.data.repository;

import com.zrlog.model.Plugin;


import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.PluginDTO;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

/**
 * 存放了与侧边栏相关的数据，主要用于保存侧边栏的key，对应数据库的plugin表（目前实现方式不友好，建议直接使用主题设置，替换该设置）
 */
public class PluginRepository extends BaseRepository<Plugin, Long> {

    @Inject
    public PluginRepository(DataSourceWrapper dataSource) {
        super(Plugin.class, dataSource);
    }

    public List<PluginDTO> findAll() throws SQLException {
        List<PluginDTO> result = new java.util.ArrayList<>();
        for (Plugin row : select("WHERE level>?", 0)) {
            PluginDTO item = new PluginDTO();
            item.setId(row.getId());
            item.setPluginName(row.getPluginName());
            item.setSystem(row.getIsSystem());
            item.setLevel(row.getLevel() == null ? null : row.getLevel().longValue());
            item.setContent(row.getContent());
            item.setpTitle(row.getPTitle());
            result.add(item);
        }
        return result;
    }
}
