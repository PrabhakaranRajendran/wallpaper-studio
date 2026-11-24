# Wallpaper Studio

A clean Android live-wallpaper app (made for the OnePlus 12, works on Android 10+).

- **Video wallpaper** – upload any video; it is trimmed, cropped to the screen shape, scaled and re-encoded so it plays smoothly and saves battery.
- **Photo changer** – pick folders, tick the photos you want, and they rotate every 1 min / 15 min / 30 min / 1 hr / 6 hr / daily / custom, or on every unlock. Shuffle or in order; fade, slide or zoom transitions; fill, fit or blur-fill.
- **Crop & zoom** – per-photo zoom, position, rotate, flip and brightness, shown inside a frame shaped like the phone screen.
- **3D gyro effect** – tilt the phone and the photo moves with depth (3D tilt, shift or zoom styles).
- **Material 3** – light, dark or system theme, plus Material You colors.
- **Updates from GitHub** – Settings › Check for updates looks at this repo's Releases page.

No ads, no tracking. The only internet use is the update check.

## Install

Open the [Releases page](https://github.com/PrabhakaranRajendran/wallpaper-studio/releases) on the phone, download the latest `WallpaperStudio-vX.Y.Z.apk` and open it. Android asks once to allow installs from your browser.

## How builds work

`.github/workflows/build.yml` builds the APK on GitHub:

- every push to `main` builds a test APK (download it from the Actions run);
- **Actions › Build APK › Run workflow** with a version (e.g. `1.1.0`) builds the signed APK and publishes it as a release (pushing a tag like `v1.1.0` does the same).

Releases are signed with the key stored in the `SIGNING_KEY` repository secret. Keep a backup of that key: every update must be signed with the same key, or Android refuses to install it over the old version.

### Publishing a new version

1. Update `release-notes.md` (this text appears in the app's update dialog).
2. Open **Actions › Build APK › Run workflow**, type the new version (e.g. `1.1.0`) and press **Run workflow**.
