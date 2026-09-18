# Pinso

Offline Android cat-food tracker, with independent dry and wet bowls. Requires Android 8 or newer; targets Android 17 (API 37).

## Build

Install JDK 17 or newer, Android platform 37 and build tools 37.0.0. Set `sdk.dir` in your untracked `local.properties` (this laptop uses `/opt/android-sdk`).

```
./gradlew testDebugUnitTest assembleDebug lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` after connecting a phone with USB debugging enabled.

## Behavior

- Add increases a bowl's recorded balance.
- Weigh compares food found with the previous balance, recording the difference as estimated consumption.
- Replace performs that comparison, discards the remainder, and records new food.
- Throw away performs that comparison, discards the remainder, and leaves zero.
- Weights are food-only grams, stored as integer milligrams. Tare the scale first.
- Entries have editable timestamps and optional notes. History supports editing/deleting and recalculates subsequent entries. Changes that would produce negative consumption are rejected; log missing additions first.
- Insights attribute estimated consumption to the measurement timestamp, not the unknown time food was actually eaten. Evaporation and spills can affect estimates.

Data is stored locally in SQLite, with no account, network permission, or cloud backup. Uninstalling clears the data. The first version has two fixed bowls. Initial bowl amounts are entered with Add. The UI uses native Android views; charts, custom bowls, export, and reminders are not yet included.
