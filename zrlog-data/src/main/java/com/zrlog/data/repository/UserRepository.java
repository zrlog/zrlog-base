package com.zrlog.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.model.User;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;

/** Shared user lookups for author display, identity and preferences. */
public class UserRepository extends BaseRepository<User, Long> {
    @Inject
    public UserRepository(DataSourceWrapper dataSource) {
        super(User.class, dataSource);
    }

    public static UserBasicDTO toBasic(User user) {
        if (user == null) return null;
        UserBasicDTO result = new UserBasicDTO();
        result.setUserId(user.getId());
        result.setUserName(user.getUserName());
        result.setHeader(user.getHeader());
        return result;
    }

    public List<UserBasicDTO> findBasicAll() throws SQLException {
        return queryList("select userName,userId,header from user", UserBasicDTO.class);
    }

    public UserBasicDTO findBasicById(long id) throws SQLException {
        return queryOne(
                "select userName,userId,header from user where userId=?", UserBasicDTO.class, id);
    }
}
