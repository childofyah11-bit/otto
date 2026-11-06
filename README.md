# Paint by Number Auto Bot

This repo contains a starter Android automation app for the game **Paint by Number: Coloring Game** by Oakever Games.

It is designed to do the following:

- reopen the game automatically if the Play Store opens
- close common ad screens when the X/Close/Skip button appears
- trigger a new puzzle when the game offers a next or play action
- provide a foundation for tap-based painting automation

Important:
- This is a starter project. Exact painting logic must be tuned to the current Oakever UI because app layouts can change.
- This project uses Android Accessibility Services, which are the correct system hooks for app automation on Android.
- You must enable the accessibility permission for this app in Settings > Accessibility.

## Android package
This project is targeting the Oakever package:

- `com.pbn.pbox.oakever.coloring.drawing.paint.by.number.colour.art`

## Quick start
1. Open this project in Android Studio.
2. Let Gradle sync.
3. Install the app on your Android device.
4. Open Settings > Accessibility > Installed Services and enable `Paint by Number Auto`.
5. Open the target app and let the service run.

## Notes
This service is intentionally built around a safe automation pattern:
- detect and close ad dialogs
- redirect away from the Play Store back into the target game
- support a next/new puzzle action

To make the color-filling portion perfectly match the real game screen, capture a few images of the in-game painting area and modify the tap logic in `AutoPaintAccessibilityService.kt`.

