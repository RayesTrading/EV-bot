# Adventure Oracle v0.6 Autonomous Alpha

Android 11+ experimental on-device OCR/visual state machine for Eatventure. No APK has been compiled or device-tested by the author. Use GitHub Actions workflow to compile and download APK.

## Combined features
- Screenshot capture and ML Kit OCR every cycle.
- Coin-upgrade menu: OCR price lines and blue-button sampling; attempts top visible coin upgrade, then scrolls.
- Station panel: OCR Level/MAX and blue-price candidate; optional calibrated fallback.
- Unlock: targets recognized text bounding box.
- Renovate: requires Before, After, Renovate and a blue lower coin button.
- Welcome screen after restaurant/city transition: attempts recognized blue Continue/Play/Start.
- Loading: waits; offline popup: only non-ad Collect.
- Screenshot/OCR/gesture diagnostics, action limits, premium/ad exclusions.

## Known limitations / cautions
- **Not verified as fully working**. UI screenshots alone cannot establish all screen states. OCR text detection may miss buttons or misread prices; color sampling can confuse UI elements.
- Gesture callback confirms Android dispatch, **not game response**. Review action logs and observe the game.
- Station selection and exploration are heuristic; scroll behavior is incomplete. Some welcome popups may need manual dismissal.
- In-game coin expenditures and renovation are enabled in AUTO mode; supervise. Ads, gems, and real-money purchases should never be automated.
- When returning to the bot app, automation stops. On TCL, use Home then launch Eatventure icon to trigger package recognition.
- The previous Kotlin duplicate-class fix is embedded in app/build.gradle, so don't replace it with older versions.

## Build
Push all extracted project files to GitHub main. GitHub Actions > Build Android APK > newest run > artifact `EV-bot-APK`. The artifact contains `app-debug.apk`.
