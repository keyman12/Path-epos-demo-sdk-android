# Changelog — OrderChampion EPOS (Android demo)

Human-readable history of notable changes, newest first.

**Versioning.** `MAJOR.MINOR` (e.g. `1.0` → `1.1`). A small/additive change bumps the
**minor** (`1.0` → `1.1`); a big or breaking change bumps the **major** (`1.0` → `2.0`).
The single source of truth is `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts);
the About screen reads it via `BuildConfig.VERSION_NAME`, so the two can't drift. When you
bump, also raise the integer `versionCode` by 1 and add a dated entry below. Policy:
`~/Developer/Path-PSDK-TestHarnesses/docs/VERSIONING.md`.

## 1.0 — 2026-06-25

Baseline: versioning + changelog mechanism established; About now reads the build version.
Current feature state (most recent capability last):
- Loopback + emulator (TCP/BLE) + Verifone terminal backends, canonical backend labels.
- Sale, linked refund, linked void; receipts (merchant/customer) + email receipts.
- Terminal-native **tip** (read back from the terminal, no double-tip).
- **Pre-authorization** and the **bar/café tab** flow (open a tab, add rounds, close &
  settle — capture when ≤ hold, void + standard sale when over).
- Customer-display merchant logo / attract mode.
