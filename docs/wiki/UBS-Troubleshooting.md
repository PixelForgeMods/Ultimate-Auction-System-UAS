# UBS Troubleshooting

UAS depends on UBS for listing fees, bid escrow, refunds, buyouts, payouts, sales tax, and alerts. Most payment problems should be checked from both UAS and UBS.

## Missing UBS

Symptom:

```text
Ultimate Banking System is required. Install UBS [1.2.0,) and restart the server.
```

Fix:

- Install the UBS jar on the server and clients.
- Confirm the UBS mod id is `ultimatebankingsystem`.
- Restart the game or server.
- Run `/uas status`.

## UBS Version Too Old

Symptom:

```text
Ultimate Banking System <version> is too old. Install UBS [1.2.0,).
```

Fix:

- Replace UBS with version `1.2.0` or newer.
- Restart the server.
- Run `/uas status` again.

## UBS Server API Unavailable

Symptom:

```text
UBS server available: no
```

Fix:

- Check server logs for UBS saved-data load errors.
- Confirm the world loaded fully and UBS central bank data initialized.
- Restart if UBS reported null overworld, failed saved-data setup, or early init problems.
- Do not trust auction settlement until `/uas status` reports `UBS server available: yes`.

## Player Cannot Create Auction

Common causes:

- Player has no UBS account.
- Player has no UBS primary account selected.
- Player primary account is frozen or cannot send funds.
- Player cannot pay the listing fee.
- Player reached the active listing cap.
- Item is empty, restricted, or no longer matches the pending listing.
- Selected bundle contains an empty or restricted stack.
- Auction duration is too short or too long.
- Pending listing confirmation expired.

Fix:

- Use UBS admin tooling to create or repair the player account.
- Check whether the account is frozen.
- Confirm the listing fee is affordable.
- Review `limits.bannedAuctionEntries`.
- Try the GUI create flow because it shows fee, account, item, duration, and validation details before confirm.

## Bid Or Buyout Fails

Common causes:

- Bidder has no usable UBS primary account or selected account.
- Bidder account is frozen.
- Bid is below the minimum increment.
- Auction has ended, been cancelled, been claimed, or entered failed settlement.
- Buyout price is missing or already reached by the current bid.
- Seller self-bid or self-buyout is disabled.
- UBS transfer failed.
- Seller account cannot receive funds.
- Player is rate limited.
- Admin ban blocks bidding, buyouts, or notifications.

Fix:

- Run `/uas admin inspect <auctionId>` to see state, bid history, financial events, and suspicion signals.
- Run `/uas status` to confirm UBS is available.
- Check UBS account status for sender and receiver.
- If the auction is in `FAILED_SETTLEMENT`, run `/uas admin settlement retry <auctionId>` or retry from the admin dashboard.

## Settlement Failed

`FAILED_SETTLEMENT` means UAS stopped normal claim flow because money movement could not finish safely.

Typical causes:

- UBS was unavailable during settlement.
- Seller account was missing or could not receive payout.
- Winning bidder account was missing during final settlement.
- Sales tax destination account was invalid or unavailable.
- A refund or escrow reversal failed.

Fix:

1. Run `/uas admin inspect <auctionId>`.
2. Review the previous failure and financial events.
3. Fix the UBS account or config problem.
4. Run `/uas admin settlement retry <auctionId>` or use the dashboard retry action.

If retry still fails, keep the auction in failed settlement until the underlying UBS issue is fixed.

## Claim Or Delivery Fails

Common causes:

- Player inventory is full.
- Auction has not ended.
- Player is not the winner or seller of an unsold return.
- Settlement has not recovered yet.
- Delivery storage is unavailable.

Fix:

- Ask the player to clear inventory space and try again.
- Use `/ah` Delivery Storage if the item was delivered there.
- Run `/uas admin inspect <auctionId>` if ownership or settlement state is unclear.

## Banned Item Confusion

If a player cannot list an item:

- Check `limits.bannedAuctionEntries`.
- Check Banned Items in the admin dashboard.
- Exact items use `minecraft:bedrock`.
- Tags use `#minecraft:shulker_boxes`.
- Whole mods use `@minecraft`.

Existing active auctions that newly match a banned rule are flagged in the Moderation queue. UAS does not auto-cancel them.

## Saved Data Recovery

UAS stores auctions, deliveries, admin data, recovery entries, audit data, bid history, and financial events through Minecraft SavedData. On load, UAS applies schema migrations, repairs bid/highest-bid state when possible, and skips invalid records.

Use `/uas status` to check storage health. If storage reports skipped, repaired, or failed records, review server logs before continuing live auctions.
