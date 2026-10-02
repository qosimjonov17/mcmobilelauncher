package net.kdt.pojavlaunch.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.accounts.LauncherProfile;
import net.kdt.pojavlaunch.accounts.LauncherProfileManager;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Collects and restores the data covered by cloud sync: allowlisted launcher settings, control
 * layouts and launcher profile names. It never reads the Minecraft accounts folder or the account
 * selection, so Microsoft tokens cannot end up in a backup.
 */
public final class CloudSync {
    private static final String TAG = "CloudSync";
    private static final String STATE_PREFS = "mayoq_cloud_sync";
    private static final String KEY_LAST_BACKUP = "last_backup";
    private static final String KEY_LAST_RESTORE = "last_restore";

    private CloudSync() {}

    public static SyncSnapshot collect() {
        SyncSnapshot snapshot = new SyncSnapshot();
        snapshot.createdAt = System.currentTimeMillis();
        snapshot.device = Build.MANUFACTURER + " " + Build.MODEL;
        snapshot.putSettings(LauncherPreferences.DEFAULT_PREF.getAll());

        File[] controlFiles = new File(Tools.CTRLMAP_PATH).listFiles();
        if (controlFiles != null) {
            for (File file : controlFiles) {
                if (!file.isFile() || file.length() > SyncSnapshot.MAX_CONTROL_FILE_BYTES) continue;
                if (!SyncSnapshot.isValidControlFileName(file.getName())) continue;
                try {
                    snapshot.putControl(file.getName(), Tools.read(file));
                } catch (IOException e) {
                    Log.w(TAG, "Skipping unreadable control file " + file.getName(), e);
                }
            }
        }

        for (LauncherProfile profile : LauncherProfileManager.getAll()) {
            if (profile.type == LauncherProfile.Type.GOOGLE && profile.googleSignedOut) continue;
            SyncSnapshot.ProfileInfo info = new SyncSnapshot.ProfileInfo();
            info.type = profile.type.name();
            info.displayName = profile.displayName;
            snapshot.profiles.add(info);
        }
        return snapshot;
    }

    /** Writes a downloaded backup over the local launcher settings and control layouts */
    public static void apply(Context context, SyncSnapshot snapshot) throws IOException {
        SharedPreferences.Editor editor = LauncherPreferences.DEFAULT_PREF.edit();
        for (Map.Entry<String, Object> entry : snapshot.restorableSettings().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> set = (Set<String>) value;
                editor.putStringSet(entry.getKey(), set);
            }
        }
        editor.commit();

        for (Map.Entry<String, String> control : snapshot.restorableControls().entrySet()) {
            Tools.write(new File(Tools.CTRLMAP_PATH, control.getKey()).getAbsolutePath(), control.getValue());
        }

        if (snapshot.profiles != null) {
            for (SyncSnapshot.ProfileInfo info : snapshot.profiles) {
                if (info != null && LauncherProfile.Type.GUEST.name().equals(info.type)) {
                    LauncherProfileManager.addGuestIfMissing(info.displayName);
                }
            }
        }

        LauncherPreferences.loadPreferences(context);
        state(context).edit().putLong(KEY_LAST_RESTORE, System.currentTimeMillis()).apply();
    }

    public static void markBackedUp(Context context) {
        state(context).edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply();
    }

    public static long getLastBackup(Context context) {
        return state(context).getLong(KEY_LAST_BACKUP, 0);
    }

    public static long getLastRestore(Context context) {
        return state(context).getLong(KEY_LAST_RESTORE, 0);
    }

    public static void clearState(Context context) {
        state(context).edit().clear().apply();
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }
}
