# AirPods Connect — Android TV App

One-tap AirPods connection for Android TV, with optional auto-connect on startup.

---

## Features
- Detects paired AirPods / Beats / EarPods automatically
- One-tap Connect & Disconnect buttons
- Live connection status (Connected / Connecting / Disconnected)
- Auto-connect toggle — connects on TV boot silently
- D-pad navigable, TV-optimised dark UI

---

## Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android TV running Android 5.1+ (API 22+)
- AirPods **already paired** to the TV at least once

---

## Build & Install

### 1. Open in Android Studio
```
File → Open → select the AirPodsTV folder
```

### 2. Sync Gradle
Android Studio will prompt to sync — click **Sync Now**.

### 3. Build the APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 4. Install on Android TV

**Option A — ADB (easiest if TV is on same network):**
```bash
# Enable Developer Options on TV: Settings → About → click Build number 7×
# Enable ADB debugging: Settings → Developer Options → USB Debugging ON

adb connect <TV_IP_ADDRESS>
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option B — Sideload via USB drive:**
1. Copy the APK to a USB stick
2. Plug into TV
3. Open a file manager app (e.g. FX File Explorer) on the TV
4. Navigate to the APK and tap Install

---

## First-Time Setup

1. **Pair AirPods to TV** (one-time only):
   - Put AirPods in case, hold the button on back until light flashes white
   - TV: Settings → Remotes & Accessories → Add accessory → select AirPods

2. **Open the app** from the TV home screen

3. The app will detect your AirPods and show the device name. Press **Connect AirPods**.

---

## Auto-Connect

The app auto-connects on boot by default. Toggle it off in the app UI if you don't want this.

The BootReceiver waits 12 seconds after boot before connecting to give the BT stack time to initialise.

---

## Troubleshooting

| Issue | Fix |
|---|---|
| "No AirPods found" | Pair AirPods in TV BT settings first |
| "Bluetooth is off" | Turn on BT in TV Settings → Network & Accessories |
| Connect button does nothing | Try again — sometimes the A2DP proxy takes a moment |
| Auto-connect not working | Some TV manufacturers block BOOT_COMPLETED for sideloaded apps. Use the app manually instead. |
| "Failed: null" on connect | Your TV's ROM may block the hidden A2DP API. Try a custom ROM or ADB workaround. |

---

## How It Works

Android TV has full `BluetoothA2dp` API access. However, `BluetoothA2dp.connect()` is a **hidden API** — it's not in the public SDK. The app accesses it via Java reflection:

```kotlin
val method = BluetoothA2dp::class.java.getDeclaredMethod("connect", BluetoothDevice::class.java)
method.isAccessible = true
method.invoke(a2dpProxy, device)
```

This works reliably on stock Android TV (Google TV, Sony, Xiaomi, etc.) but may be blocked on heavily customised ROMs.

---

## Project Structure

```
AirPodsTV/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/lootdealsks/airpodstv/
│   │   ├── MainActivity.kt        ← UI + BT state receiver
│   │   ├── BluetoothConnector.kt  ← All A2DP logic
│   │   └── BootReceiver.kt        ← Auto-connect on boot
│   └── res/
│       ├── layout/activity_main.xml
│       └── values/
│           ├── strings.xml
│           └── themes.xml
├── build.gradle
├── settings.gradle
└── gradle.properties
```
