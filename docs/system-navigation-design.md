# System navigation design handoff

The approved UI for the UIS7870 Jeep Grand Cherokee WK2 is documented in the
[wiki design handoff](https://github.com/chrisuthe/7870-Projects.wiki/wiki/12-System-Navigation-Design).

Build only the approved variants:

- **2a:** a 1080 x 227 resting bar with six fixed functions and one
  temperature-adaptive shortcut.
- **1d:** a 1080 x 1693 climate page that opens over app content while the
  227 px bar remains visible.

The future HVAC app will be linked here when it is ready. The design prototype
is a visual reference, not production code; use this repository's established
module 7 subscriptions and vehicle-specific command table for wiring.

Implementation invariants:

- Keep `com.syu.air` running underneath.
- Do not grow the reserved navigation inset; ordinary apps cannot change the
  framework's 227 px `navigation_bar_height`.
- Every tappable region must be at least 96 px.
- Airflow controls are direct idempotent setters, not a cycle.
- Render vehicle-reported state rather than optimistic local state.
- Climate power-off is press-and-hold only.
- Omit controls for hardware that is not fitted.

See the wiki page for the complete geometry, interaction rules, state mapping,
colors, typography, and asset guidance.
