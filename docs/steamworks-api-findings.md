# Steamworks API findings

Verified against official documentation on 2026-11-16.

## Wishlist reporting

`IPartnerFinancialsService/GetAppWishlistReporting/v001/` requires a publisher key with Financial permissions, an AppID, and a GMT calendar date. It returns daily additions, deletions, purchases, and gifts, with country and language breakdowns. The documented schema does not expose an outstanding wishlist balance.

Valve documents refreshes at least daily; this provides no guarantee of immediate per-wishlist events. Notifications should describe changes detected during synchronization.

`app_min_date` identifies the earliest available reporting date. Out-of-range responses contain that field without a report. Valve recommends querying today and yesterday; older closed dates need not be repeatedly fetched.

Before promising an exact outstanding total, validate history completeness and movement semantics against a real Steamworks dashboard. Summed additions alone are not the outstanding balance. A verified baseline may be necessary.

Source: [Steamworks financial API](https://partner.steamgames.com/doc/webapi/IPartnerFinancialsService#GetAppWishlistReporting).

## Game discovery

The financial documentation directs app enumeration to `ISteamApps/GetPartnerAppListForWebAPIKey` using a normal, non-Financial publisher key. Do not assume the financial key alone supports discovery.

Version 2 returns app names and supports filtering by application type. Its documentation explicitly requires calls from a secure server and forbids direct client calls. Automatic discovery therefore needs a server-side integration.

Source: [Steamworks app discovery API](https://partner.steamgames.com/doc/webapi/ISteamApps#GetPartnerAppListForWebAPIKey).

## Manual AppIDs and direct device access

Manual AppIDs eliminate discovery and its separate ordinary publisher key. The wishlist reporting request needs only the financial key, AppID, and reporting date.

This does not establish official support for direct mobile access: Valve's general authentication documentation describes publisher keys as intended for requests originating from secure publisher servers. Financial keys are special publisher credentials and expose partner-wide financial data. User entry avoids bundling a shared secret into the APK, but does not resolve that server-origin guidance. A successful device request would demonstrate technical connectivity, not Valve approval of this architecture. Confirm this user-supplied-key scenario with Valve before presenting a serverless public release as supported.

Sources: [Web API authentication](https://partner.steamgames.com/doc/webapi_overview/auth#publisher-keys), [Financial API overview](https://partner.steamgames.com/doc/webapi/IPartnerFinancialsService).
