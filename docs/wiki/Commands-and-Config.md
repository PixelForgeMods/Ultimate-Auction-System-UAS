# Commands and Config

This page is the quick reference for commands, GUI behavior, and important server config.

## Player Commands

Open the auction house GUI:

```text
/ah
```

List active auctions in chat:

```text
/ah list
```

View one auction in chat with clickable shortcuts:

```text
/ah view <auctionId>
```

Show your own auctions, optionally filtered and paged:

```text
/ah mine
/ah mine active
/ah mine sold
/ah mine cancelled
/ah mine expired
/ah mine active 2
```

Show your auction participation stats:

```text
/ah stats
```

Show the public marketplace leaderboard when the server owner enables it:

```text
/ah leaderboard
```

Create an auction from the item in your main hand:

```text
/ah create <Starting Price> <Duration Hours> <Description>
/ah create <Starting Price> <Duration Hours> buyout <Buyout Price> <Description>
```

Confirm or discard the pending command-created listing:

```text
/ah confirm
/ah discard
```

Bid on an active auction:

```text
/ah bid <auctionId> <amount>
```

Preview and confirm a buyout:

```text
/ah buyout <auctionId>
/ah buyout confirm <auctionId>
```

Cancel your own active no-bid auction:

```text
/ah cancel <auctionId>
```

Claim a won item or an expired unsold seller return:

```text
/ah claim <auctionId>
```

The GUI is preferred for most player workflows because it includes inventory slot selection, bundle creation, optional buyout, optional hidden reserve price, optional sealed-bid format, date/time picking, account selection, listing-fee preview, bid review, delivery storage, relisting expired unsold auctions, and validation before submission.

Server owners can place Auction Teller NPCs in hubs or marketplaces. Right-clicking an Auction Teller opens the same Auction House GUI as `/ah`. Existing `ultimate_auction_system:auction_terminal` blocks still work for older worlds. Disabling terminal/teller access does not disable `/ah`.

## Auction House GUI

Player tabs:

- Dashboard: active, ending soon, claimable, watching, and history sections for the current player
- Browse: active listings across the server
- My Bids: auctions where the player has bid, is winning, won, or can claim
- My Auctions: auctions created by the player

Supported Browse filters:

- Search text: item display names, item registry ids, bundled item names/ids, seller names, seller UUIDs, descriptions, and auction IDs
- Category: all, weapons, armor, tools, consumables, blocks, or misc
- Mod: all mods or a specific mod id from active listings
- Price range: current bid or positive buyout price
- Time left: any time, under 1 hour, under 24 hours, or under 7 days
- Sort: newest, ending soon, highest bid, lowest price, or buyout price

The Filters modal can save named Browse presets. Saved searches persist per player, include search text, category, mod, price, time-left, and sort settings, and can be run, renamed, or deleted from the GUI.

Bid and raise-bid modals use a two-step flow. The first action refreshes the UBS account snapshot and shows the selected account, balance, bid amount, expected remaining balance, and warnings. The second action submits the bid to the same server service used by `/ah bid`.

Reserve-price auctions keep the reserve amount hidden from bidders, but auction rows show whether the reserve has been met. If an auction ends below reserve, UAS refunds the held highest bid and lets the seller claim the unsold item return.

Sealed-bid auctions hide active bid amounts and bid history from non-admin players until the auction ends. A bidder can still see and raise their own sealed bid. At close, UAS chooses the highest valid sealed bid; equal sealed bids keep the earliest accepted bid as winner.

Create-auction supports one inventory slot or a bundle of up to 18 stacks. Bundle auctions show a bundle title and a contents preview. UAS validates the selected item contents again on confirm before escrow. When reserve prices are enabled, the reserve must be at least the starting bid, and any positive buyout must be at least the reserve. When sealed-bid auctions are enabled, the create and relist modals can switch the listing format between normal and sealed bid.

Relist appears on expired unsold auctions owned by the current player. It reuses the old escrowed item contents and previous pricing fields, including any visible reserve price, lets the seller edit the new price/date/description, charges the configured listing fee again, creates a fresh active auction, and keeps the original expired auction as an audited history entry.

Auction stats track auctions listed, auctions won, gross sold value, gross spent value, and UUID-based marketplace ranks. Stats update from server-side listing activation and successful settlement events, not from client UI actions. `/ah stats` shows a player's own stats; `/ah leaderboard` shows top sellers and buyers only when enabled by config.

The GUI account selector lists the player's UBS accounts with account type, bank UUID prefix, balance, primary flag, and frozen status. A selected account UUID is sent with create, relist, bid, and buyout actions, then UAS re-checks ownership and fetches a fresh UBS snapshot before taking listing fees, bid escrow, or buyout escrow. If UBS cannot return the selected account snapshot, the action fails with a clear message instead of falling back silently.

Delivery Storage appears when an item cannot be placed directly into a player's inventory or when an admin releases a recovered item. Players can withdraw delivery items after making inventory space.

Auction events and chat list rows use clickable shortcuts with hover text. Buyout shortcuts always open a preview/confirm flow before spending money.

## Admin Commands

Show dependency, storage, config, and auction health:

```text
/uas status
```

Open the admin dashboard GUI:

```text
/uas admin
/uas admin gui
```

Inspect one seller's auction stats:

```text
/uas admin seller <player>
```

Show a persisted economy report:

```text
/uas admin report
/uas admin report day
/uas admin report week
/uas admin report all
```

Export auction history to a server-side CSV or JSON file:

```text
/uas admin export csv
/uas admin export json
/uas admin export csv custom-name.csv
```

Inspect a specific auction record and full bid history:

```text
/uas admin inspect <auctionId>
```

Retry a failed settlement:

```text
/uas admin settlement retry <auctionId>
```

Force-cancel a non-final auction with an audit reason:

```text
/uas admin forcecancel <auctionId> return <reason...>
/uas admin forcecancel <auctionId> recover <reason...>
```

`return` sends the escrowed item back to the seller through inventory or delivery storage. `recover` refunds the bidder when needed, then moves the escrowed item into admin recovery storage for later release.

## Admin Dashboard

The dashboard includes:

- Overview cards for auction, economy, settlement, and moderation health
- Auction inspection, bid history, force-cancel, and failed-settlement retry tools
- Player inspection with granular auction-house bans for create, bid, buyout, and notification actions
- Player delivery storage counts and compact previews for recovery checks
- Economy windows for 24h, 7d, and all-time auction activity
- Moderation queues for failed settlements and active auctions matching banned item rules
- Suspicion signals for rapid bid escalation, repeated bidder pairs, seller self-bids, and repeated cancellations
- Live banned auction entries editor
- Recovery storage for force-cancelled auctions held for admin review
- Audit log for dashboard admin actions

Auction exports are written asynchronously under the server/world `uas_exports/` directory. UAS sanitizes custom file names so exports stay inside that directory, includes item ids/names, prices, reserve price/status, states, timestamps, seller/winner UUIDs, bid counts, and settlement references, and records each export in the admin audit log.

## Admin Permission

`/uas status`, `/uas admin`, `/uas admin gui`, `/uas admin seller`, `/uas admin report`, `/uas admin export`, `/uas admin inspect`, `/uas admin settlement retry`, and `/uas admin forcecancel` use:

```text
admin.statusPermissionLevel
```

Default: `2`.

## Player Action Permissions

Player actions use separate Minecraft permission-level checks:

- `permissions.listPermissionLevel`: create/confirm/relist auction listings, default `0`
- `permissions.bidPermissionLevel`: place bids, default `0`
- `permissions.buyoutPermissionLevel`: buy out auctions, default `0`
- `permissions.cancelOwnPermissionLevel`: cancel your own no-bid auction, default `0`
- `permissions.claimPermissionLevel`: claim won or unsold auction items, default `0`
- `permissions.terminalAccessPermissionLevel`: open the Auction House through an Auction Teller or legacy auction terminal block, default `0`

`0` keeps the current everyone-can-use behavior. Higher values use standard Minecraft permission levels. Commands, GUI payloads, and public API player methods all call the same server-side checks. Admin and automation code paths that do not have a player must pass an explicit permitted/bypass flag.

Other mods can install a UAS permission hook through the Java API to override the config-backed permission-level check without bypassing UAS validation, settlement, or storage rules.

## Important Config Values

Config is generated in `ultimate_auction_system-common.toml`.

Economy:

- `economy.listingFeeRate`: fraction of starting bid charged when creating a listing, default `0.05`
- `economy.cancellationFeeRate`: fraction of starting bid charged when a seller cancels a no-bid auction, default `0.0`
- `economy.salesTaxRate`: fraction of final sale deducted from seller proceeds, default `0.05`
- `economy.salesTaxDestinationAccountUuid`: optional UBS account UUID that receives tax; blank means tax is recorded as a money sink
- `economy.minimumBidIncrementDollars`: minimum whole-dollar increase over current highest bid, default `1`

Bidding and limits:

- `bidding.allowSellerSelfBid`: whether sellers can bid on or buy out their own auctions, default `false`
- `limits.maxActiveListingsPerPlayer`: active listing cap per seller, default `25`
- `limits.minAuctionDurationMinutes`: minimum auction duration, default `5`
- `limits.maxAuctionDurationHours`: maximum auction duration, default `168`
- `limits.pendingListingConfirmationSeconds`: pending listing confirmation timeout, default `60`
- `limits.bannedAuctionEntries`: exact item, tag, or mod restrictions for new listings

Marketplace:

- `marketplace.maxSavedSearchesPerPlayer`: named Browse filter presets each player can save, default `12`
- `marketplace.enableLeaderboards`: expose `/ah leaderboard` top seller/buyer rankings, default `false`
- `marketplace.enableAuctionTerminal`: allow Auction Teller and legacy `ultimate_auction_system:auction_terminal` interactions to open the Auction House GUI, default `true`
- `marketplace.enableReservePrices`: allow sellers to set optional hidden reserve prices on new listings, default `true`
- `marketplace.enableSealedBidAuctions`: allow sellers to create sealed-bid listings, default `true`

Settlement:

- `settlement.requireUbsForListing`: require a usable UBS primary account before creating listings, default `true`
- `settlement.autoSettleExpiredAuctions`: settle expired auctions automatically, default `true`
- `settlement.physicalCashListingFees`: allow future command/API paths to pay listing fees with exact UBS bills/coins, default `false`
- `settlement.physicalCashBuyouts`: allow future command/API paths to pay buyouts with exact UBS bills/coins, default `false`
- `settlement.chequePayouts`: issue qualifying seller payouts as UBS cheques instead of direct account deposits, default `false`
- `settlement.chequePayoutSourceAccountUuid`: UBS account debited when UAS creates seller payout cheques, default blank
- `settlement.chequePayoutMinimumDollars`: minimum whole-dollar net payout that uses cheque payout, default `0`
- `settlement.chequePayoutIssuerPlayerUuid`: optional UBS cheque writer UUID, default blank
- `settlement.chequePayoutIssuerName`: display name stored as the UBS cheque writer, default `Auction House`
- `settlement.retryAttempts`: automatic settlement retry attempts, default `3`
- `settlement.retryDelaySeconds`: delay between automatic retry attempts, default `60`

Notifications:

- `notifications.maxWatchedAuctionsPerPlayer`: active watched auction limit per player, default `64`
- `notifications.endingSoonThresholdMinutes`: one-time watched auction ending-soon alert threshold, default `60`; set `0` to disable ending-soon alerts

Rate limits:

- `rateLimits.createCooldownSeconds`: per-player cooldown for preparing listings, default `5`
- `rateLimits.bidCooldownSeconds`: per-player cooldown for bids, default `2`
- `rateLimits.buyoutCooldownSeconds`: per-player cooldown for buyouts, default `2`
- `rateLimits.cancelCooldownSeconds`: per-player cooldown for seller cancellations, default `5`
- `rateLimits.searchCooldownSeconds`: per-player cooldown for listing/search refreshes, default `1`

Audit and moderation:

- `audit.rejectedBids`: persist rejected bid attempts with reason codes, default `true`
- `audit.stateTransitions`: log auction lifecycle transitions, default `true`
- `audit.suspiciousBidPatterns`: log non-punitive suspicious bid/cancel/outbid evidence, default `true`
- `audit.suspiciousRapidBidWindowSeconds`: rapid escalation detection window, default `300`
- `audit.suspiciousRapidBidCount`: bid count in the rapid window before a signal, default `4`
- `audit.suspiciousRepeatedBidderPairCount`: alternating outbid turns before a repeated-pair signal, default `3`
- `audit.suspiciousCancelWindowHours`: repeated seller cancellation detection window, default `24`
- `audit.suspiciousCancelCount`: cancelled listing count before a signal, default `3`
- `audit.sellerSelfBidSignals`: log seller self-bid attempts and accepted self-bids as suspicion signals, default `true`
- `audit.externalSuspicionSignalHooks`: reserved opt-in hook for future privacy-reviewed integrations, default `false`

Permissions:

- `permissions.listPermissionLevel`: permission level required to create or relist listings, default `0`
- `permissions.bidPermissionLevel`: permission level required to bid, default `0`
- `permissions.buyoutPermissionLevel`: permission level required to buy out auctions, default `0`
- `permissions.cancelOwnPermissionLevel`: permission level required to cancel own no-bid auctions, default `0`
- `permissions.claimPermissionLevel`: permission level required to claim auction items, default `0`
- `permissions.terminalAccessPermissionLevel`: permission level required to open `/ah` through an Auction Teller or legacy auction terminal block, default `0`
- `admin.statusPermissionLevel`: permission level required for admin commands and dashboard tools, default `2`

Storage:

- `storage.autosaveIntervalTicks`: SavedData autosave cadence, default `6000`

Admins with UAS admin permission bypass player rate limits. Claims, delivery withdrawals, settlement recovery, and admin recovery tools are not rate-limited.

## Banned Auction Entries

Supported entry forms:

```text
minecraft:bedrock
#minecraft:shulker_boxes
@minecraft
```

Dashboard changes save back to `limits.bannedAuctionEntries`. New listings are checked immediately. Existing active auctions are not auto-cancelled, but matching listings appear in the admin Moderation queue.

## UBS Bidding And Settlement Policy

UAS uses the selected UBS account in GUI flows and the player's UBS primary account in command/API flows that do not pass an explicit account.

- Accepted bids use immediate withdrawal escrow because UBS does not expose a dedicated reserve/hold API.
- When a higher bid is accepted, UAS refunds the previous highest bidder before committing the new bid.
- If a previous-bid refund cannot complete safely, the new bid is rejected and the auction keeps the previous highest bidder.
- Buyout immediately escrows buyer funds, refunds the previous highest bidder when needed, pays the seller net proceeds, and leaves the item claimable by the buyer.
- Sealed bids escrow the full submitted amount. Raising a sealed bid refunds the bidder's previous held sealed amount before accepting the new amount. When a sealed auction ends, UAS refunds losing sealed bids before payout; if a buyout is accepted, UAS refunds all held sealed bids before committing the buyout.
- Sales tax is deducted before seller payout.
- Optional UBS cheque payouts are disabled by default. When enabled and the net seller payout is a qualifying whole-dollar amount, UAS asks UBS to issue a cheque from the configured payout source account and delivers that cheque to the seller instead of duplicating cheque redemption rules.
- If tax deposit, seller payout, or escrow refund fails, the auction moves to `FAILED_SETTLEMENT` for admin recovery.

## UBS Reference Format

Every UAS-triggered UBS transaction uses:

```text
UAS_<EVENT_TYPE>:<auctionId>
```

Current event types:

- `LISTING_FEE`
- `LISTING_FEE_REFUND`
- `BID_ESCROW`
- `BID_ESCROW_REFUND`
- `BUYOUT_ESCROW`
- `BUYOUT_ESCROW_REFUND`
- `OUTBID_REFUND`
- `AUCTION_PAYOUT`
- `SALES_TAX`
- `ADMIN_FORCE_CANCEL_REFUND`
- `CANCELLATION_FEE`

`/uas admin inspect <auctionId>` shows auction id, UBS reference, transaction id when UBS returns one, amount, result, bid history, state transitions, and financial events.

## Currency

UAS displays money in dollars. UBS remains the source of truth for account balances and settlement.

Optional physical cash settlement support is available for future command/API paths, but it is disabled by default. When enabled for listing fees or buyouts, UAS validates exact UBS bill and coin denominations/counts server-side and calls UBS cash APIs instead of introducing UAS currency items. The normal `/ah` GUI and existing account-based settlement remain the required MVP path.

Optional UBS cheque payouts are also disabled by default. They only apply to whole-dollar net seller payouts at or above `settlement.chequePayoutMinimumDollars`, and require `settlement.chequePayoutSourceAccountUuid` so UAS can debit a real UBS source account. Cheques carry UBS recipient/writer data plus UAS auction reference metadata. If UBS cannot create the cheque or UAS cannot deliver it, the auction moves to `FAILED_SETTLEMENT` for admin recovery instead of silently losing the payout.

## Developer API

Other mods should use `UasAuctionApi` instead of reading auction storage directly. See [Developer API](Developer-API.md).
