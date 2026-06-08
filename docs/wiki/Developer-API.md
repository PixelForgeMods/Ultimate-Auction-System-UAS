# Developer API

UAS exposes a small Java API for other mods to query and create auctions without reading mutable auction storage directly.

## Version

```java
String version = UasAuctionApi.get().apiVersion();
```

Current API version: `1`.

## Read Auctions

```java
UasAuctionApi api = UasAuctionApi.get();

List<UasAuctionSnapshot> active = api.queryActive(UasAuctionQuery.defaults());
UasAuctionResult status = api.inspectStatus(auctionId);
Optional<UasAuctionSnapshot> snapshot = api.getAuctionSnapshot(auctionId);
```

`UasAuctionSnapshot` is immutable from the caller perspective. Item stacks returned from `contents()` and `displayItem()` are defensive copies, so integrations cannot mutate escrowed auction items.

## Query Filters

`UasAuctionQuery` supports:

- Search text
- `AuctionCategory`
- Minimum price
- Maximum price
- Maximum hours left
- `AuctionSort`
- Mod id
- Result limit

Limits are clamped between `1` and `120`. The default query uses all categories, no price bounds, no mod filter, `ENDING_SOON` sort, and a limit of `120`.

Categories:

- `ALL`
- `WEAPONS`
- `ARMOR`
- `TOOLS`
- `CONSUMABLES`
- `BLOCKS`
- `MISC`

Sorts:

- `NEWEST`
- `ENDING_SOON`
- `HIGHEST_BID`
- `LOWEST_PRICE`
- `BUYOUT_PRICE`

## Create Listings

```java
UasCreateAuctionRequest request = new UasCreateAuctionRequest(
        List.of(0, 1),
        "PVP Kit",
        "Helmet and sword bundle",
        new BigDecimal("100"),
        new BigDecimal("500"),
        new BigDecimal("250"),
        LocalDateTime.now().plusHours(6)
);

UasAuctionResult result = UasAuctionApi.get().createListing(serverPlayer, request);
```

`inventorySlots` can contain one slot or multiple slots for a bundle listing. UAS normalizes null, duplicate, and negative slots before validation. The same server service used by the GUI validates auction-house bans, item restrictions, UBS account state, listing fees, duration limits, bundle size, and escrow transfer before activating the auction.

`buyoutPrice` may be zero to create a normal bid-only auction. `reservePrice` may be zero for no reserve. A positive reserve must be at least the starting bid, and a positive buyout must be at least both the starting bid and reserve. The compatibility constructor without `reservePrice` still creates no-reserve listings.

`UasAuctionSnapshot` exposes `reservePrice()` and `reserveMet()`. Read APIs expose the actual reserve amount because they are server-side integration APIs; normal client auction summaries only send the amount to the seller or admins, while bidders receive reserve status without the hidden price.

## Cancel Listings

```java
UasAuctionResult result = UasAuctionApi.get().cancelListing(serverPlayer, auctionId);
```

This uses the same ownership, no-bid, state, and delivery-storage path as `/ah cancel`. Normal validation failures are returned as result objects instead of being thrown.

## Physical UBS Cash Settlement

UAS includes a disabled-by-default service for future command/API paths that want to settle a specific listing fee or buyout with exact UBS bills and coins instead of account balance:

```java
UasPhysicalCashSettlementService cash = new UasPhysicalCashSettlementService();
UasCashBreakdown breakdown = new UasCashBreakdown(
        Map.of(10, 1),
        Map.of(25, 2)
);

UasCashSettlementResult result = cash.takeExactCash(
        playerId,
        new BigDecimal("10.50"),
        breakdown,
        UasCashSettlementUse.BUYOUT
);
```

The service validates the requested denominations/counts against the UBS supported bill and coin denominations, verifies the player's matching inventory counts server-side, and only accepts a breakdown whose total exactly matches the expected dollars-and-cents amount. Cash movement uses the UBS bill/coin APIs; UAS does not create custom currency items.

Cash settlement is gated by config:

- `settlement.physicalCashListingFees`
- `settlement.physicalCashBuyouts`

Both are `false` by default. Existing GUI, command, and API listing/bid/buyout flows continue to use UBS accounts unless a future path explicitly calls the cash settlement service.

## UBS Cheque Payouts

UAS can optionally request UBS cheque issuance for seller payouts. This is a server-owner settlement setting, not a separate public command path:

- `settlement.chequePayouts`
- `settlement.chequePayoutSourceAccountUuid`
- `settlement.chequePayoutMinimumDollars`
- `settlement.chequePayoutIssuerPlayerUuid`
- `settlement.chequePayoutIssuerName`

The default remains direct account deposit. Cheque payout only applies when enabled, the configured source account UUID is present, and the net seller payout is a whole-dollar amount at or above the configured minimum. UAS passes seller recipient data, amount, issuer identity, and recipient name to UBS, then annotates the returned cheque item with the UAS auction id/reference before delivering it through normal inventory or delivery storage.

Failed cheque creation or failed cheque delivery moves the auction to `FAILED_SETTLEMENT`, records an `AUCTION_PAYOUT` financial event, and keeps the auction available for admin settlement recovery. Cheque loss, redemption, and paper-instrument rules stay owned by UBS.

## Result Codes

All mutating API calls return `UasAuctionResult`:

- `success`: whether the action completed
- `code`: stable machine-readable `UasAuctionResultCode`
- `reason`: displayable reason text
- `auctionId`: affected auction UUID when known
- `balanceAfter`: optional UBS balance returned by a payment-aware result
- `settlementReference`: optional UBS/UAS settlement reference
- `auctionSnapshot()`: optional snapshot for status/create/cancel responses

Stable result codes:

- `SUCCESS`
- `VALIDATION_FAILED`
- `INVALID_STATE`
- `MISSING_AUCTION`
- `PERMISSION_DENIED`
- `UBS_UNAVAILABLE`
- `UBS_ACCOUNT_MISSING`
- `INSUFFICIENT_FUNDS`
- `STORAGE_UNAVAILABLE`
- `RATE_LIMITED`
- `SETTLEMENT_FAILED`
- `ESCROW_FAILED`
- `UNKNOWN_FAILURE`

Normal validation failures should be handled by checking `success` and `code`; integrations should not parse `reason` except for display.

## Auction Events

UAS posts server-side NeoForge events under:

```text
net.austizz.ultimate_auction_system.api.event.UasAuctionEvents
```

Available post-action events:

- `ListingCreated`
- `BidAccepted`
- `Outbid`
- `BuyoutAccepted`
- `Sold`
- `Cancelled`
- `SettlementFailed`
- `Claimed`

Each event carries an immutable `UasAuctionSnapshot` plus relevant UUIDs and amounts. The snapshot returns defensive `ItemStack` copies, so listeners cannot mutate escrowed items.

Ordering is deterministic: UAS mutates the auction, marks storage dirty, then posts the event, then sends normal alerts/chat messages. Settlement-related events fire after the relevant UBS action succeeds or after the auction is moved into `FAILED_SETTLEMENT`.

These events are notification and audit hooks only. They are not cancellable pre-action hooks. Integrations must not assume they can veto core listing, bidding, settlement, cancellation, or claim flows.

## Integration Guidance

- Use `UasAuctionApi` for reads and listing creation.
- Treat `UasAuctionSnapshot` as read-only.
- Use result codes for control flow.
- Do not read or write UAS SavedData directly.
- Do not assume auction IDs imply state; inspect the current snapshot before acting.
- Do not duplicate UBS transfers around UAS actions. UAS owns listing fees, escrow, refunds, buyouts, taxes, and payouts for its auctions.
