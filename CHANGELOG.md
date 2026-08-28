# Changelog

## 1.1.0 - 2026-08-28

- Added auction display blocks with configurable display types, auction selection, custom sizing, hologram support, and editable item-model transforms.
- Added the in-game display editor with position, rotation, scale, spinning, spectator protection, and interactive transform controls.
- Added custom auction-display hover UI with bundle current-item details and item-detail previews.
- Added live UAS auction leaderboards to NeoEssentials for seller revenue, buyer spending, auctions listed, and auctions won.
- Updated UAS to UBS `2.1.1` or newer and migrated auction alerts to the UBS Alert v2 notification API.
- Kept NeoEssentials as an optional integration; UAS continues to require UBS for auction settlement.

## 1.0.3 - 2026-06-11

- Added a UBS web admin dashboard for UAS with Overview, Auctions, Players, Moderation, Recovery, and Audit pages.
- Added UBS dashboard KPI cards, alert panels, charts, tables, and action forms backed by UAS admin snapshots.
- Added web admin actions for force-cancelling auctions, retrying failed settlements, releasing recovery entries, applying/revoking auction-house player bans, and editing banned auction entries.
- Reused the existing in-game admin dashboard data, audit log, recovery storage, and config save paths so web admin changes stay consistent with in-game tools.

## 1.0.2 - 2026-06-10

- Raised the required UBS dependency to `2.1.1` so UAS uses the latest UBS release and its Alert v2 notification API.

## 1.0.1 - 2026-06-09

- Fixed the create auction flow so dismissed modal widgets cannot remain visible without the modal frame while waiting for the server confirmation snapshot.

## 1.0.0 - 2026-06-09

- Added the Auction House GUI for browsing, bidding, buying out, claiming, watching, and managing auctions.
- Added UBS-backed listing fees, bid escrow, buyouts, refunds, payouts, sales tax, alerts, and failed-settlement recovery.
- Added bundle auctions, reserve prices, sealed-bid auctions, relisting, delivery storage, saved searches, and mod/category/price/time filters.
- Added the admin dashboard for auction inspection, player moderation, economy reports, suspicious activity, banned items, recovery storage, and audit history.
- Added the Auction Teller NPC and retained the legacy Auction Terminal block for existing worlds.
- Added multilingual UI files for English, Dutch, German, and French.
