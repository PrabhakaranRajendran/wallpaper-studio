#!/usr/bin/env bash
# Runs inside the Android emulator job: installs the test build, sets it up,
# and takes screenshots of every screen, the live wallpaper and the lock screen.
set -x
PKG=com.prabhu.wallpaperstudio
OUT=shots
mkdir -p "$OUT"
APK=app/build/outputs/apk/debug/app-debug.apk

# The emulator's own launcher sometimes freezes; close its "isn't responding" box (Back = Wait).
no_anr() {
  if timeout 10 adb shell dumpsys window | grep -q "mCurrentFocus.*Application Not Responding"; then
    echo "closing a not-responding box"; adb shell input keyevent KEYCODE_BACK; sleep 1
  fi
}
shot() { sleep "${2:-3}"; no_anr; adb exec-out screencap -p > "$OUT/$1.png"; }
start() { no_anr; timeout 40 adb shell am start -S -W -n "$PKG/.MainActivity" "$@" > /dev/null; }
swipe_up() { adb shell input swipe 540 1900 540 700 400; }
tap_text() { # tap the first on-screen element whose text matches $1
  no_anr
  timeout 30 adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui_$2.xml" > /dev/null 2>&1
  local xy
  xy=$(python3 ci/tap_text.py "$OUT/ui_$2.xml" "$1")
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped $1 at $xy"; else echo "NOT FOUND: $1"; fi
}

adb wait-for-device
adb shell input keyevent 82
adb shell svc power stayon true
adb install -r -g "$APK" || { echo "install failed"; exit 0; }
adb shell input keyevent KEYCODE_HOME; sleep 5
adb logcat -c

# ---- app screens (light Aqua)
start --ez gen_photos true --ez demo true --es palette aqua --es mode light --es tab home
shot 01_home 5
swipe_up; shot 02_home_scrolled
start --es tab wallpaper --ei sub 0
shot 03_wallpaper_top
swipe_up; shot 04_wallpaper_photos
swipe_up; swipe_up; shot 05_wallpaper_photos_more
start --es tab wallpaper --ei sub 1
swipe_up; shot 06_wallpaper_3d
start --es tab wallpaper --ei sub 2
swipe_up; shot 07_widgets_preview
swipe_up; shot 08_widgets_clock
start --es tab lock
shot 09_lock_top
swipe_up; shot 10_lock_album
tap_text "^Select$" 10b_select; sleep 1
tap_text "^All$" 10c_all; sleep 1
shot 10d_lock_selected 1
tap_text "^Hide$" 10e_hide; sleep 1
shot 10f_lock_hidden 1
tap_text "^Show$" 10g_show; sleep 1
tap_text "^Done$" 10h_done; sleep 1
tap_text "(?i)^Text . date$" 10i_text; sleep 1
shot 10j_lock_text 2
swipe_up; shot 10k_lock_text_more 1
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
adb emu sensor set gyroscope 0.8:1.6:0
sleep 0.4
adb emu sensor set gyroscope 0:0:0
shot 20b_render_photo_tilted 0.3
start --es only wide --es tab home > /dev/null
adb shell am start -S -W -n "$PKG/.debug.RenderTestActivity" --es kind photo > /dev/null
shot 20c_render_wide_photo_smart 5
start --es only all --es tab home > /dev/null
start --es tab home --ez demo true
# back to photos for the live wallpaper test

# ---- set the live wallpapers through Android's own screen (no force-stop after this:
#      force-stopping a live wallpaper's app makes Android drop it)
set_live() { # $1 = service class, $2 = name for screenshots
  adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER \
    --ecn android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT "$PKG/.wallpaper.$1" > /dev/null
  shot "${2}_a_preview" 6
  tap_text "(?i)^(set wallpaper|apply|set)$" "${2}_a"
  sleep 3
  tap_text "(?i)^home screen$" "${2}_b"
  sleep 3
  adb shell input keyevent KEYCODE_HOME
  shot "${2}_b_launcher" 6
}
set_live PhotoWallpaperService 32_photo_live
adb shell am start -W -n "$PKG/.MainActivity" --es tab home > /dev/null
shot 33_home_with_photos 4

# ---- lock screen slideshow (changes when the screen turns off)
adb shell locksettings set-disabled false
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell input keyevent KEYCODE_SLEEP
sleep 5
adb shell input keyevent KEYCODE_WAKEUP
shot 34_lock_screen 3
adb shell input keyevent KEYCODE_SLEEP
sleep 5
adb shell input keyevent KEYCODE_WAKEUP
shot 35_lock_screen_next 3
adb shell input keyevent 82
sleep 2
adb shell am start -W -n "$PKG/.MainActivity" --es tab settings > /dev/null
swipe_up; shot 36_settings_check 3

# ---- logs
adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d | grep -iE "wallpaperstudio|AndroidRuntime|E/|W/WallpaperService" | tail -400 > "$OUT/log.txt"
adb shell dumpsys wallpaper > "$OUT/dumpsys_wallpaper.txt" 2>&1
adb shell "run-as $PKG cat shared_prefs/studio.xml" > "$OUT/prefs.xml" 2>&1
ls -la "$OUT"
exit 0
