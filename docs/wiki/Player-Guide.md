# Player Guide

This page explains UAS from a player point of view. Server owners and moderators should also read the [Admin Guide](Admin-Guide.md).

## Before You Start

UAS uses UBS accounts for all money movement. Before creating auctions, bidding, or buying out a listing, make sure you have a usable UBS primary account with enough available balance. If your UBS account is frozen, missing, or not selected as primary, UAS will block payment actions and show a message.

## Open The Auction House

Run:

```text
/ah
```

Some servers also place `ultimate_auction_system:auction_terminal` blocks in hubs or marketplaces. Use the terminal to open the same Auction House GUI. Servers can disable or permission-gate terminal access without disabling `/ah`.

The auction house GUI has these main tabs:

- Dashboard: your active auctions, ending-soon items, claimable items, watched listings, and history
- Browse: all active public listings
- My Bids: auctions where you are bidding or can claim a won item
- My Auctions: auctions you created

## Browse Listings

The Browse tab is server-filtered, so results stay consistent with the server state. You can filter and sort by:

- Search text: item names, item ids, bundled contents, seller names, seller UUIDs, descriptions, and auction IDs
- Category: all, weapons, armor, tools, consumables, blocks, or misc
- Mod: all mods or one mod id from active listings
- Price range: current bid or available buyout price
- Time left: any time, under 1 hour, under 24 hours, or under 7 days
- Sort: newest, ending soon, highest bid, lowest price, or buyout price

Rows include valid actions for the current state, such as Bid, Raise Bid, Buyout, Notify, View Bids, Claim, Relist, or Cancel.

## Saved Searches

Open Filters to save named Browse filter presets. A saved search stores the current search text, category, mod filter, price range, time-left filter, and sort order. Use Run to apply it later; use Rename or Delete to manage old presets. Saved searches persist per player across server restarts and run against the current live auction list, so missing items or changed mods simply produce fewer results instead of breaking the browser.

Servers can limit how many saved searches each player can keep.

## Create An Auction In The GUI

The GUI is the recommended way to create auctions.

1. Open `/ah`.
2. Choose Create Auction.
3. Select one inventory slot, or select multiple slots for a bundle auction.
4. Enter a bundle title if you selected multiple stacks.
5. Enter a starting bid.
6. Optionally enter a buyout price.
7. Pick an end date and time.
8. Add a description.
9. Review the listing fee, selected UBS account, and item preview.
10. Confirm the listing.

Bundle auctions can include up to 18 item stacks. UAS escrows the selected item contents when the listing is confirmed, so players cannot sell an item and keep using it at the same time.

## Create An Auction With Commands

Command creation uses the item in your main hand. It is useful for quick listings, but it does not expose the full GUI item picker or bundle flow.

Create without buyout:

```text
/ah create <Starting Price> <Duration Hours> <Description>
```

Create with buyout:

```text
/ah create <Starting Price> <Duration Hours> buyout <Buyout Price> <Description>
```

After the command preview, confirm or discard:

```text
/ah confirm
/ah discard
```

Pending listing confirmations expire after the configured timeout, default `60` seconds.

## Bid, Raise, And Buy Out

Bids use whole-dollar UBS amounts. The first bid must meet the starting bid. Later bids must beat the current highest bid by at least the configured minimum increment, default `$1`.

The bid modal uses a two-step review:

1. Review refreshes your UBS account snapshot and shows balance, bid amount, and expected remaining balance.
2. Confirm submits the bid to the server.

Buyout is also confirmed before money is spent. A buyout is only available while the current highest bid is below the buyout price.

Command buyout flow:

```text
/ah buyout <auctionId>
/ah buyout confirm <auctionId>
```

## Watch Auctions

Use Notify in the GUI to subscribe or unsubscribe from auction notifications on active auctions. Watched listings appear on your Dashboard. You receive alerts when watched auctions are updated, ending soon, sold, cancelled, or end without a buyer. Servers can limit how many active auctions each player watches, and admins can block notification access for specific players if needed.

## Auction Stats And Leaderboards

View your own marketplace stats with:

```text
/ah stats
```

UAS tracks auctions listed, auctions won, gross sold value, gross spent value, and marketplace ranks by UUID, so name changes do not reset your history. Stats are updated from server-side auction listing and settlement events.

Some servers enable a public leaderboard:

```text
/ah leaderboard
```

The leaderboard shows top sellers and buyers. Server owners can disable it for privacy or competitive balance, so `/ah stats` may work even when `/ah leaderboard` is unavailable.

## Claim Items

Use Claim in the GUI or:

```text
/ah claim <auctionId>
```

You can claim:

- Items you won after settlement succeeds
- Unsold items from your own expired auctions
- Items returned after eligible cancellations

If your inventory is full, or an admin releases a recovered item later, UAS can place the item into delivery storage. Open the Delivery Storage modal from `/ah` and press Withdraw when you have space.

## Relist Expired Unsold Auctions

Expired auctions with no accepted bids show a Relist action for the original seller in `/ah`. Relist opens a themed edit modal with the old item contents already selected and the previous starting bid, buyout, and description filled in. You can adjust those fields and choose a new end date before submitting.

Relisting charges the normal configured listing fee again and creates a new active auction. The original expired auction remains in history as an audited record and cannot be claimed separately after the relist succeeds. Relist still respects current banned-item rules, account checks, listing limits, and rate limits.

## Refunds And Failed Settlement

UAS refunds losing bids automatically when a higher bid is accepted, when a buyout replaces a bid, or when an eligible admin force-cancel returns money. If UBS cannot safely refund, pay out, or reverse escrow, UAS marks the auction as failed settlement and blocks normal claim flow until an admin fixes the UBS/account problem and retries settlement.

## Manage Your Auctions

Use My Auctions in the GUI or:

```text
/ah mine
/ah mine active
/ah mine sold
/ah mine cancelled
/ah mine expired
/ah mine active 2
```

You can cancel your own active auction only when it has no bids. If a bid exists, the auction is already financially committed and must finish normally or be handled by an admin.

## Common Reasons An Action Fails

- Your UBS primary account is missing, frozen, or cannot send funds
- Your balance is too low for a listing fee, bid, or buyout
- The auction ended, was claimed, was cancelled, or entered failed settlement
- Your bid is below the minimum
- You are trying to bid on or buy out your own auction while self-bids are disabled
- The item is restricted by server auction rules
- You hit a rate limit and need to wait a few seconds
