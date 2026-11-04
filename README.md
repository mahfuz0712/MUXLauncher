# MUX Launcher

Sci-fi HUD (Jarvis style) home screen for Android 8.0+. Same design language as Dialer, Scanner and Keyboard (cyan / amber HUD).

- Package: `com.mux.launcher` - Version: `1.0.0`

## Features
- Home screen: DASH page (system status, quick actions, favorites) + HOME page (clock, search, icons) + dock
- App drawer: search bar, category filter (ALL, FAV, SOCIAL, MEDIA, GAMES, WORK, TOOLS, MONEY, TRAVEL, SHOP, OTHER), A-Z rail, long-press menu
- Edge dock: slide-out app panel from the left and/or right screen edge, over any app (like the Honor App Dock). Tap mode: FULL / FLOAT / SPLIT
- Window bar over apps: MIN (home), MAX (leave split), SPLIT, CLOSE
- Gestures: swipe up / down, double tap, long press (each configurable); optional edge-swipe back and bottom bar (home / recents / back)
- Actions: back, home, recents, notifications, quick panel, lock screen, screenshot, split screen, flashlight
- App menu: add to home / dock / edge dock / favorites, change category, hide, app info, uninstall, floating window, split with another app

## First run
1. SETUP tab > SET AS DEFAULT HOME > choose MUX Launcher
2. SETUP tab > OPEN ACCESSIBILITY SETTINGS > turn on MUX Launcher (if greyed out: App info > menu > Allow restricted settings)
3. Honor: Settings > Battery > App launch > MUX Launcher > manage manually, all switches on

## Limits (Android, no root)
- MIN / MAX / CLOSE / SPLIT use system accessibility actions; they work on most phones but the exact result depends on the vendor.
- FLOAT windows need freeform support (Developer options). Otherwise the app opens normally.
- The service only sees which app is in front, never screen text.

## Build
`gradle assembleDebug` (Gradle 8.9, JDK 17) or the GitHub Action. `app/debug.keystore` is shared with the other apps.
