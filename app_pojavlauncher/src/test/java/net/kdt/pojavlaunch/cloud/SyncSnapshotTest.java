package net.kdt.pojavlaunch.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.JsonParseException;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class SyncSnapshotTest {

    @Test
    public void onlyAllowlistedSettingsAreBackedUp() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("mousespeed", 120);
        prefs.put("force_english", true);
        prefs.put("javaArgs", "-XX:+UseG1GC");
        // Device-specific or sensitive values must never leave the device
        prefs.put("allocation", 2048);
        prefs.put("defaultRuntime", "Internal-21");
        prefs.put("defaultCtrl", "/storage/emulated/0/games/Mayoq/controlmap/default.json");
        prefs.put("currentProfile", "abc");
        prefs.put("msaRefreshToken", "secret-refresh-token");
        prefs.put("accessToken", "secret-access-token");

        SyncSnapshot snapshot = new SyncSnapshot();
        snapshot.putSettings(prefs);

        assertEquals(new HashSet<>(Arrays.asList("mousespeed", "force_english", "javaArgs")), snapshot.settings.keySet());
        String json = snapshot.toJson();
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("allocation"));
    }

    @Test
    public void settingsRoundTripWithTheirTypes() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("mousespeed", 120);
        prefs.put("timeLongPressTrigger", 300L);
        prefs.put("resolutionRatio", 0.75f);
        prefs.put("force_english", true);
        prefs.put("javaArgs", "-Xss2m");

        SyncSnapshot original = new SyncSnapshot();
        original.putSettings(prefs);
        Map<String, Object> restored = SyncSnapshot.fromJson(original.toJson()).restorableSettings();

        assertEquals(prefs, restored);
    }

    @Test
    public void restoreIgnoresSettingsOutsideTheAllowlist() {
        String tampered = "{\"format\":1,\"settings\":{"
                + "\"allocation\":{\"type\":\"int\",\"value\":\"99999\"},"
                + "\"mousespeed\":{\"type\":\"int\",\"value\":\"80\"}}}";
        Map<String, Object> restored = SyncSnapshot.fromJson(tampered).restorableSettings();
        assertEquals(1, restored.size());
        assertEquals(80, restored.get("mousespeed"));
    }

    @Test
    public void controlFileNamesCannotEscapeTheControlsFolder() {
        assertTrue(SyncSnapshot.isValidControlFileName("default.json"));
        assertTrue(SyncSnapshot.isValidControlFileName("My layout (2).json"));
        assertFalse(SyncSnapshot.isValidControlFileName("../accounts/Steve.json"));
        assertFalse(SyncSnapshot.isValidControlFileName("sub/dir.json"));
        assertFalse(SyncSnapshot.isValidControlFileName(".hidden.json"));
        assertFalse(SyncSnapshot.isValidControlFileName("notes.txt"));

        String tampered = "{\"format\":1,\"controls\":{"
                + "\"../../accounts/Steve.json\":\"{}\",\"default.json\":\"{\\\"a\\\":1}\"}}";
        assertEquals(1, SyncSnapshot.fromJson(tampered).restorableControls().size());
    }

    @Test
    public void newerBackupFormatIsRejected() {
        try {
            SyncSnapshot.fromJson("{\"format\":" + (SyncSnapshot.FORMAT + 1) + "}");
            fail("Expected a newer format to be rejected");
        } catch (JsonParseException expected) {
            // a newer launcher wrote this backup
        }
    }
}
