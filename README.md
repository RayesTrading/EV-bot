# Adventure Oracle v0.5 (Android 11) — supervised beta

Fixes v0.4's incorrectly offset Unlock taps (now targets OCR label center), adds one-tap calibration, optional calibrated upgrade actions, screenshot/foreground logging and gesture confirmation.

## Test first
1. Upload this project's contents (including `app/`) to your GitHub repository and commit.
2. Download the APK artifact from the successful GitHub Actions run.
3. Enable Accessibility. Open bot, set X/Y percentages corresponding to a *visible, harmless* game button. Save.
4. Tap **ONE TEST TAP**, immediately switch to Eatventure, and verify the game visibly responds. An Android `TAP completed` log only proves gesture dispatch, not that the game used it.
5. After confirming, enable auto-upgrade ONLY when the station upgrade panel is open and X/Y point at its upgrade button. Start OCR BOT; supervise and stop if wrong.

## Limitations
- This is not a fully autonomous restaurant-completion bot. MAX stations, scrolling, offline earnings and Renovate require manual handling.
- OCR may mistake buttons, screen sizes vary, and purchases are never intentionally automated.
- No APK was compiled locally; GitHub Actions must build it.
