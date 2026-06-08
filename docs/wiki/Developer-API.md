# Developer API

UAS exposes a small Java API for other mods to query and create auctions without reaching into mutable auction storage.

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

## Create Listings

```java
UasCreateAuctionRequest request = new UasCreateAuctionRequest(
        List.of(0, 1),
        "PVP Kit",
        "Helmet and sword bundle",
        new BigDecimal("100"),
        new BigDecimal("500"),
        LocalDateTime.now().plusHours(6)
);

UasAuctionResult result = UasAuctionApi.get().createListing(serverPlayer, request);
```

The API uses the same server service as `/ah` and the GUI. It validates auction-house bans, item restrictions, UBS account state, listing fees, duration limits, bundle size, and escrow transfer before activating the auction.

## Cancel Listings

```java
UasAuctionResult result = UasAuctionApi.get().cancelListing(serverPlayer, auctionId);
```

This uses the same ownership and delivery-storage path as `/ah cancel`. Normal validation failures are returned as result objects instead of being thrown.

## Result Codes

All mutating API calls return `UasAuctionResult`:

- `success`: whether the action completed
- `code`: stable machine-readable `UasAuctionResultCode`
- `reason`: user/admin-readable reason text
- `auctionId`: affected auction UUID when known
- `balanceAfter`: optional UBS balance returned by a future payment-aware result
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
