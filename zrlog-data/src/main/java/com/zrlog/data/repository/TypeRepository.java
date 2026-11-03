package com.zrlog.data.repository;

import com.zrlog.model.Type;


import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.TypeDTO;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 存放文章的分类信息，对应数据的 type 表
 */
public class TypeRepository extends BaseRepository<Type, Long> {

    @Inject
    public TypeRepository(DataSourceWrapper dataSource) {
        super(Type.class, dataSource);
    }

    public List<TypeDTO> findAll() throws SQLException {
        return queryList("select t.typeId as id,t.alias,t.typeName,t.remark,t.arrange_plugin,(select count(logId) from " + LogRepository.TABLE_NAME +
                " where rubbish=? and privacy=? and typeid=t.typeid) as typeamount from " + tableName + " t", TypeDTO.class, false, false);
    }

    public Type findByAlias(String alias) throws SQLException {
        return findMatching(Map.of("alias", alias), 1).stream().findFirst().orElse(null);
    }

}
