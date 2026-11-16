# Wishlist UI study

Disposable browser prototypes for choosing an Android UI/UX direction. These are not a native app and do not connect to Steam.

Run from the repository root:

```sh
python3 -m http.server 4173 --bind 127.0.0.1 --directory prototypes/wishlist-ui
```

Open http://127.0.0.1:4173/ for the selected Focus direction. Use `/?view=compare` to revisit all four, or use:

- `/?variant=A` — Pulse: portfolio overview and balance history.
- `/?variant=B` — Ledger: interval activity and cross-game comparison.
- `/?variant=C` — Focus: single-game view and milestones.
- `/?variant=D` — Signal: latest-report briefing and activity feed.

Use the bottom switcher or left/right arrow keys to change direction. URL parameters preserve the direction, not the temporary app state. Each direction has independent memory-only preferences. Reloading resets simulated application state.

Focus supports `theme=system`, `theme=light`, and `theme=dark`. The default follows the device preference, including changes while the prototype is open. Use the Appearance selector beside the phone or Settings → Appearance inside the phone. The choice also updates the widget preview. Theme is stored only in the URL, so reloading that URL preserves appearance while resetting demo game/filter state.

- `/?variant=C&theme=light` — Focus light.
- `/?variant=C&theme=dark` — Focus dark.
- `/?variant=C&theme=system` — Focus follows the device.

Interactive flows: report ranges and custom dates, per-game details, metric selection, notification configuration and inline previews, widget game/metric selection, simulated home-screen widget, offline and empty states, synthetic CSV export, and an AppID setup preview. AppID submission is a simulation and does not add a real monitored game. Do not enter real credentials.

All three games, their artwork, and their data are synthetic. Financial totals are shown as derived balances, not verified Steam outstanding totals. There are 90 consecutive GMT reporting days ending November 15, 2026. Additions, removals, purchases, net movements, and derived balances share one dataset across directions.

The browser previews are designed around Android Material navigation and touch-target conventions. They do not validate native Android behavior, actual home-screen widgets, notification permissions, system font scaling, or background scheduling. These need emulator/device validation after native implementation.

The user selected Focus in conversation, with light and dark themes. Other directions remain available for reference; their “Choose this direction” buttons only mark a choice in memory and do not send a message or write project configuration.

Guidance: Impeccable 4.9.2, retrieved from https://github.com/pbakaus/impeccable, and the local prototype skill. The Impeccable engine could not run in this environment; its design, Operate, Android, and craft-floor references were read directly. No engine-generated assignment or audit is claimed.
