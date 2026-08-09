---
name: upstream-new-version
description: Sync the shiroikuma-tenki fork onto a new upstream release tag of breezy-weather/breezy-weather — advance main to the new tag, rebase custom, reset BUILD_NUMBER, build the new +001. Use when 白い熊 says a new upstream version is out, asks to check/update/sync to upstream, or to rebase custom onto the latest Breezy Weather release. ALWAYS present the proceed-gated upstream-changes table BEFORE rebasing.
---

# Sync shiroikuma-tenki onto a new upstream Breezy Weather release

This fork tracks [breezy-weather/breezy-weather](https://github.com/breezy-weather/breezy-weather) —
a feature-rich FOSS weather app with 50+ sources. `main` mirrors the newest upstream **release tag**;
`custom` carries our patches and is rebased onto it.

**We follow release TAGS, not the branch tip** (白い熊, 2026-08-09). Breezy tags every release
(`v6.2.1`, `v6.2.0`, …) and bumps both `versionCode` and `versionName` with it, while `upstream/main`
keeps moving daily and already declares the *next* version. Basing on tags means every base is a
state upstream itself called finished, and the version literal really moves on each sync. So a sync
happens when a **new tag** appears — not just because commits landed on `upstream/main`.

> **Never `git push` or `git commit` unprompted.** After the rebase + build you stop and let 白い熊
> test; you push only on their explicit **"Push"** (`custom` needs `--force-with-lease` after a
> rebase; `main` is reset to a tag, so it needs it too).

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `main` | Mirrors the newest upstream release tag. No fork work here. | `git checkout -B main <newtag>` |
| `custom` | Our patches; the working/dev branch and the GitHub default branch. | rebased onto `main` each sync |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-tenki` (push). `upstream` =
`https://github.com/breezy-weather/breezy-weather` (fetch only; push URL `DISABLED`).

## Steps

1. **Check for a newer upstream release:**
   ```bash
   git fetch upstream --tags
   git tag --sort=-version:refname | head        # newest Breezy tag, e.g. v6.2.2
   git describe --tags --exact-match main        # the tag our base sits on
   git show <newtag>:app/build.gradle.kts | grep -E 'versionCode|versionName' | head -2
   ```
   If nothing is newer than our base, stop and report "already current".

2. **⛔ PROCEED GATE — present the upstream changes as a table, then STOP.** 白い熊's standing
   requirement: **before** anything is rebased, show what the new upstream release actually brings.

   Gather the material from all of these — they complement each other:
   ```bash
   gh release view <newtag> -R breezy-weather/breezy-weather   # upstream writes real release notes
   git show <newtag>:CHANGELOG.md | head -80                   # per-version changelog
   git log --oneline --no-merges <oldtag>..<newtag>            # what really landed
   git log --merges --format='%s' <oldtag>..<newtag>           # which PRs were merged
   git diff --stat <oldtag>..<newtag>                          # where the weight is
   ls fastlane/metadata/android/en-US/changelogs/              # per-versionCode store notes
   ```
   Weblate translation commits are the bulk of Breezy's traffic — fold them into **one** row, never
   list them individually. Renovate dependency bumps likewise get one row.

   Present a **descriptive markdown table** — one row per feature/change, in plain language, not raw
   commit subjects:

   | Area | Change | What it means for us |
   | --- | --- | --- |
   | Sources | … | … |
   | UI | … | … |
   | Widgets / notifications | … | … |
   | Build / deps | … | … |

   Cover features, fixes, new/removed weather sources, and anything touching files our patches own
   — **flag those rows**, they are the likely conflict sites:
   `app/build.gradle.kts`, `gradle.properties`, `.gitignore`, `app/src/main/AndroidManifest.xml`,
   `app/src/res_fork/**`, and whatever de-branding we hold in `values*/strings.xml` and the root docs.
   If several upstream versions are being jumped at once, cover each.

   Also state the stack size (`git rev-list --count <oldtag>..custom`) and the plan.

   **Then stop and wait for 白い熊's explicit go-ahead.** Do not move `main`, do not rebase, do not
   build until they say proceed. If they decline, nothing has been touched.

3. **Advance `main` to the new release tag** (mirror; no fork work lives here):
   ```bash
   git checkout -B main <newtag>
   git push --force-with-lease origin main
   ```

4. **Rebase `custom`:**
   ```bash
   git checkout custom
   git rebase main
   ```
   Resolve conflicts so **all** our customizations survive (table below). Reconcile, don't drop: if
   upstream restructured a file we patch, port our change to the new structure rather than forcing
   the old diff. Keep **upstream's** `versionCode` / `versionName` literals — our fork block
   multiplies and appends to them, so they are never edited by hand. **If conflicts are significant,
   stop and plan with 白い熊** before continuing.

5. **Reset the build tail:** in `gradle.properties`, set **`BUILD_NUMBER=1`**. This happens on
   **every** sync, so `+N` always reads as "our Nth build on this upstream base".

6. **Verify our customizations are intact after the rebase:**

   | What | Expected | Where |
   | --- | --- | --- |
   | Installed app id | `shiroikuma.tenki` | `app/build.gradle.kts` → `defaultConfig.applicationId` |
   | Code namespace | `org.breezyweather` (unchanged from upstream) | `app/build.gradle.kts` → `namespace` |
   | Brand / app label | `白い熊 天気` | `brand_name` in `app/src/res_fork/values/strings.xml` |
   | Launcher label | `MainActivity android:label="@string/brand_name"` | `app/src/main/AndroidManifest.xml` |
   | Fork version block | `forkBuildNumber` / `paddedBuildNumber` / `forkVersionName` / `forkVersionCode` | `app/build.gradle.kts` |
   | Signing | `keystorePropertiesFile` + `signingConfigs { release }` + release `signingConfig` | `app/build.gradle.kts` |
   | Archive name + task | `archivesName = "shiroikuma-tenki_…"` + the `buildFork` task | `app/build.gradle.kts` (end of file) |
   | Build tail | `BUILD_NUMBER=1` | `gradle.properties` |
   | Fork links | the `app.*` block filled with our repo (never the `breezy.*` ones) | `gradle.properties` |
   | Black-yellow icon | yellow line-art foreground + black background, all densities | `app/src/res_fork/**` |
   | De-branding | our name + our GitHub links in every user-visible string and root doc | `values*/strings.xml`, `README.md`, `HELP.md`, `INSTALL.md`, `PRIVACY.md`, About/Help screens |
   | Committed agent files | `CLAUDE.md`, `.claude/skills/` tracked; only `.claude/settings.local.json` ignored | `.gitignore` |

   Watch for **new upstream strings that reintroduce "Breezy Weather"** — every release adds some.
   Grep after the rebase and re-de-brand:
   ```bash
   grep -rn "Breezy" app/src/main/res/values/strings.xml app/src/main/kotlin | grep -v breezyweather | head -40
   ```
   Sanity check the script still evaluates:
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:tasks --console=plain | head`.

7. **Build the new `+001`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork < /dev/null`), then deliver it
   via the global **`/after-build`** skill (no transfer prompt). This is the first build of the new
   upstream line (`<newVersion>+001`).

8. **Stop.** Let 白い熊 test. Commit/push only on their explicit **"Push"**.

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history)
  over merging, so the customization set stays easy to audit and replay.
- **Never pass `-Pbreezy`.** It flips the build to upstream's brand assets (`app/src/res_breezy/`,
  the `breezy.*` links, `config/` for AboutLibraries), which upstream's licence forbids redistributing
  in a modified APK.
- If upstream adds a new `app.*` fork property to `gradle.properties`, fill it with our own link —
  an empty mandatory property makes the build log an error and can disable sources.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
