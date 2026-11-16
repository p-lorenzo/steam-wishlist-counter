# Steam Wishlist Counter

Status: the native personal Android prototype is implemented under `app/`, with Focus selected from the historical browser studies. It uses manual AppIDs, public Steam metadata, and direct reporting requests with a user-supplied encrypted financial key. Public-release support for that credential model remains unresolved. Real private reporting still requires validation with an authorized key.

## Product direction

A small Android app for Steamworks developers, starting with personal use, followed by a public open-source release, F-Droid distribution, and potentially Google Play. Store admission is a later validation step, not an assumed outcome.

The primary experience is opening the app or looking at a home-screen widget to understand wishlist performance for each managed game.

## First release

- Setup: enter a financial key and manual AppIDs for the personal native prototype. The browser UI study accepts no real credentials. Automatic discovery and backend setup are deferred.
- Games: show each game's latest available metrics, reporting date, and sync status.
- Game detail: daily additions, removals, purchases, and gifts; date presets of 7, 30, and 90 days plus a custom inclusive range. Clearly distinguish activity from outstanding wishlists.
- Notifications: per-game enablement, minimum new-addition threshold, daily digest, quiet hours, and milestone alerts when a reliable outstanding balance exists.
- Widget: select a game per widget, show its verified balance when available, recent additions, reporting date, and stale-data indication; tapping opens that game's detail.
- Settings: synchronization preferences, credential replacement, data export, and complete local-data deletion.

## Correctness before presentation

See [verified API findings](steamworks-api-findings.md). The first integration milestone must compare a real report with the Steamworks dashboard. Do not expose a current-total counter until its derivation has been verified. If full history cannot establish a trustworthy total, support a dated user-provided baseline and explicitly label the resulting balance as derived. Never treat missing reports as zero activity.

Synchronize recent dates repeatedly while they may still change. Backfill older dates in resumable batches and persist progress. A chart must expose gaps and incomplete history. Dates represent reporting days rather than the phone's local calendar boundaries.

Initial history import must not emit historical notifications. Repeated synchronization of the same data must not emit duplicates. Changes to a provisional day's additions may produce an alert only for newly observed additions, with a persisted watermark. Track alert delivery independently of import progress so an interrupted sync does not silently consume alerts. Describe alerts as detected updates, without promising immediate delivery for individual wishlist events.

## Proposed architecture

Implemented Android stack: Kotlin and Jetpack Compose for screens; SQLiteOpenHelper for cached reporting data and atomic notification watermarks; WorkManager for deferrable synchronization; RemoteViews for native widgets; Android notifications for alerts. One application module, with separate reporting, persistence, synchronization, credential, widget, and presentation files. Room and Glance from the initial proposal were not required for this small first implementation.

Personal experiment: the Android app would call wishlist reporting directly, keeping the user-entered financial key encrypted with a key protected by Android Keystore. Manual AppIDs remove discovery. This experiment does not establish official Valve support for public direct-client access; see the API findings.

Deferred alternative: a small self-hostable service could store Steam credentials securely, cache reports, and expose authenticated read operations to paired devices. That design would let the Android app store a revocable device token instead of publisher credentials. Revisit before public distribution if required by Valve's guidance or the chosen discovery flow.

Start with one partner on one device. Keep partner isolation explicit before opening any future hosted service to other users. Public distribution does not itself require operating a shared credential-hosting service.

Avoid mandatory Google Play Services, proprietary analytics, advertising, and Firebase notification delivery. Periodic local synchronization is sufficient for the initial notification model, with timing subject to Android scheduling. Keep the backend URL configurable for self-hosting.

Never log credentials, authorization headers, or upstream URLs containing keys. Redact upstream errors before exposing them to clients. Exclude local device tokens from backups, provide revocation, and remove credentials and data when disconnecting.

## Implementation milestones

1. Use the selected Focus direction with light, dark, and system appearance; validate direct upstream authentication, one real game's reporting history, and total-balance semantics for personal use.
2. Build native Android setup with user-supplied credentials and AppIDs, a game list, local persistence, and manual sync.
3. Add chart ranges and resumable historical import.
4. Add configurable notifications and independently configurable widgets.
5. Prepare open-source licensing, build instructions, release signing, dependency review, and distribution metadata. Validate F-Droid and Google Play policies at submission time.

## Validation targets

Exercise reporting gaps, partial days, inclusive date ranges, process interruption during import, duplicate syncs, offline launch, revoked credentials, denied notification permission, quiet hours, and multiple widget instances. Verify widget and app show the same reporting date and data. Test on a real device before claiming background behavior works.

## Platform references

- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance): native Kotlin widget framework.
- [F-Droid inclusion policy](https://f-droid.org/docs/Inclusion_Policy/): open-source licensing, build transparency, and dependency restrictions must be considered before release.
