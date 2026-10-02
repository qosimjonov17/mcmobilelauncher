# Authentication architecture

This document describes how accounts work in Mayoq Launcher (inherited from
Amethyst/PojavLauncher). It distinguishes the launcher identity from the
Minecraft account used to start the game.

## Two separate concepts

| Concept | Purpose | Types | Grants game access? |
|---|---|---|---|
| **Minecraft account** (`value/MinecraftAccount`) | Identity passed to the game (`auth_player_name`, `auth_access_token`, `auth_uuid`) | Microsoft (licensed), Demo (Microsoft without the game), Local/offline | Microsoft-licensed only (Demo: demo mode) |
| **Mayoq identity** (`accounts/LauncherProfile`) | Launcher user and their locally chosen Minecraft nickname | Guest, Google | **Never in this milestone** |

Mayoq identities are stored separately and are never shown in the Minecraft
account spinner or counted by `GameEntitlement`. The current milestone does
not pass them to the game, so existing Microsoft and local account behavior is
unchanged.

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

## Mayoq identities

- `accounts/LauncherProfile` — `GUEST` or `GOOGLE`, display name, optional
  email, creation time, plus Google identities' stable subject, game nickname
  and stable Mayoq player UUID.
- `accounts/LauncherIdentityStore` — validates and migrates stored identities.
  A Google identity's UUID is deterministically derived from its Google
  subject, so it survives a nickname change, sign-out and re-sign-in. It is a
  Mayoq/offline UUID, not a Minecraft premium UUID.
- `accounts/LauncherProfileManager` stores all identities and the current one
  in `<app data>/launcher_identity.json`, using atomic writes. It does not
  overwrite an unreadable existing store.
- `fragments/GuestProfileFragment` creates or renames a guest profile.
- `fragments/CloudSyncFragment` lets signed-in Google users select a local
  Minecraft nickname (`A-Z`, `a-z`, `0-9`, `_`, 3–16 characters) and shows
  the unofficial-account limitation.
- Entry points: the account selection screen (shown on first start) and
  Settings → Launcher profile.

## Google Sign-In and cloud sync

Google sign-in is a launcher profile, not a Minecraft account. The code lives in `cloud/` and
never references `MinecraftAccount` or the Microsoft login.

| Step | Class | Notes |
|---|---|---|
| Sign in | `cloud/GoogleAuth.signIn` | Credential Manager "Sign in with Google" with the Web client ID in `@string/google_web_client_id`. Reads the provider subject only to create a local Mayoq identity; the ID token is never retained. |
| Drive access | `cloud/GoogleAuth.authorizeDriveAppData` | Separate authorization for `drive.appdata` only, scoped to the selected Google email. Shows the consent screen the first time or after the user revokes access. |
| Backup / restore | `fragments/CloudSyncFragment`, `cloud/DriveAppDataClient` | Drive v3 REST on one file, `mayoq_launcher_backup.json`, in the hidden app data folder |
| What is synced | `cloud/SyncSnapshot`, `cloud/CloudSync` | Allowlisted settings, control layouts (`controlmap/*.json`), launcher profile names |

Never synced: the `accounts/` folder, the selected Minecraft account, stable Mayoq UUIDs and
nicknames, Microsoft or Minecraft tokens, passwords, Google tokens, RAM allocation, Java runtime
choice and absolute file paths. This keeps local player identity and game-session data off Drive.
`SyncSnapshot` uses an allowlist, and the same allowlist and file name checks are applied again
when restoring, so a tampered backup cannot write other settings or files outside `controlmap/`.

Error handling:
- Offline: checked before every request, with a message instead of a hang.
- Expired token (HTTP 401/403): the token is cleared and requested again once.
- Revoked access: the retry returns a consent screen, so the user can grant access again.
- Missing Google Play services or no Google account on the device: a clear message.
- Unreadable or newer-format backup: rejected without changing local settings.

Google Cloud setup: OAuth clients exist for the debug package `uz.mayoq.launcher.debug` (debug
keystore SHA-1 `17:D6:F8:A1:A3:8E:B2:EF:B7:B2:C7:A7:75:99:9C:F4:0D:46:84:10`). Release builds
need their own Android OAuth client with the release key's SHA-1.

Tests: `SyncSnapshotTest` checks the allowlist, typed round trips, tampered backups and file name
validation. `LauncherIdentityStoreTest` covers migration, UUID stability, nickname validation and
sign-out/re-sign-in; `GoogleIdentityClaimsTest` covers safe local parsing of provider metadata.
CI runs them with `testDebugUnitTest`.
