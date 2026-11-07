# Adventure Oracle v0.4 vision beta (Android 11+)

This is an **experimental supervised prototype**, not a complete autonomous restaurant player.

## Implemented
- Uses Android Accessibility screenshots and on-device Google ML Kit OCR (no cloud image uploads).
- Restricts actions to the configured Eatventure foreground package.
- Detects unlock text and attempts a tap; recognizes MAX station and dismisses its popup.
- Recognizes Renovate screen but **does not automatically confirm renovation**; this is intentionally disabled pending reliable enabled-button detection.
- Detects upgrade panels but **does not automatically upgrade** pending reliable button location and affordability checks.
- Logs OCR samples and decisions for debugging; preserves Start/Stop and export diagnostics.
- Stops after configurable tap limit; returning to the app stops the bot.

## Known limitations
- OCR can miss text or locate text incorrectly. The unlock tap offset is experimental and may hit the wrong location. Supervise all tests.
- No reliable navigation across stations, completion or automatic renovation yet.
- Android 11+ required for accessibility screenshots.
- No APK has been compiled or installed/tested for this source release; GitHub Actions must build it.

## Build
Replace the existing project source with the contents of this folder (keep your `.git` folder), commit and push. The included GitHub Actions workflow builds a debug APK and uploads it as an artifact.
