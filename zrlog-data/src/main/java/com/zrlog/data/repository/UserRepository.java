package com.zrlog.data.repository;

import com.zrlog.model.User;


import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.UserBasicDTO;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 存放用于管理员的相关信息，对应数据的 user 表
 */
public class UserRepository extends BaseRepository<User, Long> {


    @Inject
    public UserRepository(DataSourceWrapper dataSource) {
        super(User.class, dataSource);
    }

    public List<UserBasicDTO> findBasicAll() throws SQLException {
        return queryList("select userName,userId,header from user", UserBasicDTO.class);
    }

    public User getUserByUserName(String userName) throws SQLException {
        return findMatching(Map.of("userName", userName), 1).stream().findFirst().orElse(null);
    }

    public UserBasicDTO findBasicById(long id) throws SQLException {
        return queryOne("select userName,userId,header from user where userId=?", UserBasicDTO.class, id);
    }

    public com.zrlog.data.security.AccountAccess ownerAccess() throws SQLException {
        return com.zrlog.data.security.AccountAccess.fromSnapshot(queryOne(
                "SELECT userId,role,enabled,authVersion FROM user WHERE role=? AND enabled=? ORDER BY userId LIMIT 1",
                com.zrlog.data.security.AccountAccess.Snapshot.class, "owner", true));
    }

    public static UserBasicDTO toBasic(User user) {
        if (user == null) return null;
        UserBasicDTO result = new UserBasicDTO();
        result.setUserId(user.getId());
        result.setUserName(user.getUserName());
        result.setHeader(user.getHeader());
        return result;
    }

    public String getPasswordByUserId(int userId) throws SQLException {
        return (String) queryFirstObj("select password from " + tableName + " where userId=?", userId);
    }

    public boolean updatePassword(int userId, String password) throws SQLException {
        User user = loadById((long) userId);
        if (user == null) return false;
        user.setPassword(password);
        return update(user);
    }

    public void updateEmailUserNameHeaderByUserId(String email, String userName, String header, int userId) throws SQLException {
        User user = loadById((long) userId);
        if (user == null) return;
        user.setHeader(header);
        user.setEmail(email);
        user.setUserName(userName);
        update(user);
    }
}
