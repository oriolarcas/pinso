# Pinso

Offline Android cat food and body-weight tracker. Requires Android 8 or newer; targets Android 17 (API 37).

The main navigation has Food and Weight. Food contains Bowls, History, and Insights. Medicines is reserved for a future release and is not shown yet.

## Cat weight

Weight supports direct entry in kilograms, or two readings: you holding the cat, and you without the cat. The app subtracts the latter from the former and retains both readings. Entries have editable timestamps and notes, can be edited or deleted, and are sorted chronologically. The latest measurement and change from the previous measurement appear above the history. Values are stored as integer grams (up to three decimal places in kilograms). Updating the installed app preserves existing food entries.

## Daily food goals

In **Food → Insights → Today**, choose **Set daily allowances**. Enter each food's full daily allowance in grams as if it were the only food. Both must be positive. There are no default recommendations.

Progress is dry food eaten divided by the dry allowance, plus wet food eaten divided by the wet allowance. For example, 42/60 g dry and 60/200 g wet contribute 70% and 30%, reaching 100%. The stacked bar uses green for dry and orange for wet, with an empty remainder below 100%. Above 100%, the percentage remains uncapped and the full bar displays the relative contributions of the two foods. Only estimated consumption counts, attributed to the measurement date in the device's local timezone; added and discarded food do not count. Editing allowances immediately recalculates today's display.

## Build

Install JDK 17 or newer, Android platform 37 and build tools 37.0.0. Set `sdk.dir` in your untracked `local.properties` (this laptop uses `/opt/android-sdk`).

```
./gradlew testDebugUnitTest assembleDebug lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` after connecting a phone with USB debugging enabled.

## Behavior

- Add increases a bowl's recorded balance. An optional current bowl weight first records consumption, then adds the new food in the same entry. Blank means no measurement; explicit zero means the bowl is empty. No food is discarded by Add.
- Weigh compares food found with the previous balance, recording the difference as estimated consumption.
- Replace performs that comparison, discards the remainder, and records new food.
- Throw away performs that comparison, discards the remainder, and leaves zero.
- Weights are food-only grams, stored as integer milligrams. Tare the scale first.
- Entries have editable timestamps and optional notes. History supports editing/deleting and recalculates subsequent entries. Changes that would produce negative consumption are rejected; log missing additions first.
- Insights attribute estimated consumption to the measurement timestamp, not the unknown time food was actually eaten. Evaporation and spills can affect estimates.

Data is stored locally in SQLite, with no account, network permission, or automatic cloud backup. Uninstalling clears the data. The first version has two fixed bowls. Initial bowl amounts are entered with Add. The UI uses native Android views; charts, custom bowls, and reminders are not yet included.

## Backups

Use **Backups** at the top of any screen to export a JSON file through Android's file picker, or import a previously exported file. Backups preserve both food bowls and cat weight history, including entry IDs, exact weights, original difference readings, timestamps, actions, and notes. You can choose local storage or a cloud document provider installed on your phone. Files are unencrypted.

Import validates the file and recalculates its history before asking for confirmation. It **replaces**, rather than merges, all current food and weight entries and daily food allowances, including empty logs or unset allowances. All are replaced in one database transaction: a failed restore rolls back to the previous data. Export before importing if you want to retain your current history. Format version 4 supports files up to 16 MB and preserves optional measurements on Add entries. Previous backup formats are deliberately unsupported; export a new backup after upgrading.
