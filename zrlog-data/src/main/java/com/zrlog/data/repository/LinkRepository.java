package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.model.Link;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 存放程序的友情链接，对应数据库 link 表 */
public class LinkRepository extends BaseRepository<Link, Long> {

    @Inject
    public LinkRepository(DataSourceWrapper dataSource) {
        super(Link.class, dataSource);
    }

    public List<LinkDTO> findAll() throws SQLException {
        List<LinkDTO> result = new ArrayList<>();
        for (Link row : select("ORDER BY sort")) {
            LinkDTO item = new LinkDTO();
            item.setId(row.getId());
            item.setLinkName(row.getLinkName());
            item.setSort(row.getSort() == null ? null : row.getSort().longValue());
            item.setUrl(row.getUrl());
            item.setAlt(row.getAlt());
            item.setIcon(row.getIcon());
            result.add(item);
        }
        return result;
    }
}
