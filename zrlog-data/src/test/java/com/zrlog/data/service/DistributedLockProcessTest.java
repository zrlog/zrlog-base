package com.zrlog.data.service;

import com.hibegin.common.dao.InMemoryDatabase;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class DistributedLockProcessTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 30000)
    public void separateJvmCannotAcquireOrReleaseAnotherProcessesLock() throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.SQLITE);
             DistributedLock lock = DaggerDataComponent.factory().create(() -> db.dataSource(), () -> true)
                     .locks().create("process-test")) {
            assertTrue(lock.tryLock());
            Object owner = db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "process-test");
            runChild(db, false);
            assertEquals(owner, db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "process-test"));
            lock.unlock();
            runChild(db, true);
            assertNull(db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.LOCK_PREFIX + "process-test"));
            assertTrue(lock.tryLock());
        }
    }

    private void runChild(ZrLogTestDatabase db, boolean shouldAcquire) throws Exception {
        Path output = temporary.newFile().toPath();
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Contender.class.getName(), db.properties().getProperty("jdbcUrl"), Boolean.toString(shouldAcquire))
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue("Lock contender did not finish", child.waitFor(10, TimeUnit.SECONDS));
            assertEquals(Files.readString(output), 0, child.exitValue());
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    public static class Contender {
        public static void main(String[] args) throws Exception {
            Properties properties = new Properties();
            properties.setProperty("driverClass", "org.sqlite.JDBC");
            properties.setProperty("jdbcUrl", args[0]);
            try (InMemoryDatabase db = InMemoryDatabase.open(properties, false);
                 DistributedLock lock = DaggerDataComponent.factory().create(db::dataSource, () -> true)
                         .locks().create("process-test")) {
                assertEquals(Boolean.parseBoolean(args[1]), lock.tryLock());
                lock.unlock();
            }
        }
    }
}
