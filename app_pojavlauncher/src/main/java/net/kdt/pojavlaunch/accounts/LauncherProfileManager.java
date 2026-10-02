package net.kdt.pojavlaunch.accounts;

import android.util.AtomicFile;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores launcher profiles in {@code <app data>/launcher_identity.json}, apart from the Minecraft
 * account files in {@link Tools#DIR_ACCOUNT_NEW}, so a launcher profile can never be mistaken for
 * a Minecraft account.
 */
public final class LauncherProfileManager {
    private static final String FILE_NAME = "launcher_identity.json";
    /** Display name only, so any letters (including Uzbek ones), digits, spaces and _ . - are fine */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_ .-]{2,24}$");

    private static LauncherIdentityStore sStore;
    private static boolean sReadFailed;

    private LauncherProfileManager() {}

    public static boolean isValidNickname(@Nullable String nickname) {
        return nickname != null && !nickname.trim().isEmpty()
                && NICKNAME_PATTERN.matcher(nickname.trim()).matches();
    }

    public static synchronized boolean hasProfile() {
        return store().current() != null;
    }

    @Nullable
    public static synchronized LauncherProfile getCurrent() {
        return store().current();
    }

    public static synchronized List<LauncherProfile> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(store().profiles));
    }

    /** Renames the current guest profile, or creates and selects a new one if the current profile is not a guest. */
    public static synchronized LauncherProfile saveGuest(String nickname) {
        if (!isValidNickname(nickname)) throw new IllegalArgumentException("Invalid nickname");
        LauncherProfile current = getCurrent();
        if (current != null && current.type == LauncherProfile.Type.GUEST) {
            current.displayName = nickname.trim();
            save();
            return current;
        }
        LauncherProfile guest = new LauncherProfile();
        guest.id = UUID.randomUUID().toString();
        guest.type = LauncherProfile.Type.GUEST;
        guest.displayName = nickname.trim();
        guest.createdAt = System.currentTimeMillis();
        LauncherIdentityStore store = store();
        store.profiles.add(guest);
        store.currentId = guest.id;
        save();
        return guest;
    }

    /** Creates or migrates a Google identity without touching existing Minecraft accounts. */
    public static synchronized LauncherProfile saveGoogle(String subject, String email, @Nullable String displayName) {
        LauncherProfile google = store().signInGoogle(subject, email, displayName);
        save();
        return google;
    }

    @Nullable
    public static synchronized LauncherProfile getGoogleProfile() {
        return store().google();
    }

    public static synchronized void signOutGoogle() {
        store().signOutGoogle();
        save();
    }

    public static synchronized void saveMinecraftNickname(String id, String nickname) {
        store().setMinecraftNickname(id, nickname);
        save();
    }

    /** Adds a guest profile restored from a backup, unless one with that name already exists */
    public static synchronized void addGuestIfMissing(@Nullable String nickname) {
        if (!isValidNickname(nickname)) return;
        LauncherIdentityStore store = store();
        for (LauncherProfile profile : store.profiles) {
            if (profile.type == LauncherProfile.Type.GUEST && profile.displayName.equals(nickname.trim())) return;
        }
        LauncherProfile guest = new LauncherProfile();
        guest.id = UUID.randomUUID().toString();
        guest.type = LauncherProfile.Type.GUEST;
        guest.displayName = nickname.trim();
        guest.createdAt = System.currentTimeMillis();
        store.profiles.add(guest);
        if (store.currentId == null) store.currentId = guest.id;
        save();
    }

    public static synchronized void select(String id) {
        store().currentId = id;
        save();
    }

    public static synchronized void remove(String id) {
        LauncherIdentityStore store = store();
        for (int i = 0; i < store.profiles.size(); i++) {
            if (store.profiles.get(i).id.equals(id)) {
                store.profiles.remove(i);
                break;
            }
        }
        if (id.equals(store.currentId)) {
            store.currentId = store.profiles.isEmpty() ? null : store.profiles.get(0).id;
        }
        save();
    }

    private static AtomicFile file() {
        return new AtomicFile(new File(Tools.DIR_DATA, FILE_NAME));
    }

    private static LauncherIdentityStore store() {
        if (sStore == null) sStore = load();
        return sStore;
    }

    private static LauncherIdentityStore load() {
        AtomicFile file = file();
        sReadFailed = false;
        // AtomicFile recovers its .bak on open after an interrupted write.
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) {
            return new LauncherIdentityStore();
        }
        try {
            LauncherIdentityStore store = Tools.GLOBAL_GSON.fromJson(
                    new String(file.readFully(), StandardCharsets.UTF_8), LauncherIdentityStore.class);
            if (store == null) throw new JsonParseException("Empty identity store");
            store.normalize();
            return store;
        } catch (IOException | JsonParseException e) {
            // Keep the launcher usable, but refuse writes over unreadable identity data.
            sReadFailed = true;
            Log.e("LauncherProfileManager", "Cannot read launcher identities; preserving the file");
            return new LauncherIdentityStore();
        }
    }

    private static void save() {
        if (sReadFailed) {
            sStore = null;
            throw new IllegalStateException("Existing launcher identities could not be read");
        }
        AtomicFile file = file();
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(Tools.GLOBAL_GSON.toJson(sStore).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException e) {
            file.failWrite(output);
            sStore = null; // Reload the last committed store; never report an unsaved UUID as saved.
            throw new IllegalStateException("Unable to save launcher identities", e);
        }
    }
}
