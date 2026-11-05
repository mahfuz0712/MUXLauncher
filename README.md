# MUX Launcher

Sci-fi HUD (Jarvis style) home screen for Android 8.0+. Same design language as Dialer, Scanner and Keyboard (cyan / amber HUD).

- Package: `com.mux.launcher` - Version: `1.0.1`

## Features
- Home screen: DASH page (system status, quick actions, favorites) + free-grid HOME pages + dock (5 slots)
- Drag and drop: long-press any icon (home, dock, drawer, folder) and drag it anywhere. Release on a cell to place it, on REMOVE / UNINSTALL / EDGE DOCK / APP INFO at the top. Drag to the screen edge to switch (or create) pages. Long-press without moving opens the menu (add to dock / edge dock, etc.)
- Folders: drop an app on another app to make a folder; tap to open, rename, drag apps out
- Widgets: HOME EDITOR > WIDGETS (any Android widget, resizable by its default span)
- Home editor: pinch-in (zoom out) or swipe up with two fingers on the home screen > WIDGETS, WALLPAPER, SETTINGS, + PAGE
- App drawer: search bar, category filter, A-Z rail, long-press menu / drag out to home
- Edge dock: no visible handles. Touch the left or right screen edge, swipe inward and hold (default 3 s, 1-5 s in settings) until the vibration, then the dock slides out over any app. Tap mode: FULL / FLOAT / SPLIT
- Gestures: swipe up / down, double tap, long press (each configurable); optional bottom bar (home / recents / back)
- Split screen: SPLIT mode in the edge dock or app menu (toggles system split, then opens the app beside it)

## First run
1. SETUP tab > SET AS DEFAULT HOME > choose MUX Launcher
2. SETUP tab > OPEN ACCESSIBILITY SETTINGS > turn on MUX Launcher (if greyed out: App info > menu > Allow restricted settings)
3. Honor: Settings > Battery > App launch > MUX Launcher > manage manually, all switches on

## Limits (Android, no root)
- Split screen uses the system split action; the result depends on the vendor (Honor/MagicOS untested).
- Third-party apps cannot draw minimize / maximize / close buttons on other apps. Those only appear if freeform windows are enabled on the phone (system draws them).
- After updating, re-enable the accessibility service if it was turned off.
- FLOAT windows need freeform support (Developer options). Otherwise the app opens normally.
- The service only sees which app is in front, never screen text.

## Build
`gradle assembleDebug` (Gradle 8.9, JDK 17) or the GitHub Action. `app/debug.keystore` is shared with the other apps.
