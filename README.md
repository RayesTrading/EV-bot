# Adventure Oracle v1.1 — Menu Exit & Restaurant Map Alpha

Changes from v1.0:
- Main Upgrades purchases capped at 8 per batch, then forced menu exit.
- Once menu exit is requested, no more purchases or menu scrolling until it closes.
- Tries five outside-panel tap locations; stops and requests calibration if all fail.
- Main Upgrades cooldown after closing to avoid immediate re-entry.
- Restaurant map pans periodically and when the same station arrow repeats or no arrows are visible.
- Alternates forward and reverse pans for large restaurants.
- Keeps station hold, arrow detection, strategies, premium safety checks and conservative auto-renovate default OFF.

Known limitations: screen coordinates and OCR are heuristic; not tested on a physical device. Restaurant swipes might require adjustment for the game camera. Do not run unattended.

To build: upload extracted project contents to the GitHub repository, run the Android APK GitHub Actions workflow, install the resulting app-debug.apk.
