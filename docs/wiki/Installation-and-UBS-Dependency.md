# Installation and UBS Dependency

## Required Mods

Install both jars on the client and server:

- `ultimate_auction_system`
- `ultimatebankingsystem` version `1.2.0` or newer

The UBS mod id is `ultimatebankingsystem`. UAS declares this as a required dependency in `neoforge.mods.toml` with this range:

```toml
[[dependencies."ultimate_auction_system"]]
modId = "ultimatebankingsystem"
type = "required"
reason = "UAS settles auction payments through the Ultimate Banking System API."
versionRange = "[1.2.0,)"
ordering = "AFTER"
side = "BOTH"
```

## Startup Diagnostics

During common setup, UAS checks that UBS is loaded and that the loaded UBS version is at least `1.2.0`. If the dependency is missing or too old, startup fails with a clear message.

After the server starts, UAS also logs whether the UBS API and server data layer are available. This catches cases where UBS loaded as a mod but its saved data or API provider is not ready.

## Runtime Validation

Use:

```text
/uas status
```

A healthy server should report:

```text
UBS loaded: yes
UBS mod version: 1.2.0
UBS required range: [1.2.0,)
UBS API version: 1.2.0
UBS server available: yes
```

If `UBS server available` is `no`, auctions may open but banking-backed actions such as creating auctions, placing bids, listing-fee withdrawal, buyout, settlement, and alerts can fail.
