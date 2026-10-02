# Mayoq Launcher: project status

**Status: active development** (2026-10-02). Work is being done on an isolated development branch;
the `latest` public release remains unchanged.

Downloading and launching Minecraft: Java Edition without an account that owns the game is not
implemented. Any change to download or launch eligibility requires a separate compatibility and
licensing review before implementation. See `docs/auth-architecture.md`.

## Base

Fork of [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) (LGPLv3), branch
`v3_openjdk`, with its full history. Upstream can be merged with
`git fetch upstream v3_openjdk && git merge upstream/v3_openjdk`.

## Implemented

| Area | What was done | Verified |
|---|---|---|
| Branding | Name "Mayoq Launcher", package `uz.mayoq.launcher` (debug: `uz.mayoq.launcher.debug`), new icons, game folder `games/Mayoq` on Android 9 and older | CI build; icon previewed |
| Uzbek language | Full Uzbek (Latin) translation, `values-uz` | XML and format specifiers checked by script |
| CI / distribution | GitHub Actions builds `mayoq-arm64.apk`, `mayoq-armv7.apk`, `mayoq-x86_64.apk`; public release publishing requires an explicit manual opt-in on the default branch | Green CI on `b1c9c3b` and `d26082a`; publish job skipped |
| Bundled Java | Java 8, 17 and 21 built into each per-ABI APK (`scripts/bundle_jre.py`) from the stable openjdk-build releases; no runtime download needed | Tested on LDPlayer (x86_64) |
| Download reliability | 30 s timeout and up to 5 retries with backoff for game files | Tested on LDPlayer |
| LWJGL fix | Natives re-extracted when missing after a reinstall (fixed `NoClassDefFoundError: org.lwjgl.glfw.GLFW`) | Game reached the world on LDPlayer |
| Launcher profiles | Guest profile (nickname), separate from Minecraft accounts; `GameEntitlement` centralises ownership checks | CI build |
| Google Sign-In | Credential Manager with the Web client ID; creates a local Mayoq identity with a stable UUID and user-selected local nickname | Focused JVM tests; physical Android test passed on 2026-10-02 |
| Cloud sync | Manual backup/restore of allowlisted settings, control layouts and profile names to the Google Drive app data folder | Unit tests and physical Android backup/restore test passed on 2026-10-02 |

## Milestone 2 physical-device validation

The ARM64 debug APK from GitHub Actions was tested on a physical Android device on 2026-10-02.
All planned Mayoq onboarding checks passed:

- Google Sign-In completed and displayed the selected account's name and email.
- A selected Minecraft nickname persisted across a full app restart and after Google sign-out and
  re-sign-in to the same account.
- Switching Google accounts worked, with each locally stored Mayoq identity retaining its own
  nickname.
- Google Drive manual backup and restore completed successfully.

Mayoq identities remained launcher-only throughout these tests. Existing Microsoft accounts and
their game-session behavior were preserved; Mayoq identities were not added to the Minecraft
account selector.

## Unfinished

- Cloud sync is manual only (no automatic backup).
- Release signing: APKs are signed with the public upstream debug keystore. A private release key
  and a matching Android OAuth client (release package `uz.mayoq.launcher`) were never set up.
- Telegram link: the main menu still has Amethyst's Wiki and Discord buttons.
- Mayoq game-session integration is not started: Mayoq identities are not passed to Minecraft and
  do not change download or launch eligibility yet.
- Server list, low-end device preset: proposed, not started.

## Known issues

- **Local accounts share the UUID `00000000-0000-0000-0000-000000000000`**, so two offline
  nicknames on one device collide in world player data and on LAN. Inherited from upstream.
- The Google OAuth consent screen must list testers while it is in "Testing" mode, or sign-in is
  blocked.
- `debug.keystore` is public (from upstream), so anyone can sign an APK as this app's debug build.
- Java 25 (needed by Minecraft 26.x) is not bundled and is still downloaded from GitHub at
  runtime, which timed out on the tester's network for Java 21 before bundling.
- Microsoft rate-limited the tester's account after many logins; this is Microsoft-side and
  clears on its own.
- On Android 9 and older, uninstalling the old "MC Mobile" build leaves `games/Amethyst` on
  shared storage.
- APKs are large (155–166 MB) because Java runtimes are bundled.
- A HTTP 403 from Google Drive (which can also mean rate limiting) is treated as an auth error
  and retried once before showing "access not granted".
