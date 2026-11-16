# Design study

Status: Focus (direction C) was selected by the user. A native Android personal prototype now implements its game-first structure, Material navigation, and light/dark/system appearance.

The browser study at `prototypes/wishlist-ui/` compares four Android-oriented experiences using one shared synthetic dataset. Its surrounding comparison interface is an evaluation tool, not part of the Android app.

| Direction | Organization | Surface / primary / container / text | Signature |
| --- | --- | --- | --- |
| A · Pulse | Portfolio summary, history, game list | `#f3f7f6` / `#236d54` / `#d9eadd` / `#19352f` | Portfolio balance history connected to latest movement |
| B · Ledger | Date interval, daily bars, comparison table | `#f9fafc` / `#254d8d` / `#e2eafa` / `#253850` | Games compared within the same reporting window |
| C · Focus | Game picker, artwork, history, milestone | `#201f2b` / `#d7c3fa` / `#40364f` / `#f0edf7` | A selected game anchors the entire report |
| D · Signal | Latest-report summary and update timeline | `#fbf5eb` / `#94502d` / `#f1dfc7` / `#443428` | Reporting data read as a daily briefing |

Use a system sans stack, tabular numerals in data, consistent authored outline SVG icons, and role-based color variables. The prototype uses CSS pixels; a native implementation must translate these into Material type, shape, spacing, and color roles, support system font scaling, and validate on Android hardware/emulator.

Common navigation: main view, notifications, widget, settings. Game reports and setup are secondary flows with a back action. Reduce Motion is respected in the browser study. Controls are functional simulations; no browser behavior is evidence of actual Android scheduling, notifications, or widgets.

Illustrations are original code-authored SVG assets for fictional games. No external fonts, image hosts, trackers, or runtime dependencies are required by the study.

The four original studies remain available as comparison references. Focus is the selected direction based on the user's explicit choice in conversation.

## Focus appearance

The default appearance follows the device preference. Users can explicitly choose Light or Dark in Settings. The design-study sidebar offers the same control for quick comparison. All Focus screens and its widget preview use the resolved appearance. URL parameter `theme=system|light|dark` makes the preview shareable and reload-stable without storing preferences or credentials.

The native app persists appearance in local preferences and uses Android configuration changes for system mode. Native widgets use day/night resources in system mode and explicit palettes for manual overrides. UI text follows system font scaling. Native screenshots are kept separately under `captures/android/`; browser captures are not evidence of Android behavior.

| Role | Light | Dark |
| --- | --- | --- |
| Canvas | `#faf8fd` | `#201f2b` |
| Primary text | `#30283c` | `#f0edf7` |
| Secondary text | `#6c6178` | `#bcb7ce` |
| Primary action / chart | `#74539a` | `#d7c3fa` |
| On-primary | `#ffffff` | `#302140` |
| Container | `#e9dff4` | `#40364f` |
| Elevated surface | `#ffffff` | `#302b3d` |
| Outline | `#ded7e8` | `#494553` |
| Selected chip | `#e4d5f1` | `#534266` |
| Positive data | `#306c43` | `#b7d7b4` |

Light preserves the violet identity with a pale canvas and darker action colors; dark retains the original layered violet surfaces. Game artwork keeps its original colors in both schemes. Native form controls inherit the resolved browser color scheme. Native implementation must use Android theme preferences rather than browser media queries and persist the user's selection on-device.
