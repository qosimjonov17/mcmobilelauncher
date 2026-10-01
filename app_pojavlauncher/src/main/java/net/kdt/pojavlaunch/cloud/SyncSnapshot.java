package net.kdt.pojavlaunch.cloud;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * What gets backed up to Google Drive: launcher settings, control layouts and launcher profile
 * names. Plain Java (no Android types) so the filtering rules can be unit-tested.
 *
 * Only keys in {@link #SYNCED_SETTINGS} are ever copied, so Minecraft account data, Microsoft
 * tokens and anything added to preferences later cannot leak into the backup by accident.
 */
@Keep
public class SyncSnapshot {
    public static final int FORMAT = 1;

    /**
     * Launcher settings that are safe and meaningful on another device. Deliberately excluded:
     * "allocation" (RAM depends on the device), "defaultRuntime" (installed runtimes differ),
     * "defaultCtrl" (an absolute file path), "currentProfile" (a local game profile id) and
     * "sodium_override" (an unsupported-mod opt-in that must be confirmed on each device).
     */
    public static final Set<String> SYNCED_SETTINGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "alternate_surface", "always_grab_mouse", "arc_capes", "bigCoreAffinity", "buttonAllCaps",
            "buttonscale", "checkLibraries", "disable_autojre_select", "disableDoubleTap", "disableGestures", "downloadSource",
            "dump_shaders", "enableGyro", "forceEnableTouchController", "force_english", "force_vsync",
            "gamepadPassthruForced", "gamepad_deadzone_scale", "gyroInvertX", "gyroInvertY",
            "gyroSampleRate", "gyroSensitivity", "gyroSmoothing", "ignoreNotch", "javaArgs",
            "keyboardPanning", "mg_renderer_computeShaderext", "mg_renderer_dsaExt",
            "mg_renderer_setting_angle", "mg_renderer_setting_angleDepthClearFixMode", "mg_renderer_setting_errorSetting",
            "mg_renderer_setting_glsl_cache_size", "mg_renderer_setting_timerQueryExt", "mouse_start",
            "mousescale", "mousespeed", "resolutionRatio", "sustainedPerformance",
            "timeLongPressTrigger", "touchControllerVibrateLength", "verifyManifest", "vsync_in_zink",
            "zinkPreferSystemDriver")));

    /** Control layout file names: a plain .json name, no folders, so a tampered backup cannot write elsewhere */
    private static final Pattern CONTROL_FILE_NAME = Pattern.compile("^[\\w\\- .()]{1,100}\\.json$");
    public static final int MAX_CONTROL_FILE_BYTES = 512 * 1024;
    public static final int MAX_CONTROL_FILES = 50;

    /** A preference value with its type, so it can be written back with the right setter */
    @Keep
    public static class Setting {
        public String type; // boolean, int, long, float, string, string_set
        public String value;
        public List<String> values;
    }

    /** Launcher profile metadata only: no email, no ids, no tokens */
    @Keep
    public static class ProfileInfo {
        public String type;
        public String displayName;
    }

    public int format = FORMAT;
    public long createdAt;
    public String device;
    public Map<String, Setting> settings = new TreeMap<>();
    public Map<String, String> controls = new TreeMap<>();
    public List<ProfileInfo> profiles = new ArrayList<>();

    private static final Gson GSON = new Gson();

    public static boolean isSyncedSetting(String key) {
        return SYNCED_SETTINGS.contains(key);
    }

    public static boolean isValidControlFileName(@Nullable String name) {
        return name != null && CONTROL_FILE_NAME.matcher(name).matches() && !name.startsWith(".");
    }

    /** Copies the allowlisted settings from a SharedPreferences.getAll() map */
    public void putSettings(Map<String, ?> all) {
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (!isSyncedSetting(entry.getKey())) continue;
            Setting setting = toSetting(entry.getValue());
            if (setting != null) settings.put(entry.getKey(), setting);
        }
    }

    public void putControl(String fileName, String content) {
        if (!isValidControlFileName(fileName)) return;
        if (content.length() > MAX_CONTROL_FILE_BYTES || controls.size() >= MAX_CONTROL_FILES) return;
        controls.put(fileName, content);
    }

    /** @return the settings that may be restored, typed values ready for SharedPreferences */
    public Map<String, Object> restorableSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (settings == null) return result;
        for (Map.Entry<String, Setting> entry : settings.entrySet()) {
            if (!isSyncedSetting(entry.getKey())) continue;
            Object value = fromSetting(entry.getValue());
            if (value != null) result.put(entry.getKey(), value);
        }
        return result;
    }

    /** @return the control files that may be restored (validated names and sizes) */
    public Map<String, String> restorableControls() {
        Map<String, String> result = new LinkedHashMap<>();
        if (controls == null) return result;
        for (Map.Entry<String, String> entry : controls.entrySet()) {
            if (result.size() >= MAX_CONTROL_FILES) break;
            String content = entry.getValue();
            if (isValidControlFileName(entry.getKey()) && content != null
                    && content.length() <= MAX_CONTROL_FILE_BYTES) {
                result.put(entry.getKey(), content);
            }
        }
        return result;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /** @throws JsonParseException when the backup is unreadable or from a newer, unknown format */
    public static SyncSnapshot fromJson(String json) {
        SyncSnapshot snapshot = GSON.fromJson(json, SyncSnapshot.class);
        if (snapshot == null) throw new JsonParseException("Empty backup");
        if (snapshot.format > FORMAT) throw new JsonParseException("Backup format " + snapshot.format + " is newer than this launcher");
        return snapshot;
    }

    @Nullable
    private static Setting toSetting(Object value) {
        Setting setting = new Setting();
        if (value instanceof Boolean) setting.type = "boolean";
        else if (value instanceof Integer) setting.type = "int";
        else if (value instanceof Long) setting.type = "long";
        else if (value instanceof Float) setting.type = "float";
        else if (value instanceof String) setting.type = "string";
        else if (value instanceof Set) {
            setting.type = "string_set";
            setting.values = new ArrayList<>();
            for (Object item : (Set<?>) value) setting.values.add(String.valueOf(item));
            Collections.sort(setting.values);
            return setting;
        } else return null;
        setting.value = String.valueOf(value);
        return setting;
    }

    @Nullable
    private static Object fromSetting(@Nullable Setting setting) {
        if (setting == null || setting.type == null) return null;
        try {
            switch (setting.type) {
                case "boolean": return Boolean.parseBoolean(setting.value);
                case "int": return Integer.parseInt(setting.value);
                case "long": return Long.parseLong(setting.value);
                case "float": return Float.parseFloat(setting.value);
                case "string": return setting.value;
                case "string_set": return setting.values == null ? null : new HashSet<>(setting.values);
                default: return null;
            }
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
