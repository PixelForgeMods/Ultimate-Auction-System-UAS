package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import org.slf4j.Logger;

import java.util.Optional;

public final class UasDependencyDiagnostics {
    public static final String UBS_MOD_ID = "ultimatebankingsystem";
    public static final String MINIMUM_UBS_VERSION = "1.2.0";
    public static final String REQUIRED_UBS_RANGE = "[1.2.0,)";

    private UasDependencyDiagnostics() {
    }

    public static StartupDiagnostics collect() {
        Optional<LoadedUbsMod> loadedMod = loadedUbsMod();
        boolean modLoaded = loadedMod.isPresent();
        String modVersion = loadedMod.map(LoadedUbsMod::version).orElse("not loaded");
        boolean versionSupported = loadedMod
                .map(mod -> isVersionAtLeast(mod.version(), MINIMUM_UBS_VERSION))
                .orElse(false);

        String apiVersion = "unavailable";
        boolean apiAvailable = false;
        String apiMessage = "UBS API provider is not available yet.";
        try {
            UasBankingService bankingService = new UbsBankingService();
            apiVersion = bankingService.getApiVersion();
            apiAvailable = bankingService.isAvailable();
            apiMessage = apiAvailable
                    ? "UBS API is available."
                    : "UBS API exists, but the UBS server data layer is not available yet.";
        } catch (RuntimeException exception) {
            apiMessage = "UBS API check failed: " + exception.getMessage();
        }

        String message;
        if (!modLoaded) {
            message = "Ultimate Banking System is required. Install UBS " + REQUIRED_UBS_RANGE + " and restart the server.";
        } else if (!versionSupported) {
            message = "Ultimate Banking System " + modVersion + " is too old. Install UBS " + REQUIRED_UBS_RANGE + ".";
        } else {
            message = apiMessage;
        }

        return new StartupDiagnostics(
                modLoaded,
                modVersion,
                versionSupported,
                apiVersion,
                apiAvailable,
                message
        );
    }

    public static void validateRequiredUbs(Logger logger) {
        StartupDiagnostics diagnostics = collect();
        if (!diagnostics.modLoaded()) {
            logger.error("[UAS] {}", diagnostics.message());
            throw new IllegalStateException(diagnostics.message());
        }
        if (!diagnostics.versionSupported()) {
            logger.error("[UAS] {}", diagnostics.message());
            throw new IllegalStateException(diagnostics.message());
        }
        logger.info(
                "[UAS] UBS dependency OK: modVersion={}, requiredRange={}, apiVersion={}",
                diagnostics.modVersion(),
                REQUIRED_UBS_RANGE,
                diagnostics.apiVersion()
        );
    }

    public static void logServerDiagnostics(Logger logger) {
        StartupDiagnostics diagnostics = collect();
        if (diagnostics.apiAvailable()) {
            logger.info("[UAS] UBS server API is available. apiVersion={}", diagnostics.apiVersion());
        } else {
            logger.warn("[UAS] {}", diagnostics.message());
            logger.warn("[UAS] If auctions cannot settle payments, confirm UBS loaded its server saved data and run /uas status.");
        }
    }

    private static Optional<LoadedUbsMod> loadedUbsMod() {
        try {
            ModList modList = ModList.get();
            if (modList != null) {
                return modList.getModContainerById(UBS_MOD_ID)
                        .map(container -> new LoadedUbsMod(container.getModInfo().getVersion().toString()));
            }
        } catch (RuntimeException ignored) {
        }

        try {
            LoadingModList loadingModList = LoadingModList.get();
            if (loadingModList == null) {
                return Optional.empty();
            }
            return loadingModList.getMods().stream()
                    .filter(modInfo -> UBS_MOD_ID.equals(modInfo.getModId()))
                    .findFirst()
                    .map(modInfo -> new LoadedUbsMod(modInfo.getVersion().toString()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isVersionAtLeast(String actual, String minimum) {
        int[] actualParts = parseVersion(actual);
        int[] minimumParts = parseVersion(minimum);
        int length = Math.max(actualParts.length, minimumParts.length);
        for (int index = 0; index < length; index++) {
            int actualPart = index < actualParts.length ? actualParts[index] : 0;
            int minimumPart = index < minimumParts.length ? minimumParts[index] : 0;
            if (actualPart != minimumPart) {
                return actualPart > minimumPart;
            }
        }
        return true;
    }

    private static int[] parseVersion(String version) {
        if (version == null || version.isBlank()) {
            return new int[]{0};
        }
        String[] rawParts = version.split("\\.");
        int[] parts = new int[rawParts.length];
        for (int index = 0; index < rawParts.length; index++) {
            String numericPrefix = rawParts[index].replaceFirst("^(\\d+).*$", "$1");
            if (!numericPrefix.matches("\\d+")) {
                parts[index] = 0;
                continue;
            }
            try {
                parts[index] = Integer.parseInt(numericPrefix);
            } catch (NumberFormatException ignored) {
                parts[index] = 0;
            }
        }
        return parts;
    }

    private record LoadedUbsMod(String version) {
    }

    public record StartupDiagnostics(
            boolean modLoaded,
            String modVersion,
            boolean versionSupported,
            String apiVersion,
            boolean apiAvailable,
            String message
    ) {
    }
}
