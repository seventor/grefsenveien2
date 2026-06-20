# Grefsenveien Smart Home (Android Auto & Wear OS)

A custom-built Android application designed specifically to integrate smart home features (such as driveway cameras and garage doors) directly into **Android Auto** displays and **Wear OS** smartwatches. 

The application fetches a live-updated security camera image from an Amazon S3 bucket and provides physical buttons to trigger Home Assistant (Nabu Casa) Webhooks to open the Garage and the Gate.

## Features

**Android Auto (`app` module)**
- Displays a full-screen, dynamically scaled security camera feed.
- Shows a live timestamp of when the image was captured, bypassing internal HTTP caches.
- Features a floating `ActionStrip` with **Garasje** and **Port** control buttons.
- Features an **Oppdater** button to manually force a fresh image download.

**Wear OS (`wear` module)**
- Provides a dedicated Wear OS Tile (Widget) for immediate access from the watch face.
- Interactive, native buttons to control the Garage and Gate without opening the full app.
- Status feedback text directly on the watch face upon triggering Nabu Casa webhooks.

**Phone App Companion**
- Simple debug UI on the phone to test the Webhooks if not connected to a car.

## Project Structure

### `app` module
- `MainCarScreen.java` - Core Android Auto logic: Handles `SurfaceCallback`, canvas drawing, S3 image scaling, and webhook triggers.
- `CarAppService.java` - Android Auto entry point and validation service.
- `MainActivity.java` - Main phone UI fallback with basic webhook testing buttons.

### `wear` module
- `ActionTileService.java` - Native Wear OS Tile provider that handles the swipe-accessible widget.
- `MainActivity.java` - Basic fallback activity for the Wear OS app grid.

## Run locally

1. Clone the project and open it in **Android Studio**.
2. Create `local.properties` with `sdk.dir` and your secret URLs (see [Configuration & Secrets](#configuration--secrets-local-setup) below).
3. Sync Gradle (**File → Sync Project with Gradle Files**).
4. Connect a physical Android phone.
5. Accept debugging on the phone.
6. Install and run the **`app`** module on the phone, or use the **Desktop Head Unit (DHU)** emulator for the full Android Auto UI.
7. Open Setti ngs on Android device, goto Apps. Find Android Auto, click "More settings in app", the the three dost on top right, then Turn on Server for main unit.

**Detailed testing guide (phone, DHU, car, Wear OS, troubleshooting):** [TESTING.md](TESTING.md)

**Automatic deploy to Google Play Internal Testing on push to `master`:** [DEPLOY.md](DEPLOY.md)

Quick start for Android Auto on DHU:

```bash
./gradlew :app:installDebug
adb forward tcp:5277 tcp:5277
desktop-head-unit -c square_dhu.ini   # or mache_dhu.ini
```

Ensure `adb` and `desktop-head-unit` are on your `PATH` — see [TESTING.md](TESTING.md#forutsetninger).

## Configuration & Secrets (Local Setup)

To keep sensitive URLs and signing keys out of version control, this project uses local property files that are ignored by Git. 

**You must create these files locally before building the project:**

### 1. Webhooks & Image URLs (`local.properties`)
Create or open the `local.properties` file in the root directory (where `sdk.dir` is usually defined) and add your secret URLs:

```ini
GARAGE_WEBHOOK_URL=https://your-home-assistant.url/api/webhook/secret_code_garage
GATE_WEBHOOK_URL=https://your-home-assistant.url/api/webhook/secret_code_gate
S3_IMAGE_URL=https://your-s3-bucket-url.com/latest.jpg
```
*Gradle will read these during compilation and automatically inject them into both the Mobile App and the Wear OS Tile via `BuildConfig`.*

### 2. Signing Keys for Release (`keystore.properties`)
To build a signed `.aab` for the Google Play Store (`bundleRelease`), you need the original keystore and its passwords.
Place the `pixelspore.keystore` file in the `app/` folder, and create a `keystore.properties` file in the project root:

```ini
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```
