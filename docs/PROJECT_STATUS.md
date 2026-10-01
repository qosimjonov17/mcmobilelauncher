# Mayoq Launcher: project status

**Status: paused** (2026-10-01). Development stopped at the owner's request. No features are in
progress. The branch `claude/gifted-feynman-rcuxcm` and the `latest` release are kept as they are.

The product requirement that ended development: downloading and launching Minecraft: Java Edition
without an account that owns the game. That is not implemented and will not be, because it means
distributing a paid game without a license. See `docs/auth-architecture.md`.

## Base

Fork of [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) (LGPLv3), branch
`v3_openjdk`, with its full history. Upstream can be merged with
`git fetch upstream v3_openjdk && git merge upstream/v3_openjdk`.

## Implemented

| Area | What was done | Verified |
|---|---|---|
| Branding | Name "Mayoq Launcher", package `uz.mayoq.launcher` (debug: `uz.mayoq.launcher.debug`), new icons, game folder `games/Mayoq` on Android 9 and older | CI build; icon previewed |
| Uzbek language | Full Uzbek (Latin) translation, `values-uz` | XML and format specifiers checked by script |
| CI / distribution | GitHub Actions builds `mayoq-arm64.apk`, `mayoq-armv7.apk`, `mayoq-x86_64.apk` and publishes them to the `latest` release | Green builds |
| Bundled Java | Java 8, 17 and 21 built into each per-ABI APK (`scripts/bundle_jre.py`) from the stable openjdk-build releases; no runtime download needed | Tested on LDPlayer (x86_64) |
| Download reliability | 30 s timeout and up to 5 retries with backoff for game files | Tested on LDPlayer |
| LWJGL fix | Natives re-extracted when missing after a reinstall (fixed `NoClassDefFoundError: org.lwjgl.glfw.GLFW`) | Game reached the world on LDPlayer |
| Launcher profiles | Guest profile (nickname), separate from Minecraft accounts; `GameEntitlement` centralises ownership checks | CI build |
| Google Sign-In | Credential Manager with the Web client ID; creates a Google launcher profile | CI build only, **not tested on a device** |
| Cloud sync | Manual backup/restore of allowlisted settings, control layouts and profile names to the Google Drive app data folder | Unit tests (`SyncSnapshotTest`, 5 tests) in CI; **not tested on a device** |

## Unfinished

- Google Sign-In and cloud sync have never been run on a real device or emulator.
- Cloud sync is manual only (no automatic backup).
- Release signing: APKs are signed with the public upstream debug keystore. A private release key
  and a matching Android OAuth client (release package `uz.mayoq.launcher`) were never set up.
- Telegram link: the main menu still has Amethyst's Wiki and Discord buttons.
- Mayoq account redesign (account screen, "Playing as" bar, nickname registration) was proposed
  but not started.
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
