# Adventure Oracle Bot — TCL 10 Pro diagnostic prototype 0.3

## What this build actually does
- Runs a configurable fixed-position tap test only while the configured Eatventure app is foreground.
- Explains service connection, foreground package mismatches, tap success/failure, and stopping conditions in the app.
- Keeps a timestamped text log privately in app storage (`adventure_oracle_debug.txt`).
- **Refresh diagnostics** displays the latest log and service state in the app.
- **Share full debug report** opens Android's share sheet so you can save the TXT file to Files/Drive or send it to ChatGPT.
- **Copy report text** is a fallback if Android file sharing fails.
- No root, screenshot access, or storage permission needed.

## Build an APK using GitHub Actions (no Android Studio required)
1. Unzip this project and upload all contents to the root of a **private GitHub repository**, including `.github/workflows/build-apk.yml`.
2. Open **Actions > Build Android APK > Run workflow**, or wait for the push build.
3. Download the `AdventureOracleBot-debug-apk` workflow artifact, unzip it, and install `app-debug.apk` on the TCL 10 Pro.
4. Enable **Adventure Oracle Bot** under Android Settings > Accessibility. Android may display a sensitive permission warning.
5. Open the bot. For a first test, keep **5 taps / 1500 ms**, select a harmless upgrade button, and verify the game package name.
6. Press START, switch to Eatventure, and watch the five taps. Return to the bot, tap **Refresh diagnostics**.
7. If anything fails, tap **Share full debug report** and share the TXT file, or **Copy report text** and paste it into the chat.

## Debug messages
- `Accessibility Service connected`: permission and service activation succeeded.
- `Start rejected: service not connected`: Accessibility Service needs enabling.
- `Waiting for Eatventure`: the game is not recognized as foreground; inspect `foreground` and `expected` package names.
- `Completed tap N`: Android confirmed a gesture completed (not necessarily a game upgrade).
- `Android cancelled tap gesture`: Android interrupted the gesture.
- `Tap limit reached`: normal automatic stop.

## Limitations
This is a controlled tap and diagnostics prototype, **not** an autonomous station/city/Adventure player. No OCR or visual game-state recognition is included. The game package default is not independently verified. Android builds and actual TCL behavior are not tested in this environment; use GitHub Actions to compile and share build errors if any.

The bot cannot detect purchases or distinguish harmless and premium controls. Test supervised and do not point it at premium purchases, ads, or account controls. Automation may conflict with game rules.
