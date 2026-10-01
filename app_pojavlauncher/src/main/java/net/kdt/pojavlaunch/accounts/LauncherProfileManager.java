package net.kdt.pojavlaunch.accounts;

import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.JsonParseException;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.IOException;
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
    private static final String TAG = "LauncherProfileManager";
    private static final String FILE_NAME = "launcher_identity.json";
    /** Display name only, so any letters (including Uzbek ones), digits, spaces and _ . - are fine */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_ .-]{2,24}$");

    @Keep
    private static class Store {
        String currentId;
        List<LauncherProfile> profiles = new ArrayList<>();
    }

    private static Store sStore;

    private LauncherProfileManager() {}

    public static boolean isValidNickname(@Nullable String nickname) {
        return nickname != null && !nickname.trim().isEmpty()
                && NICKNAME_PATTERN.matcher(nickname.trim()).matches();
    }

    public static synchronized boolean hasProfile() {
        return !store().profiles.isEmpty();
    }

    @Nullable
    public static synchronized LauncherProfile getCurrent() {
        Store store = store();
        for (LauncherProfile profile : store.profiles) {
            if (profile.id.equals(store.currentId)) return profile;
        }
        return store.profiles.isEmpty() ? null : store.profiles.get(0);
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
        Store store = store();
        store.profiles.add(guest);
        store.currentId = guest.id;
        save();
        return guest;
    }

    /** Creates or updates the Google launcher profile for this email and selects it */
    public static synchronized LauncherProfile saveGoogle(String email, @Nullable String displayName) {
        Store store = store();
        LauncherProfile google = null;
        for (LauncherProfile profile : store.profiles) {
            if (profile.type == LauncherProfile.Type.GOOGLE && email.equalsIgnoreCase(profile.email)) {
                google = profile;
                break;
            }
        }
        if (google == null) {
            google = new LauncherProfile();
            google.id = UUID.randomUUID().toString();
            google.type = LauncherProfile.Type.GOOGLE;
            google.email = email;
            google.createdAt = System.currentTimeMillis();
            store.profiles.add(google);
        }
        google.displayName = displayName == null || displayName.trim().isEmpty() ? email : displayName.trim();
        store.currentId = google.id;
        save();
        return google;
    }

    /** @return the signed-in Google launcher profile, if any */
    @Nullable
    public static synchronized LauncherProfile getGoogleProfile() {
        for (LauncherProfile profile : store().profiles) {
            if (profile.type == LauncherProfile.Type.GOOGLE) return profile;
        }
        return null;
    }

    /** Adds a guest profile restored from a backup, unless one with that name already exists */
    public static synchronized void addGuestIfMissing(@Nullable String nickname) {
        if (!isValidNickname(nickname)) return;
        Store store = store();
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
        Store store = store();
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

    private static File file() {
        return new File(Tools.DIR_DATA, FILE_NAME);
    }

    private static Store store() {
        if (sStore == null) sStore = load();
        return sStore;
    }

    private static Store load() {
        File file = file();
        if (!file.isFile()) return new Store();
        try {
            Store store = Tools.GLOBAL_GSON.fromJson(Tools.read(file.getAbsolutePath()), Store.class);
            if (store == null) return new Store();
            if (store.profiles == null) store.profiles = new ArrayList<>();
            // Drop entries a newer or broken file might contain that this version cannot use
            for (int i = store.profiles.size() - 1; i >= 0; i--) {
                LauncherProfile profile = store.profiles.get(i);
                if (profile == null || profile.id == null || profile.type == null) store.profiles.remove(i);
            }
            return store;
        } catch (IOException | JsonParseException e) {
            Log.e(TAG, "Failed to read launcher profiles, starting fresh", e);
            return new Store();
        }
    }

    private static void save() {
        try {
            Tools.write(file().getAbsolutePath(), Tools.GLOBAL_GSON.toJson(sStore));
        } catch (IOException e) {
            Log.e(TAG, "Failed to save launcher profiles", e);
        }
    }
}
