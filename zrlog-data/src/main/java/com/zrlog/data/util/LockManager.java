package com.zrlog.data.util;

import com.zrlog.common.vo.LockVO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.repository.WebSiteRepository;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Lists and administratively clears database-backed locks, independent of their implementation. */
public class LockManager {
    public static final String LOCK_PREFIX = "lock_";
    private final jakarta.inject.Provider<WebSiteRepository> websites;

    @Inject
    public LockManager(jakarta.inject.Provider<WebSiteRepository> websites) {
        this.websites = Objects.requireNonNull(websites);
    }

    public static String storageKey(String name) {
        Objects.requireNonNull(name);
        return name.startsWith(LOCK_PREFIX) ? name : LOCK_PREFIX + name;
    }

    public List<LockVO> getLocks() throws SQLException {
        var rows =
                websites.get()
                        .queryList(
                                "SELECT name,remark FROM website WHERE name LIKE ? ESCAPE '!'",
                                LockVO.class,
                                LOCK_PREFIX.replace("_", "!_") + "%");
        for (var lock : rows) {
            lock.setName(lock.getName().substring(LOCK_PREFIX.length()));
        }
        return rows;
    }

    /** The caller must stop protected work first; clearing a record does not cancel that work. */
    public void releaseLock(String name) {
        try {
            String key = storageKey(name);
            String owner = websites.get().findValueByName(key);
            if (owner != null) websites.get().deleteLock(key, owner);
        } catch (SQLException failure) {
            throw new DAOException(failure);
        }
    }
}
