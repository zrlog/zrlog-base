package com.zrlog.data.repository;

import com.zrlog.model.Tag;


import com.hibegin.common.dao.DataSourceWrapper;
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
public class TagRepository extends BaseRepository<Tag, Long> {

    private static final Logger LOGGER = LoggerUtil.getLogger(TagRepository.class);
    private final DataSourceWrapper dataSource;

    @Inject
    public TagRepository(DataSourceWrapper dataSource) {
        super(Tag.class, dataSource);
        this.dataSource = dataSource;
    }

    public List<TagDTO> findAll() throws SQLException {
        return queryList("select tagId as id,text,count from tag", TagDTO.class);
    }

    private Set<String> strToSet(String str) {
        // A case variant in the same article still represents one tag.
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String tag : str.split(",")) {
            if (!tag.trim().isEmpty()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    public boolean updateArticleTags(String nowTagStr, String oldTagStr) {
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
        // Preserve the first spelling and merge case variants before the unique-index insert.
        Map<String, Integer> countMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<com.zrlog.data.dto.ArticleBasicDTO> logs =
                new LogRepository(dataSource)
                        .queryList(
                                "select keywords from "
                                        + LogRepository.TABLE_NAME
                                        + " where rubbish=? and privacy=? ",
                                com.zrlog.data.dto.ArticleBasicDTO.class, false,
                                false);
        for (com.zrlog.data.dto.ArticleBasicDTO log : logs) {
            if (StringUtils.isNotEmpty((String) log.getKeywords()) && !((String) log.getKeywords()).trim().isEmpty()) {
                Set<String> tagSet = strToSet(log.getKeywords() + ",");
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
        StringBuilder insertSql = new StringBuilder("insert into " + tableName + " (tagId,text,count,createdAt,updatedAt,version) values ");
        List<Object> params = new ArrayList<>();
        int id = idBase;
        StringJoiner valueJoiner = new StringJoiner(",");
        List<TagDTO> all = new ArrayList<>();
        for (Map.Entry<String, Integer> tag : countMap.entrySet()) {
            id += 1;
            valueJoiner.add("(?,?,?,?,?,0)");
            params.add(id);
            params.add(tag.getKey());
            params.add(tag.getValue());
            String now = new java.sql.Timestamp(System.currentTimeMillis()).toString();
            params.add(now);
            params.add(now);
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
                Tag t = select("WHERE text=? LIMIT 1", add).stream().findFirst().orElse(null);
                if (t == null) {
                    Tag tag = new Tag();
                    tag.setText(add);
                    tag.setCount(1);
                    insert(tag);
                } else {
                    execute("UPDATE tag SET count=count+1,updatedAt=?,version=version+1 WHERE tagId=?",
                            new java.sql.Timestamp(System.currentTimeMillis()).toString(), t.getId());
                }
            } catch (SQLException e) {
                throw new DAOException(e);
            }
        }
    }

    private void deleteTag(Set<String> old) {
        for (String del : old) {
            try {
                Tag t = select("WHERE text=? LIMIT 1", del).stream().findFirst().orElse(null);
                if (t != null) {
                    if (t.getCount() > 1) {
                        execute("UPDATE tag SET count=count-1,updatedAt=?,version=version+1 WHERE tagId=? AND count>1",
                                new java.sql.Timestamp(System.currentTimeMillis()).toString(), t.getId());
                    } else {
                        execute("DELETE FROM tag WHERE tagId=? AND count<=1", t.getId());
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
