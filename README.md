# PickAPlayer

PickAPlayer is a utility app designed to help you pick a starting player or split a group of people into teams. It is NOT a game itself, but a tool to facilitate board games, card games, or any activity requiring random selection or team division.

## Features

*   **Starting Player Picker**: Everyone places a finger on the screen, and the app randomly selects one person.
*   **Group Splitter**: Everyone places a finger on the screen, and the app randomly divides the group into two teams (Cyan and Magenta).
*   **Visual & Audio Feedback**: Circles appear under fingers, with bounce animations and sound effects building up to the selection.
*   **Vibration**: The device vibrates when a selection is made.

## How to Use

1.  Open the app.
2.  Select the mode from the menu (top-left hamburger icon):
    *   **Starting Player**: Default mode. Picks one winner.
    *   **Group**: Splits fingers into two groups.
3.  Have all participants place one finger on the screen.
4.  Wait for the countdown sound.
5.  The app will highlight the winner(s) and vibrate.

## Project Structure

The project is a standard Android application built with Kotlin.

*   **app/src/main/java/com/example/chwazi2/**: Contains the Kotlin source code.
    *   `MainActivity.kt`: The main entry point, handles the UI layout and menu interaction.
    *   `FingerPickerView.kt`: Custom View handling touch events, rendering, and game logic.
    *   `Finger.kt`: Data class representing a touch point.
    *   `GameMode.kt`: Enum class for the game modes.
    *   `RisingTonePlayer.kt`: Helper class for generating the rising tone sound effect.
*   **app/src/main/res/**: Resources (layouts, drawables, values).
    *   `drawable/ic_menu.xml`: Vector drawable for the hamburger menu icon.
*   **app/src/main/AndroidManifest.xml**: App manifest file.

## License

This project is licensed under a Non-Commercial License.

**You are free to:**

*   **Use**: You may use this application for personal purposes.
*   **Modify**: You may modify the source code for your own personal use.

**Under the following conditions:**

*   **Non-Commercial**: You may NOT use this application or its source code for commercial purposes. This includes, but is not limited to, selling the app, including it in a paid product, or using it to promote a business.
