# Sparse wishlist chart correction

## Reproduction and diagnosis

The selected Nov 8–16 interval contained five cached reports with additions 3, 2, 3, 1, and 2 on Nov 8, 9, 10, 11, and 15. The old drawing code started a new path whenever adjacent reports were not on consecutive calendar days. The last point therefore became isolated even though the total additions were correct.

Extracted the actual drawing connections into `ChartData.kt` without changing behavior and added a minimal two-point regression (Nov 11 and Nov 15). Ran `:app:testDebugUnitTest --tests '*ChartDataTest'`: one test failed because the expected connection was absent. After correction, the test passes and the five-point fixture has four connections, exactly one spanning missing days, and an unchanged sum of 11 additions.

Considered drawing segmentation, absent upstream reports, and incomplete synchronization/parsing. A temporary opt-in Android instrumentation probe ran only on the owner's Pixel 10a. It reused the existing credential inside the target application process; no credential or authentication header was exported or logged. Requests for Nov 12, 13, 14, and 16 all returned HTTP 200 with only `app_min_date` and no `wishlist_summary`. Thus these are absent reports, not values dropped by the cache or parser. The temporary probe source, dependencies, and installed test package were removed afterward.

## Behavior

- Connect all available points. A dashed segment distinguishes a connection that crosses dates with no report; it is not a claim that intermediate values were measured or zero.
- Draw the visible range from the later of the requested start and first known positive addition in cached game history. Preserve later zero-activity periods and never expand the requested interval. All-zero/empty histories retain their selected range.
- Tap or horizontally drag to snap the cursor to a reporting day. Show the date in GMT and additions for that day. Net/balance views also show the corresponding metric. On the `Additions` and `Net change` views, a missing report says “No report available”; on the `Balance` view, it shows the carried-forward cumulative balance alongside “No report available”.
- Enable the `Balance` chart automatically whenever cached reports exist (accumulating net changes from `0` starting at the earliest cached report), and support optional manual baseline anchoring in *Game details* (including today's count) to reconstruct both earlier and later daily balances.
- Expose previous/next-day accessibility actions and an accessible selection description. Horizontal scrubbing leaves vertical page scrolling available.
- Compute reporting coverage against the trimmed visible range. Keep interval totals and exported underlying reports unchanged.
- Display fractional axis ticks faithfully instead of truncating, for example, 1.5 to 1.

The first known cached positive addition is not proof of the first-ever wishlist when earlier history has not been imported. No outstanding-balance, import, or notification semantics were changed.

## Verification

Version 0.1.1. Thirteen unit tests cover reporting and chart rules; Android lint and debug APK assembly pass. Physical Pixel 10a validation covers the exact custom interval, first-addition trim, available/unknown-day tooltips, and horizontal scrubbing. Screenshots are local under `captures/android/chart-*.png`. No Pixel 6a operation or financial-key replacement is part of this change.
