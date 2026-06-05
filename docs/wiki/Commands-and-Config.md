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

Create an auction from the item in your main hand:

```text
/ah create <Starting Price> <Description> <Ending Date dd-MM>
```

Most creation flows should use the GUI because it supports buyout, expiry selection, inventory item selection, listing-fee preview, and validation.

## Admin Commands

Show dependency, storage, config, and auction health:

```text
/uas status
```

Inspect a specific auction record and full bid history:

```text
/uas admin inspect <auctionId>
```

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

## Admin Permission

`/uas status` and `/uas admin inspect` use `Config.adminStatusPermissionLevel`.

## Important Config Values

- `listingFeeRate`: percentage charged when creating an auction
- `salesTaxRate`: percentage charged on sale settlement
- `minimumBidIncrementDollars`: minimum increase over current bid
- `maxActiveListingsPerPlayer`: active listing cap per seller
- `maxAuctionDurationHours`: maximum listing duration
- `autosaveIntervalTicks`: persistent save cadence
- `auditRejectedBids`: whether rejected bid attempts are stored in bid history
- `auditStateTransitions`: whether lifecycle transitions are logged

## Currency

UAS displays money with dollars. UBS still owns account balances and payment settlement.
