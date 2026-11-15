# GeoRelay

Minimal Android GPS recorder and JPEG share intermediary.

## Usecase

For when your DSLR doesn't track geocoordinates which naturalist observation applications require. 
1. Run GeoRelay while out photographing wildlife.
2. Attach your storage card to your Android device.
3. Share your desired image to GeoRelay.
4. The now geocoordinated image is prepared to be relayed to your observation app.

## Current boundaries

- JPEG only, one image per share, up to 100 MB.
- EXIF offset and fractional seconds are respected. Without an offset, the device's current timezone is assumed. Photos from another timezone require correct EXIF offsets. Invalid or ambiguous local timestamps are rejected.
- Missing capture time or missing later fix stops processing.
- GPS availability and Android power management affect intervals. Recording does not restart after force-stop, process death, or reboot.
