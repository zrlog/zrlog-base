package com.zrlog.business.version;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

/** A bootstrap lock shared by JDBC and WebAPI; uses only the historical website columns. */
public final class SchemaUpgradeLock implements AutoCloseable {
    public static final String KEY = "schema_upgrade_lock";
    private final DAO dao;
    private final String owner = UUID.randomUUID().toString();

    private SchemaUpgradeLock(DataSourceWrapper source) {
        dao = new DAO(source);
    }

    public static SchemaUpgradeLock acquire(DataSourceWrapper source) throws SQLException {
        SchemaUpgradeLock lock = new SchemaUpgradeLock(source);
        try {
            lock.dao.execute("INSERT INTO website(name,value,remark) VALUES(?,?,?)", KEY, lock.owner,
                    "Schema migration started at " + new Timestamp(System.currentTimeMillis()));
        } catch (SQLException failure) {
            throw new SQLException("Cannot acquire schema migration lock. Another instance may be upgrading. "
                    + "After an abrupt termination, confirm all previous instances have stopped before removing "
                    + "website row '" + KEY + "' and restarting.", failure);
        }
        return lock;
    }

    @Override
    public void close() throws SQLException {
        // No expiry/takeover: a paused old process must never resume DDL after another instance took over.
        dao.execute("DELETE FROM website WHERE name=? AND value=?", KEY, owner);
    }
}
