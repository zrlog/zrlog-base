package com.zrlog.data.service;

import com.zrlog.model.User;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserPreferenceStoreTest {
    @Test public void sessionPreferencesUseAccountJsonAndValidateStoredValues() throws Exception {
        for (ZrLogTestDatabase.DatabaseType database : ZrLogTestDatabase.DatabaseType.values()) {
            try (ZrLogTestDatabase ignored = ZrLogTestDatabase.open(database)) {
                User user = new User();
                user.execute("insert into user(userId,userName) values(1,'one')");
                user.execute("insert into user(userId,userName) values(2,'two')");
                UserPreferenceStore store = new UserPreferenceStore();
                assertEquals(1440, store.sessionTimeoutMinutes(1, 1440));
                store.mutate(1, root -> root.add("session", com.google.gson.JsonParser.parseString("{\"timeoutMinutes\":60}")));
                assertEquals(60, store.sessionTimeoutMinutes(1, 1440));
                assertEquals(1440, store.sessionTimeoutMinutes(2, 1440));
                for (String invalid : new String[]{"5", "100000", "1.5", "\"60\"", "true", "null", "1e100"}) {
                    user.execute("update user set preferences=? where userId=1", "{\"session\":{\"timeoutMinutes\":" + invalid + "}}");
                    assertEquals(30, store.sessionTimeoutMinutes(1, 30));
                }
                user.execute("update user set preferences=? where userId=1", "{broken");
                assertEquals(30, store.sessionTimeoutMinutes(1, 30));
            }
        }
    }
}
