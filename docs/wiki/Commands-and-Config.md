# Commands and Config

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

Bid on an active auction with your UBS primary account:

```text
/ah bid <auctionId> <amount>
```

Preview a buyout for an active auction that still has a buyout price above the current highest bid:

```text
/ah buyout <auctionId>
```

The preview creates a short-lived confirmation. Confirm the spend with:

```text
/ah buyout confirm <auctionId>
```

Claim a won item or an expired unsold seller return:

```text
/ah claim <auctionId>
```

Create an auction from the item in your main hand:

```text
/ah create <Starting Price> <Description> <Ending Date dd-MM>
```

Most creation flows should use the GUI because it supports buyout, expiry selection, inventory item selection, listing-fee preview, and validation.

## Auction House GUI Filters

The browser filters run server-side before listings are sent to the client. Text search matches item display names, item registry IDs such as `minecraft:diamond_sword`, bundled item names/registry IDs, seller names, seller UUIDs, descriptions, and auction IDs. Price filters match either the current bid or a positive buyout price, and the time filter limits results to auctions ending within the selected window.

Bid and raise-bid modals use a two-step confirmation flow. The first click refreshes the player's UBS primary account snapshot and shows the selected account, current balance, bid amount, expected remaining balance, and any unavailable/frozen/insufficient-funds warning. The second click submits to the same server-side bid service used by `/ah bid`.

Auction events and chat list rows use clickable shortcuts with hover text. Outbid, won, sold, expired, cancelled, settlement failed/recovered, payout, and refund-related messages include the auction ID plus relevant shortcuts such as `[View]`, `[Bid]`, `[Buyout]`, `[Claim]`, `[Open /ah]`, or `[My Auctions]`. Buyout shortcuts open the preview flow and never spend money on the first click.

The personal Dashboard tab is fetched server-side and capped for large histories. It separates Active, Ending Soon, Claimable, Watching, and History sections, each using the same auction-row actions as the browser where valid.

## Admin Commands

Show dependency, storage, config, and auction health:

```text
/uas status
```

Inspect a specific auction record and full bid history:

```text
/uas admin inspect <auctionId>
```

Retry a failed settlement after reviewing the previous failure and proposed action:

```text
/uas admin settlement retry <auctionId>
```

Open the admin dashboard GUI:

```text
/uas admin
/uas admin gui
```

The admin dashboard includes:

- Overview cards for auction, economy, settlement, and moderation health
- Auction inspection, bid history, force-cancel, and failed-settlement retry tools
- Player inspection with granular auction-house bans for creating, bidding, buyouts, and notifications
- Economy windows for 24h, 7d, and all-time auction activity
- Moderation queues for failed settlements and active auctions that now match banned item rules
- A live banned auction entries editor
- An audit log for dashboard admin actions

The inspect command prints:

- Auction id
- Item and quantity
- Description
- State
- Seller player UUID
- Seller account UUID
- Starting price, current price, buyout price
- Highest bidder
- Start/end/created/updated timestamps
- Escrow flag, source, and timestamp
- Notification subscriber count
- Every bid record, including rejected records when audit config stores them
- Settlement reference and transaction id when present
- Every financial event, including UBS reference, amount, transaction id, and result

## Admin Permission

`/uas status`, `/uas admin inspect`, `/uas admin settlement retry`, and the admin dashboard use `Config.adminStatusPermissionLevel`.

## Important Config Values

- `listingFeeRate`: percentage charged when creating an auction
- `salesTaxRate`: percentage charged on sale settlement
- `salesTaxDestinationAccountUuid`: optional UBS account UUID that receives sales tax
- `minimumBidIncrementDollars`: minimum increase over current bid
- `allowSellerSelfBid`: whether sellers may bid on or buy out their own auctions
- `maxActiveListingsPerPlayer`: active listing cap per seller
- `maxAuctionDurationHours`: maximum listing duration
- `autoSettleExpiredAuctions`: whether UAS scans expired auctions and pays sellers before winner claim
- `autosaveIntervalTicks`: persistent save cadence
- `auditRejectedBids`: whether rejected bid attempts are stored in bid history
- `auditStateTransitions`: whether lifecycle transitions are logged
- `bannedAuctionEntries`: item, tag, or mod restrictions for future auction listings
- `rateLimits.createCooldownSeconds`: per-player cooldown for preparing auction listings
- `rateLimits.bidCooldownSeconds`: per-player cooldown for bids
- `rateLimits.buyoutCooldownSeconds`: per-player cooldown for buyouts
- `rateLimits.cancelCooldownSeconds`: per-player cooldown for seller cancellations
- `rateLimits.searchCooldownSeconds`: per-player cooldown for listing/search refreshes

Admins with the configured UAS admin permission bypass player rate limits. Claims, delivery withdrawals, settlement recovery, and admin recovery tools are not rate-limited.

## UBS Bidding and Settlement Policy

UAS uses the player's UBS primary account by default.

- Bid placement validates the bidder account, available balance, auction state, self-bid config, and minimum increment server-side.
- Accepted bids use immediate withdrawal escrow because UBS does not expose a dedicated reserve/hold API.
- First bids must meet or exceed the starting price. Later bids must exceed the current highest bid by at least `minimumBidIncrementDollars`.
- When a new highest bid is accepted, UAS refunds the previous highest bidder before committing the new bid. If the refund cannot be completed safely, the new bid is rejected and the auction remains with the previous highest bidder.
- Buyout remains available while the current highest bid is below the buyout price. A buyout immediately escrows buyer funds, refunds the previous highest bidder when needed, pays the seller net proceeds, and leaves the item claimable by the buyer.
- `salesTaxRate` is deducted from seller proceeds.
- When `salesTaxDestinationAccountUuid` is blank, sales tax stays a recorded money sink.
- When `salesTaxDestinationAccountUuid` is set, UAS deposits the tax into that UBS account with the `SALES_TAX` reference before paying the seller.
- If a configured tax deposit fails, settlement moves to `FAILED_SETTLEMENT` so admins can retry instead of partially paying the seller first.
- Failed seller payout or escrow refund states move the auction into `FAILED_SETTLEMENT` where normal claim is blocked until an admin retry succeeds.

## UBS Reference Format

Every UAS-triggered UBS transaction uses a stable reference:

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

`SALES_TAX` is recorded in UAS financial events. If a tax destination account is configured, the event contains the UBS transfer result and transaction id when UBS returns one. If no destination is configured, the event records the tax as a money-sink deduction. `/uas admin inspect <auctionId>` shows the UAS auction id, UBS reference, transaction id when UBS returns one, amount, and result for each financial event.

## Banned Auction Entries

Admins can change banned auction entries from the admin dashboard without restarting the server. Dashboard changes apply immediately to new listing attempts and are saved back to `limits.bannedAuctionEntries` in the common config.

Supported entry forms:

- `minecraft:bedrock` for one exact item id
- `#minecraft:shulker_boxes` for an item tag
- `@minecraft` for every item from a mod id

Existing active auctions are not auto-cancelled when a new banned entry is added. They are flagged in the dashboard moderation queue so admins can inspect or force-cancel them manually.

## Currency

UAS displays money with dollars. UBS still owns account balances and payment settlement.
