---
name: build-apk
description: Build the signed release APK of shiroikuma-tenki (白い熊 天気 — our fork of Breezy Weather) with the buildFork Gradle task, then deliver it automatically via the global /after-build skill (adb push if the phone is reachable, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone, and after any functional code change.
---

# Build the 白い熊 天気 release APK and deliver it

> **ALWAYS build, then ALWAYS deliver — no asking (白い熊's standing authorization, 2026-07-09).**
> After ANY functional change, build **immediately** and deliver. Do not stop at a compile-check, do
> not offer to build, do not ask how to transfer it. Build-and-deliver does **not** commit or push —
> a commit/push still waits for 白い熊's explicit "Push". (Skip the build only for non-functional
> edits — docs, comments.)

## Build environment (this machine)

The default `java` is **JDK 11**, which cannot run Gradle 9.x. Always export JDK 21:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

The Android SDK path comes from the gitignored `local.properties`
(`sdk.dir=/home/shiroikuma/android-sdk`) — recreate it if a build fails with **`SDK location not
found`** (a background shell does not inherit `ANDROID_HOME`).

## Steps

1. **Note the output filename / version.**
   - `grep -nE 'versionCode = |versionName = ' app/build.gradle.kts | head -2` — upstream's base
     (e.g. `60201` / `6.2.1`); these track upstream and are **never hand-edited**.
   - `grep -E '^BUILD_NUMBER' gradle.properties` — the `N` used for THIS build (the task bumps it
     afterwards).
   - APK will be `shiroikuma-tenki_<versionName>+<NNN>_arm64-v8a.apk` (`N` zero-padded to three
     digits in the name), e.g. `shiroikuma-tenki_6.2.1+001_arm64-v8a.apk`.
   - versionCode for this build = `<upstream code> * 10000 + N` (plain, unpadded), e.g. `602010001`.

2. **Build** (signed `basic`/"standard" release) — from the repo root:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork --console=plain < /dev/null
   ```
   - `buildFork` runs `assembleBasicRelease` (R8 minify + resource shrink, signed from
     `keystore.properties`), copies the **arm64-v8a** split to `~/tmp/<apk name>`, and increments
     `BUILD_NUMBER` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>` in cyan — use those to confirm the exact
     filename/code; confirm `BUILD SUCCESSFUL`.
   - **Never pass `-Pbreezy`.** That flag switches the build to upstream's brand (icon, name, links),
     which upstream's licence forbids redistributing in a modified APK. Our build must always take
     `app/src/res_fork/` + the `app.*` properties.
   - A cold build (fresh checkout / after `git clean`) downloads the Gradle distro and the whole
     dependency set and takes many minutes — run it with `run_in_background` if it may exceed the
     foreground timeout. Warm builds are much faster.
   - **Fast iteration:** `./gradlew :app:assembleBasicDebug` gives a debug-signed APK under
     `app/build/outputs/apk/basic/debug/` (no R8; installs as `shiroikuma.tenki.debug`, side-by-side
     with the release). The shippable build is always `buildFork`.

3. **Deliver via the global `/after-build` skill** — no exceptions, no asking. It runs `/adb-check`
   UNSANDBOXED, `adb push`es **this repo's** newest `~/tmp/shiroikuma-tenki_*.apk` to `/sdcard/tmp/`
   if the phone is reachable, otherwise `scp`s it to `skhw:~/tmp/`, then announces what landed.
   `~/tmp/` is shared with parallel chats building sister apps — always pick the
   `shiroikuma-tenki_*` APK, never merely the newest file there.

4. **Never delete or prune older APKs** — not in `~/tmp/`, not in `/sdcard/tmp/`. Every build carries
   a unique `+NNN`; older builds stay where they are so 白い熊 can roll back.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads `keystore.properties` (gitignored,
at the repo root) which points at `~/.android-keystores/shiroikuma-tenki.jks`, alias `tenki`
(PKCS12/RSA-4096, 10000-day validity, created 2026-08-09). The password is recorded in
`~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org` and the key is backed up to that directory's
`android-keystores/`. If `keystore.properties` is missing, the release build comes out **unsigned**
and will not install — restore it rather than working around it.

## Versioning (how the numbers are formed)

- Upstream's own `versionCode` / `versionName` in `app/build.gradle.kts` `defaultConfig` are the
  base; a rebase brings the new values in automatically. **Never hand-edit them.**
- `BUILD_NUMBER` in `gradle.properties` is **our** increment, bumped on every `buildFork` and reset
  to `1` by `/upstream-new-version` on each new upstream base.
- `versionName = "<upstream name>+<NNN>"` (zero-padded to three digits so `+002` sorts before `+010`);
  `versionCode = <upstream code> * 10000 + N` (plain integer). When upstream's code climbs
  (`60201` → `60202`), the new line's codes all exceed the previous line's, keeping upgrades monotonic.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
