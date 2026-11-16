# Metadata source decision

Use Steam directly for public game metadata. SteamDB is an explicit external link, not a data source.

Verified the public Steam Store `appdetails` endpoint with AppID 620. Its response includes `name`, `short_description`, `header_image`, and `website`. The canonical store URL is constructed from the validated unsigned 32-bit AppID. The header image is used as the Focus hero cover; this is not a promise of a separate library-hero asset.

This endpoint is accessible without a key but is not documented as a stable Steamworks API contract. Cache results, refresh explicitly, isolate parsing and requests, and keep the ability to add unpublished/unavailable apps by AppID and manual name. Never attach the financial credential to this request. A public store response does not prove ownership or access to private wishlist reports.

SteamDB's FAQ states that an API may be offered through future developer/publisher partnerships and prohibits automated scraping. Therefore it is unsuitable as a general unauthenticated metadata backend for this prototype.

Sources: [Steam Store response for AppID 620](https://store.steampowered.com/api/appdetails?appids=620&l=english), [SteamDB FAQ](https://steamdb.info/faq/), [documented Steam IStoreService](https://partner.steamgames.com/doc/webapi/IStoreService).
