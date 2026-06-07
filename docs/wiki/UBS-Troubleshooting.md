# UBS Troubleshooting

## Missing UBS

Symptom:

```text
Ultimate Banking System is required. Install UBS [1.2.0,) and restart the server.
```

Fix:

- Install the UBS jar on the server and client.
- Confirm the UBS mod id is `ultimatebankingsystem`.
- Restart the game/server.

## UBS Version Too Old

Symptom:

```text
Ultimate Banking System <version> is too old. Install UBS [1.2.0,).
```

Fix:

- Replace UBS with version `1.2.0` or newer.
- Re-run `/uas status` after restart.

## UBS Server API Unavailable

Symptom:

```text
UBS server available: no
```

Fix:

- Check server logs for UBS saved-data load errors.
- Confirm the world has loaded and UBS central bank data initialized.
- Restart if UBS reported null overworld or failed saved-data setup during early server init.
- Do not trust auction settlement until `/uas status` reports `UBS server available: yes`.

## Player Cannot Create Auction

Common causes:

- Player has no UBS account.
- Player has no primary account.
- Player primary account is frozen or cannot send funds.
- Player cannot pay the listing fee.
- Item is empty or cannot be escrowed.
- Auction duration exceeds config.

Fix:

- Use UBS admin tooling to create or repair the player account.
- Check whether the account is frozen.
- Confirm the listing fee is affordable.
- Try the GUI create flow because it shows validation earlier.

## Bid or Buyout Fails

Common causes:

- Bidder has no available primary account.
- Bid is below minimum increment.
- Auction has ended, been cancelled, or failed settlement.
- UBS transfer failed.
- Seller account cannot receive funds.

Fix:

- Run `/uas admin inspect <auctionId>` to see state and bid history.
- Run `/uas status` to confirm UBS is available.
- Check UBS account status for sender and receiver.
- If the auction is in `FAILED_SETTLEMENT`, run `/uas admin settlement retry <auctionId>`. The command prints the previous failure and proposed retry action before it executes.

## Saved Data Recovery

UAS stores auctions in `AuctionSavedData` with schema migrations. On load, it repairs bid/highest-bid state when possible and skips invalid records.

Use `/uas status` to check storage health. If storage reports skipped, repaired, or failed records, review server logs before continuing live auctions.
