package com.zrlog.model;

import com.hibegin.common.dao.BasePageableDAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.data.exception.DAOException;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

/**
 * 统计文章中标签出现的次数，方便展示。文章的数据发生变化后，会自动更新记录，无需额外程序控制，对应数据库的 tag 表
 */
public class Tag extends BasePageableDAO {

    private static final Logger LOGGER = LoggerUtil.getLogger(Tag.class);
    private final DataSourceWrapper dataSource;

    @Inject
    public Tag(DataSourceWrapper dataSource) {
        super(dataSource);
        this.dataSource = dataSource;
        this.tableName = "tag";
        this.pk = "tagId";
    }

    public List<TagDTO> findAll() throws SQLException {
        return doConvertList(queryList("tagId as id,text,count"), TagDTO.class);
    }

    private Set<String> strToSet(String str) {
        Set<String> tags = new HashSet<>();
        for (String tag : str.split(",")) {
            if (!tag.trim().isEmpty()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    public boolean update(String nowTagStr, String oldTagStr) {
        String copyNewTagStr = nowTagStr;
        String copyOldTagStr = oldTagStr;
        if (copyNewTagStr == null && oldTagStr == null) {
            return true;
        }
        if (copyNewTagStr == null) {
            copyNewTagStr = "";
        }
        if (copyOldTagStr == null) {
            copyOldTagStr = "";
        }
        if (copyNewTagStr.equals(copyOldTagStr)) {
            return true;
        }
        String[] oldArr = copyOldTagStr.split(",");
        String[] nowArr = copyNewTagStr.split(",");
        Set<String> addSet = new HashSet<>();
        Set<String> deleteSet = new HashSet<>();
        for (String str : nowArr) {
            addSet.add(str.trim());
        }
        for (String str : oldArr) {
            if (!addSet.contains(str)) {
                deleteSet.add(str.trim());
            } else {
                addSet.remove(str);
            }
        }
        insertTag(addSet);
        deleteTag(deleteSet);

        return true;
    }

    public List<TagDTO> refreshTag() throws SQLException {
        execute("delete from " + tableName);
        Map<String, Integer> countMap = new HashMap<>();
        List<Map<String, Object>> logs =
                new Log(dataSource)
                        .queryListWithParams(
                                "select keywords from "
                                        + Log.TABLE_NAME
                                        + " where rubbish=? and privacy=? ",
                                false,
                                false);
        for (Map<String, Object> log : logs) {
            if (StringUtils.isNotEmpty((String) log.get("keywords")) && !((String) log.get("keywords")).trim().isEmpty()) {
                Set<String> tagSet = strToSet(log.get("keywords") + ",");
                for (String tag : tagSet) {
                    countMap.merge(tag, 1, Integer::sum);
                }
            }
        }
        List<TagDTO> all = new ArrayList<>();
        for (Map<String, Integer> t : splitList(countMap, 10)) {
            List<TagDTO> maps = batchInsert(t, all.size());
            all.addAll(maps);
        }
        return all;
    }

    private List<Map<String, Integer>> splitList(Map<String, Integer> countMap, int maxCount) {
        List<Map<String, Integer>> splitList = new ArrayList<>();
        Map<String, Integer> chunkMap = new LinkedHashMap<>(maxCount);
        for (Map.Entry<String, Integer> tag : countMap.entrySet()) {
            if (chunkMap.size() == maxCount) {
                splitList.add(new LinkedHashMap<>(chunkMap));
                chunkMap.clear();
            }
            chunkMap.put(tag.getKey(), tag.getValue());
        }
        if (!chunkMap.isEmpty()) {
            splitList.add(chunkMap);
        }
        return splitList;
    }

    private List<TagDTO> batchInsert(Map<String, Integer> countMap, int idBase) throws SQLException {
        StringBuilder insertSql = new StringBuilder("insert into " + tableName + " (tagId,text,count) values ");
        List<Object> params = new ArrayList<>();
        int id = idBase;
        StringJoiner valueJoiner = new StringJoiner(",");
        List<TagDTO> all = new ArrayList<>();
        for (Map.Entry<String, Integer> tag : countMap.entrySet()) {
            id += 1;
            valueJoiner.add("(?,?,?)");
            params.add(id);
            params.add(tag.getKey());
            params.add(tag.getValue());
            TagDTO tagDO = new TagDTO();
            tagDO.setId((long) id);
            tagDO.setText(tag.getKey());
            tagDO.setCount(Long.valueOf(tag.getValue()));
            tagDO.setKeycode((long) tag.getKey().hashCode());
            all.add(tagDO);
        }
        insertSql.append(valueJoiner);
        execute(insertSql.toString(), params.toArray());
        return all;
    }

    private void insertTag(Set<String> now) {
        for (String add : now) {
            try {
                Map<String, Object> t = queryFirstWithParams("select * from " + tableName + " where text=?", add);
                if (t == null) {
                    new Tag(dataSource).set("text", add).set("count", 1).save();
                } else {
                    new Tag(dataSource)
                            .set("count", (int) t.get("count") + 1)
                            .updateById(t.get(pk));
                }
            } catch (SQLException e) {
                throw new DAOException(e);
            }
        }
    }

    private void deleteTag(Set<String> old) {
        for (String del : old) {
            try {
                TagDTO t = ResultBeanUtils.convert(queryFirstWithParams("select *,tagId as id from " + tableName + " where text=?", del), TagDTO.class);
                if (t != null) {
                    if (t.getCount() > 1) {
                        new Tag(dataSource).set("count", t.getCount() - 1).updateById(t.getId());
                    } else {
                        new Tag(dataSource).deleteById(Math.toIntExact(t.getId()));
                    }
                }
            } catch (SQLException e) {
                throw new DAOException(e);
            }
        }
    }

    public PageData<TagDTO> find(PageRequest page) {
        return queryPageData("select tagId as id,text,count from " + tableName, page, new Object[0], TagDTO.class);
    }
}
