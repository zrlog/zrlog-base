package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.common.cache.dto.LinkDTO;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;

/**
 * 存放程序的友情链接，对应数据库 link 表
 */
public class Link extends BasePageableDAO {

    @Inject
    public Link(DataSourceWrapper dataSource) {
        super(dataSource);
        this.tableName = "link";
        this.pk = "linkId";
    }

    public List<LinkDTO> findAll() throws SQLException {
        return doConvertList(queryListWithParams("select linkName,linkId as id,sort,url,alt,icon from " + tableName + " order by sort"), LinkDTO.class);
    }

    public PageData<LinkDTO> find(PageRequest page) {
        return queryPageData("select linkName,linkId as id,sort,url,alt,icon from " + tableName + " order by sort", page, new Object[0], LinkDTO.class);
    }
}
