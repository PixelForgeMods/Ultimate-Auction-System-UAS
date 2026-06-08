package net.austizz.ultimate_auction_system.api;

import net.austizz.ultimate_auction_system.Config;

public enum UasPermissionAction {
    LIST("permissions.listPermissionLevel", "You do not have permission to create auction listings."),
    BID("permissions.bidPermissionLevel", "You do not have permission to place bids."),
    BUYOUT("permissions.buyoutPermissionLevel", "You do not have permission to buy out auctions."),
    CANCEL_OWN("permissions.cancelOwnPermissionLevel", "You do not have permission to cancel your own auctions."),
    CLAIM("permissions.claimPermissionLevel", "You do not have permission to claim auction items."),
    TERMINAL("permissions.terminalAccessPermissionLevel", "You do not have permission to use auction terminals."),
    ADMIN("admin.statusPermissionLevel", "You do not have permission to use auction admin tools.");

    private final String configKey;
    private final String deniedMessage;

    UasPermissionAction(String configKey, String deniedMessage) {
        this.configKey = configKey;
        this.deniedMessage = deniedMessage;
    }

    public String configKey() {
        return configKey;
    }

    public String deniedMessage() {
        return deniedMessage;
    }

    public int requiredPermissionLevel() {
        return switch (this) {
            case LIST -> Config.listPermissionLevel;
            case BID -> Config.bidPermissionLevel;
            case BUYOUT -> Config.buyoutPermissionLevel;
            case CANCEL_OWN -> Config.cancelOwnPermissionLevel;
            case CLAIM -> Config.claimPermissionLevel;
            case TERMINAL -> Config.terminalAccessPermissionLevel;
            case ADMIN -> Config.adminStatusPermissionLevel;
        };
    }
}
