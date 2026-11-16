# Android prototype validation

## Build

JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Android SDK/build-tools 35. The build toolchain was installed under `/tmp/wishlist-toolchain`; Maven/Gradle caches are isolated under `/tmp/wishlist-gradle-home`. These are development-machine paths, not runtime requirements or paths embedded in the APK.

Tasks: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`.

Seven unit tests cover dated end-of-day baselines, all movement exit types, missing reporting days, replacement of daily snapshots, invalid negative balances, notification high-watermarks, quiet-hour boundaries across midnight, and unsigned 32-bit AppID validation. Android lint completes without errors; remaining warnings concern dependency updates, style suggestions, intentional synchronous preference acknowledgement, and compatible widget attributes.

## Native emulator evidence

Used a dedicated Pixel 6 / AOSP Android 15 (API 35), x86_64 emulator on port 5580, with no Google Play Services. In that initial pass, the USB-connected device was inaccessible to ADB due to USB permissions. A later authorized installation and chart verification used the separately connected Pixel 10a; the Pixel 6a was not modified.

Verified:

- Clean first-run state and manual AppID input.
- Real public Steam metadata lookup for AppID 620 (Portal 2), including name, header cover, description, and store link.
- Game cache and manual dark preference surviving process termination and relaunch.
- System light/dark changes and independent manual appearance overrides.
- Notification settings and widget preview rendering.
- System font scale 1.3; a filter-label wrapping issue found in this pass was corrected by allowing horizontal scrolling.
- Actual widget pin flow through AOSP Launcher, configuration selection, and pin-success callback persisting AppID 620. In-app pinning required an explicit configuration step because the launcher skipped the normal picker configuration activity; this was corrected and retested.

Native screenshots are under `captures/android/`, which is ignored by Git. They are emulator captures, not browser mockups. The native APK contains no seeded reporting data or financial credential; Portal 2 was added only to the isolated emulator as a public metadata test.

## Not yet verified

The initial build had no financial credential available for testing. The owner subsequently configured their key directly on the Pixel 10a. A follow-up on-device probe authenticated successfully and confirmed absent reports for the sparse dates, without exporting the key; see chart-regression.md. Comparison with a Steamworks outstanding total, actual financial-data-driven notification delivery, and long-running background behavior still need verification. Unit tests validate local calculation and deduplication rules, not all upstream account behavior.

The public Store endpoint is a best-effort integration, not a guaranteed Steamworks contract. Public distribution still needs the credential-use clarification and release decisions recorded in README.md.
