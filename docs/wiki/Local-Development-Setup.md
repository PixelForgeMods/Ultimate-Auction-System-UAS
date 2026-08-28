# Local Development Setup

## Repository Layout

The UAS workspace expects UBS to be available for compile and runtime testing. The current local development setup supports a sibling UBS checkout/build:

```text
Ultimate mod series/
  Ultimate Auction System UAS/
  Ultimate-Banking-System-UBS-main/ (or `Ultimate-Banking-System-UBS-dev-1.21.1-neoforge/`)
```

`build.gradle` uses UBS as an implementation dependency. It first uses the local UBS jar path, then falls back to the Maven coordinate if that jar is not present:

```groovy
implementation files(ubsDependencyFile)
```

Override the local jar path when needed:

```text
./gradlew build -Pubs_dependency_jar=C:/path/to/ultimatebankingsystem-2.1.1.jar
```

## Build

From the UAS repo root:

```text
./gradlew build
```

This compiles UAS, runs JUnit tests, and validates resources.

## GameTest Verification

UAS includes a NeoForge GameTest for persistence reload coverage. Run:

```text
./gradlew runGameTestServer
```

Expected result:

```text
All 1 required tests passed :)
```

The GameTest creates an active auction with escrow metadata, bid history, highest bidder, and notification subscribers, serializes it through `AuctionSavedData`, reloads it, and verifies the data survived.

## Dev Username Override

Client runs support several username inputs. The first one found wins:

```text
./gradlew runClient -Pusername=Steve123
./gradlew runClient -Pdev_username=Steve123
./gradlew runClient -Pminecraft_dev_username=Steve123
```

You can also set:

```text
MINECRAFT_DEV_USERNAME=Steve123
```

If none are set, the configured fallback is used.
