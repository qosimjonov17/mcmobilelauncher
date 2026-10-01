# Authentication architecture

This document describes how accounts work in Mayoq Launcher (inherited from
Amethyst/PojavLauncher) and how launcher profiles were added on top without
changing how Minecraft ownership is verified.

## Two separate concepts

| Concept | Purpose | Types | Grants game access? |
|---|---|---|---|
| **Minecraft account** (`value/MinecraftAccount`) | Identity passed to the game (`auth_player_name`, `auth_access_token`, `auth_uuid`) | Microsoft (licensed), Demo (Microsoft without the game), Local/offline | Microsoft-licensed only (Demo: demo mode) |
| **Launcher profile** (`accounts/LauncherProfile`) | Who is using the launcher UI; later cloud sync of launcher settings | Guest (nickname), Google (planned) | **Never** |

Launcher profiles are stored separately and are never passed to the game,
never shown in the Minecraft account spinner, and never counted by
`GameEntitlement`.

## Where Minecraft accounts live

- One JSON file per account: `<app data>/accounts/<username>.json`
  (`Tools.DIR_ACCOUNT_NEW`), serialised from `MinecraftAccount` with Gson.
  Microsoft accounts hold the MSA refresh token, Minecraft access token,
  profile UUID and XUID.
- The selected account name: SharedPreferences `pojav_profile` → `file`
  (`PojavProfile`).
- Account types are inferred, not stored:
  - `isDemo()` — username starts with `Demo.` (Microsoft account without the game)
  - `isLocal()` — access token is `"0"` and not demo (offline account)
  - licensed — neither of the above (set by `MicrosoftBackgroundLogin`)
- `MicrosoftBackgroundLogin` performs MSA → Xbox Live → XSTS → Minecraft
  services login and the entitlement check (`/entitlements/mcstore`). A
  Microsoft account that does not own the game becomes `Demo.Player`.
- `mcAccountSpinner` loads the files, refreshes expired Microsoft sessions in
  the background and handles new logins.

## Where Microsoft ownership is enforced

All checks go through `accounts/GameEntitlement` (previously
`Tools.hasOnlineProfile()` / `Tools.hasNoOnlineProfileDialog()`):

| Location | What it gates | Why it stays gated |
|---|---|---|
| `SelectAuthFragment` / `LocalLoginFragment` | Creating an offline account | Offline play is only offered on devices that already hold a licensed account |
| `MinecraftDownloader.start` | Downloading game files with a local account or offline | Game files come from Mojang and require ownership |
| `LauncherActivity` launch listener | Launching without an account; demo version limits | Same as above |
| `ProfileTypeSelectFragment.tryInstall` | Fabric/Forge/NeoForge/Quilt/OptiFine/BTA/LWJGL3ify installers | Installers download or patch the Minecraft client |
| `ModpackCreateFragment.tryInstall` | Modpack installation | Modpacks download the game and mod loaders |
| `MainMenuFragment` "Execute a .jar" | Running installer jars | Same as mod loader installers |
| `Tools.getMinecraftClientArgs` | `--demo` flag for demo accounts | Demo mode must stay demo |

Not gated (available to everyone, including guests): settings, Java runtime
manager, renderer/controls configuration, custom control editor, creating and
editing vanilla profiles, browsing installed versions, logs, and (since the
launcher profile change) opening the game folder to manage mods, resource
packs and worlds. Opening the folder is still blocked for demo accounts so
demo-only files are not mixed with the full game.

## Launcher profiles (Profile Manager)

- `accounts/LauncherProfile` — `GUEST` or `GOOGLE`, display name, optional
  email, creation time.
- `accounts/LauncherProfileManager` — stores all launcher profiles and the
  current one in `<app data>/launcher_identity.json`.
- `fragments/GuestProfileFragment` — create or rename a guest profile.
- Entry points: the account selection screen (shown on first start) and
  Settings → Launcher profile.

## Planned: Google Sign-In and cloud sync

Google Sign-In will create a `GOOGLE` launcher profile and back up launcher
data (settings, control layouts, launcher profiles list) to the user's Google
Drive app data folder. It needs a Google Cloud project with an OAuth client for
the app's package name and signing certificate. Minecraft account files and
tokens are never uploaded, and a Google profile never grants game access.
