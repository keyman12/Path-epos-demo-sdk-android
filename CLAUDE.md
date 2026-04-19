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

Depends on **`path-terminal-sdk-android`** via JitPack. No vendored SDK source — consumes published JitPack artifacts.

`settings.gradle.kts`:
```kotlin
maven { url = uri("https://jitpack.io") }
```

`app/build.gradle.kts`:
```kotlin
implementation("com.github.keyman12.path-terminal-sdk-android:path-core-models:v1.x")
implementation("com.github.keyman12.path-terminal-sdk-android:path-terminal-sdk:v1.x")
implementation("com.github.keyman12.path-terminal-sdk-android:path-emulator-adapter:v1.x")
```

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
