#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the test build, sets it up,
# and takes screenshots of every screen, the live wallpaper and the lock screen.
set -x
PKG=com.prabhu.wallpaperstudio
OUT=shots
mkdir -p "$OUT"
APK=app/build/outputs/apk/debug/app-debug.apk

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; }
start() { adb shell am start -S -W -n "$PKG/.MainActivity" "$@" > /dev/null; }
swipe_up() { adb shell input swipe 540 1900 540 700 400; }
tap_text() { # tap the first on-screen element whose text matches $1
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui_$2.xml" > /dev/null 2>&1
  local xy
  xy=$(python3 ci/tap_text.py "$OUT/ui_$2.xml" "$1")
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped $1 at $xy"; else echo "NOT FOUND: $1"; fi
}

adb wait-for-device
adb shell input keyevent 82
adb shell svc power stayon true
adb install -r -g "$APK" || { echo "install failed"; exit 0; }
adb logcat -c

# ---- test video: landscape pixels with a 90° rotation note (like phone portrait videos after optimizing)
ffmpeg -loglevel error -f lavfi -i testsrc=size=1280x720:rate=30 -t 6 -c:v libx264 -pix_fmt yuv420p -y /tmp/land.mp4
ffmpeg -loglevel error -display_rotation 90 -i /tmp/land.mp4 -c copy -y /tmp/rot.mp4 || cp /tmp/land.mp4 /tmp/rot.mp4
adb push /tmp/rot.mp4 /data/local/tmp/rot.mp4
adb push /tmp/land.mp4 /data/local/tmp/land.mp4
adb shell "cat /data/local/tmp/rot.mp4 | run-as $PKG sh -c 'cat > files/rot.mp4'"
adb shell "cat /data/local/tmp/land.mp4 | run-as $PKG sh -c 'cat > files/land.mp4'"
VID_ROT=/data/user/0/$PKG/files/rot.mp4
VID_LAND=/data/user/0/$PKG/files/land.mp4

# ---- app screens (light Aqua)
start --ez gen_photos true --ez demo true --es palette aqua --es mode light --es tab home
shot 01_home 5
swipe_up; shot 02_home_scrolled
start --es tab wallpaper --ei sub 0
shot 03_wallpaper_top
swipe_up; shot 04_wallpaper_photos
swipe_up; swipe_up; shot 05_wallpaper_photos_more
start --es tab wallpaper --ei sub 2
swipe_up; shot 06_wallpaper_3d
start --es tab wallpaper --ei sub 3
swipe_up; shot 07_widgets_preview
swipe_up; shot 08_widgets_clock
start --es tab lock
shot 09_lock_top
swipe_up; shot 10_lock_album
start --es tab themes
shot 11_themes
swipe_up; swipe_up; shot 12_themes_bottom
start --es tab settings
shot 13_settings
swipe_up; shot 14_settings_more
start --es tab themes --es palette sunset --es mode dark
shot 15_themes_dark_sunset
start --es tab home --es palette aqua --es mode dark
shot 16_home_dark
start --es palette aqua --es mode light --es tab home > /dev/null

# ---- the real renderers, full screen
adb shell am start -S -W -n "$PKG/.debug.RenderTestActivity" --es kind photo > /dev/null
shot 20_render_photo 5
start --es video "$VID_ROT" --es tab wallpaper --ei sub 1
shot 21_wallpaper_video_tab 5
adb shell am start -S -W -n "$PKG/.debug.RenderTestActivity" --es kind video > /dev/null
shot 22_render_video_rotated 6
start --es video "$VID_LAND" --es tab home
adb shell am start -S -W -n "$PKG/.debug.RenderTestActivity" --es kind video > /dev/null
shot 23_render_video_landscape 6
start --es tab home --ez demo true
# back to photos for the live wallpaper test

# ---- set the live wallpaper through Android's own screen
adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER \
  --ecn android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT "$PKG/.wallpaper.PhotoWallpaperService"
shot 30_system_preview 6
tap_text "(?i)^(set wallpaper|apply|set)$" a
sleep 3
shot 31_after_set_tap 1
tap_text "(?i)home and lock|home screen and lock|^home screen$" b
sleep 3
adb shell input keyevent KEYCODE_HOME
shot 32_launcher_home 5

# ---- lock screen slideshow
adb shell am start -S -W -n "$PKG/.MainActivity" --ez lock_now true --es tab lock > /dev/null
sleep 6
adb shell input keyevent KEYCODE_SLEEP
sleep 4
adb shell input keyevent KEYCODE_WAKEUP
shot 33_lock_screen 3
adb shell input keyevent 82
sleep 2
start --es tab settings
swipe_up; shot 34_settings_check 3

# ---- logs
adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d | grep -iE "wallpaperstudio|AndroidRuntime|GlVideo|ExoPlayer|E/|W/WallpaperService" | tail -400 > "$OUT/log.txt"
adb shell dumpsys wallpaper > "$OUT/dumpsys_wallpaper.txt" 2>&1
adb shell "run-as $PKG cat shared_prefs/studio.xml" > "$OUT/prefs.xml" 2>&1
ls -la "$OUT"
exit 0
