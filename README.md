# Adventure Oracle v1.0 — Panel Navigation Alpha

Changes from v0.9:
- Removed Android Back from panel dismissal. The game accepts outside taps instead.
- Dismissal tries at most three outside locations and stops with a diagnostic rather than looping indefinitely.
- Detects return to restaurant view and resets dismissal state.
- Faster rescan after coin purchases (650 ms).
- Cancelled/rejected gestures clear pending verification, avoiding false confirmations.
- Main menu open attempts have a recovery cap.
- Retains Sequential, First & Last, Balanced, Adaptive; Normal, Fast, Turbo.
- Auto-renovate remains off by default because complete station MAX detection is not reliable.

Build with GitHub Actions, then test supervised with a 15–20 action limit.
Not compiled or device-tested here.
