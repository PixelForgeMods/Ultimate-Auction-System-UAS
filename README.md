# Ultimate Auction System (UAS)

Ultimate Auction System is a Minecraft `1.21.1` NeoForge auction-house mod. It lets players open an auction GUI with `/ah`, list items, place or raise bids, view bid history, manage auction notifications, and settle payments through Ultimate Banking System.

## Runtime Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Java `21`
- Ultimate Banking System `1.2.0` or newer

UAS requires the UBS mod id `ultimatebankingsystem`. Startup diagnostics fail fast when UBS is missing or too old, because auction listing fees, bids, payouts, refunds, alerts, and settlement all depend on UBS.

## Features

- Auction House GUI opened with `/ah`
- Browse, My Bids, and My Auctions views
- Create-auction flow with item selection, starting bid, buyout, description, expiry selection, and listing-fee preview
- Place Bid and Raise Bid flows
- Scrollable bid-history modal
- Auction notifications for watched auctions
- UBS-backed listing fees, bids, sale payout, tax, refunds, and alerts
- Persistent auction storage through Minecraft SavedData
- Admin health and inspection commands
- Language files for English, Dutch, German, and French

## Player Commands

```text
/ah
/ah list
/ah create <Starting Price> <Description> <Ending Date dd-MM>
```

Most auction creation should happen through the GUI because it exposes the full item picker, buyout, expiry, fee preview, and validation flow.

## Admin Commands

```text
/uas status
/uas admin inspect <auctionId>
```

Use `/uas status` after startup to verify UBS, config, storage, and auction health.

## Local Development

Build and test:

```text
./gradlew build
./gradlew runGameTestServer
```

Expected GameTest result:

```text
All 1 required tests passed :)
```

Run a client with a custom development username:

```text
./gradlew runClient -Pusername=Steve123
./gradlew runClient -Pdev_username=Steve123
./gradlew runClient -Pminecraft_dev_username=Steve123
```

You can also set `MINECRAFT_DEV_USERNAME`. If none are set, the fallback username is `Dev`.

For local UBS development, this repo can consume a sibling checkout at:

```text
Ultimate mod series/
  Ultimate Auction System UAS/
  Ultimate-Banking-System-UBS-main/
```

## Documentation

The GitHub Wiki contains the operational docs:

- [Wiki Home](https://github.com/NadirKhoulali/Ultimate-Auction-System-UAS/wiki)
- [Installation and UBS Dependency](https://github.com/NadirKhoulali/Ultimate-Auction-System-UAS/wiki/Installation-and-UBS-Dependency)
- [Local Development Setup](https://github.com/NadirKhoulali/Ultimate-Auction-System-UAS/wiki/Local-Development-Setup)
- [Commands and Config](https://github.com/NadirKhoulali/Ultimate-Auction-System-UAS/wiki/Commands-and-Config)
- [UBS Troubleshooting](https://github.com/NadirKhoulali/Ultimate-Auction-System-UAS/wiki/UBS-Troubleshooting)

## License

All Rights Reserved.
