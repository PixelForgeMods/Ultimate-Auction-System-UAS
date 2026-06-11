# Changelog

## 1.0.3 - 2026-06-11

- Added a UBS web admin dashboard for UAS with Overview, Auctions, Players, Moderation, Recovery, and Audit pages.
- Added UBS dashboard KPI cards, alert panels, charts, tables, and action forms backed by UAS admin snapshots.
- Added web admin actions for force-cancelling auctions, retrying failed settlements, releasing recovery entries, applying/revoking auction-house player bans, and editing banned auction entries.
- Reused the existing in-game admin dashboard data, audit log, recovery storage, and config save paths so web admin changes stay consistent with in-game tools.

## 1.0.2 - 2026-06-10

- Raised the required UBS dependency to `1.2.5` so UAS cannot run against older UBS releases that do not include the API hooks used by auction settlement and alerts.

## 1.0.1 - 2026-06-09

- Fixed the create auction flow so dismissed modal widgets cannot remain visible without the modal frame while waiting for the server confirmation snapshot.

## 1.0.0 - 2026-06-09

- Added the Auction House GUI for browsing, bidding, buying out, claiming, watching, and managing auctions.
- Added UBS-backed listing fees, bid escrow, buyouts, refunds, payouts, sales tax, alerts, and failed-settlement recovery.
- Added bundle auctions, reserve prices, sealed-bid auctions, relisting, delivery storage, saved searches, and mod/category/price/time filters.
- Added the admin dashboard for auction inspection, player moderation, economy reports, suspicious activity, banned items, recovery storage, and audit history.
- Added the Auction Teller NPC and retained the legacy Auction Terminal block for existing worlds.
- Added multilingual UI files for English, Dutch, German, and French.
