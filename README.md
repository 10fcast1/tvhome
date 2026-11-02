# TV Home: three-look launcher for Fire TV (Fire OS 7)

A small launcher app with three switchable looks:

| Look | What it is |
|------|------------|
| **Android TV** | Classic row of wide banner cards |
| **Google TV**  | Big title area on top, round app icons below |
| **Apple TV**   | Large floating tiles that grow when focused |

These are visual styles on top of Fire OS. They do not install real Google TV, Android TV, or tvOS (the bootloader is locked).
Google TV's personalized recommendations need Google services, so the Google TV look is apps-only.

Default apps: YouTube, Netflix, YouTube TV, Hulu, Disney+, Peacock (whichever are installed).
Add or remove any app any time with **Edit apps** on the home screen.

Controls: D-pad to move, OK to open, **Menu (≡) button cycles the look**, or pick a look from the pills at the top.

---

## 1. Build the APK

Needs Android Studio (any recent version). The Android SDK was not reachable from the workspace that wrote this project, so it has not been compiled yet. Expect to fix small things on first build.

1. Android Studio → **Open** → select this `TvHome` folder.
2. Let Gradle sync (it will offer to install SDK platform 34).
3. **Build → Build APK(s)**. Output: `app/build/outputs/apk/debug/app-debug.apk`.

Or from a terminal with the SDK installed: `gradle assembleDebug`.

## 2. Install on the TV

On the TV: Settings → My Fire TV → Developer Options → ADB debugging ON.

```bash
adb connect 192.168.68.54:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.tvhome/.MainActivity     # test it opens
```

Install your streaming apps from the Amazon Appstore first (YouTube, Netflix, YouTube TV, Hulu, Disney+, Peacock).

## 3. Make it the Home button (do this last, and carefully)

Fire OS keeps the Amazon launcher as Home. Test TV Home by hand first (step 2). Then find which package currently answers Home:

```bash
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
```

Two routes:

- **Safest:** sideload a Home-button remapper such as *Launcher Manager* (Downloader app), then pick "TV Home".
- **ADB:** disable the Amazon launcher package printed above, for example
  `adb shell pm disable-user --user 0 <that package>`
  Android then falls back to TV Home for the Home button.
  **Undo if anything looks wrong:** `adb shell pm enable <that package>` (works over ADB as long as TV Home is installed).

Do not disable the Amazon launcher before TV Home is installed and tested, and do not remove the Amazon OTA/update packages.

Then use `firetv.sh` (from earlier) to disable ads/bloat one package at a time.

## 4. Adding apps later

- On the TV: **Edit apps**, OK to add, ◀/▶ to reorder.
- In code: add a row to `DEFAULTS` in `AppRepo.java` (display name + package names).

## Known limits

- App icons use each app's banner when it has one, otherwise its icon.
- Some apps have different package names on Fire TV vs Android TV; `DEFAULTS` lists both for YouTube and YouTube TV. If a default is missing, add it with **Edit apps**.
- Amazon may re-enable its launcher after a Fire OS update. Re-run the disable command if so.
