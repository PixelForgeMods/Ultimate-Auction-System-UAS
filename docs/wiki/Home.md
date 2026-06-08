# Ultimate Auction System Wiki

Ultimate Auction System (UAS) is a Minecraft 1.21.1 NeoForge auction-house mod. It depends on Ultimate Banking System (UBS) for accounts, balances, transfers, alerts, and payment settlement.

## Required Runtime Stack

- Minecraft: `1.21.1`
- NeoForge: `21.1.x`
- UAS: `ultimate_auction_system`
- UBS: `ultimatebankingsystem` version `1.2.0` or newer

UAS declares UBS as a required mod dependency and also performs a startup diagnostics check. If UBS is missing or older than `1.2.0`, UAS fails startup with a remediation message instead of letting auctions run without banking.

## Start Here

- [Installation and UBS Dependency](Installation-and-UBS-Dependency.md)
- [Local Development Setup](Local-Development-Setup.md)
- [Commands and Config](Commands-and-Config.md)
- [Developer API](Developer-API.md)
- [UBS Troubleshooting](UBS-Troubleshooting.md)

## Admin Health Check

Run this after server start:

```text
/uas status
```

Expected healthy signals:

- UBS loaded: yes
- UBS mod version: `1.2.0` or newer
- UBS required range: `[1.2.0,)`
- UBS server available: yes
- Storage: persistent auction storage loaded or recently saved
