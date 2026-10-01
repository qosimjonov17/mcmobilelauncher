package net.kdt.pojavlaunch.accounts;

import android.content.Context;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.value.MinecraftAccount;

/**
 * Answers whether this device may download and run Minecraft: Java Edition.
 *
 * Only a Microsoft account whose ownership was verified by
 * {@link net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin} grants this.
 * Launcher profiles ({@link LauncherProfile}, guest or Google) identify the person using the
 * launcher and are deliberately not consulted here.
 */
public final class GameEntitlement {
    private GameEntitlement() {}

    /** @return whether the account holds a verified Minecraft session (not local, not demo) */
    public static boolean isLicensed(@Nullable MinecraftAccount account) {
        return account != null && !account.isLocal() && !account.isDemo();
    }

    /** @return whether any saved Minecraft account on this device owns the game */
    public static boolean hasLicensedAccount() {
        for (MinecraftAccount account : PojavProfile.getAllProfiles()) {
            if (isLicensed(account)) return true;
        }
        return false;
    }

    /** @return whether the selected Minecraft account is a demo account */
    public static boolean isDemoSelected(Context context) {
        MinecraftAccount current = PojavProfile.getCurrentProfileContent(context, null);
        return current != null && current.isDemo();
    }

    /** @return whether the selected Minecraft account is missing or local */
    public static boolean isLocalSelected(Context context) {
        MinecraftAccount current = PojavProfile.getCurrentProfileContent(context, null);
        return current == null || current.isLocal();
    }
}
