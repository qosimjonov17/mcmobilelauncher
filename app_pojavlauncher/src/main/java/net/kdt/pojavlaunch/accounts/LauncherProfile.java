package net.kdt.pojavlaunch.accounts;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

/**
 * Identifies the person using the launcher (for greetings and, later, cloud sync of launcher
 * settings). It is not a Minecraft account: it is never passed to the game and never grants
 * access to it. See {@link GameEntitlement} for game ownership.
 */
@Keep
public class LauncherProfile {
    public enum Type { GUEST, GOOGLE }

    public String id;
    public Type type;
    public String displayName;
    /** Google account email, null for guests */
    @Nullable public String email;
    /** Provider subject, obtained only from Credential Manager. Never a Minecraft credential. */
    @Nullable public String googleSubject;
    /** Local Minecraft nickname; independent from the Google display name. */
    @Nullable public String minecraftNickname;
    @Nullable public String playerUuid;
    /** Keep identity metadata on sign-out so signing back in preserves the nickname. */
    public boolean googleSignedOut;
    public long createdAt;
}
