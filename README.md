# Path EPOS Demo — Android

A full-featured Android EPOS (point-of-sale) application demonstrating the **Path Terminal SDK** end-to-end. Built with Jetpack Compose, it targets tablet form factors (Remi Pad 2) and matches the functionality of the iOS Path EPOS Demo.

This app serves as:
- The **reference integration** for the Path Terminal SDK on Android
- The **development/testing ground** for new SDK features
- A **parallel implementation** to the iOS demo — both apps are kept in sync

## Features

| Feature | Description |
|---------|-------------|
| Product grid + cart | Coffee shop menu, quantities, totals |
| Card payment | Full state machine via Path Terminal SDK + BLE emulator |
| Cash payment | Simulated cash with change calculation |
| Card refund | Refund against original transaction reference |
| Cancel | 30-second timeout detection + manual cancel button |
| Transaction log | Persistent order history with URN, masked card, auth code |
| Receipt display | On-screen receipt with Path Cafe logo, EMV card block |
| Receipt PDF | Generated via Android `PdfDocument` API |
| Receipt print | Android `PrintManager` with preview dialog |
| Receipt email | Direct SMTP send (STARTTLS) — no Android share sheet |
| BLE device management | Scan, RSSI display, connect/disconnect per device |
| Developer diagnostics | SDK versions, connection state, log viewer (500 entries) |
| Support bundle | One-tap JSON export to clipboard |
| GetTransactionStatus | Query terminal for any transaction status by reference |
| SMTP configuration | Host, port, TLS, credentials stored in SharedPreferences |

## Architecture

```
app/src/main/kotlin/tech/path2ai/epos/
├── EPOSApplication.kt
├── MainActivity.kt                     — DI root: wires SDKTerminalManager → AppTerminalManager
├── terminal/
│   ├── SDKTerminalManager.kt           — Wraps PathTerminal, handles events Flow, logging
│   ├── AppTerminalManager.kt           — ViewModel; exposes StateFlows to Compose UI
│   ├── TerminalConnectionManager.kt    — Interface (SDK surface abstraction)
│   └── TerminalModels.kt               — Connection state, request/response models
├── managers/
│   ├── InventoryManager.kt             — Product catalogue
│   └── OrderManager.kt                 — Order history, SharedPreferences persistence
├── models/
│   ├── Cart.kt, Order.kt, Product.kt, Receipt.kt
├── email/
│   ├── SMTPClient.kt                   — Raw SMTP with STARTTLS (Dispatchers.IO)
│   └── SMTPConfig.kt                   — SMTP config data class + SharedPreferences storage
└── ui/screens/
    ├── EPOSScreen.kt                   — Product grid + cart
    ├── PaymentScreen.kt                — Payment method chooser
    ├── CardPaymentScreen.kt            — Card payment state machine
    ├── CashPaymentScreen.kt            — Cash payment
    ├── RefundPaymentScreen.kt          — Card refund flow
    ├── OrderHistoryScreen.kt           — Transaction log with receipt/refund actions
    ├── ReceiptScreen.kt                — Receipt display + PDF/print/email
    ├── ReceiptRenderer.kt              — PDF + plain-text receipt generation
    ├── DeveloperDiagnosticsScreen.kt   — Diagnostics + support bundle + log viewer
    ├── SMTPConfigScreen.kt             — Email settings UI
    ├── SettingsScreen.kt               — Split-pane settings (sidebar + detail)
    └── SplashScreen.kt                 — Launch screen with Path logo
```

## SDK Dependency

The app consumes the Path Terminal SDK via Gradle composite build:

```kotlin
// settings.gradle.kts
includeBuild("../path-terminal-sdk-android") {
    dependencySubstitution {
        substitute(module("tech.path2ai.sdk:path-core-models")).using(project(":path-core-models"))
        substitute(module("tech.path2ai.sdk:path-terminal-sdk")).using(project(":path-terminal-sdk"))
        substitute(module("tech.path2ai.sdk:path-emulator-adapter")).using(project(":path-emulator-adapter"))
        substitute(module("tech.path2ai.sdk:path-mock-adapter")).using(project(":path-mock-adapter"))
        substitute(module("tech.path2ai.sdk:path-diagnostics")).using(project(":path-diagnostics"))
    }
}
```

The SDK repo must be cloned as a sibling directory:

```
android/
├── path-terminal-sdk-android/     ← SDK
└── Path-epos-demo-sdk-android/    ← This app
```

## Build & Run

```bash
# Clone sibling SDK repo first
cd /path/to/android/
git clone git@github.com:keyman12/path-terminal-sdk-android.git

# Open Path-epos-demo-sdk-android/ in Android Studio
# or build from command line:
./gradlew assembleDebug
```

Requires:
- Android Studio Hedgehog or later
- Minimum SDK: API 26 (Android 8.0)
- Target SDK: API 35
- Bluetooth LE hardware (or emulator with BLE support)
- Path POS Emulator running on the same network/BLE range

## First-time Setup

1. **Connect terminal** — Settings → Payment Terminal → Scan → connect to Path Emulator
2. **Configure email** — Settings → Email Settings (host, port, TLS, credentials)
3. **Add items to cart** → Pay Now → Card
4. **Present NFC tag** to emulator → Approved
5. Receipt appears with EMV data, Path Cafe logo

## Receipt Email (SMTP)

The app sends receipts directly via SMTP — no Android share sheet, matching iOS behaviour. Configure in Settings → Email Settings. Tested with Gmail (smtp.gmail.com:587, App Password).

## Companion Repos

| Repo | Purpose |
|------|---------|
| [path-terminal-sdk-android](https://github.com/keyman12/path-terminal-sdk-android) | The SDK this app depends on |
| [Path-EPOS-TestHarness-Android](https://github.com/keyman12/Path-EPOS-TestHarness-Android) | Blank harness for testing new SDK integrations |
