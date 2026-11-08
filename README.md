# Adventure Oracle v0.7 — Verified Actions Alpha

Based on v0.6. OCR and blue-button recognition remain experimental.

Changes:
- After each unlock, station upgrade, ordinary coin upgrade, renovation or welcome action, compare the next OCR result and sampled screen fingerprint.
- Distinguish Android gesture completion from game-confirmed changes in logs.
- Pause after two consecutive unconfirmed actions instead of repeating taps blindly.
- Log verification counts in STOP event.
- Disable arbitrary fallback station tapping.

Limitations: verification is heuristic. Animated game screens may change visually without purchase success; text recognition may miss level changes. The bot does not guarantee autonomous progression, and some states may require manual navigation. No premium/ad purchases intended. Default action limit for fresh installs is 12; existing settings may retain their previous value. Keep testing supervised.

Build: upload extracted project folders/files to repository root; GitHub Actions assembles debug APK. No Android build was run in this environment.
