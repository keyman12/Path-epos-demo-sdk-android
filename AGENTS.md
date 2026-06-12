# Path EPOS Demo — Android

You are working in the **fuller Android EPOS demo** app. Canonical remote: `https://github.com/keyman12/Path-epos-demo-sdk-android`.

## Ecosystem at a glance

One of **7 repos** in the Path semi-integrated terminal system:

| iOS | Android | Cross-platform |
|---|---|---|
| `Path-terminal-sdk-IOS` | `path-terminal-sdk-android` | `Path-mcp-server` |
| `Path-epos-demo-sdk-IOS` | `Path-epos-demo-sdk-android` ← **you are here** | `PosEmulator` (Pico firmware) |
| `Path-EPOS-TestHarness-IOS` | `Path-EPOS-TestHarness-Android` | |

See `Path-terminal-sdk-IOS/DEVELOPMENT.md` for the canonical map.

## Role

Fuller Jetpack Compose Android EPOS — the reference app we build **new SDK features** against on Android. Mirror of `Path-epos-demo-sdk-IOS`, used to develop features in a realistic context before they're considered done.

Distinction:
- **This repo (demo)** = fuller app, develop new SDK features here.
- **`Path-EPOS-TestHarness-Android`** = simpler app kept in parity with the iOS harness. Used to test **agentic SDK installs**, not for development.

## SDK dependency

Depends on **`path-terminal-sdk-android`** via a **Gradle composite build** pointing at the sibling working copy. This is intentional — the demo exists to develop new SDK features in a realistic context, so it consumes SDK source from `../path-terminal-sdk-android`, not a published artifact.

`settings.gradle.kts` wires it up with `includeBuild("../path-terminal-sdk-android")` and substitutes the `tech.path2ai.sdk:*` module coordinates onto the local projects (`path-core-models`, `path-terminal-sdk`, `path-emulator-adapter`, `path-mock-adapter`, `path-diagnostics`).

`app/build.gradle.kts` then declares them version-less:
```kotlin
implementation("tech.path2ai.sdk:path-core-models")
implementation("tech.path2ai.sdk:path-terminal-sdk")
implementation("tech.path2ai.sdk:path-emulator-adapter")
implementation("tech.path2ai.sdk:path-diagnostics")
```

### Setup requirements

- The sibling repo must be checked out at `../path-terminal-sdk-android` (i.e. both repos as siblings under the same parent directory).
- **Both** repos need a `local.properties` with `sdk.dir=<Android SDK path>`. The composite build configures the sibling as a real Gradle build, so it independently needs to locate the Android SDK. Missing this gives: *"SDK location not found ... at '.../path-terminal-sdk-android/local.properties'"*.

## Commands

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Open in Android Studio for normal development.

## When modifying payment flow

Keep functional parity with:
- `Path-EPOS-TestHarness-Android` (simpler app — must match behaviour)
- `Path-epos-demo-sdk-IOS` (iOS sibling demo)
