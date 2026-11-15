# GeoRelay

Minimal Android GPS recorder and JPEG share intermediary. Requires Android 8.0 or newer.

## Build with GitHub

Create an empty GitHub repository, then push this directory. The Android build workflow runs tests, Android lint, and builds a debug APK on each push or pull request. It can also be started manually from Actions. Download **GeoRelay-debug** from the completed run's artifacts and unzip it to install the APK.

No local Java or Android SDK is needed for GitHub builds. For local development, open the project in Android Studio or use Java 17 and Android SDK 35 with `./gradlew testDebugUnitTest lintDebug assembleDebug`.

## Use

1. Open Recorder, grant precise location and notification permissions, and tap Start.
2. GPS fixes are saved locally at a requested 15-second interval. The notification provides Stop; its stopped state provides Start, which opens the app to satisfy Android location-service launch rules.
3. Share a JPEG from another app to GeoRelay. GeoRelay copies it, reads EXIF DateTimeOriginal, selects the earliest stored fix strictly after capture, writes GPS EXIF, and opens Android's share chooser.
4. The Coordinates tab lists recorded fixes, newest first.

The original image is retained. Image pixels are not decoded or recompressed, but the EXIF library rewrites metadata structures. GPS metadata is updated to avoid retaining stale GPS measurements. Other EXIF fields are retained by the library.

## Current boundaries

- JPEG only, one image per share, up to 100 MB. Unsupported files receive an explanation.
- EXIF offset and fractional seconds are respected. Without an offset, the device's current timezone is assumed. Photos from another timezone require correct EXIF offsets. Invalid or ambiguous local timestamps are rejected.
- Missing capture time or missing later fix stops processing. There is no interpolation, earlier-fix fallback, or maximum gap limit; the matched time and gap are shown on the preparation screen.
- GPS availability and Android power management affect intervals. Recording does not restart after force-stop, process death, or reboot. Real-device screen-off testing is still required.
- Coordinates remain in private SQLite storage. No internet permission, cloud service, maps, ads, or analytics. Temporary shared copies older than seven days are cleaned up on the next share.
- The APK is a development build. CI and device validation have not yet run.

## Device verification

Test precise-location denial, notification denial, GPS off/on, recording with the screen off, notification Stop/Start, and image sharing across the actual source and destination apps. Check that a captured image matches the first later fix, and compare image scan data and non-GPS metadata before/after. Test missing timestamps, timezone offsets, missing later fixes, and unsupported image formats.
