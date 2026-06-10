# Installation and UBS Dependency

UAS is a NeoForge mod for Minecraft `1.21.1`. It requires Ultimate Banking System (UBS) because auction payments are settled through UBS accounts.

## Required Runtime Stack

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Java `21`
- UAS: `ultimate_auction_system`
- UBS: `ultimatebankingsystem` version `1.2.5` or newer

Install both UAS and UBS on the dedicated server and on every client that connects to it. In singleplayer, install both mods in the local client.

## Basic Install

1. Stop the server.
2. Add the UAS jar to the `mods` folder.
3. Add the UBS jar, version `1.2.5` or newer, to the same `mods` folder.
4. Start the server once to generate config.
5. Run `/uas status`.
6. Confirm UBS and storage are healthy before players start using auctions.

## Player Requirement

Players need a usable UBS primary account before they can create listings, bid, buy out auctions, or receive payouts. If a player has no account, no primary account, a frozen account, or insufficient funds, UAS blocks the action and shows the reason.

## Declared Mod Dependency

UAS declares UBS as a required dependency in `neoforge.mods.toml`:

```toml
[[dependencies."ultimate_auction_system"]]
modId = "ultimatebankingsystem"
type = "required"
reason = "UAS settles auction payments through the Ultimate Banking System API."
versionRange = "[1.2.5,)"
ordering = "AFTER"
side = "BOTH"
```

If UBS is missing, NeoForge should stop the mod load before live auction logic starts.

## Startup Diagnostics

During setup, UAS checks that:

- UBS is loaded
- The loaded UBS version is at least `1.2.5`
- The UBS API version is available
- The UBS server data layer is available after the world loads

If UBS is missing or too old, UAS fails startup with a remediation message. If UBS loads but its server API is unavailable, `/uas status` reports that state so operators know payment actions are unsafe.

## Runtime Validation

Use:

```text
/uas status
```

A healthy server should report:

```text
UBS loaded: yes
UBS mod version: 1.2.5
UBS required range: [1.2.5,)
UBS API version: 1.2.5
UBS server available: yes
```

Also check that config and storage are healthy. If storage reports skipped, repaired, or failed auction records, review the server log before allowing live auction activity.

## First Config Review

Before opening the auction house to players, review:

- Listing fee and cancellation fee
- Sales tax and optional tax destination account
- Minimum bid increment
- Minimum and maximum auction duration
- Maximum active listings per player
- Settlement retry settings
- Rate limits
- Banned auction entries
- Admin permission level

See [Commands and Config](Commands-and-Config.md) for the full reference.

## Updating UAS Or UBS

When updating either mod:

1. Back up the world.
2. Stop the server.
3. Replace the jar files.
4. Start the server.
5. Run `/uas status`.
6. Inspect the log for storage migration or UBS dependency warnings.

Do not downgrade UBS below `1.2.5`; UAS requires that API range for auction settlement and alerts.
