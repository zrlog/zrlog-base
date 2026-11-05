package com.zrlog.business.version;

import static org.junit.Assert.*;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.business.service.DbUpgradeService;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.exception.DatabaseInitializationException;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.plugin.IPlugin;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupLoader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SchemaMigrationStopTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 30000)
    public void applicationStopReleasesOnlyItsOwnMigrationLockBeforeClosingDatabase()
            throws Exception {
        // stop() also closes the process-wide HTTP client; isolate the real lifecycle.
        Path output = temporary.newFile("migration-stop.log").toPath();
        Process child =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-cp",
                                System.getProperty(
                                        "surefire.test.class.path",
                                        System.getProperty("java.class.path")),
                                "org.junit.runner.JUnitCore",
                                Scenarios.class.getName())
                        .redirectErrorStream(true)
                        .redirectOutput(output.toFile())
                        .start();
        try {
            assertTrue("Migration stop did not finish", child.waitFor(25, TimeUnit.SECONDS));
            assertEquals(Files.readString(output), 0, child.exitValue());
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    public static class Scenarios {
        @Rule public TemporaryFolder temporary = new TemporaryFolder();

        private static DataSourceWrapper borrowedSource(
                ZrLogTestDatabase db, AtomicBoolean closed) {
            return (DataSourceWrapper)
                    Proxy.newProxyInstance(
                            DataSourceWrapper.class.getClassLoader(),
                            new Class<?>[] {DataSourceWrapper.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("close")) {
                                    closed.set(true);
                                    return null;
                                }
                                try {
                                    return method.invoke(db.dataSource(), args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
        }

        @Test(timeout = 10000)
        public void stopWaitsForInFlightDdlThenReleasesLockAndAllowsResume() throws Exception {
            System.setProperty("sws.root.path", temporary.getRoot().getAbsolutePath());
            try (ZrLogTestDatabase db =
                    ZrLogTestDatabase.openWebApi("/schema/v30-install-e8d8c9d.sql")) {
                db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
                CountDownLatch executing = new CountDownLatch(1);
                CountDownLatch finishStatement = new CountDownLatch(1);
                AtomicBoolean paused = new AtomicBoolean();
                AtomicBoolean closed = new AtomicBoolean();
                db.beforeWebApiUpdate(
                        (sql, params) -> {
                            if (sql.startsWith("ALTER TABLE")
                                    && paused.compareAndSet(false, true)) {
                                executing.countDown();
                                try {
                                    if (!finishStatement.await(5, TimeUnit.SECONDS))
                                        throw new SQLException("Test DDL timed out");
                                } catch (InterruptedException e) {
                                    throw new SQLException(e);
                                }
                            }
                        });
                StopConfig config = new StopConfig();
                ExecutorService workers = Executors.newFixedThreadPool(2);
                try {
                    Future<?> startup =
                            workers.submit(() -> config.start(borrowedSource(db, closed)));
                    assertTrue(executing.await(5, TimeUnit.SECONDS));
                    Object owner =
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY);
                    assertNotNull(owner);
                    Future<?> stop = workers.submit(config::stop);
                    assertTrue(config.stopping.await(2, TimeUnit.SECONDS));
                    assertThrows(
                            TimeoutException.class, () -> stop.get(100, TimeUnit.MILLISECONDS));
                    assertFalse(closed.get());
                    assertEquals(
                            owner,
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                    finishStatement.countDown();
                    stop.get(3, TimeUnit.SECONDS);
                    ExecutionException failure =
                            assertThrows(
                                    ExecutionException.class,
                                    () -> startup.get(3, TimeUnit.SECONDS));
                    assertTrue(failure.getCause() instanceof DatabaseInitializationException);
                    assertTrue(closed.get());
                    assertNull(
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                    assertEquals(
                            "30",
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    CacheService.ZRLOG_SQL_VERSION_KEY));
                    assertNull(
                            db.scalar(
                                    "SELECT value FROM website WHERE name='base_data_init_cache_v3'"));
                    new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
                    assertEquals(
                            String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    CacheService.ZRLOG_SQL_VERSION_KEY));
                    assertNull(
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                } finally {
                    finishStatement.countDown();
                    workers.shutdownNow();
                    workers.awaitTermination(5, TimeUnit.SECONDS);
                    config.stop();
                }
            }
        }

        @Test(timeout = 10000)
        public void stoppingAWaitingInstanceLeavesOtherInstancesLockIntact() throws Exception {
            System.setProperty("sws.root.path", temporary.getRoot().getAbsolutePath());
            try (ZrLogTestDatabase db =
                            ZrLogTestDatabase.openWebApi("/schema/v30-install-e8d8c9d.sql");
                    DistributedLock owner =
                            DaggerDataComponent.factory()
                                    .create(() -> db.dataSource(), () -> true)
                                    .lockManager()
                                    .acquire(
                                            DistributedLock.SCHEMA_UPGRADE_KEY,
                                            30,
                                            TimeUnit.SECONDS,
                                            () -> false)) {
                db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
                Object token =
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY);
                CountDownLatch waiting = new CountDownLatch(1);
                db.afterWebApiQuery(
                        (sql, params) -> {
                            if (params.length == 1
                                    && DistributedLock.SCHEMA_UPGRADE_KEY.equals(params[0]))
                                waiting.countDown();
                        });
                StopConfig config = new StopConfig();
                AtomicBoolean closed = new AtomicBoolean();
                ExecutorService workers = Executors.newFixedThreadPool(2);
                try {
                    Future<?> startup =
                            workers.submit(() -> config.start(borrowedSource(db, closed)));
                    assertTrue(waiting.await(3, TimeUnit.SECONDS));
                    workers.submit(config::stop).get(3, TimeUnit.SECONDS);
                    assertThrows(ExecutionException.class, () -> startup.get(3, TimeUnit.SECONDS));
                    assertTrue(closed.get());
                    assertEquals(
                            token,
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                    assertEquals(
                            "30",
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    CacheService.ZRLOG_SQL_VERSION_KEY));
                } finally {
                    workers.shutdownNow();
                    workers.awaitTermination(5, TimeUnit.SECONDS);
                    config.stop();
                }
            }
        }

        @Test(timeout = 10000)
        public void stopRetriesAnEarlierFailedLockReleaseBeforeClosingDatabase() throws Exception {
            System.setProperty("sws.root.path", temporary.getRoot().getAbsolutePath());
            try (ZrLogTestDatabase db =
                    ZrLogTestDatabase.openWebApi("/schema/v30-install-e8d8c9d.sql")) {
                db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
                AtomicBoolean failRelease = new AtomicBoolean(true);
                AtomicBoolean closed = new AtomicBoolean();
                db.beforeWebApiUpdate(
                        (sql, params) -> {
                            if (sql.startsWith("DELETE FROM website")
                                    && failRelease.compareAndSet(true, false)) {
                                throw new SQLException(
                                        "Temporary failure while releasing schema lock");
                            }
                        });
                StopConfig config = new StopConfig();
                try {
                    assertThrows(
                            DatabaseInitializationException.class,
                            () -> config.start(borrowedSource(db, closed)));
                    assertNotNull(
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                    assertFalse(closed.get());
                    config.stop();
                    assertTrue(closed.get());
                    assertNull(
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                    assertEquals(
                            String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    CacheService.ZRLOG_SQL_VERSION_KEY));
                } finally {
                    config.stop();
                }
            }
        }

        private static final class StopConfig extends ZrLogConfig {
            private final CountDownLatch stopping = new CountDownLatch(1);

            private StopConfig() {
                super(0, null, "");
            }

            private void start(DataSourceWrapper source) {
                dataSource = source;
                webSetups.addAll(
                        WebSetupLoader.load(
                                new WebSetupContext(
                                        this, dbPropertiesFile, installLockFile, "", null)));
                initializeWebSetups();
            }

            @Override
            protected void beforeStop() {
                stopping.countDown();
            }

            @Override
            public boolean isInstalled() {
                return dataSource != null;
            }

            @Override
            protected TokenService initTokenService() {
                return null;
            }

            @Override
            public List<IPlugin> getBasePluginList() {
                return Collections.emptyList();
            }
        }
    }
}
