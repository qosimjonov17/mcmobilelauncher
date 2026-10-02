package net.kdt.pojavlaunch.accounts;

import com.google.gson.Gson;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class LauncherIdentityStoreTest {
    private static final Gson GSON = new Gson();

    private LauncherIdentityStore reload(LauncherIdentityStore store) {
        LauncherIdentityStore result = GSON.fromJson(GSON.toJson(store), LauncherIdentityStore.class);
        result.normalize();
        return result;
    }

    @Test public void migratesLegacyGoogleWithoutReplacingGuestOrLocalId() {
        LauncherIdentityStore store = GSON.fromJson("{\"currentId\":\"old-google\",\"profiles\":["
                + "{\"id\":\"guest\",\"type\":\"GUEST\",\"displayName\":\"Old Guest\",\"createdAt\":10},"
                + "{\"id\":\"old-google\",\"type\":\"GOOGLE\",\"email\":\"A@example.com\",\"createdAt\":20}]} "
                , LauncherIdentityStore.class);
        store.normalize();
        assertNotNull(store.google());
        LauncherProfile google = store.signInGoogle("subject-a", "a@example.com", "Alice");
        assertEquals("old-google", google.id);
        assertEquals(20, google.createdAt);
        assertEquals(2, store.profiles.size());
        assertEquals("Old Guest", store.profiles.get(0).displayName);
        assertNull(store.profiles.get(0).playerUuid);
        assertNull(google.minecraftNickname);
    }

    @Test public void uuidIsStableAcrossRestartRenameEmailChangeAndSignOut() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile p = store.signInGoogle("subject-a", "a@example.com", "Alice");
        String id = p.id, uuid = p.playerUuid;
        assertNotEquals(new UUID(0, 0).toString(), uuid);
        UUID.fromString(uuid);
        store.setMinecraftNickname(id, "Player_One");
        store = reload(store);
        store.setMinecraftNickname(id, "Player_Two");
        store.signOutGoogle();
        store = reload(store);
        assertNull(store.google());
        assertNull(store.current());
        p = store.signInGoogle("subject-a", "renamed@example.com", "New display name");
        assertEquals(id, p.id);
        assertEquals(uuid, p.playerUuid);
        assertEquals("Player_Two", p.minecraftNickname);
        assertEquals("renamed@example.com", p.email);
    }

    @Test public void sameGoogleSubjectOnAnotherDeviceGetsSameUuid() {
        LauncherProfile a = new LauncherIdentityStore().signInGoogle("subject-a", "a@example.com", null);
        LauncherProfile b = new LauncherIdentityStore().signInGoogle("subject-a", "a@example.com", null);
        assertNotEquals(a.id, b.id);
        assertEquals(a.playerUuid, b.playerUuid);
    }

    @Test public void differentGoogleSubjectsDoNotMergeEvenIfEmailMatches() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile a = store.signInGoogle("subject-a", "a@example.com", null);
        LauncherProfile b = store.signInGoogle("subject-b", "a@example.com", null);
        assertNotEquals(a.playerUuid, b.playerUuid);
        assertTrue(a.googleSignedOut);
        assertSame(b, store.google());
        assertSame(b, store.current());
        assertEquals(2, store.profiles.size());
    }

    @Test public void signOutRetainsIdentityAndReturnsToGuest() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile guest = new LauncherProfile();
        guest.id = "guest"; guest.type = LauncherProfile.Type.GUEST; guest.displayName = "Guest";
        store.profiles.add(guest);
        store.signInGoogle("subject-a", "a@example.com", null);
        store.signInGoogle("subject-b", "b@example.com", null);
        store.signOutGoogle();
        assertNull(store.google());
        assertSame(guest, store.current());
        assertEquals(3, store.profiles.size());
    }

    @Test public void validatesMinecraftNamesWithoutChangingGuestDisplayNameRules() {
        for (String name : new String[]{"abc", "Player_123", "1234567890123456"}) {
            assertTrue(name, LauncherIdentityStore.isValidMinecraftNickname(name));
        }
        for (String name : new String[]{"", "ab", "12345678901234567", "has space", "has.dot", "a/b", "Demo.Player", " abc", "abc\n", "Oʻzbek"}) {
            assertFalse(name, LauncherIdentityStore.isValidMinecraftNickname(name));
        }
        assertFalse(LauncherIdentityStore.isValidMinecraftNickname(null));
    }

    @Test public void rejectsDuplicateNicknameIncludingSignedOutIdentity() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile a = store.signInGoogle("a", "a@example.com", null);
        store.setMinecraftNickname(a.id, "Player");
        LauncherProfile b = store.signInGoogle("b", "b@example.com", null);
        assertThrows(IllegalArgumentException.class, () -> store.setMinecraftNickname(b.id, "player"));
        assertNull(b.minecraftNickname);
        assertEquals("Player", a.minecraftNickname);
    }

    @Test public void invalidNicknameDoesNotReplaceExistingIdentity() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile p = store.signInGoogle("a", "a@example.com", null);
        store.setMinecraftNickname(p.id, "Valid");
        String before = GSON.toJson(store);
        assertThrows(IllegalArgumentException.class, () -> store.setMinecraftNickname(p.id, "../bad"));
        assertEquals(before, GSON.toJson(store));
    }

    @Test public void signedOutIdentityCannotBeRenamedUntilSignIn() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile p = store.signInGoogle("a", "a@example.com", null);
        store.signOutGoogle();
        assertThrows(IllegalStateException.class, () -> store.setMinecraftNickname(p.id, "Player"));
    }

    @Test public void existingAssignedUuidIsNeverRegenerated() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile p = store.signInGoogle("a", "a@example.com", null);
        String assigned = UUID.randomUUID().toString();
        p.playerUuid = assigned;
        store = reload(store);
        assertEquals(assigned, store.signInGoogle("a", "a@example.com", null).playerUuid);
    }

    @Test public void invalidGoogleIdentityDoesNotSignOutExistingUser() {
        LauncherIdentityStore store = new LauncherIdentityStore();
        LauncherProfile p = store.signInGoogle("a", "a@example.com", null);
        assertThrows(IllegalArgumentException.class, () -> store.signInGoogle(null, "a@example.com", null));
        assertSame(p, store.google());
    }
}
