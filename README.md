# Adventure Oracle v0.8 — Strategy Controls (experimental source)

## New settings in Android app

- Strategy: **Sequential**, **First & Last**, **Balanced**, **Adaptive**.
- Upgrade speed: **Normal** (450 ms), **Fast** (950 ms), **Turbo** (1700 ms) press durations.
- Automatically buy ordinary affordable main upgrades (checkbox).
- Allow auto-renovation only after heuristic completion checks (checkbox, OFF by default).

Choose settings in the app, save, and start. Changing them requires stopping and restarting the bot (returning to the app stops the service).

## How it works

The station upgrade panel uses a sustained press on a recognized blue coin-price button, followed by OCR verification. When no affordable station purchase is detected, the bot attempts to close the station panel and navigate to the bottom-right main Upgrades control. In the restaurant view it searches for red arrow-like pixel clusters and chooses targets according to the selected strategy. Strategy differences are implemented in `chooseArrow()`; these are heuristic screen positions, **not reliable station identities**. The existing OCR rules handle Unlock, coin upgrades, loading, welcome and renovation dialogs.

## Important limitations

- **Not verified on a real device or compiled in this environment.** The source ZIP is for a GitHub Actions build.
- The red-arrow detector is a coarse visual heuristic. It can mistake other red objects for arrows. Stop the bot if it selects the wrong thing.
- Bottom-right Upgrades navigation uses a relative screen location and may need calibration.
- **The bot does not know how many stations exist in a restaurant**. It cannot yet reliably establish that *all* stations are MAX. Therefore auto-renovation is OFF by default; even if enabled, the gating is heuristic, not proof of full completion.
- Rapid holds are followed by OCR checks, not continuous pixel monitoring while a finger is held. Do not assume grey button detection is instantaneous.
- Existing 5-action limit may carry over from saved settings; raise it gradually after supervised tests.
- Do not use with real-money purchases, gems, or advertisements. The existing premium-screen text blocker is retained but imperfect.

## Build

Extract ZIP and upload the **contents** to the repository root (including `.github/`), then run GitHub Actions `assembleDebug` workflow. No APK is included.
