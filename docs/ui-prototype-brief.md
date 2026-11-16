# UI prototype brief

## Scope

Operate mode. Four disposable, code-led browser previews of an Android wishlist-monitoring app. The user requested Impeccable and a choice of four directions before implementation. The previous conversation establishes the primary user, personal-first distribution path, manual AppIDs, financial-key entry, reporting charts, configurable notifications, and home-screen widgets.

The question is which information hierarchy best serves the owner's daily use, not which color palette to apply to an identical layout.

## Direction contracts

- Pulse: orient around the portfolio. A reporting date, derived balance, history, interval metrics, and game list lead into game detail. Restrained green surfaces suit a quick daytime check. Its signature is portfolio history; its tradeoff is obscuring individual variation until the game rows are read.
- Ledger: orient around a reporting interval. A blue reporting header establishes scope, followed by daily activity and a shared comparison table. Its signature is cross-game comparison; density is its tradeoff.
- Focus: orient around one launch. A persistent game picker precedes artwork, selected-game data, history, and milestone progress. A dark violet scheme supports evening checks. Its signature is the game-scoped working area; portfolio checks require switching.
- Signal: orient around the latest update. A warm surface and dated summary lead into a timeline of additions, progress, and other games. Its signature is briefing-style reporting; historical analysis requires opening a report.

Each direction supports game details, intervals and custom dates, notification preferences, a widget preview, AppID setup, cached/offline presentation, and an empty portfolio. No winner is assumed. All displayed data and artwork are synthetic.

## Skill execution

Read Impeccable 4.9.2 from the upstream repository, including new-work, new-work-world, Android, Operate, and craft-floor guidance. Engine context and concept-seed could not execute because its binary was absent and its default cache was not writable. Proceeded with direct project context and written guidance; no engine assignment is claimed. The user's explicit four-prototype comparison takes precedence over a skill-generated selection tournament. The local prototype skill supplies the shared route, URL variant, and floating switcher pattern.

## Validation evidence

Playwright with an isolated headless Chrome instance checked all four flows: custom reporting intervals, metric selection, alert thresholds, widget game/metric configuration, simulated widget-to-report navigation, offline cache, empty portfolio, AppID setup preview, and in-memory direction selection. No JavaScript errors were observed.

Checked document overflow at widths 320, 390, and 768; no horizontal overflow was observed. Captured the comparison and individual directions at desktop width 1512 and mobile width 390. Visually inspected comparison, Pulse, Ledger, and mobile Focus/Signal, and adjusted compact preview height and Ledger density to expose more useful content.

Captures live in `prototypes/wishlist-ui/captures/`. Native Android validation remains outstanding; these screenshots are browser previews, not emulator captures. Accessibility verification here is limited to implementation inspection, contrast-oriented palette selection, keyboard affordances, and layout checks; no formal accessibility audit is claimed.

## Selected direction and appearance refinement

The user selected Focus and requested light and dark themes. Focus is now the default preview, with a System / Light / Dark selector and the equivalent Settings appearance preference. The URL preserves the theme; other prototype state stays memory-only. Live system changes update the app and widget preview without discarding game or date-filter state.

Browser verification passed for both themes on every Focus screen, the simulated home widget, live system preference changes, manual overrides, theme URL reload, and widths 320, 390, and 768. Inspected both rendered desktop themes. Measured light-theme contrast on the rendered preview: primary text 13.32:1, secondary text 5.50:1, badge text 10.92:1, and chart line 5.78:1. These are selected pairs, not a complete accessibility audit.

New captures use the `focus-light` and `focus-dark` filename prefixes. The original four-direction captures document the earlier comparison round.
