# Suzy Snooze

Android alarm / snooze app built with **Kotlin**, **ViewBinding**, and **Material Design 3**.

Runs as a foreground service to manage alarms and snooze timers reliably, even when the device is in Doze mode.

---

## Prerequisites

| Tool | Min version |
|------|-------------|
| Android Studio / Gradle | 8.x |
| JDK | 17 |
| Android SDK | `compileSdk 34` |
| Kotlin | 1.9+ |

---

## Build

```bash
./build.sh [BUILD_TYPE] [OPTIONS]
```

### Build types (pick exactly one)

| Flag | Description |
|------|-------------|
| `d` *(default)* | Debug APK |
| `a` | Release APK |
| `p` | Production AAB — **signed**, ready for Play Store upload |

### Options (combinable)

| Flag | Description |
|------|-------------|
| `c` | Clean build (`gradlew clean` first) |
| `m` | Minified / R8 obfuscation (generates `mapping.txt`) |
| `o` | Online mode (allow dependency downloads; default is offline) |
| `t1`..`t4` | Set Gradle worker threads (default: `t2`) |
| `l1` | Enable standard linting |
| `l2` | Enable strict linting (`--warning-mode all`) |
| `1` | Bump minor version (resets patch to 0) |
| `h` / `?` | Show usage and exit |

### Examples

```bash
./build.sh               # Debug APK (default)
./build.sh a             # Release APK
./build.sh p             # Production AAB (signed)
./build.sh p c m         # Production AAB, clean, minified
./build.sh d o t4        # Debug APK, online, 4 worker threads
./build.sh a m l1        # Release APK, minified, with linting
```

---

## Signing

Release and production builds are signed using environment variables.

1. **Set `$S2N_SIGN_FILE`** to point to your `.jks` keystore (in your shell profile):

   ```bash
   export S2N_SIGN_FILE=/path/to/upload-key.jks
   ```

2. **Set password / alias env vars** directly, or put them in `~/.reachme_signing.env`:

   ```bash
   export REACHME_KEYSTORE_PASS=your_keystore_password
   export REACHME_KEY_ALIAS=sel2in_upload
   export REACHME_KEY_PASS=your_key_password   # optional, defaults to keystore pass
   ```

3. Build a signed AAB:

   ```bash
   ./build.sh p
   ```

The build script uses `$S2N_SIGN_FILE` as the keystore path and reads passwords from env vars.

> **⚠️ Never commit keystore files or signing env files to git.** They are excluded by `.gitignore`.

---

## Version Management

- **`version.txt`** — Semantic version (e.g. `1.0.8`). Auto-incremented (patch) on every build; use `1` flag to bump minor instead.
- **`buildDate.txt`** — Timestamp of the last build, written automatically.

Both files are read by `app/build.gradle.kts` at compile time and embedded into `BuildConfig`.

---

## Build Outputs

| Build type | Output path |
|------------|-------------|
| Debug APK | `app/build/outputs/apk/debug/sel2in_snooze_debug.apk` |
| Release APK | `app/build/outputs/apk/release/sel2in_snooze_release.apk` |
| Production AAB | `app/build/outputs/bundle/release/sel2in_snooze_release.aab` |

If `adb` detects a connected device and the build is an APK, it will auto-install.

---

## Project Structure

```
.
├── app/                  # Android application module
│   ├── build.gradle.kts  # App-level Gradle config (signing, build types)
│   └── src/              # Kotlin source, resources, manifests
├── build.sh              # Main build script
├── version.txt           # Semantic version (source of truth)
├── buildDate.txt         # Last build timestamp
├── gradle/               # Gradle wrapper
├── scripts/              # Helper scripts
├── docs/                 # Documentation
├── iconMake/             # Icon generation assets
├── logs/                 # Build logs (gitignored)
└── ref/                  # Reference files from sibling project
```
