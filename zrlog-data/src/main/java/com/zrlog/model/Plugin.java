package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.PluginDTO;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

/**
 * 存放了与侧边栏相关的数据，主要用于保存侧边栏的key，对应数据库的plugin表（目前实现方式不友好，建议直接使用主题设置，替换该设置）
 */
public class Plugin extends BasePageableDAO {

    @Inject
    public Plugin(DataSourceWrapper dataSource) {
        super(dataSource);
        this.tableName = "plugin";
    }

    public List<PluginDTO> findAll() throws SQLException {
        return doConvertList(queryListWithParams("select * from " + tableName + " where level>?", 0), PluginDTO.class);
    }
}
