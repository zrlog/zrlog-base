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

    private final DataSourceWrapper dataSource;

    @Inject
    public UserRepository(DataSourceWrapper dataSource) {
        super(User.class, dataSource);
        this.dataSource = dataSource;
    }

    public List<UserBasicDTO> findBasicAll() throws SQLException {
        return doConvertList(
                new UserRepository(dataSource).queryList("userName", "userId", "header"), UserBasicDTO.class);
    }

    public Map<String, Object> getUserByUserName(String userName) throws SQLException {
        return queryFirstWithParams("select * from " + tableName + " where userName=?", userName);
    }

    public String getPasswordByUserId(int userId) throws SQLException {
        return (String) queryFirstWithParams("select password from " + tableName + " where userId=?", userId).get("password");
    }

    public boolean updatePassword(int userId, String password) throws SQLException {
        return execute("update " + tableName + " set password=?,updatedAt=?,version=version+1 where userId=?", password, new java.sql.Timestamp(System.currentTimeMillis()).toString(), userId);
    }

    public void updateEmailUserNameHeaderByUserId(String email, String userName, String header, int userId) throws SQLException {
        execute("update " + tableName + " set header=?,email=?,userName=?,updatedAt=?,version=version+1 where userId=?", header, email, userName, new java.sql.Timestamp(System.currentTimeMillis()).toString(), userId);
    }
}
