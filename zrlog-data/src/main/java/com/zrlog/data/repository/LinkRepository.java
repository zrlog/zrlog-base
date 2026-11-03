package com.zrlog.data.repository;

import com.zrlog.model.Link;


import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.LinkDTO;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

/**
 * 存放程序的友情链接，对应数据库 link 表
 */
public class LinkRepository extends BaseRepository<Link, Long> {

    @Inject
    public LinkRepository(DataSourceWrapper dataSource) {
        super(Link.class, dataSource);
    }

    public List<LinkDTO> findAll() throws SQLException {
        return queryList("select linkName,linkId as id,sort,url,alt,icon from " + tableName + " order by sort", LinkDTO.class);
    }
}
