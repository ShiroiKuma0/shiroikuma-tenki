# Installing 白い熊 天気

Go to the [releases page](https://github.com/ShiroiKuma0/shiroikuma-tenki/releases) and download the
newest `shiroikuma-tenki_<version>_arm64-v8a.apk`. Install it and you're done.

The app installs **side-by-side** with any other weather app, including upstream Breezy Weather — it
has its own application id (`shiroikuma.tenki`) and its own signing key.

## Flavor

Only one flavor is built here: upstream's `basic` (which upstream's own releases call "standard"). It
is fully open source and carries all ~50 weather sources. Upstream's `freenet` flavor — restricted to
free-network sources — is not built for this fork.

## Architecture

Only the `arm64-v8a` build is published, which is what every phone in use here runs. The build itself
still supports `armeabi-v7a`, `x86` and `x86_64`; run
`./gradlew :app:assembleBasicRelease` to get every split plus a universal APK under
`app/build/outputs/apk/basic/release/`.

## Updates

The app can check GitHub for new releases (Settings › About, or the update notification after adding
your first location). It looks at this fork's own releases page, matching assets whose name starts
with `shiroikuma-tenki`.

## Building it yourself

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork
```

Requires JDK 21 and an Android SDK (`local.properties` with `sdk.dir=…`). Release signing reads a
gitignored `keystore.properties`; without it the release build is unsigned and will not install.
