package net.kdt.pojavlaunch.accounts;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Local identity rules, separate from Minecraft accounts and Android persistence. */
@Keep
public final class LauncherIdentityStore {
    public String currentId;
    public List<LauncherProfile> profiles = new ArrayList<>();
    private static final Pattern GAME_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public void normalize() {
        if (profiles == null) profiles = new ArrayList<>();
        for (int i = profiles.size() - 1; i >= 0; i--) {
            LauncherProfile p = profiles.get(i);
            if (p == null || p.id == null || p.type == null) profiles.remove(i);
        }
    }

    @Nullable public LauncherProfile current() {
        for (LauncherProfile p : profiles) {
            if (p.id.equals(currentId) && available(p)) return p;
        }
        for (LauncherProfile p : profiles) if (available(p)) return p;
        return null;
    }

    @Nullable public LauncherProfile google() {
        LauncherProfile current = current();
        if (current != null && current.type == LauncherProfile.Type.GOOGLE) return current;
        // Legacy stores did not explicitly track the signed-in Google identity.
        for (LauncherProfile p : profiles) {
            if (p.type == LauncherProfile.Type.GOOGLE && !p.googleSignedOut) return p;
        }
        return null;
    }

    private static boolean available(LauncherProfile p) {
        return p.type != LauncherProfile.Type.GOOGLE || !p.googleSignedOut;
    }

    public LauncherProfile signInGoogle(String subject, String email, @Nullable String displayName) {
        if (subject == null || subject.trim().isEmpty() || email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing Google identity");
        }
        LauncherProfile match = null;
        for (LauncherProfile p : profiles) {
            if (p.type == LauncherProfile.Type.GOOGLE && subject.equals(p.googleSubject)) match = p;
        }
        if (match == null) {
            // Upgrade the old email-only profile without replacing its local id or creation time.
            for (LauncherProfile p : profiles) {
                if (p.type == LauncherProfile.Type.GOOGLE && p.googleSubject == null
                        && email.equalsIgnoreCase(p.email)) { match = p; break; }
            }
        }
        if (match == null) {
            match = new LauncherProfile();
            match.id = UUID.randomUUID().toString();
            match.type = LauncherProfile.Type.GOOGLE;
            match.createdAt = System.currentTimeMillis();
            profiles.add(match);
        }
        for (LauncherProfile p : profiles) {
            if (p.type == LauncherProfile.Type.GOOGLE) p.googleSignedOut = true;
        }
        match.googleSubject = subject;
        match.email = email;
        match.displayName = displayName == null || displayName.trim().isEmpty() ? email : displayName.trim();
        if (match.playerUuid == null) {
            // Namespaced Mayoq UUID, stable across devices and renames; never a premium UUID.
            match.playerUuid = UUID.nameUUIDFromBytes(
                    ("Mayoq:Google:" + subject).getBytes(StandardCharsets.UTF_8)).toString();
        }
        match.googleSignedOut = false;
        currentId = match.id;
        return match;
    }

    public void signOutGoogle() {
        for (LauncherProfile p : profiles) {
            if (p.type == LauncherProfile.Type.GOOGLE) p.googleSignedOut = true;
        }
        LauncherProfile next = current();
        currentId = next == null ? null : next.id;
    }

    public static boolean isValidMinecraftNickname(@Nullable String nickname) {
        return nickname != null && GAME_NAME.matcher(nickname).matches();
    }

    public void setMinecraftNickname(String id, String nickname) {
        if (!isValidMinecraftNickname(nickname)) throw new IllegalArgumentException("Invalid Minecraft nickname");
        LauncherProfile target = null;
        for (LauncherProfile p : profiles) {
            if (p.id.equals(id)) target = p;
            else if (nickname.equalsIgnoreCase(p.minecraftNickname)) {
                throw new IllegalArgumentException("Nickname already used on this device");
            }
        }
        if (target == null || target.type != LauncherProfile.Type.GOOGLE
                || target.googleSignedOut || target.playerUuid == null) {
            throw new IllegalStateException("Sign in with Google before choosing a nickname");
        }
        target.minecraftNickname = nickname;
    }
}
