package com.zrlog.data.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.zrlog.data.repository.UserRepository;
import com.zrlog.model.User;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Test;

public class UserPreferenceStoreTest {
    @Test
    public void sessionPreferencesUseAccountJsonAndValidateStoredValues() throws Exception {
        for (ZrLogTestDatabase.DatabaseType database : ZrLogTestDatabase.DatabaseType.values()) {
            try (ZrLogTestDatabase ignored = ZrLogTestDatabase.open(database)) {
                UserRepository users = new UserRepository(ignored.dataSource());
                User one = new User();
                one.setUserName("one");
                users.insert(one);
                User two = new User();
                two.setUserName("two");
                users.insert(two);
                int firstId = Math.toIntExact(one.getId());
                int secondId = Math.toIntExact(two.getId());
                UserPreferenceStore store =
                        new UserPreferenceStore(() -> new UserRepository(ignored.dataSource()));
                assertNull(store.raw(999));
                assertNull(store.raw(firstId));
                assertEquals(1440, store.sessionTimeoutMinutes(firstId, 1440));
                store.mutate(
                        firstId,
                        root ->
                                root.add(
                                        "session",
                                        com.google.gson.JsonParser.parseString(
                                                "{\"timeoutMinutes\":60}")));
                assertEquals(60, store.sessionTimeoutMinutes(firstId, 1440));
                assertEquals(1440, store.sessionTimeoutMinutes(secondId, 1440));
                for (String invalid :
                        new String[] {"5", "100000", "1.5", "\"60\"", "true", "null", "1e100"}) {
                    User current = users.loadById(one.getId());
                    current.setPreferences("{\"session\":{\"timeoutMinutes\":" + invalid + "}}");
                    users.update(current);
                    assertEquals(30, store.sessionTimeoutMinutes(firstId, 30));
                }
                User current = users.loadById(one.getId());
                current.setPreferences("{broken");
                users.update(current);
                assertEquals(30, store.sessionTimeoutMinutes(firstId, 30));
            }
        }
    }
}
