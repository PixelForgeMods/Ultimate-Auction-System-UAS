# Ultimate Auction System Wiki

Ultimate Auction System (UAS) adds a full auction house to Minecraft `1.21.1` on NeoForge. Players can sell items through `/ah`, bid against each other, use optional buyout prices, watch listings, and claim won or returned items. Payments are handled through Ultimate Banking System (UBS), so auction money moves through real UBS accounts instead of a separate economy.

## What Players Can Do

- Open the auction house with `/ah`
- Browse active listings with search, category, mod, price, time, and sort filters
- List one item stack or a bundle of inventory stacks
- Set a starting bid, optional buyout price, optional sealed-bid format, description, and end time
- Review listing fees before confirming an auction
- Choose which UBS account pays listing fees, bids, and buyouts in the GUI
- Bid, raise bids, or buy out auctions with fresh UBS account balance checks
- Watch auctions for notifications
- Track active listings, bids, claimable items, watched auctions, and history from the Dashboard tab
- Claim won items, unsold returns, and delivery-storage items

## What Server Owners Get

- Required UBS dependency checks at startup
- Persistent auction, delivery, admin, audit, and recovery storage
- Configurable listing fees, cancellation fees, sales tax, bid increments, duration limits, listing caps, and rate limits
- Optional hidden reserve prices and sealed-bid auction format
- Admin commands and an admin GUI for health, auctions, players, economy, moderation, suspicious activity, banned items, recovery, and audit history
- Failed-settlement detection with automatic retries and manual admin retry tools
- Banned auction entries for exact items, tags, or whole mods
- Granular auction-house bans for creating, bidding, buyouts, and notifications
- Java API and NeoForge events for other mods
- Language files for English, Dutch, German, and French

## Required Runtime Stack

- Minecraft: `1.21.1`
- NeoForge: `21.1.x`
- Java: `21`
- UAS mod id: `ultimate_auction_system`
- UBS mod id: `ultimatebankingsystem`
- UBS version: `1.2.0` or newer

UAS requires UBS because listing fees, bid escrow, outbid refunds, buyout payments, seller payouts, sales tax, alerts, and failed-settlement recovery all depend on UBS account services.

## First Steps

For players:

1. Join a server with both UAS and UBS installed.
2. Make sure you have a usable UBS primary account.
3. Run `/ah`.
4. Use Browse to buy, Dashboard to track activity, and My Auctions to manage your listings.

For server owners:

1. Install UAS and UBS on the server and on connecting clients.
2. Start the server once so config files are generated.
3. Run `/uas status`.
4. Confirm UBS is loaded, the UBS server API is available, and auction storage is healthy.
5. Review `ultimate_auction_system-common.toml` before opening the auction house to players.

## Wiki Pages

- [Player Guide](Player-Guide.md)
- [Admin Guide](Admin-Guide.md)
- [Installation and UBS Dependency](Installation-and-UBS-Dependency.md)
- [Commands and Config](Commands-and-Config.md)
- [Developer API](Developer-API.md)
- [UBS Troubleshooting](UBS-Troubleshooting.md)
- [Local Development Setup](Local-Development-Setup.md)

## Admin Health Check

Run this after server start:

```text
/uas status
```

Healthy signals include:

- UBS loaded: yes
- UBS mod version: `1.2.0` or newer
- UBS required range: `[1.2.0,)`
- UBS API/server available: yes
- Config loaded without unsafe fallback values
- Persistent auction storage loaded or recently saved
- No failed storage migration blocking auction actions
