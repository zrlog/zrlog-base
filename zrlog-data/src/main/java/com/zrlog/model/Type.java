package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.common.cache.dto.TypeDTO;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 存放文章的分类信息，对应数据的 type 表
 */
public class Type extends BasePageableDAO {

    @Inject
    public Type(DataSourceWrapper dataSource) {
        super(dataSource);
        this.pk = "typeId";
        this.tableName = "type";
    }

    public List<TypeDTO> findAll() throws SQLException {
        return doConvertList(queryListWithParams("select t.typeId as id,t.alias,t.typeName,t.remark,t.arrange_plugin,(select count(logId) from " + Log.TABLE_NAME +
                " where rubbish=? and privacy=? and typeid=t.typeid) as typeamount from " + tableName + " t", false, false), TypeDTO.class);
    }

    public PageData<TypeDTO> find(PageRequest page) {
        return queryPageData("select t.typeId as id,t.alias,t.typeName,t.remark,t.arrange_plugin,(select count(logId) from " + Log.TABLE_NAME +
                " where typeid=t.typeid) as typeamount from " + tableName + " t", page, new Object[0], TypeDTO.class);
    }

    public Map<String, Object> findByAlias(String alias) throws SQLException {
        return queryFirstWithParams("select * from " + tableName + " where alias=?", alias);
    }

}
