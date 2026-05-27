# Hourly Activity Tracker

A **Wear OS** app for Google Pixel Watch 4 that reads per-hour step counts from **Health Connect** and displays them in a 4-column grid.  Hours with ≥ 250 steps are highlighted in green.

---

## Features

- Queries Health Connect for `StepsRecord` data, bucketed into one-hour slices for the current day.
- Displays all 24 hours (00 – 23) in a scrollable 4-column grid.
- **Green cells** = ≥ 250 steps that hour.
- **Dark grey cells** = some steps but below threshold.
- **Near-black cells** = no recorded steps.
- Handles the Health Connect permission flow gracefully, prompting the user when needed.
- Detects if Health Connect is unavailable and shows an informative message.

---

## Requirements

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| Android Gradle Plugin | 8.3.x |
| Kotlin | 2.0 |
| Compile / Target SDK | 35 |
| Min SDK | 30 (Wear OS 3.0+) |
| JDK | 17 |

A **Google Pixel Watch 4** (or Wear OS 4/5 emulator) is required to run the app.  
Health Connect is pre-installed on Wear OS 4+ devices.

---

## Setup

1. **Clone the repository**

   ```bash
   git clone https://github.com/nealmcb/hourly-activity-tracker.git
   cd hourly-activity-tracker
   ```

2. **Open in Android Studio**

   *File → Open* → select the `hourly-activity-tracker` directory.  
   Android Studio will sync Gradle automatically and download all dependencies.

3. **Gradle wrapper bootstrap** (first-time only, if not using Android Studio)

   ```bash
   gradle wrapper --gradle-version 8.7
   ```

   Then use `./gradlew` for all subsequent commands.

---

## Build

```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (requires signing config)
./gradlew :app:assembleRelease
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Install & Run

```bash
# Install to connected watch (or emulator)
./gradlew :app:installDebug

# Or use adb directly
adb install app/build/outputs/apk/debug/app-debug.apk
```

On first launch the app will request the **Steps** Health Connect permission. Tap **Grant Access** and approve the permission in the Health Connect dialog.

---

## Project Structure

```
app/src/main/java/net/nealmcb/hourlyactivitytracker/
├── MainActivity.kt               # Entry point; wires permission launcher
├── HourlyActivityApp.kt          # Top-level Compose scaffold + all screens
├── HourlyActivityViewModel.kt    # State holder; drives UI state transitions
├── HealthConnectManager.kt       # Health Connect queries (hourly step buckets)
└── ui/theme/
    └── Theme.kt                  # Wear OS MaterialTheme wrapper
```

---

## Health Connect Permissions

The app declares and requests a single runtime permission:

| Permission | Purpose |
|-----------|---------|
| `android.permission.health.READ_STEPS` | Read step-count records from Health Connect |

The `ACTIVITY_RECOGNITION` permission is also declared as a fallback for older Wear OS API levels.

---

## Customising the Threshold

The highlight threshold defaults to **250 steps/hour**.  To change it, edit the constant in `HourlyActivityApp.kt`:

```kotlin
private const val STEP_HIGHLIGHT_THRESHOLD = 250L
```

---

## Architecture

```
MainActivity
  └─ HourlyActivityViewModel  (AndroidViewModel + StateFlow)
       └─ HealthConnectManager
            └─ HealthConnectClient.aggregateGroupByDuration()
```

The ViewModel exposes a `UiState` sealed interface that the Compose UI observes via `collectAsStateWithLifecycle()`.
