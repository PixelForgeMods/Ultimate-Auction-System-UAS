# Admin Guide

This page covers operator and moderator workflows. Players should start with the [Player Guide](Player-Guide.md).

## Access

UAS admin commands and the admin dashboard use `admin.statusPermissionLevel`, default `2`. Players without that permission can still use normal `/ah` features unless restricted by an auction-house ban.

Open the admin dashboard:

```text
/uas admin
/uas admin gui
```

Run the health check:

```text
/uas status
```

## First Server Checklist

1. Install UAS and UBS on the server and clients.
2. Start the server once to generate config.
3. Run `/uas status`.
4. Confirm UBS is loaded, UBS version is `1.2.0` or newer, UBS server API is available, config loaded, and storage is healthy.
5. Review economy settings before players create live auctions.
6. Decide whether to configure banned auction entries before opening the auction house.
7. Decide whether to allow hidden reserve prices with `marketplace.enableReservePrices`.

## Admin Dashboard Sections

- Overview: quick health cards for auctions, economy, settlement, and moderation
- Auctions: inspect auctions, bid history, force-cancel, and retry failed settlements
- Players: inspect player auction activity and apply or revoke auction-house bans
- Economy: 24h, 7d, and all-time sales, fees, taxes, failed settlements, top sellers, categories, and items
- Moderation: failed settlements and active auctions that now match banned entry rules
- Suspicion: non-punitive evidence for rapid bidding, repeated bidder pairs, seller self-bids, and repeated cancellations
- Banned Items: live editor for exact item, tag, and mod restrictions
- Recovery: items moved to admin recovery by force-cancel actions
- Audit: admin dashboard actions and results

## Player Auction-House Bans

The Players section can block specific actions:

- Create
- Bid
- Buyout
- Watch notifications

Bans can be permanent or expire at an ISO date-time such as:

```text
2026-06-06T18:30
```

Use a clear reason. The reason is stored in admin data and helps later reviewers understand why the restriction exists.

## Banned Auction Entries

Use Banned Items in the admin dashboard or edit `limits.bannedAuctionEntries` in config.

Supported forms:

```text
minecraft:bedrock
#minecraft:shulker_boxes
@minecraft
```

- Exact item ids block one item.
- Tag entries block every item in the tag.
- Mod entries block every item from a mod id.

New restrictions apply to new listing attempts immediately after dashboard save or config reload. Existing active auctions are not auto-cancelled. Instead, they appear in the Moderation queue so admins can inspect and force-cancel if appropriate.

## Force-Cancel And Recovery

Force-cancel requires an audit reason and can target any non-final auction.

Return mode:

```text
/uas admin forcecancel <auctionId> return <reason...>
```

Recover mode:

```text
/uas admin forcecancel <auctionId> recover <reason...>
```

Both modes refund the current highest bidder when needed. Return mode sends the escrowed item back to the seller through inventory or delivery storage. Recover mode moves the item into admin recovery storage for later review and release.

Use Recovery in the dashboard to release recovered items back to seller delivery storage.

## Settlement Failures

UAS moves auctions into `FAILED_SETTLEMENT` when it cannot safely finish a payout, refund, or tax transfer. Normal claims are blocked until settlement is recovered.

Manual retry:

```text
/uas admin settlement retry <auctionId>
```

The dashboard can also retry failed settlements. Settlement retry tools show the previous failure and proposed action before attempting recovery.

Config values:

- `settlement.retryAttempts`: automatic retry attempts, default `3`
- `settlement.retryDelaySeconds`: delay between attempts, default `60`
- `settlement.autoSettleExpiredAuctions`: whether UAS settles expired auctions automatically, default `true`

## Economy Reports

Commands:

```text
/uas admin report
/uas admin report day
/uas admin report week
/uas admin report all
```

Reports are based on persisted auction records and financial events. Completed sale volume uses successful payout events. Fees use successful listing and cancellation fee events. Taxes use successful `SALES_TAX` events. Broad reports show summaries and do not expose bidder details.

## Auction Data Export

Admins can export persisted auction history for external economy analysis:

```text
/uas admin export csv
/uas admin export json
/uas admin export csv custom-name.csv
```

Exports are written under the server/world `uas_exports/` directory. UAS sanitizes custom filenames, runs the file write asynchronously, and records an `AUCTION_EXPORT` audit entry. Export rows include auction ids, item ids/names, prices, reserve price/status, states, timestamps, seller/winner UUIDs, bid counts, and settlement references.

## Reserve-Price Auctions

Reserve-price auctions are controlled by `marketplace.enableReservePrices`, default `true`. Sellers can set an optional hidden reserve in the GUI or through API-created listings. Bidders see only whether the reserve has been met.

If an auction ends below reserve, UAS deposits the held highest bid back to the bidder account using a `RESERVE_REFUND` financial event, clears the winning bidder from the auction, and leaves the escrowed item claimable by the seller. If that refund cannot be completed, the auction moves to `FAILED_SETTLEMENT` for admin recovery instead of silently ending.

Admin inspect output shows the reserve amount and reserve-met status. Auction exports include `reserve_price` and `reserve_met` columns for offline review.

## Audit And Suspicion Signals

Suspicion signals are evidence only. UAS does not automatically punish, cancel, or ban players from suspicion checks.

Current signals include:

- Rapid bid escalation
- Repeated bidder pairs
- Seller self-bid attempts or accepted self-bids
- Repeated seller cancellations

Use these signals to decide whether to inspect auctions, apply an auction-house ban, or force-cancel a listing.

## Config Reload

Use the standard NeoForge config reload flow after editing `ultimate_auction_system-common.toml`.

Reloaded settings apply to future actions. Existing auctions keep their escrowed item and original end time. Banned entry changes affect new listings immediately and flag existing matching active auctions for moderation review.

## Storage Notes

UAS persists auction records, delivery storage, admin data, recovery entries, financial events, bid history, and audit data through Minecraft SavedData. If `/uas status` reports skipped, repaired, or failed records, review the server log before allowing live auction activity to continue.
