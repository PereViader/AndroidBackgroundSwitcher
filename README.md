# Android Background Switcher

An Android application that automatically cycles through photo albums and sets the phone's wallpaper using centered "cover" aspect-ratio scaling. Integrated with local device albums and Google Photos.

For detailed architecture diagrams, mathematical derivations, and Google Photos API deprecation background, see [docs/ARCHITECTURE_PLAN.md](docs/ARCHITECTURE_PLAN.md).

---

## Key Features

1. **Automatic Album Discovery (Zero Link Pasting)**:
   * Uses Android's native `MediaStore` API (`BUCKET_DISPLAY_NAME` / `BUCKET_ID`) to automatically detect all albums on the device (Camera, Google Photos synced device folders, Downloads, Screenshots).
   * Displays all albums in an in-app grid with cover thumbnails, selection checkboxes, and photo counts.

2. **Google Photos Integration**:
   * **1-Tap Share Sheet**: Open Google Photos $\rightarrow$ tap "Share Album" $\rightarrow$ choose "Background Switcher". The app automatically captures the album title, cover, and photo stream without manual URL typing.
   * **Automatic Discovery of New Photos**: Android's `ContentObserver` monitors for newly added photos and synchronizes them into the rotation queue without user intervention.

3. **Centered "Cover" Aspect Ratio Fitting**:
   * Mathematical implementation of CSS `object-fit: cover`.
   * Preserves exact source aspect ratio, scales to cover 100% of the screen without empty bars, and centers symmetrically along the excess axis.

4. **Dual Wallpaper Rotation Engine**:
   * **Static Mode**: Uses Android `WallpaperManager` via Jetpack `WorkManager` (configurable target: Home Screen, Lock Screen, or Both).
   * **Live Wallpaper Mode**: High-performance `WallpaperService` engine with smooth cross-fading, instant screen-unlock rotation, and zero battery drain when the screen is off.

5. **Battery-Aware Background Scheduling**:
   * Powered by Jetpack `WorkManager` 2.10+ with constraints (`NetworkType.UNMETERED`, `requiresBatteryNotLow`).
   * Pre-fetches candidate images in the background to guarantee instantaneous wallpaper switching with 0ms network lag.

---

## Tech Stack (Latest Android 15 / API 35)

* **Language**: Kotlin 2.1+ / Coroutines & Flow
* **UI**: Jetpack Compose + Material 3 (Material You Dynamic Theming)
* **Architecture**: Clean Architecture + MVVM + Unidirectional Data Flow (UDF)
* **Dependency Injection**: Jetpack Hilt 2.52+
* **Database**: Room 2.6+ with KSP
* **Preferences**: Jetpack DataStore Preferences
* **Image Engine**: Coil 2.7+ (Compose-native image loader)
* **Background Processing**: Jetpack WorkManager 2.10+
* **Display Metrics**: AndroidX Window Metrics Calculator

---

## Project Structure

```
AndroidBackgroundSwitcher/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/pereviader/backgroundswitcher/
│   │   │   │   ├── BackgroundSwitcherApp.kt
│   │   │   │   ├── background/         # WorkManager Workers, BootReceiver, Scheduler
│   │   │   │   ├── core/
│   │   │   │   │   ├── crop/           # Cover crop calculation & matrix engine
│   │   │   │   │   ├── model/          # Album, PhotoItem, WallpaperSettings
│   │   │   │   │   └── wallpaper/      # WallpaperManagerHelper & LiveWallpaperService
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/          # Room DB, DAOs, DataStore Preferences
│   │   │   │   │   ├── mediastore/     # MediaStore scanner & ContentObserver
│   │   │   │   │   ├── remote/         # Google Photos parser
│   │   │   │   │   └── repository/     # Repository implementations
│   │   │   │   ├── di/                 # Hilt Dependency Injection Modules
│   │   │   │   ├── domain/             # Repository interfaces & Use Cases
│   │   │   │   └── ui/                 # Jetpack Compose UI (Preview, Albums, Settings)
│   │   │   └── res/                    # Drawables, Themes, Icons, Wallpaper XML
│   │   └── test/                       # Unit tests (CenterCropEngineTest)
│   └── build.gradle.kts
├── docs/
│   └── ARCHITECTURE_PLAN.md            # Complete architecture & design specifications
├── gradle/
│   └── libs.versions.toml              # Gradle Version Catalog
├── build.gradle.kts
└── settings.gradle.kts
```

---

## How to Build & Run

### Prerequisites
* **Android Studio** (Ladybug / 2024.2+ recommended) or JDK 17+ with Android SDK 35.
* Physical Android device or Emulator running Android 8.0 (API 26) through Android 15 (API 35).

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```
