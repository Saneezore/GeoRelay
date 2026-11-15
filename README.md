# GeoRelay

Minimal Android GPS recorder and JPEG share intermediary.

## Usecase

For when your DSLR doesn't track geocoordinates which naturalist observation applications require. 
1. Run GeoRelay while out photographing wildlife.
2. Attach your storage card to your Android device.
3. Share your desired images to GeoRelay.
4. The now geocoordinated images are prepared to be relayed to your observation app.

## Current boundaries

- JPEG only (.jpg/.jpeg), up to 100 MB per image.
- EXIF offset and fractional seconds are respected. Without an offset, the device's current timezone is assumed. Photos from another timezone require correct EXIF offsets. Invalid or ambiguous local timestamps are rejected.
- Missing capture time or missing later fix stops the whole batch and identifies the failing image. Each image uses its own capture time.
- GPS availability and Android power management affect intervals. Android is asked to restart recording after system process termination. Recording does not restart after force-stop or reboot. A missing GPS fix is shown after 90 seconds, with GPS subscription retries every two minutes while waiting.
