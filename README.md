# Adventure Oracle v0.9 — Navigation Alpha

Experimental changes over v0.8:
- Red station arrows require a red ring AND a white upward-arrow shape (not just red pixels).
- Search excludes top notification badges and bottom navigation.
- Automatically taps below a detected station arrow.
- When station upgrades are grey, immediately attempts to close the station panel using Android Back.
- Main upgrades use list-change evidence and periodically exit to revisit stations.
- Four strategies and three speeds remain selectable in the app.

## Important limitations
- Not compiled or tested on a physical phone here.
- Arrow shape and Android Back behavior must be tested on your actual game screen.
- Android Back may not dismiss every game popup. If it exits the game, stop and report the log.
- Main Upgrades menu detection, restaurant scrolling, MAX tracking and renovation are still experimental.
- Auto-renovation remains OFF by default; do not enable until all-station completion can be verified.
- Use a supervised first run and a 10–20 action limit.

Build with GitHub Actions: upload extracted project files to your repository and run the existing workflow.
