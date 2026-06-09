package net.austizz.ultimate_auction_system.client;

import net.austizz.ultimate_auction_system.AuctionCategory;
import net.austizz.ultimate_auction_system.AuctionSort;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.austizz.ultimate_auction_system.network.AuctionAdminActionPayload;
import net.austizz.ultimate_auction_system.network.AuctionAdminDashboardPayload;
import net.austizz.ultimate_auction_system.network.AuctionActionPayload;
import net.austizz.ultimate_auction_system.network.AuctionAccountSummary;
import net.austizz.ultimate_auction_system.network.AuctionBidSummary;
import net.austizz.ultimate_auction_system.network.AuctionDeliverySummary;
import net.austizz.ultimate_auction_system.network.AuctionEntrySummary;
import net.austizz.ultimate_auction_system.network.AuctionModFilterSummaryPayload;
import net.austizz.ultimate_auction_system.network.AuctionPendingListingSummary;
import net.austizz.ultimate_auction_system.network.AuctionSavedSearchPayload;
import net.austizz.ultimate_auction_system.network.AuctionSnapshotPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class AuctionHouseScreen extends Screen {
    private static final Duration MIN_CLIENT_AUCTION_DURATION = Duration.ofMinutes(5);
    private static final int SEARCH_REFRESH_DEBOUNCE_TICKS = 4;
    private static final int DASHBOARD_SECTION_HEADER_HEIGHT = 26;
    private static final int DASHBOARD_SECTION_EMPTY_HEIGHT = 28;
    private static final int DASHBOARD_SECTION_GAP = 10;
    private static final int DESCRIPTION_FIELD_HEIGHT = 70;
    private static final int COMPACT_DESCRIPTION_FIELD_HEIGHT = 56;
    private static final int LISTING_FEE_PANEL_HEIGHT = 66;
    private static final int HELP_TOOLTIP_TEXT_WIDTH = 220;

    private record DatePickerLayout(int cell,
                                    int calendarWidth,
                                    int calendarX,
                                    int monthY,
                                    int weekdayY,
                                    int dayTop,
                                    int timeX,
                                    int timeY,
                                    int timeWidth,
                                    int actionY,
                                    boolean sideBySide) {
    }

    private record RowAction(String label, AuctionButton.Style style, Consumer<AuctionButton> onPress, boolean active) {
        private RowAction(String label, AuctionButton.Style style, Consumer<AuctionButton> onPress) {
            this(label, style, onPress, true);
        }
    }

    private record ModOption(String modId, String displayName, int activeAuctionCount) {
    }

    private record ChartMetric(String label, int color, BigDecimal value, String display) {
    }

    private record DashboardSection(String title, List<AuctionEntrySummary> entries, int color) {
    }

    private enum Tab {
        DASHBOARD("Dashboard"),
        BROWSE("Browse"),
        MY_BIDS("My Bids"),
        MY_AUCTIONS("My Auctions");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum AdminSection {
        OVERVIEW("Overview"),
        AUCTIONS("Auctions"),
        PLAYERS("Players"),
        ECONOMY("Economy"),
        MODERATION("Moderation"),
        SUSPICION("Suspicion"),
        BANNED_ITEMS("Banned Items"),
        RECOVERY("Recovery"),
        AUDIT("Audit");

        private final String label;

        AdminSection(String label) {
            this.label = label;
        }
    }

    private enum Modal {
        NONE,
        BID,
        CREATE,
        RELIST,
        CONFIRM_CREATE,
        DATE_PICKER,
        BIDS,
        DELIVERY,
        FILTER,
        MOD_FILTER,
        CONTENTS,
        ACCOUNT_SELECTOR,
        ADMIN_FORCE_CANCEL
    }

    private AuctionSnapshotPayload payload;
    private Tab activeTab = Tab.BROWSE;
    private AdminSection adminSection = AdminSection.OVERVIEW;
    private Modal modal = Modal.NONE;
    private AuctionEntrySummary selectedAuction;
    private int selectedInventorySlot = -1;
    private List<Integer> selectedInventorySlots = new ArrayList<>();
    private int auctionScroll = 0;
    private AuctionCategory category = AuctionCategory.ALL;
    private AuctionSort sort = AuctionSort.ENDING_SOON;
    private long maxHoursLeft = 0L;
    private String searchDraft = "";
    private String minPriceDraft = "";
    private String maxPriceDraft = "";
    private String startingBidDraft = "";
    private String buyoutDraft = "";
    private String reserveDraft = "";
    private String bidDraft = "";
    private UUID selectedAccountId;
    private final Set<String> expandedDashboardSections = new HashSet<>();
    private Modal accountSelectorReturnModal = Modal.NONE;
    private Modal datePickerReturnModal = Modal.CREATE;
    private UUID bidReviewAuctionId;
    private String bidReviewAmount = "";
    private boolean bidReviewFresh = false;
    private boolean bidReviewPending = false;
    private String bundleTitleDraft = "";
    private String descriptionDraft = "";
    private boolean sealedBidDraft = false;
    private String selectedModId = "";
    private String pendingModId = "";
    private String modSearchDraft = "";
    private String savedSearchNameDraft = "";
    private String adminSearchDraft = "";
    private String adminBannedEntryDraft = "";
    private String adminBanReasonDraft = "";
    private String adminBanExpiryDraft = "";
    private String adminForceCancelReasonDraft = "";
    private UUID selectedAdminPlayerId;
    private boolean adminBlockCreate = true;
    private boolean adminBlockBid = true;
    private boolean adminBlockBuyout = true;
    private boolean adminBlockWatch = true;
    private LocalDate selectedEndDate = LocalDate.now().plusDays(1);
    private int selectedEndHour = 12;
    private int selectedEndMinute = 0;
    private YearMonth calendarMonth = YearMonth.now();

    private EditBox searchBox;
    private EditBox minPriceBox;
    private EditBox maxPriceBox;
    private EditBox bidBox;
    private EditBox startingBidBox;
    private EditBox buyoutBox;
    private EditBox reserveBox;
    private EditBox bundleTitleBox;
    private AuctionTextArea descriptionBox;
    private EditBox endHourBox;
    private EditBox endMinuteBox;
    private EditBox modSearchBox;
    private EditBox savedSearchNameBox;
    private EditBox adminSearchBox;
    private EditBox adminBannedEntryBox;
    private EditBox adminBanReasonBox;
    private EditBox adminBanExpiryBox;
    private EditBox adminForceCancelReasonBox;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int contentLeft;
    private int contentTop;
    private int contentWidth;
    private int contentHeight;
    private int inventoryGridLeft;
    private int inventoryGridTop;
    private int createScroll = 0;
    private int filterScroll = 0;
    private int bidsScroll = 0;
    private int modScroll = 0;
    private int contentsScroll = 0;
    private int accountScroll = 0;
    private int adminScroll = 0;
    private int modalRenderableStart = 0;
    private int modalChildStart = 0;
    private int searchRefreshDelay = 0;
    private boolean modSearchRebuildPending = false;
    private boolean refocusHeaderSearch = false;
    private boolean refocusModSearch = false;
    private String hoveredCreateHelpTitleKey = "";
    private String hoveredCreateHelpBodyKey = "";

    public AuctionHouseScreen(AuctionSnapshotPayload payload) {
        super(Component.translatable("Auction House"));
        this.payload = payload;
    }

    public void refresh(AuctionSnapshotPayload updated) {
        bidDraft = sanitizeMoneyInput(value(bidBox, bidDraft));
        UUID selectedAuctionId = selectedAuction == null ? null : selectedAuction.auctionId();
        this.payload = updated;
        if (selectedAuctionId != null) {
            selectedAuction = findAuction(updated, selectedAuctionId);
        }
        ensureSelectedAccount();
        if (modal == Modal.BID && bidReviewPending && bidReviewMatchesCurrent()) {
            bidReviewPending = false;
            bidReviewFresh = true;
        }
        if (updated.pendingListing().present() && (modal == Modal.NONE || modal == Modal.CREATE || modal == Modal.CONFIRM_CREATE)) {
            modal = Modal.CONFIRM_CREATE;
        } else if (!updated.pendingListing().present() && modal == Modal.CONFIRM_CREATE) {
            modal = Modal.NONE;
        }
        rebuildWidgets();
    }

    @Override
    protected void init() {
        ensureSelectedAccount();
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (searchRefreshDelay > 0) {
            searchRefreshDelay--;
            if (searchRefreshDelay == 0) {
                refreshFromServer();
            }
        }
        if (modSearchRebuildPending) {
            modSearchRebuildPending = false;
            refocusModSearch = true;
            rebuildWidgets();
        }
    }

    @Override
    protected void rebuildWidgets() {
        searchDraft = value(searchBox, searchDraft);
        minPriceDraft = sanitizeMoneyInput(value(minPriceBox, minPriceDraft));
        maxPriceDraft = sanitizeMoneyInput(value(maxPriceBox, maxPriceDraft));
        bidDraft = sanitizeMoneyInput(value(bidBox, bidDraft));
        startingBidDraft = sanitizeMoneyInput(value(startingBidBox, startingBidDraft));
        buyoutDraft = sanitizeMoneyInput(value(buyoutBox, buyoutDraft));
        reserveDraft = sanitizeMoneyInput(value(reserveBox, reserveDraft));
        bundleTitleDraft = value(bundleTitleBox, bundleTitleDraft);
        savedSearchNameDraft = value(savedSearchNameBox, savedSearchNameDraft);
        adminSearchDraft = value(adminSearchBox, adminSearchDraft);
        adminBannedEntryDraft = value(adminBannedEntryBox, adminBannedEntryDraft);
        adminBanReasonDraft = value(adminBanReasonBox, adminBanReasonDraft);
        adminBanExpiryDraft = value(adminBanExpiryBox, adminBanExpiryDraft);
        clearWidgets();
        panelWidth = Math.min(1000, Math.max(360, width - 22));
        panelHeight = Math.min(700, Math.max(260, height - 22));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        if (payload.adminMode()) {
            rebuildAdminWidgets();
            return;
        }

        boolean compactHeader = panelWidth < 760;
        boolean narrowHeader = panelWidth < 560;
        boolean stackedTabs = panelWidth < 560;
        int tabY = panelTop + (stackedTabs ? 122 : compactHeader ? 78 : 54);
        headerHeight = stackedTabs ? 162 : compactHeader ? 118 : 92;
        contentLeft = panelLeft + 16;
        contentTop = panelTop + headerHeight + 12;
        contentWidth = panelWidth - 32;
        contentHeight = Math.max(48, panelHeight - headerHeight - 34);
        if (activeTab == Tab.MY_AUCTIONS && (stackedTabs || panelWidth < 760)) {
            contentTop = Math.max(contentTop, tabY + 64);
            contentHeight = Math.max(48, panelTop + panelHeight - contentTop - 22);
        }

        int closeW = 58;
        int closeX = panelLeft + panelWidth - closeW - 16;
        addAuctionButton(closeX, panelTop + 14, closeW, 20, "Close", AuctionButton.Style.GRAY, button -> onClose());

        int refreshW = narrowHeader ? 78 : 94;
        int deliveryW = narrowHeader ? 108 : 124;
        int headerButtonY = compactHeader ? panelTop + 42 : panelTop + 14;
        int headerButtonLeft = compactHeader ? panelLeft + 16 : panelLeft + Math.min(250, Math.max(164, panelWidth / 4));
        addAuctionButton(headerButtonLeft, headerButtonY, refreshW, 22, "Refresh", AuctionButton.Style.GRAY, button -> refreshFromServer());
        addAuctionButton(headerButtonLeft + refreshW + 8, headerButtonY, deliveryW, 22, Component.translatable("Deliveries").getString() + labelCount(payload.deliveries().size()), AuctionButton.Style.GRAY, button -> {
            modal = Modal.DELIVERY;
            rebuildWidgets();
        });

        int searchY = narrowHeader ? panelTop + 70 : compactHeader ? panelTop + 42 : panelTop + 14;
        int searchLeft = narrowHeader ? panelLeft + 16 : headerButtonLeft + refreshW + deliveryW + 20;
        int searchRight = closeX - 12;
        if (compactHeader && !narrowHeader) {
            searchRight = closeX - 12;
        }
        if (narrowHeader) {
            searchRight = panelLeft + panelWidth - 16;
        }
        int searchWidth = Math.max(90, searchRight - searchLeft);
        searchBox = new AuctionEditBox(font, searchLeft, searchY, searchWidth, 22, Component.translatable("Search"));
        searchBox.setValue(searchDraft);
        searchBox.setHint(Component.translatable("Search items or sellers"));
        searchBox.setResponder(value -> {
            searchDraft = value;
            auctionScroll = 0;
            refocusHeaderSearch = true;
            scheduleSearchRefresh();
        });
        addRenderableWidget(searchBox);
        if (refocusHeaderSearch && modal == Modal.NONE) {
            setFocused(searchBox);
            searchBox.setFocused(true);
        }

        int tabX = panelLeft + 16;
        addAuctionButton(tabX, stackedTabs ? tabY - 30 : tabY, 74, 24, "Filters", filtersActive() ? AuctionButton.Style.TAB_ACTIVE : AuctionButton.Style.GRAY, button -> {
            modal = Modal.FILTER;
            filterScroll = 0;
            rebuildWidgets();
        });
        int tabsX = stackedTabs ? tabX : tabX + 82;
        if (stackedTabs) {
            int tabW = Math.max(70, (panelWidth - 48) / 4);
            addTabButton(tabsX, tabY, tabW, Tab.DASHBOARD);
            addTabButton(tabsX + tabW + 4, tabY, tabW, Tab.BROWSE);
            addTabButton(tabsX + (tabW + 4) * 2, tabY, tabW, Tab.MY_BIDS);
            addTabButton(tabsX + (tabW + 4) * 3, tabY, tabW, Tab.MY_AUCTIONS);
        } else {
            addTabButton(tabsX, tabY, 104, Tab.DASHBOARD);
            addTabButton(tabsX + 110, tabY, 74, Tab.BROWSE);
            addTabButton(tabsX + 190, tabY, 88, Tab.MY_BIDS);
            addTabButton(tabsX + 284, tabY, 104, Tab.MY_AUCTIONS);
        }

        if (activeTab == Tab.MY_AUCTIONS && !payload.adminMode()) {
            int createY = stackedTabs || panelWidth < 760 ? tabY + 30 : tabY;
            addAuctionButton(panelLeft + panelWidth - 170, createY, 154, 24, "Create Auction", AuctionButton.Style.GRAY, button -> {
                modal = Modal.CREATE;
                createScroll = 0;
                resetCreateForm();
                rebuildWidgets();
            });
        }

        clampAuctionScroll();
        addContentButtons();

        modalRenderableStart = renderables.size();
        modalChildStart = children().size();
        if (modal != Modal.NONE) {
            clearFocus();
            clampModalScrolls();
            addModalWidgets();
            if (refocusModSearch && modal == Modal.MOD_FILTER && modSearchBox != null) {
                setFocused(modSearchBox);
                modSearchBox.setFocused(true);
            }
            refocusHeaderSearch = false;
            refocusModSearch = false;
        } else {
            refocusHeaderSearch = false;
        }
    }

    private void addTabButton(int x, int y, int w, Tab tab) {
        AuctionButton button = addAuctionButton(x, y, w, 24, tab.label, activeTab == tab ? AuctionButton.Style.TAB_ACTIVE : AuctionButton.Style.DARK, ignored -> {
            activeTab = tab;
            auctionScroll = 0;
            modal = Modal.NONE;
            rebuildWidgets();
        });
        button.active = activeTab != tab;
    }

    private void rebuildAdminWidgets() {
        boolean wideNav = adminWideNav();
        headerHeight = wideNav ? 70 : 138;
        int navW = wideNav ? 136 : 0;
        contentLeft = panelLeft + 16 + navW;
        contentTop = panelTop + headerHeight + 12;
        contentWidth = panelWidth - 32 - navW;
        contentHeight = Math.max(48, panelHeight - headerHeight - 34);

        int closeW = 58;
        int closeX = panelLeft + panelWidth - closeW - 16;
        addAuctionButton(closeX, panelTop + 14, closeW, 22, "Close", AuctionButton.Style.GRAY, button -> onClose());
        addAuctionButton(closeX - 88, panelTop + 14, 80, 22, "Refresh", AuctionButton.Style.GRAY, button -> refreshFromServer());

        addAdminNavButtons(wideNav);
        clampAdminScroll();
        if (adminSection == AdminSection.AUCTIONS || adminSection == AdminSection.MODERATION) {
            clampAuctionScroll();
            addContentButtons();
        } else if (adminSection == AdminSection.PLAYERS) {
            addAdminPlayerWidgets();
        } else if (adminSection == AdminSection.BANNED_ITEMS) {
            addAdminBannedEntryWidgets();
        } else if (adminSection == AdminSection.SUSPICION) {
            addAdminSuspicionWidgets();
        } else if (adminSection == AdminSection.RECOVERY) {
            addAdminRecoveryWidgets();
        }

        modalRenderableStart = renderables.size();
        modalChildStart = children().size();
        if (modal != Modal.NONE) {
            clearFocus();
            clampModalScrolls();
            addModalWidgets();
            refocusHeaderSearch = false;
            refocusModSearch = false;
        }
    }

    private boolean adminWideNav() {
        return panelWidth >= 720;
    }

    private void addAdminNavButtons(boolean wideNav) {
        AdminSection[] sections = AdminSection.values();
        if (wideNav) {
            int x = panelLeft + 18;
            int y = contentTop;
            for (AdminSection section : sections) {
                addAdminSectionButton(x, y, 112, 22, section);
                y += 28;
            }
            return;
        }

        int x = panelLeft + 16;
        int y = panelTop + 74;
        int buttonW = Math.max(78, (panelWidth - 44) / 4);
        for (int i = 0; i < sections.length; i++) {
            AdminSection section = sections[i];
            addAdminSectionButton(x + (i % 4) * (buttonW + 4), y + (i / 4) * 26, buttonW, 22, section);
        }
    }

    private void addAdminSectionButton(int x, int y, int w, int h, AdminSection section) {
        AuctionButton button = addAuctionButton(x, y, w, h, section.label, adminSection == section ? AuctionButton.Style.TAB_ACTIVE : AuctionButton.Style.DARK, ignored -> {
            adminSection = section;
            adminScroll = 0;
            auctionScroll = 0;
            modal = Modal.NONE;
            rebuildWidgets();
        });
        button.active = adminSection != section;
    }

    private AuctionButton addAuctionButton(int x, int y, int w, int h, String label, AuctionButton.Style style, Consumer<AuctionButton> onPress) {
        return addRenderableWidget(new AuctionButton(x, y, w, h, Component.translatable(label), style, onPress));
    }

    private AuctionButton addAuctionButton(int x, int y, int w, int h, Component label, AuctionButton.Style style, Consumer<AuctionButton> onPress) {
        return addRenderableWidget(new AuctionButton(x, y, w, h, label, style, onPress));
    }

    private AbstractButton addInvisibleButton(int x, int y, int w, int h, Component label, Consumer<AbstractButton> onPress) {
        AbstractButton button = new AbstractButton(x, y, w, h, label) {
            @Override
            public void onPress() {
                if (onPress != null) {
                    onPress.accept(this);
                }
            }

            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
                defaultButtonNarrationText(narrationElementOutput);
            }
        };
        return addRenderableWidget(button);
    }

    private void addContentButtons() {
        if (activeTab == Tab.DASHBOARD && !payload.adminMode()) {
            addDashboardActionButtons();
            return;
        }
        List<AuctionEntrySummary> entries = visibleEntries();
        int rowHeight = auctionRowHeight();
        int listTop = auctionListTop();
        int listBottom = auctionListBottom();
        int start = Math.max(0, auctionScroll / rowHeight);
        int visibleRows = Math.max(1, auctionListViewportHeight() / rowHeight);
        int end = Math.min(entries.size(), start + visibleRows + 2);

        for (int i = start; i < end; i++) {
            AuctionEntrySummary entry = entries.get(i);
            int rowTop = listTop + i * rowHeight - auctionScroll;
            if (rowTop + rowHeight < listTop || rowTop > listBottom) {
                continue;
            }
            addRowActionButtons(entry, rowTop, rowHeight);
        }
    }

    private void addDashboardActionButtons() {
        int listTop = auctionListTop();
        int listBottom = auctionListBottom();
        int y = listTop - auctionScroll;
        int rowHeight = auctionRowHeight();
        for (DashboardSection section : dashboardSections()) {
            int headerY = y;
            if (headerY + DASHBOARD_SECTION_HEADER_HEIGHT >= listTop && headerY <= listBottom) {
                boolean expanded = dashboardSectionExpanded(section);
                AuctionButton button = addAuctionButton(
                        contentLeft + contentWidth - 38,
                        headerY + 1,
                        24,
                        18,
                        Component.literal(expanded ? "^" : "v"),
                        AuctionButton.Style.DARK,
                        ignored -> toggleDashboardSection(section)
                );
                button.visible = headerY + 20 > listTop && headerY < listBottom;
                if (!button.visible) {
                    button.active = false;
                }
            }
            y += DASHBOARD_SECTION_HEADER_HEIGHT;
            if (dashboardSectionExpanded(section)) {
                if (section.entries().isEmpty()) {
                    y += DASHBOARD_SECTION_EMPTY_HEIGHT;
                } else {
                    for (AuctionEntrySummary entry : section.entries()) {
                        if (y + rowHeight >= listTop && y <= listBottom) {
                            addRowActionButtons(entry, y, rowHeight);
                        }
                        y += rowHeight;
                    }
                }
            }
            y += DASHBOARD_SECTION_GAP;
        }
    }

    private void addRowActionButtons(AuctionEntrySummary entry, int rowTop, int rowHeight) {
        List<RowAction> actions = new ArrayList<>();
        if (payload.adminMode()) {
            actions.add(new RowAction("Inspect", AuctionButton.Style.GRAY, button -> openBids(entry)));
            if ("FAILED_SETTLEMENT".equals(normalizedState(entry))) {
                actions.add(new RowAction("Retry Pay", AuctionButton.Style.GREEN, button -> sendAdminAuctionAction("ADMIN_RETRY_SETTLEMENT", entry)));
            }
            if (adminCanForceCancel(entry)) {
                actions.add(new RowAction("Force Cancel", AuctionButton.Style.RED, button -> openAdminForceCancel(entry)));
            }
            addRowButtons(actions, rowTop);
            return;
        }

        actions.add(new RowAction(entry.viewerReceivesNotifications() ? "Watching" : "Notify", entry.viewerReceivesNotifications() ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> sendAuctionAction("TOGGLE_NOTIFICATIONS", entry, "", null)));
        actions.add(new RowAction("View Bids", AuctionButton.Style.GRAY, button -> openBids(entry)));
        if (entry.bundle()) {
            actions.add(new RowAction("Contents", AuctionButton.Style.GRAY, button -> openContents(entry)));
        }

        if (activeTab == Tab.BROWSE || activeTab == Tab.DASHBOARD) {
            if (entry.canBid()) {
                actions.add(new RowAction(bidActionLabel(entry), AuctionButton.Style.GREEN, button -> openBid(entry)));
            }
            if (entry.canBuyout()) {
                actions.add(new RowAction("Buyout", AuctionButton.Style.GRAY, button -> sendAuctionAction("BUYOUT", entry, "", null)));
            }
            if (entry.canCancel()) {
                actions.add(new RowAction("Cancel", AuctionButton.Style.RED, button -> sendAuctionAction("CANCEL", entry, "", null)));
            }
            if (canRelist(entry)) {
                actions.add(new RowAction("Relist", AuctionButton.Style.GREEN, button -> openRelist(entry)));
            }
            if (isClaimed(entry)) {
                actions.add(new RowAction("Claimed", AuctionButton.Style.CLAIMED, button -> {
                }, false));
            } else if (entry.canClaim()) {
                actions.add(new RowAction("Claim", AuctionButton.Style.GREEN, button -> sendAuctionAction("CLAIM", entry, "", null)));
            }
        } else if (activeTab == Tab.MY_BIDS) {
            if (entry.canBid()) {
                actions.add(new RowAction(bidActionLabel(entry), AuctionButton.Style.GREEN, button -> openBid(entry)));
            }
            if (isClaimed(entry)) {
                actions.add(new RowAction("Claimed", AuctionButton.Style.CLAIMED, button -> {
                }, false));
            } else if (entry.canClaim()) {
                actions.add(new RowAction("Claim", AuctionButton.Style.GREEN, button -> sendAuctionAction("CLAIM", entry, "", null)));
            }
        } else {
            if (entry.canCancel()) {
                actions.add(new RowAction("Cancel", AuctionButton.Style.RED, button -> sendAuctionAction("CANCEL", entry, "", null)));
            }
            if (canRelist(entry)) {
                actions.add(new RowAction("Relist", AuctionButton.Style.GREEN, button -> openRelist(entry)));
            }
            if (isClaimed(entry)) {
                actions.add(new RowAction("Claimed", AuctionButton.Style.CLAIMED, button -> {
                }, false));
            } else if (entry.canClaim()) {
                actions.add(new RowAction("Claim", AuctionButton.Style.GREEN, button -> sendAuctionAction("CLAIM", entry, "", null)));
            }
        }

        addRowButtons(actions, rowTop);
    }

    private void addRowButtons(List<RowAction> actions, int rowTop) {
        int buttonH = 20;
        int gap = 6;
        int buttonW = 86;
        int totalW = actions.size() * buttonW + Math.max(0, actions.size() - 1) * gap;
        if (contentWidth >= 760 && totalW <= contentWidth - 260) {
            int x = contentLeft + contentWidth - totalW - 14;
            int y = rowTop + auctionRowHeight() - 44;
            for (RowAction action : actions) {
                AuctionButton button = addAuctionButton(x, y, buttonW, buttonH, action.label(), action.style(), action.onPress());
                button.active = action.active();
                setVisibleInAuctionList(button);
                x += buttonW + gap;
            }
            return;
        }

        int stackedButtonW = 88;
        int x = contentLeft + contentWidth - stackedButtonW - 14;
        int y = rowTop + 12;
        for (RowAction action : actions) {
            AuctionButton button = addAuctionButton(x, y, stackedButtonW, buttonH, action.label(), action.style(), action.onPress());
            button.active = action.active();
            setVisibleInAuctionList(button);
            y += buttonH + gap;
        }
    }

    private void addModalWidgets() {
        int modalW = modalWidth();
        int modalH = modalHeight();
        int x = modalX(modalW);
        int y = modalY(modalH);
        int closeY = y + modalH - 30;

        addAuctionButton(x + modalW - 30, y + 8, 20, 20, Component.literal("X"), AuctionButton.Style.RED, button -> closeModal());

        if (modal == Modal.BID) {
            addBidModalWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.CREATE) {
            addCreateModalWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.RELIST) {
            addRelistModalWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.CONFIRM_CREATE) {
            addConfirmCreateModalWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.DATE_PICKER) {
            addDatePickerWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.DELIVERY) {
            int rowY = y + 58;
            for (AuctionDeliverySummary delivery : payload.deliveries().stream().limit(6).toList()) {
                addAuctionButton(x + modalW - 104, rowY, 84, 22, "Withdraw", AuctionButton.Style.GREEN, button -> sendAuctionAction("WITHDRAW_DELIVERY", null, "", delivery.deliveryId()));
                rowY += 34;
            }
            addAuctionButton(x + modalW - 86, closeY, 70, 22, "Close", AuctionButton.Style.GRAY, button -> closeModal());
        } else if (modal == Modal.BIDS) {
            if (selectedAuction != null && selectedAuction.canCancel()) {
                int actionW = Math.max(96, (modalW - 52) / 2);
                addAuctionButton(x + 20, closeY, actionW, 24, "Close", AuctionButton.Style.GRAY, button -> closeModal());
                addAuctionButton(x + modalW - actionW - 20, closeY, actionW, 24, "Cancel Auction", AuctionButton.Style.RED, button -> sendAuctionAction("CANCEL", selectedAuction, "", null));
            } else {
                addAuctionButton(x + modalW - 100, closeY, 80, 24, "Close", AuctionButton.Style.GRAY, button -> closeModal());
            }
        } else if (modal == Modal.FILTER) {
            addFilterModalWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.MOD_FILTER) {
            addModFilterModalWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.CONTENTS) {
            addAuctionButton(x + modalW - 100, closeY, 80, 24, "Close", AuctionButton.Style.GRAY, button -> closeModal());
        } else if (modal == Modal.ACCOUNT_SELECTOR) {
            addAccountSelectorModalWidgets(x, y, modalW, modalH);
        } else if (modal == Modal.ADMIN_FORCE_CANCEL) {
            addAdminForceCancelModalWidgets(x, y, modalW, modalH);
        }
    }

    private void addAccountSelectorModalWidgets(int x, int y, int modalW, int modalH) {
        int listTop = y + 60;
        int listBottom = y + modalH - 58;
        int rowH = 52;
        int rowY = listTop + 4 - accountScroll;
        for (AuctionAccountSummary account : accountOptions()) {
            AbstractButton button = addInvisibleButton(x + 24, rowY, modalW - 48, rowH - 4, Component.literal(accountSelectorRowTitle(account)), ignored -> chooseAccount(account));
            button.active = !account.frozen();
            setVisibleInModalBody(button, listTop, listBottom);
            if (account.frozen()) {
                button.active = false;
            }
            rowY += rowH;
        }
        addAuctionButton(x + 20, y + modalH - 38, Math.max(96, modalW / 3), 26, "Cancel", AuctionButton.Style.GRAY, button -> closeModal());
    }

    private void addAdminForceCancelModalWidgets(int x, int y, int modalW, int modalH) {
        int reasonY = y + 112;
        adminForceCancelReasonBox = new AuctionEditBox(font, x + 20, reasonY, modalW - 40, 24, Component.translatable("Force-Cancel Reason"));
        adminForceCancelReasonBox.setHint(Component.translatable("Required admin reason"));
        adminForceCancelReasonBox.setValue(adminForceCancelReasonDraft);
        adminForceCancelReasonBox.setResponder(value -> adminForceCancelReasonDraft = value);
        addRenderableWidget(adminForceCancelReasonBox);

        int actionY = y + modalH - 38;
        int actionW = Math.max(104, (modalW - 64) / 3);
        addAuctionButton(x + 20, actionY, actionW, 26, "Cancel", AuctionButton.Style.GRAY, button -> closeModal());
        addAuctionButton(x + 28 + actionW, actionY, actionW, 26, "Return to Seller", AuctionButton.Style.GREEN, button -> sendAdminForceCancelAction(false));
        addAuctionButton(x + modalW - actionW - 20, actionY, actionW, 26, "Move to Recovery", AuctionButton.Style.RED, button -> sendAdminForceCancelAction(true));
    }

    private void addConfirmCreateModalWidgets(int x, int y, int modalW, int modalH) {
        AuctionButton accountButton = addAuctionButton(x + modalW - 124, y + modalH - 72, 104, 22, "Change Account", AuctionButton.Style.GRAY, button -> openAccountSelector());
        accountButton.active = !accountOptions().isEmpty();
        int actionY = y + modalH - 38;
        int actionW = Math.max(112, (modalW - 52) / 2);
        addAuctionButton(x + 20, actionY, actionW, 26, "Discard", AuctionButton.Style.GRAY, button -> sendAuctionAction("DISCARD_CREATE", null, "", null));
        AuctionButton confirm = addAuctionButton(x + modalW - actionW - 20, actionY, actionW, 26, "Confirm Auction", AuctionButton.Style.GREEN, button -> sendAuctionAction("CONFIRM_CREATE", null, "", null));
        confirm.active = payload.pendingListing().present();
    }

    private void addBidModalWidgets(int x, int y, int modalW, int modalH) {
        int actionY = y + modalH - 38;
        int quickY = actionY - 42;
        int inputY = quickY - 52;
        int inputX = x + 20;
        int minW = 52;
        int gap = 8;

        bidBox = new AuctionEditBox(font, inputX, inputY + 16, modalW - 40 - minW - gap, 24, Component.translatable("Bid Amount"));
        bidBox.setHint(Component.translatable("Bid Amount"));
        bidBox.setFilter(this::moneyInput);
        if (bidDraft.isBlank()) {
            bidDraft = nextBidValue(selectedAuction);
        }
        bidBox.setValue(bidDraft);
        bidBox.setResponder(value -> {
            bidDraft = sanitizeMoneyInput(value);
            if (!bidReviewMatchesCurrent()) {
                bidReviewFresh = false;
                bidReviewPending = false;
            }
        });
        addRenderableWidget(bidBox);

        addAuctionButton(x + modalW - 20 - minW, inputY + 16, minW, 24, Component.translatable("MIN"), AuctionButton.Style.GRAY, button -> {
            bidDraft = nextBidValue(selectedAuction);
            bidBox.setValue(bidDraft);
            bidReviewFresh = false;
            bidReviewPending = false;
        });

        int quickW = Math.max(52, (modalW - 64) / 4);
        int quickX = x + 20;
        addAuctionButton(quickX, quickY, quickW, 24, Component.literal("+$100"), AuctionButton.Style.GRAY, button -> addBidIncrement("100"));
        addAuctionButton(quickX + quickW + gap, quickY, quickW, 24, Component.literal("+$500"), AuctionButton.Style.GRAY, button -> addBidIncrement("500"));
        addAuctionButton(quickX + (quickW + gap) * 2, quickY, quickW, 24, Component.literal("+$1000"), AuctionButton.Style.GRAY, button -> addBidIncrement("1000"));
        addAuctionButton(quickX + (quickW + gap) * 3, quickY, quickW, 24, Component.literal("+$5000"), AuctionButton.Style.GRAY, button -> addBidIncrement("5000"));

        int accountPanelH = modalH < 360 ? 56 : 68;
        int accountY = inputY - accountPanelH - 14;
        AuctionButton accountButton = addAuctionButton(x + modalW - 112, accountY + 8, 90, 22, "Change", AuctionButton.Style.GRAY, button -> openAccountSelector());
        accountButton.active = !accountOptions().isEmpty();

        int actionW = Math.max(110, (modalW - 52) / 2);
        addAuctionButton(x + 20, actionY, actionW, 26, "Cancel", AuctionButton.Style.GRAY, button -> closeModal());
        AuctionButton submit = addAuctionButton(x + modalW - actionW - 20, actionY, actionW, 26, bidReviewFresh ? "Confirm Bid" : "Review Bid", AuctionButton.Style.GREEN, button -> submitOrReviewBid());
        submit.active = bidReviewFresh ? bidSubmitAllowed() : moneyDraft(bidDraft).compareTo(BigDecimal.ZERO) > 0;
    }

    private void addCreateModalWidgets(int x, int y, int modalW, int modalH) {
        boolean compact = compactCreateModal(modalW);
        int scroll = createScroll;
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        int bundleOffset = createSelectionIsBundle() ? 48 : 0;
        int startingY = y + (compact ? 274 : 190) + bundleOffset - scroll;
        int buyoutY = y + (compact ? 322 : 190) + bundleOffset - scroll;
        int reserveY = y + (compact ? 370 : 190) + bundleOffset - scroll;
        int formatY = y + (compact ? 418 : 238) + bundleOffset - scroll;
        int endY = y + (compact ? 466 : 286) + bundleOffset - scroll;
        int descriptionY = y + (compact ? 514 : 334) + bundleOffset - scroll;
        int formatX = x + 20;
        int formatW = modalW - 40;
        int descriptionX = x + 20;
        int descriptionW = modalW - 40;
        int descriptionH = compact ? COMPACT_DESCRIPTION_FIELD_HEIGHT : DESCRIPTION_FIELD_HEIGHT;
        if (!compact) {
            formatW = Math.max(170, Math.min(220, (modalW - 60) / 2));
            descriptionX = formatX + formatW + 18;
            descriptionW = Math.max(160, x + modalW - 20 - descriptionX);
            descriptionY = formatY;
        }
        int fieldW = compact ? modalW - 40 : 138;

        if (createSelectionIsBundle()) {
            int titleY = y + (compact ? 274 : 190) - scroll;
            bundleTitleBox = new AuctionEditBox(font, x + 20, titleY, modalW - 40, 22, Component.translatable("Bundle Title"));
            bundleTitleBox.setHint(Component.literal(generatedSelectedBundleTitle()));
            bundleTitleBox.setValue(bundleTitleDraft);
            bundleTitleBox.setResponder(value -> bundleTitleDraft = value);
            setVisibleInModalBody(bundleTitleBox, bodyTop, bodyBottom);
            addRenderableWidget(bundleTitleBox);
        }

        startingBidBox = new AuctionEditBox(font, x + 20, startingY, fieldW, 22, Component.translatable("Starting Bid"));
        startingBidBox.setHint(Component.translatable("Enter starting bid"));
        startingBidBox.setFilter(this::moneyInput);
        startingBidBox.setValue(startingBidDraft);
        startingBidBox.setResponder(value -> startingBidDraft = value);
        setVisibleInModalBody(startingBidBox, bodyTop, bodyBottom);
        addRenderableWidget(startingBidBox);

        buyoutBox = new AuctionEditBox(font, compact ? x + 20 : x + 170, buyoutY, compact ? modalW - 40 : 128, 22, Component.translatable("Buyout"));
        buyoutBox.setHint(Component.translatable("Optional buyout"));
        buyoutBox.setFilter(this::moneyInput);
        buyoutBox.setValue(buyoutDraft);
        buyoutBox.setResponder(value -> buyoutDraft = value);
        setVisibleInModalBody(buyoutBox, bodyTop, bodyBottom);
        addRenderableWidget(buyoutBox);

        reserveBox = new AuctionEditBox(font, compact ? x + 20 : x + 310, reserveY, compact ? modalW - 40 : 128, 22, Component.translatable("Reserve"));
        reserveBox.setHint(Component.translatable("Optional hidden reserve"));
        reserveBox.setFilter(this::moneyInput);
        reserveBox.setValue(reserveDraft);
        reserveBox.setResponder(value -> reserveDraft = value);
        setVisibleInModalBody(reserveBox, bodyTop, bodyBottom);
        addRenderableWidget(reserveBox);

        AuctionButton formatButton = addAuctionButton(formatX, formatY, formatW, 22, Component.literal(sealedBidToggleLabel()), sealedBidDraft ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> {
            sealedBidDraft = !sealedBidDraft;
            rebuildWidgets();
        });
        setVisibleInModalBody(formatButton, bodyTop, bodyBottom);

        AuctionButton endDateButton = addAuctionButton(formatX, endY, formatW, 22, Component.literal(endDateDisplay()), AuctionButton.Style.DARK, button -> {
            datePickerReturnModal = Modal.CREATE;
            modal = Modal.DATE_PICKER;
            calendarMonth = YearMonth.from(selectedEndDate);
            rebuildWidgets();
        });
        setVisibleInModalBody(endDateButton, bodyTop, bodyBottom);

        descriptionBox = new AuctionTextArea(font, descriptionX, descriptionY, descriptionW, descriptionH, Component.translatable("Description"));
        descriptionBox.setHint(Component.translatable("Describe the auction"));
        descriptionBox.setMaxLength(AuctionActionPayload.MAX_DESCRIPTION_LENGTH);
        descriptionBox.setValue(descriptionDraft);
        descriptionBox.setResponder(value -> descriptionDraft = value);
        setVisibleInModalBody(descriptionBox, bodyTop, bodyBottom);
        addRenderableWidget(descriptionBox);

        int feeY = y + (compact ? 590 : 334) + bundleOffset - scroll;
        AuctionButton accountButton = addAuctionButton(x + modalW - 128, feeY + 22, 104, 22, "Change", AuctionButton.Style.GRAY, button -> openAccountSelector());
        accountButton.active = !accountOptions().isEmpty();
        setVisibleInModalBody(accountButton, bodyTop, bodyBottom);

        inventoryGridLeft = x + 20;
        inventoryGridTop = y + 72 - scroll;

        int actionY = y + modalH - 36;
        int cancelW = modalW < 360 ? 112 : 150;
        int createW = modalW < 360 ? 142 : 160;
        addAuctionButton(x + 20, actionY, cancelW, 26, "Cancel", AuctionButton.Style.GRAY, button -> closeModal());
        AuctionButton createButton = addAuctionButton(x + modalW - createW - 20, actionY, createW, 26, "Create Auction", AuctionButton.Style.GREEN, button -> sendCreateAction());
        createButton.active = !selectedInventoryStacks().isEmpty();
    }

    private void addRelistModalWidgets(int x, int y, int modalW, int modalH) {
        boolean compact = compactCreateModal(modalW);
        int scroll = createScroll;
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        int startingY = y + (compact ? 190 : 166) - scroll;
        int buyoutY = y + (compact ? 238 : 166) - scroll;
        int reserveY = y + (compact ? 286 : 166) - scroll;
        int formatY = y + (compact ? 334 : 214) - scroll;
        int endY = y + (compact ? 382 : 262) - scroll;
        int descriptionY = y + (compact ? 430 : 310) - scroll;
        int formatX = x + 20;
        int formatW = modalW - 40;
        int descriptionX = x + 20;
        int descriptionW = modalW - 40;
        int descriptionH = compact ? COMPACT_DESCRIPTION_FIELD_HEIGHT : DESCRIPTION_FIELD_HEIGHT;
        if (!compact) {
            formatW = Math.max(170, Math.min(220, (modalW - 60) / 2));
            descriptionX = formatX + formatW + 18;
            descriptionW = Math.max(160, x + modalW - 20 - descriptionX);
            descriptionY = formatY;
        }
        int fieldW = compact ? modalW - 40 : 138;

        startingBidBox = new AuctionEditBox(font, x + 20, startingY, fieldW, 22, Component.translatable("Starting Bid"));
        startingBidBox.setHint(Component.translatable("Enter starting bid"));
        startingBidBox.setFilter(this::moneyInput);
        startingBidBox.setValue(startingBidDraft);
        startingBidBox.setResponder(value -> startingBidDraft = value);
        setVisibleInModalBody(startingBidBox, bodyTop, bodyBottom);
        addRenderableWidget(startingBidBox);

        buyoutBox = new AuctionEditBox(font, compact ? x + 20 : x + 170, buyoutY, compact ? modalW - 40 : 128, 22, Component.translatable("Buyout"));
        buyoutBox.setHint(Component.translatable("Optional buyout"));
        buyoutBox.setFilter(this::moneyInput);
        buyoutBox.setValue(buyoutDraft);
        buyoutBox.setResponder(value -> buyoutDraft = value);
        setVisibleInModalBody(buyoutBox, bodyTop, bodyBottom);
        addRenderableWidget(buyoutBox);

        reserveBox = new AuctionEditBox(font, compact ? x + 20 : x + 310, reserveY, compact ? modalW - 40 : 128, 22, Component.translatable("Reserve"));
        reserveBox.setHint(Component.translatable("Optional hidden reserve"));
        reserveBox.setFilter(this::moneyInput);
        reserveBox.setValue(reserveDraft);
        reserveBox.setResponder(value -> reserveDraft = value);
        setVisibleInModalBody(reserveBox, bodyTop, bodyBottom);
        addRenderableWidget(reserveBox);

        AuctionButton formatButton = addAuctionButton(formatX, formatY, formatW, 22, Component.literal(sealedBidToggleLabel()), sealedBidDraft ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> {
            sealedBidDraft = !sealedBidDraft;
            rebuildWidgets();
        });
        setVisibleInModalBody(formatButton, bodyTop, bodyBottom);

        AuctionButton endDateButton = addAuctionButton(formatX, endY, formatW, 22, Component.literal(endDateDisplay()), AuctionButton.Style.DARK, button -> {
            datePickerReturnModal = Modal.RELIST;
            modal = Modal.DATE_PICKER;
            calendarMonth = YearMonth.from(selectedEndDate);
            rebuildWidgets();
        });
        setVisibleInModalBody(endDateButton, bodyTop, bodyBottom);

        descriptionBox = new AuctionTextArea(font, descriptionX, descriptionY, descriptionW, descriptionH, Component.translatable("Description"));
        descriptionBox.setHint(Component.translatable("Describe the auction"));
        descriptionBox.setMaxLength(AuctionActionPayload.MAX_DESCRIPTION_LENGTH);
        descriptionBox.setValue(descriptionDraft);
        descriptionBox.setResponder(value -> descriptionDraft = value);
        setVisibleInModalBody(descriptionBox, bodyTop, bodyBottom);
        addRenderableWidget(descriptionBox);

        int feeY = y + (compact ? 506 : 310) - scroll;
        AuctionButton accountButton = addAuctionButton(x + modalW - 128, feeY + 22, 104, 22, "Change", AuctionButton.Style.GRAY, button -> openAccountSelector());
        accountButton.active = !accountOptions().isEmpty();
        setVisibleInModalBody(accountButton, bodyTop, bodyBottom);

        int actionY = y + modalH - 36;
        int cancelW = modalW < 360 ? 112 : 150;
        int relistW = modalW < 360 ? 142 : 160;
        addAuctionButton(x + 20, actionY, cancelW, 26, "Cancel", AuctionButton.Style.GRAY, button -> closeModal());
        AuctionButton relistButton = addAuctionButton(x + modalW - relistW - 20, actionY, relistW, 26, "Relist Auction", AuctionButton.Style.GREEN, button -> sendRelistAction());
        relistButton.active = selectedAuction != null;
    }

    private void addFilterModalWidgets(int x, int y, int modalW, int modalH) {
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        int scroll = filterScroll;
        int fieldX = x + 18;
        int fieldW = modalW - 36;

        AuctionButton categoryButton = addAuctionButton(fieldX, y + 78 - scroll, fieldW, 22, category.label(), AuctionButton.Style.GRAY, button -> {
            AuctionCategory[] values = AuctionCategory.values();
            category = values[(category.ordinal() + 1) % values.length];
            auctionScroll = 0;
            rebuildWidgets();
        });
        setVisibleInModalBody(categoryButton, bodyTop, bodyBottom);

        AuctionButton modButton = addAuctionButton(fieldX, y + 134 - scroll, fieldW, 22, selectedModLabel(), !selectedModId.isBlank() ? AuctionButton.Style.TAB_ACTIVE : AuctionButton.Style.GRAY, button -> {
            pendingModId = selectedModId;
            modSearchDraft = "";
            modScroll = 0;
            modal = Modal.MOD_FILTER;
            rebuildWidgets();
        });
        setVisibleInModalBody(modButton, bodyTop, bodyBottom);

        minPriceBox = new AuctionEditBox(font, fieldX, y + 190 - scroll, fieldW, 20, Component.translatable("Min Price"));
        minPriceBox.setHint(Component.translatable("Min Price"));
        minPriceBox.setFilter(this::moneyInput);
        minPriceBox.setValue(minPriceDraft);
        minPriceBox.setResponder(value -> minPriceDraft = value);
        setVisibleInModalBody(minPriceBox, bodyTop, bodyBottom);
        addRenderableWidget(minPriceBox);

        maxPriceBox = new AuctionEditBox(font, fieldX, y + 218 - scroll, fieldW, 20, Component.translatable("Max Price"));
        maxPriceBox.setHint(Component.translatable("Max Price"));
        maxPriceBox.setFilter(this::moneyInput);
        maxPriceBox.setValue(maxPriceDraft);
        maxPriceBox.setResponder(value -> maxPriceDraft = value);
        setVisibleInModalBody(maxPriceBox, bodyTop, bodyBottom);
        addRenderableWidget(maxPriceBox);

        AuctionButton timeButton = addAuctionButton(fieldX, y + 280 - scroll, fieldW, 22, timeFilterLabel(), AuctionButton.Style.GRAY, button -> {
            if (maxHoursLeft == 0L) {
                maxHoursLeft = 1L;
            } else if (maxHoursLeft == 1L) {
                maxHoursLeft = 24L;
            } else if (maxHoursLeft == 24L) {
                maxHoursLeft = 168L;
            } else {
                maxHoursLeft = 0L;
            }
            auctionScroll = 0;
            rebuildWidgets();
        });
        setVisibleInModalBody(timeButton, bodyTop, bodyBottom);

        AuctionButton sortButton = addAuctionButton(fieldX, y + 342 - scroll, fieldW, 22, sort.label(), AuctionButton.Style.GRAY, button -> {
            AuctionSort[] values = AuctionSort.values();
            sort = values[(sort.ordinal() + 1) % values.length];
            auctionScroll = 0;
            rebuildWidgets();
        });
        setVisibleInModalBody(sortButton, bodyTop, bodyBottom);

        savedSearchNameBox = new AuctionEditBox(font, fieldX, y + 404 - scroll, Math.max(80, fieldW - 116), 20, Component.translatable("Saved Search Name"));
        savedSearchNameBox.setHint(Component.translatable("Name this search"));
        savedSearchNameBox.setValue(savedSearchNameDraft);
        savedSearchNameBox.setResponder(value -> savedSearchNameDraft = value);
        setVisibleInModalBody(savedSearchNameBox, bodyTop, bodyBottom);
        addRenderableWidget(savedSearchNameBox);

        AuctionButton saveSearchButton = addAuctionButton(fieldX + fieldW - 108, y + 404 - scroll, 108, 20, "Save Current", AuctionButton.Style.GREEN, button -> sendSavedSearchAction("SAVE_SEARCH", null));
        setVisibleInModalBody(saveSearchButton, bodyTop, bodyBottom);

        int rowY = y + 436 - scroll;
        int rowH = 56;
        for (AuctionSavedSearchPayload savedSearch : savedSearches()) {
            int buttonY = rowY + 32;
            int actionW = Math.max(54, Math.min(72, (fieldW - 16) / 4));
            AuctionButton runButton = addAuctionButton(fieldX + fieldW - actionW * 3 - 12, buttonY, actionW, 18, "Run", AuctionButton.Style.GREEN, button -> runSavedSearch(savedSearch));
            AuctionButton renameButton = addAuctionButton(fieldX + fieldW - actionW * 2 - 8, buttonY, actionW, 18, "Rename", AuctionButton.Style.GRAY, button -> sendSavedSearchAction("RENAME_SEARCH", savedSearch));
            AuctionButton deleteButton = addAuctionButton(fieldX + fieldW - actionW - 4, buttonY, actionW, 18, "Delete", AuctionButton.Style.RED, button -> sendSavedSearchAction("DELETE_SEARCH", savedSearch));
            setVisibleInModalBody(runButton, bodyTop, bodyBottom);
            setVisibleInModalBody(renameButton, bodyTop, bodyBottom);
            setVisibleInModalBody(deleteButton, bodyTop, bodyBottom);
            rowY += rowH;
        }

        addAuctionButton(x + 18, y + modalH - 36, modalW - 118, 26, "Apply Filters", AuctionButton.Style.GREEN, button -> applyFilters());
        addAuctionButton(x + modalW - 88, y + modalH - 36, 70, 26, "Close", AuctionButton.Style.GRAY, button -> closeModal());
    }

    private void addModFilterModalWidgets(int x, int y, int modalW, int modalH) {
        int listTop = y + 88;
        int listBottom = y + modalH - 52;
        int rowH = 32;

        modSearchBox = new AuctionEditBox(font, x + 18, y + 54, modalW - 36, 22, Component.translatable("Search Mods"));
        modSearchBox.setHint(Component.translatable("Search mods"));
        modSearchBox.setValue(modSearchDraft);
        modSearchBox.setResponder(value -> {
            modSearchDraft = value;
            modScroll = 0;
            refocusModSearch = true;
            modSearchRebuildPending = true;
        });
        addRenderableWidget(modSearchBox);

        List<ModOption> options = modOptions();
        int start = Math.max(0, modScroll / rowH);
        int visibleRows = Math.max(1, (listBottom - listTop) / rowH);
        int end = Math.min(options.size(), start + visibleRows + 1);
        for (int i = start; i < end; i++) {
            ModOption option = options.get(i);
            int rowY = listTop + i * rowH - modScroll;
            if (rowY + rowH < listTop || rowY > listBottom) {
                continue;
            }
            int buttonTop = Math.max(rowY, listTop);
            int buttonBottom = Math.min(rowY + rowH - 3, listBottom);
            if (buttonBottom <= buttonTop) {
                continue;
            }
            addInvisibleButton(x + 18, buttonTop, modalW - 36, buttonBottom - buttonTop, Component.literal(option.displayName()), button -> {
                pendingModId = option.modId();
                rebuildWidgets();
            });
        }

        addAuctionButton(x + 18, y + modalH - 36, 112, 26, "Cancel", AuctionButton.Style.GRAY, button -> {
            modal = Modal.FILTER;
            rebuildWidgets();
        });
        addAuctionButton(x + modalW - 130, y + modalH - 36, 112, 26, "Confirm", AuctionButton.Style.GREEN, button -> {
            selectedModId = pendingModId == null ? "" : pendingModId;
            auctionScroll = 0;
            modal = Modal.FILTER;
            rebuildWidgets();
        });
    }

    private void addAdminPlayerWidgets() {
        int searchY = contentTop + 8;
        int searchW = Math.max(120, contentWidth - 18);
        adminSearchBox = new AuctionEditBox(font, contentLeft, searchY, searchW, 22, Component.translatable("Search Players"));
        adminSearchBox.setHint(Component.translatable("Search player or UUID"));
        adminSearchBox.setValue(adminSearchDraft);
        adminSearchBox.setResponder(value -> {
            adminSearchDraft = value;
            adminScroll = 0;
        });
        addRenderableWidget(adminSearchBox);

        AuctionAdminDashboardPayload.Player selected = selectedAdminPlayer();
        if (selected != null) {
            int y = contentTop + 42;
            int controlsY = y + 116;
            addAuctionButton(contentLeft, controlsY, 66, 20, adminBlockCreate ? "Create" : "- Create", adminBlockCreate ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> {
                adminBlockCreate = !adminBlockCreate;
                rebuildWidgets();
            });
            addAuctionButton(contentLeft + 72, controlsY, 56, 20, adminBlockBid ? "Bid" : "- Bid", adminBlockBid ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> {
                adminBlockBid = !adminBlockBid;
                rebuildWidgets();
            });
            addAuctionButton(contentLeft + 134, controlsY, 66, 20, adminBlockBuyout ? "Buyout" : "- Buyout", adminBlockBuyout ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> {
                adminBlockBuyout = !adminBlockBuyout;
                rebuildWidgets();
            });
            addAuctionButton(contentLeft + 206, controlsY, 62, 20, adminBlockWatch ? "Watch" : "- Watch", adminBlockWatch ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> {
                adminBlockWatch = !adminBlockWatch;
                rebuildWidgets();
            });

            int boxTop = controlsY + 28;
            int formLeft = contentLeft + 6;
            int formRight = contentLeft + contentWidth - 16;
            int reasonW = Math.max(120, (formRight - formLeft) / 2 - 8);
            adminBanReasonBox = new AuctionEditBox(font, formLeft, boxTop, reasonW, 22, Component.translatable("Reason"));
            adminBanReasonBox.setHint(Component.translatable("Reason"));
            adminBanReasonBox.setValue(adminBanReasonDraft);
            adminBanReasonBox.setResponder(value -> adminBanReasonDraft = value);
            addRenderableWidget(adminBanReasonBox);

            int expiryX = formLeft + reasonW + 10;
            adminBanExpiryBox = new AuctionEditBox(font, expiryX, boxTop, Math.max(120, formRight - expiryX), 22, Component.translatable("Expires"));
            adminBanExpiryBox.setHint(Component.translatable("Expires: 2026-06-06T18:30 or blank"));
            adminBanExpiryBox.setValue(adminBanExpiryDraft);
            adminBanExpiryBox.setResponder(value -> adminBanExpiryDraft = value);
            addRenderableWidget(adminBanExpiryBox);

            int actionY = boxTop + 30;
            addAuctionButton(contentLeft, actionY, 116, 22, "Apply Ban", AuctionButton.Style.RED, button -> sendAdminBanAction("APPLY_BAN", selected));
            addAuctionButton(contentLeft + 124, actionY, 92, 22, "Unban", AuctionButton.Style.GRAY, button -> sendAdminBanAction("REVOKE_BAN", selected));
        }

        int listTop = adminPlayerListTop();
        int rowH = 44;
        List<AuctionAdminDashboardPayload.Player> players = filteredAdminPlayers();
        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - listTop) / rowH);
        int end = Math.min(players.size(), start + rows + 1);
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.Player player = players.get(i);
            int rowY = listTop + i * rowH - adminScroll;
            if (rowY + rowH < listTop || rowY > adminListBottom()) {
                continue;
            }
            addInvisibleButton(contentLeft, Math.max(rowY, listTop), contentWidth - 10, Math.min(rowH - 4, adminListBottom() - rowY), Component.literal(player.playerName()), button -> selectAdminPlayer(player));
        }
    }

    private void addAdminBannedEntryWidgets() {
        int inputW = Math.max(120, contentWidth - 128);
        adminBannedEntryBox = new AuctionEditBox(font, contentLeft, contentTop + 8, inputW, 22, Component.translatable("Banned Entry"));
        adminBannedEntryBox.setHint(Component.literal("minecraft:bedrock, #minecraft:shulker_boxes, @modid"));
        adminBannedEntryBox.setValue(adminBannedEntryDraft);
        adminBannedEntryBox.setResponder(value -> adminBannedEntryDraft = value);
        addRenderableWidget(adminBannedEntryBox);
        addAuctionButton(contentLeft + inputW + 8, contentTop + 8, 92, 22, "Add", AuctionButton.Style.GREEN, button -> sendAdminBannedEntryAction("ADD_BANNED_ENTRY", adminBannedEntryDraft));

        int listTop = contentTop + 44;
        int rowH = 36;
        List<AuctionAdminDashboardPayload.BannedEntry> entries = adminDashboard().bannedEntries();
        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - listTop) / rowH);
        int end = Math.min(entries.size(), start + rows + 1);
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.BannedEntry entry = entries.get(i);
            int rowY = listTop + i * rowH - adminScroll;
            if (rowY + rowH < listTop || rowY > adminListBottom()) {
                continue;
            }
            AuctionButton remove = addAuctionButton(contentLeft + contentWidth - 86, rowY + 7, 70, 20, "Remove", AuctionButton.Style.RED, button -> sendAdminBannedEntryAction("REMOVE_BANNED_ENTRY", entry.entry()));
            setVisibleInAdminList(remove);
        }
    }

    private void addAdminSuspicionWidgets() {
        int listTop = contentTop + 30;
        int rowH = 58;
        List<AuctionAdminDashboardPayload.Suspicion> signals = adminDashboard().suspicionSignals();
        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - listTop) / rowH);
        int end = Math.min(signals.size(), start + rows + 1);
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.Suspicion signal = signals.get(i);
            int rowY = listTop + i * rowH - adminScroll;
            if (rowY + rowH < listTop || rowY > adminListBottom() || signal.auctionId() == null) {
                continue;
            }
            AuctionEntrySummary entry = findAuction(payload.browseListings(), signal.auctionId());
            if (entry == null) {
                continue;
            }
            AuctionButton inspect = addAuctionButton(contentLeft + contentWidth - 92, rowY + 18, 76, 22, "Inspect", AuctionButton.Style.GRAY, button -> openBids(entry));
            setVisibleInAdminList(inspect);
        }
    }

    private void addAdminRecoveryWidgets() {
        int listTop = contentTop + 30;
        int rowH = 62;
        List<AuctionAdminDashboardPayload.Recovery> entries = adminDashboard().recoveryEntries();
        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - listTop) / rowH);
        int end = Math.min(entries.size(), start + rows + 1);
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.Recovery entry = entries.get(i);
            int rowY = listTop + i * rowH - adminScroll;
            if (rowY + rowH < listTop || rowY > adminListBottom()) {
                continue;
            }
            AuctionButton release = addAuctionButton(contentLeft + contentWidth - 108, rowY + 20, 92, 22, entry.active() ? "Release" : "Released", entry.active() ? AuctionButton.Style.GREEN : AuctionButton.Style.CLAIMED, button -> sendAdminRecoveryAction(entry));
            release.active = entry.active();
            setVisibleInAdminList(release);
        }
    }

    private void addDatePickerWidgets(int x, int y, int modalW, int modalH) {
        DatePickerLayout layout = datePickerLayout(x, y, modalW, modalH);
        YearMonth firstAllowed = YearMonth.from(LocalDate.now());
        YearMonth lastAllowed = YearMonth.from(LocalDate.now().plusDays(30));

        AuctionButton previousMonth = addAuctionButton(layout.calendarX(), layout.monthY(), 24, 22, Component.literal("<"), AuctionButton.Style.GRAY, button -> {
            calendarMonth = calendarMonth.minusMonths(1);
            rebuildWidgets();
        });
        previousMonth.active = calendarMonth.isAfter(firstAllowed);

        AuctionButton nextMonth = addAuctionButton(layout.calendarX() + layout.calendarWidth() - 24, layout.monthY(), 24, 22, Component.literal(">"), AuctionButton.Style.GRAY, button -> {
            calendarMonth = calendarMonth.plusMonths(1);
            rebuildWidgets();
        });
        nextMonth.active = calendarMonth.isBefore(lastAllowed);

        LocalDate firstDay = calendarMonth.atDay(1);
        int firstCol = firstDay.getDayOfWeek().getValue() % 7;
        for (int day = 1; day <= calendarMonth.lengthOfMonth(); day++) {
            LocalDate date = calendarMonth.atDay(day);
            int index = firstCol + day - 1;
            int col = index % 7;
            int row = index / 7;
            AuctionButton dayButton = addAuctionButton(
                    layout.calendarX() + col * layout.cell(),
                    layout.dayTop() + row * layout.cell(),
                    layout.cell() - 2,
                    layout.cell() - 2,
                    Component.literal(String.valueOf(day)),
                    date.equals(selectedEndDate) ? AuctionButton.Style.TAB_ACTIVE : AuctionButton.Style.DARK,
                    button -> {
                        selectedEndDate = date;
                        rebuildWidgets();
                    }
            );
            dayButton.active = selectableEndDate(date);
        }

        addTimePickerWidgets(layout);

        addAuctionButton(x + 22, layout.actionY(), 120, 26, "Cancel", AuctionButton.Style.GRAY, button -> {
            modal = datePickerReturnModal == Modal.NONE ? Modal.CREATE : datePickerReturnModal;
            datePickerReturnModal = Modal.CREATE;
            rebuildWidgets();
        });
        AuctionButton confirm = addAuctionButton(x + modalW - 142, layout.actionY(), 120, 26, "Confirm", AuctionButton.Style.GREEN, button -> {
            normalizeTimeInputs();
            modal = datePickerReturnModal == Modal.NONE ? Modal.CREATE : datePickerReturnModal;
            datePickerReturnModal = Modal.CREATE;
            rebuildWidgets();
        });
        confirm.active = selectedAuctionDuration().compareTo(MIN_CLIENT_AUCTION_DURATION) >= 0;
    }

    private DatePickerLayout datePickerLayout(int x, int y, int modalW, int modalH) {
        boolean sideBySide = modalW >= 520 && modalH >= 360;
        int actionY = y + modalH - 38;
        int monthY = y + (modalH < 390 ? 72 : 88);
        int dayTop = y + (modalH < 390 ? 114 : 140);
        int contentX = x + 22;
        int contentWidth = modalW - 44;
        int columnGap = 28;
        int columnWidth = sideBySide ? Math.max(1, (contentWidth - columnGap) / 2) : contentWidth;
        int timeWidth = sideBySide ? Math.min(220, columnWidth) : Math.max(220, modalW - 44);
        int timeY = sideBySide ? y + (modalH < 390 ? 120 : 130) : actionY - 124;
        int calendarAreaX = contentX;
        int calendarAreaWidth = sideBySide ? columnWidth : contentWidth;
        int availableBottom = sideBySide ? actionY - 18 : timeY - 24;
        int availableHeight = Math.max(18, availableBottom - dayTop);
        int minCell = modalH < 390 ? 14 : 18;
        int cellByHeight = Math.max(minCell, availableHeight / 6);
        int cellByWidth = Math.max(minCell, calendarAreaWidth / 7);
        int cell = Math.max(minCell, Math.min(30, Math.min(cellByHeight, cellByWidth)));
        int calendarWidth = cell * 7;
        int calendarX = calendarAreaX + Math.max(0, (calendarAreaWidth - calendarWidth) / 2);
        int timeX = sideBySide
                ? contentX + columnWidth + columnGap + Math.max(0, (columnWidth - timeWidth) / 2)
                : x + (modalW - timeWidth) / 2;
        int weekdayY = dayTop - 18;
        return new DatePickerLayout(cell, calendarWidth, calendarX, monthY, weekdayY, dayTop, timeX, timeY, timeWidth, actionY, sideBySide);
    }

    private void addTimePickerWidgets(DatePickerLayout layout) {
        int controlsW = 164;
        int hourW = 44;
        int minuteW = 44;
        int arrowH = 18;
        int inputH = 24;
        int controlsX = layout.timeX() + Math.max(0, (layout.timeWidth() - controlsW) / 2);
        int top = layout.timeY() + 26;
        int hourX = controlsX;
        int minuteX = hourX + hourW + 20;
        int periodX = minuteX + minuteW + 16;
        int inputY = top + arrowH + 4;
        int downY = inputY + inputH + 4;

        addAuctionButton(hourX, top, hourW, arrowH, Component.literal("^"), AuctionButton.Style.GRAY, button -> adjustSelectedHour(1));
        endHourBox = new AuctionEditBox(font, hourX, inputY, hourW, inputH, Component.translatable("Hour"));
        endHourBox.setMaxLength(2);
        endHourBox.setFilter(this::timeNumberInput);
        endHourBox.setValue(String.valueOf(displayEndHour()));
        endHourBox.setResponder(value -> updateSelectedHourFromInput(value));
        addRenderableWidget(endHourBox);
        addAuctionButton(hourX, downY, hourW, arrowH, Component.literal("v"), AuctionButton.Style.GRAY, button -> adjustSelectedHour(-1));

        addAuctionButton(minuteX, top, minuteW, arrowH, Component.literal("^"), AuctionButton.Style.GRAY, button -> adjustSelectedMinute(1));
        endMinuteBox = new AuctionEditBox(font, minuteX, inputY, minuteW, inputH, Component.translatable("Minute"));
        endMinuteBox.setMaxLength(2);
        endMinuteBox.setFilter(this::timeNumberInput);
        endMinuteBox.setValue(twoDigit(selectedEndMinute));
        endMinuteBox.setResponder(value -> updateSelectedMinuteFromInput(value));
        addRenderableWidget(endMinuteBox);
        addAuctionButton(minuteX, downY, minuteW, arrowH, Component.literal("v"), AuctionButton.Style.GRAY, button -> adjustSelectedMinute(-1));

        addInvisibleButton(periodX, inputY - 12, 42, 22, Component.translatable("AM"), button -> setSelectedPeriod(false));
        addInvisibleButton(periodX, inputY + 18, 42, 22, Component.translatable("PM"), button -> setSelectedPeriod(true));
    }

    private int modalWidth() {
        if (modal == Modal.FILTER) {
            return Math.min(280, panelWidth - 32);
        }
        if (modal == Modal.MOD_FILTER) {
            return Math.min(460, panelWidth - 44);
        }
        if (modal == Modal.DATE_PICKER) {
            int available = panelWidth - 44;
            return available >= 560 ? Math.min(580, available) : Math.min(390, available);
        }
        if (modal == Modal.BID || modal == Modal.BIDS || modal == Modal.RELIST || modal == Modal.CONFIRM_CREATE || modal == Modal.ADMIN_FORCE_CANCEL) {
            return Math.min(560, panelWidth - 44);
        }
        if (modal == Modal.CONTENTS) {
            return Math.min(560, panelWidth - 44);
        }
        if (modal == Modal.ACCOUNT_SELECTOR) {
            return Math.min(500, panelWidth - 44);
        }
        return Math.min(520, panelWidth - 44);
    }

    private int modalHeight() {
        if (modal == Modal.FILTER) {
            return Math.max(220, panelHeight - 34);
        }
        if (modal == Modal.CREATE || modal == Modal.RELIST || modal == Modal.CONFIRM_CREATE || modal == Modal.DATE_PICKER || modal == Modal.BID || modal == Modal.BIDS || modal == Modal.MOD_FILTER || modal == Modal.CONTENTS || modal == Modal.ACCOUNT_SELECTOR) {
            return Math.min(430, panelHeight - 48);
        }
        if (modal == Modal.ADMIN_FORCE_CANCEL) {
            return Math.min(250, panelHeight - 48);
        }
        return Math.min(300, panelHeight - 48);
    }

    private int modalX(int modalW) {
        return modal == Modal.FILTER ? panelLeft + 14 : panelLeft + (panelWidth - modalW) / 2;
    }

    private int modalY(int modalH) {
        return modal == Modal.FILTER ? panelTop + 17 : panelTop + (panelHeight - modalH) / 2;
    }

    private int modalBodyTop(int modalY) {
        return modalY + 46;
    }

    private int modalBodyBottom(int modalY, int modalH) {
        return modalY + modalH - 46;
    }

    private void closeModal() {
        if (modal == Modal.CONFIRM_CREATE) {
            sendAuctionAction("DISCARD_CREATE", null, "", null);
            return;
        }
        if (modal == Modal.BID) {
            resetBidReview();
        }
        if (modal == Modal.DATE_PICKER) {
            modal = datePickerReturnModal == Modal.NONE ? Modal.CREATE : datePickerReturnModal;
            datePickerReturnModal = Modal.CREATE;
            rebuildWidgets();
            return;
        }
        modal = modal == Modal.MOD_FILTER
                ? Modal.FILTER
                : modal == Modal.ACCOUNT_SELECTOR
                ? (accountSelectorReturnModal == Modal.NONE ? Modal.NONE : accountSelectorReturnModal)
                : Modal.NONE;
        if (modal != Modal.ACCOUNT_SELECTOR) {
            accountSelectorReturnModal = Modal.NONE;
        }
        rebuildWidgets();
    }

    private void resetCreateForm() {
        selectedInventorySlot = -1;
        selectedInventorySlots = new ArrayList<>();
        startingBidDraft = "";
        buyoutDraft = "";
        reserveDraft = "";
        bundleTitleDraft = "";
        descriptionDraft = "";
        sealedBidDraft = false;
        selectedEndDate = LocalDate.now().plusDays(1);
        selectedEndHour = 12;
        selectedEndMinute = 0;
        calendarMonth = YearMonth.from(selectedEndDate);
    }

    private boolean selectableEndDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        return !date.isBefore(today) && !date.isAfter(today.plusDays(30));
    }

    private LocalDateTime selectedEndDateTime() {
        return selectedEndDate.atTime(selectedEndHour, selectedEndMinute);
    }

    private Duration selectedAuctionDuration() {
        return Duration.between(LocalDateTime.now(), selectedEndDateTime());
    }

    private String endDateDisplay() {
        LocalDateTime end = selectedEndDateTime();
        String month = end.getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT);
        return Component.translatable("{0} {1}, {2}", Component.translatable(month), end.getDayOfMonth(), timeLabel(end.getHour(), end.getMinute())).getString();
    }

    private String timeLabel(int hour, int minute) {
        int displayHour = hour % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }
        return Component.translatable("{0}:{1} {2}", displayHour, twoDigit(minute), Component.translatable(hour < 12 ? "AM" : "PM")).getString();
    }

    private String twoDigit(int value) {
        return String.format(Locale.ROOT, "%02d", clamp(value, 0, 99));
    }

    private int displayEndHour() {
        int displayHour = selectedEndHour % 12;
        return displayHour == 0 ? 12 : displayHour;
    }

    private boolean selectedEndPm() {
        return selectedEndHour >= 12;
    }

    private void adjustSelectedHour(int delta) {
        normalizeTimeInputs();
        selectedEndHour = Math.floorMod(selectedEndHour + delta, 24);
        rebuildWidgets();
    }

    private void adjustSelectedMinute(int delta) {
        normalizeTimeInputs();
        int totalMinutes = selectedEndHour * 60 + selectedEndMinute + delta;
        int normalized = Math.floorMod(totalMinutes, 24 * 60);
        selectedEndHour = normalized / 60;
        selectedEndMinute = normalized % 60;
        rebuildWidgets();
    }

    private void setSelectedPeriod(boolean pm) {
        normalizeTimeInputs();
        if (pm && selectedEndHour < 12) {
            selectedEndHour += 12;
        } else if (!pm && selectedEndHour >= 12) {
            selectedEndHour -= 12;
        }
        rebuildWidgets();
    }

    private void normalizeTimeInputs() {
        if (endHourBox != null) {
            updateSelectedHourFromInput(endHourBox.getValue());
        }
        if (endMinuteBox != null) {
            updateSelectedMinuteFromInput(endMinuteBox.getValue());
        }
    }

    private void updateSelectedHourFromInput(String value) {
        String digits = digitsOnly(value);
        if (digits.isBlank()) {
            return;
        }
        int hour = clamp(parsePositiveInt(digits, displayEndHour()), 1, 12);
        boolean pm = selectedEndPm();
        if (hour == 12) {
            selectedEndHour = pm ? 12 : 0;
        } else {
            selectedEndHour = pm ? hour + 12 : hour;
        }
    }

    private void updateSelectedMinuteFromInput(String value) {
        String digits = digitsOnly(value);
        if (digits.isBlank()) {
            return;
        }
        selectedEndMinute = clamp(parsePositiveInt(digits, selectedEndMinute), 0, 59);
    }

    private String digitsOnly(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private boolean timeNumberInput(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean moneyInput(String value) {
        if (value == null) {
            return false;
        }
        boolean dotSeen = false;
        boolean dollarSeen = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == ',') {
                continue;
            }
            if (c == '.') {
                if (dotSeen) {
                    return false;
                }
                dotSeen = true;
                continue;
            }
            if (c == '$') {
                if (dollarSeen || i != 0) {
                    return false;
                }
                dollarSeen = true;
                continue;
            }
            return false;
        }
        return true;
    }

    private String sanitizeMoneyInput(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean dotSeen = false;
        boolean dollarSeen = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == ',') {
                builder.append(c);
            } else if (c == '.' && !dotSeen) {
                dotSeen = true;
                builder.append(c);
            } else if (c == '$' && !dollarSeen && builder.isEmpty()) {
                dollarSeen = true;
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private ItemStack selectedInventoryStack() {
        if (minecraft == null || minecraft.player == null || selectedInventorySlots.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = minecraft.player.getInventory();
        selectedInventorySlot = selectedInventorySlots.getFirst();
        if (selectedInventorySlot >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(selectedInventorySlot);
    }

    private List<ItemStack> selectedInventoryStacks() {
        if (minecraft == null || minecraft.player == null || selectedInventorySlots.isEmpty()) {
            return List.of();
        }
        Inventory inventory = minecraft.player.getInventory();
        return selectedInventorySlots.stream()
                .filter(slot -> slot >= 0 && slot < inventory.getContainerSize())
                .map(inventory::getItem)
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    private boolean createSelectionIsBundle() {
        return selectedInventorySlots.size() > 1;
    }

    private boolean canRelist(AuctionEntrySummary entry) {
        return entry != null
                && entry.viewerIsSeller()
                && entry.bidCount() == 0
                && entry.canClaim()
                && !"CLAIMED".equals(normalizedState(entry))
                && !"CANCELLED".equals(normalizedState(entry));
    }

    private void openBid(AuctionEntrySummary entry) {
        selectedAuction = entry;
        bidDraft = nextBidValue(entry);
        resetBidReview();
        modal = Modal.BID;
        rebuildWidgets();
    }

    private void openBids(AuctionEntrySummary entry) {
        selectedAuction = entry;
        modal = Modal.BIDS;
        bidsScroll = 0;
        rebuildWidgets();
    }

    private void openContents(AuctionEntrySummary entry) {
        selectedAuction = entry;
        modal = Modal.CONTENTS;
        contentsScroll = 0;
        rebuildWidgets();
    }

    private void openRelist(AuctionEntrySummary entry) {
        selectedAuction = entry;
        selectedInventorySlot = -1;
        selectedInventorySlots = new ArrayList<>();
        BigDecimal startingBid = moneyDraft(entry == null ? "" : entry.startingBid());
        BigDecimal buyout = moneyDraft(entry == null ? "" : entry.buyoutPrice());
        BigDecimal reserve = entry != null && entry.reservePriceVisible() ? moneyDraft(entry.reservePrice()) : BigDecimal.ZERO;
        startingBidDraft = startingBid.compareTo(BigDecimal.ZERO) <= 0 ? "0" : startingBid.stripTrailingZeros().toPlainString();
        buyoutDraft = buyout.compareTo(BigDecimal.ZERO) <= 0 ? "" : buyout.stripTrailingZeros().toPlainString();
        reserveDraft = reserve.compareTo(BigDecimal.ZERO) <= 0 ? "" : reserve.stripTrailingZeros().toPlainString();
        bundleTitleDraft = entry != null && entry.bundle() ? entry.itemName() : "";
        descriptionDraft = entry == null ? "" : entry.description();
        sealedBidDraft = entry != null && entry.sealedBid();
        LocalDateTime defaultEnd = LocalDateTime.now().plusDays(1);
        selectedEndDate = defaultEnd.toLocalDate();
        selectedEndHour = defaultEnd.getHour();
        selectedEndMinute = defaultEnd.getMinute();
        calendarMonth = YearMonth.from(selectedEndDate);
        createScroll = 0;
        datePickerReturnModal = Modal.RELIST;
        modal = Modal.RELIST;
        rebuildWidgets();
    }

    private void openAdminForceCancel(AuctionEntrySummary entry) {
        selectedAuction = entry;
        adminForceCancelReasonDraft = "";
        modal = Modal.ADMIN_FORCE_CANCEL;
        rebuildWidgets();
    }

    private void addBidIncrement(String rawIncrement) {
        if (bidBox == null) {
            return;
        }
        BigDecimal current = moneyDraft(bidBox.getValue());
        BigDecimal minimum = moneyDraft(nextBidValue(selectedAuction));
        if (current.compareTo(minimum) < 0) {
            current = minimum;
        }
        bidDraft = current.add(moneyDraft(rawIncrement)).stripTrailingZeros().toPlainString();
        bidReviewFresh = false;
        bidReviewPending = false;
        bidBox.setValue(bidDraft);
    }

    private void submitOrReviewBid() {
        bidDraft = sanitizeMoneyInput(value(bidBox, bidDraft));
        if (bidReviewFresh && bidSubmitAllowed()) {
            sendAuctionAction("BID", selectedAuction, bidDraft, null);
            resetBidReview();
            return;
        }
        reviewBid();
    }

    private void reviewBid() {
        if (selectedAuction == null || moneyDraft(bidDraft).compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        bidReviewAuctionId = selectedAuction.auctionId();
        bidReviewAmount = sanitizeMoneyInput(bidDraft);
        bidReviewFresh = false;
        bidReviewPending = true;
        refreshFromServer();
    }

    private void resetBidReview() {
        bidReviewAuctionId = null;
        bidReviewAmount = "";
        bidReviewFresh = false;
        bidReviewPending = false;
    }

    private boolean bidReviewMatchesCurrent() {
        if (selectedAuction == null || bidReviewAuctionId == null) {
            return false;
        }
        String currentAmount = sanitizeMoneyInput(value(bidBox, bidDraft));
        return bidReviewAuctionId.equals(selectedAuction.auctionId())
                && !currentAmount.isBlank()
                && currentAmount.equals(sanitizeMoneyInput(bidReviewAmount));
    }

    private boolean bidSubmitAllowed() {
        return bidReviewMatchesCurrent()
                && selectedAccount().present()
                && !selectedAccount().frozen()
                && moneyDraft(bidDraft).compareTo(moneyDraft(nextBidValue(selectedAuction))) >= 0
                && bidRemainingBalance().compareTo(BigDecimal.ZERO) >= 0;
    }

    private BigDecimal bidRemainingBalance() {
        return moneyDraft(selectedAccount().balance()).subtract(moneyDraft(bidDraft));
    }

    private String bidReviewStatus() {
        if (selectedAuction == null) {
            return "";
        }
        if (bidReviewPending) {
            return Component.translatable("Refreshing UBS account snapshot...").getString();
        }
        if (!selectedAccount().present()) {
            return Component.translatable("UBS account unavailable.").getString();
        }
        if (selectedAccount().frozen()) {
            return Component.translatable("UBS account is frozen.").getString();
        }
        if (moneyDraft(bidDraft).compareTo(moneyDraft(nextBidValue(selectedAuction))) < 0) {
            return Component.translatable("Bid is below the minimum.").getString();
        }
        BigDecimal remaining = bidRemainingBalance();
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return Component.translatable("Insufficient funds by {0}.", moneyDisplay(remaining.abs())).getString();
        }
        return bidReviewFresh && bidReviewMatchesCurrent()
                ? Component.translatable("Account refreshed. Confirm to submit.").getString()
                : Component.translatable("Review refreshes your UBS balance before submitting.").getString();
    }

    private int bidReviewStatusColor() {
        if (bidReviewFresh && bidSubmitAllowed()) {
            return 0xFF55FF55;
        }
        if (bidReviewPending) {
            return 0xFFFFD966;
        }
        if (!selectedAccount().present()
                || selectedAccount().frozen()
                || moneyDraft(bidDraft).compareTo(moneyDraft(nextBidValue(selectedAuction))) < 0
                || bidRemainingBalance().compareTo(BigDecimal.ZERO) < 0) {
            return 0xFFFF6666;
        }
        return 0xFFBDBDBD;
    }

    private String bidAccountLabel() {
        AuctionAccountSummary account = selectedAccount();
        if (!account.present()) {
            return Component.translatable("No UBS account").getString();
        }
        String type = account.accountTypeLabel() == null || account.accountTypeLabel().isBlank()
                ? "UBS account"
                : account.accountTypeLabel();
        UUID accountId = account.accountId();
        return type + (accountId == null ? "" : " " + accountId.toString().substring(0, 8));
    }

    private List<AuctionAccountSummary> accountOptions() {
        if (payload == null || payload.accounts() == null || payload.accounts().isEmpty()) {
            return payload != null && payload.account() != null && payload.account().present()
                    ? List.of(payload.account())
                    : List.of();
        }
        return payload.accounts().stream()
                .filter(account -> account != null && account.present())
                .toList();
    }

    private AuctionAccountSummary selectedAccount() {
        ensureSelectedAccount();
        if (selectedAccountId != null) {
            for (AuctionAccountSummary account : accountOptions()) {
                if (selectedAccountId.equals(account.accountId())) {
                    return account;
                }
            }
        }
        return payload == null || payload.account() == null ? AuctionAccountSummary.EMPTY : payload.account();
    }

    private void ensureSelectedAccount() {
        List<AuctionAccountSummary> options = accountOptions();
        if (options.isEmpty()) {
            selectedAccountId = null;
            return;
        }
        if (selectedAccountId != null) {
            for (AuctionAccountSummary account : options) {
                if (selectedAccountId.equals(account.accountId())) {
                    return;
                }
            }
        }
        selectedAccountId = options.stream()
                .filter(AuctionAccountSummary::primary)
                .findFirst()
                .orElse(options.getFirst())
                .accountId();
    }

    private UUID selectedAccountIdForAction() {
        ensureSelectedAccount();
        return selectedAccountId;
    }

    private String accountSelectorButtonLabel() {
        AuctionAccountSummary account = selectedAccount();
        if (!account.present()) {
            return Component.translatable("No UBS account").getString();
        }
        return Component.translatable("Account: {0}", bidAccountLabel()).getString();
    }

    private String accountDetailLine(AuctionAccountSummary account) {
        if (account == null || !account.present()) {
            return Component.translatable("No UBS account").getString();
        }
        String bank = account.bankIdLabel().isBlank()
                ? Component.translatable("Bank unavailable").getString()
                : Component.translatable("Bank: {0}", account.bankIdLabel()).getString();
        String flags = account.primary()
                ? Component.translatable("Primary").getString()
                : Component.translatable("Secondary").getString();
        if (account.frozen()) {
            flags += " - " + Component.translatable("Frozen").getString();
        }
        return bank + " - " + flags;
    }

    private String accountSelectorRowTitle(AuctionAccountSummary account) {
        if (account == null || !account.present()) {
            return Component.translatable("No UBS account").getString();
        }
        String type = account.accountTypeLabel() == null || account.accountTypeLabel().isBlank()
                ? Component.translatable("UBS account").getString()
                : account.accountTypeLabel();
        return Component.translatable("{0} - {1}", type, account.balance()).getString();
    }

    private void openAccountSelector() {
        accountSelectorReturnModal = modal == Modal.NONE ? Modal.BID : modal;
        modal = Modal.ACCOUNT_SELECTOR;
        accountScroll = 0;
        rebuildWidgets();
    }

    private void chooseAccount(AuctionAccountSummary account) {
        if (account == null || !account.present()) {
            return;
        }
        selectedAccountId = account.accountId();
        resetBidReview();
        modal = accountSelectorReturnModal == Modal.ACCOUNT_SELECTOR ? Modal.NONE : accountSelectorReturnModal;
        accountSelectorReturnModal = Modal.NONE;
        rebuildWidgets();
    }

    private AuctionEntrySummary findAuction(AuctionSnapshotPayload source, UUID auctionId) {
        if (source == null || auctionId == null) {
            return selectedAuction;
        }
        AuctionEntrySummary found = findAuction(source.browseListings(), auctionId);
        if (found != null) {
            return found;
        }
        found = findAuction(source.myBids(), auctionId);
        if (found != null) {
            return found;
        }
        found = findAuction(source.myAuctions(), auctionId);
        if (found != null) {
            return found;
        }
        found = findAuction(source.dashboardListings(), auctionId);
        return found == null ? selectedAuction : found;
    }

    private AuctionEntrySummary findAuction(List<AuctionEntrySummary> entries, UUID auctionId) {
        if (entries == null || auctionId == null) {
            return null;
        }
        for (AuctionEntrySummary entry : entries) {
            if (entry != null && auctionId.equals(entry.auctionId())) {
                return entry;
            }
        }
        return null;
    }

    private void sendCreateAction() {
        startingBidDraft = sanitizeMoneyInput(value(startingBidBox, startingBidDraft));
        buyoutDraft = sanitizeMoneyInput(value(buyoutBox, buyoutDraft));
        reserveDraft = sanitizeMoneyInput(value(reserveBox, reserveDraft));
        bundleTitleDraft = value(bundleTitleBox, bundleTitleDraft);
        descriptionDraft = value(descriptionBox, descriptionDraft);
        PacketDistributor.sendToServer(new AuctionActionPayload(
                "PREPARE_CREATE",
                null,
                null,
                selectedInventorySlot,
                selectedInventorySlots,
                createSelectionIsBundle() ? bundleTitleDraft : "",
                "",
                startingBidDraft,
                buyoutDraft,
                reserveDraft,
                sealedBidDraft ? "sealed_bid" : "normal",
                Math.max(1, (int) Duration.between(LocalDateTime.now(), selectedEndDateTime()).toHours()),
                selectedEndDateTime().toString(),
                descriptionDraft,
                searchValue(),
                category.name(),
                sort.name(),
                minPriceValue(),
                maxPriceValue(),
                maxHoursLeft,
                selectedModId,
                selectedAccountIdForAction(),
                payload.adminMode()
        ));
        modal = Modal.NONE;
    }

    private void sendRelistAction() {
        if (selectedAuction == null) {
            closeModal();
            return;
        }
        startingBidDraft = sanitizeMoneyInput(value(startingBidBox, startingBidDraft));
        buyoutDraft = sanitizeMoneyInput(value(buyoutBox, buyoutDraft));
        reserveDraft = sanitizeMoneyInput(value(reserveBox, reserveDraft));
        descriptionDraft = value(descriptionBox, descriptionDraft);
        PacketDistributor.sendToServer(new AuctionActionPayload(
                "RELIST",
                selectedAuction.auctionId(),
                null,
                -1,
                List.of(),
                selectedAuction.bundle() ? selectedAuction.itemName() : "",
                "",
                startingBidDraft,
                buyoutDraft,
                reserveDraft,
                sealedBidDraft ? "sealed_bid" : "normal",
                Math.max(1, (int) Duration.between(LocalDateTime.now(), selectedEndDateTime()).toHours()),
                selectedEndDateTime().toString(),
                descriptionDraft,
                searchValue(),
                category.name(),
                sort.name(),
                minPriceValue(),
                maxPriceValue(),
                maxHoursLeft,
                selectedModId,
                selectedAccountIdForAction(),
                payload.adminMode()
        ));
        modal = Modal.NONE;
    }

    private void sendAuctionAction(String action, AuctionEntrySummary entry, String amount, UUID deliveryId) {
        PacketDistributor.sendToServer(new AuctionActionPayload(
                action,
                entry == null ? null : entry.auctionId(),
                deliveryId,
                -1,
                List.of(),
                "",
                amount == null ? "" : sanitizeMoneyInput(amount),
                "",
                "",
                "",
                "",
                0,
                "",
                "",
                searchValue(),
                category.name(),
                sort.name(),
                minPriceValue(),
                maxPriceValue(),
                maxHoursLeft,
                selectedModId,
                selectedAccountIdForAction(),
                payload.adminMode()
        ));
        modal = Modal.NONE;
    }

    private void sendAdminAuctionAction(String action, AuctionEntrySummary entry) {
        PacketDistributor.sendToServer(new AuctionAdminActionPayload(
                action,
                entry == null ? null : entry.auctionId(),
                null,
                "",
                false,
                false,
                false,
                false,
                "",
                "",
                ""
        ));
        modal = Modal.NONE;
    }

    private void sendAdminForceCancelAction(boolean recoverItems) {
        if (selectedAuction == null) {
            return;
        }
        String reason = value(adminForceCancelReasonBox, adminForceCancelReasonDraft).trim();
        PacketDistributor.sendToServer(new AuctionAdminActionPayload(
                "ADMIN_FORCE_CANCEL",
                selectedAuction.auctionId(),
                null,
                "",
                false,
                false,
                false,
                false,
                reason,
                "",
                recoverItems ? "recover" : "return"
        ));
        modal = Modal.NONE;
    }

    private void sendAdminRecoveryAction(AuctionAdminDashboardPayload.Recovery recovery) {
        if (recovery == null || recovery.recoveryId() == null || !recovery.active()) {
            return;
        }
        PacketDistributor.sendToServer(new AuctionAdminActionPayload(
                "ADMIN_RELEASE_RECOVERY",
                recovery.recoveryId(),
                null,
                "",
                false,
                false,
                false,
                false,
                "Admin recovery release",
                "",
                ""
        ));
    }

    private void sendAdminBanAction(String action, AuctionAdminDashboardPayload.Player player) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToServer(new AuctionAdminActionPayload(
                action,
                null,
                player.playerId(),
                player.playerName(),
                adminBlockCreate,
                adminBlockBid,
                adminBlockBuyout,
                adminBlockWatch,
                value(adminBanReasonBox, adminBanReasonDraft),
                value(adminBanExpiryBox, adminBanExpiryDraft),
                ""
        ));
    }

    private void sendAdminBannedEntryAction(String action, String entry) {
        PacketDistributor.sendToServer(new AuctionAdminActionPayload(
                action,
                null,
                null,
                "",
                false,
                false,
                false,
                false,
                "",
                "",
                entry == null ? "" : entry
        ));
        if ("ADD_BANNED_ENTRY".equals(action)) {
            adminBannedEntryDraft = "";
        }
    }

    private void refreshFromServer() {
        searchRefreshDelay = 0;
        PacketDistributor.sendToServer(AuctionActionPayload.refresh(
                searchValue(),
                category.name(),
                sort.name(),
                minPriceValue(),
                maxPriceValue(),
                maxHoursLeft,
                selectedModId,
                payload.adminMode()
        ));
    }

    private void scheduleSearchRefresh() {
        searchRefreshDelay = SEARCH_REFRESH_DEBOUNCE_TICKS;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        clearCreateHelpHover();
        renderAuctionBackdrop(graphics);
        renderPanel(graphics);
        renderHeader(graphics);
        renderSidebar(graphics);
        renderContent(graphics, mouseX, mouseY);
        if (modal == Modal.NONE) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        renderWidgetRange(graphics, 0, modalRenderableStart, -1, -1, partialTick);
        graphics.flush();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        renderModal(graphics, mouseX, mouseY);
        graphics.pose().popPose();
        graphics.flush();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        renderWidgetRange(graphics, modalRenderableStart, renderables.size(), mouseX, mouseY, partialTick);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 650.0F);
        renderCreateHelpTooltip(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderWidgetRange(GuiGraphics graphics, int start, int end, int mouseX, int mouseY, float partialTick) {
        int safeStart = Math.max(0, Math.min(start, renderables.size()));
        int safeEnd = Math.max(safeStart, Math.min(end, renderables.size()));
        for (int i = safeStart; i < safeEnd; i++) {
            Renderable renderable = renderables.get(i);
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Keep the auction screen readable over the world without Minecraft's global screen blur.
    }

    private void renderAuctionBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB0000000);
    }

    private void renderPanel(GuiGraphics graphics) {
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xFF1E1E1E);
        graphics.fill(panelLeft + 3, panelTop + 3, panelLeft + panelWidth - 3, panelTop + panelHeight - 3, 0xFF6B6B6B);
        graphics.fill(panelLeft + 6, panelTop + 6, panelLeft + panelWidth - 6, panelTop + panelHeight - 6, 0xFF3E3E3E);
    }

    private void renderHeader(GuiGraphics graphics) {
        if (payload.adminMode()) {
            renderAdminHeader(graphics);
            return;
        }
        graphics.fill(panelLeft + 10, panelTop + 10, panelLeft + panelWidth - 10, panelTop + headerHeight - 10, 0xFF565656);
        graphics.drawString(font, Component.translatable(payload.adminMode() ? "ADMIN AUCTIONS" : "AUCTION HOUSE").withStyle(ChatFormatting.BOLD), panelLeft + 18, panelTop + 20, 0xFFFFAA00, false);
        int accountY = panelWidth < 760 ? panelTop + 31 : panelTop + 42;
        AuctionAccountSummary headerAccount = payload.account().present() ? payload.account() : selectedAccount();
        if (headerAccount.present()) {
            graphics.drawString(font, Component.translatable("{0} Primary Account: {1}", localPlayerName(), headerAccount.balance()), panelLeft + 18, accountY, 0xFF55FF55, false);
        } else {
            graphics.drawString(font, Component.translatable("No UBS account"), panelLeft + 18, accountY, 0xFFFF5555, false);
        }
        if (!payload.message().isBlank()) {
            String message = trimToWidth(net.austizz.ultimate_auction_system.i18n.UasTranslations.literal(payload.message()).getString(), panelWidth - 36);
            graphics.drawString(font, Component.literal(message), panelLeft + 18, contentTop - 12, payload.success() ? 0xFF55FF55 : 0xFFFF5555, false);
        }
    }

    private void renderSidebar(GuiGraphics graphics) {
        // Filters are opened from the header as a scrollable side modal.
    }

    private String localPlayerName() {
        if (minecraft != null && minecraft.player != null) {
            return minecraft.player.getName().getString();
        }
        return Component.translatable("Player").getString();
    }

    private void renderAdminHeader(GuiGraphics graphics) {
        graphics.fill(panelLeft + 10, panelTop + 10, panelLeft + panelWidth - 10, panelTop + headerHeight - 10, 0xFF202020);
        graphics.fill(panelLeft + 14, panelTop + 14, panelLeft + panelWidth - 14, panelTop + 58, 0xFF303030);
        graphics.drawString(font, Component.translatable("UAS ADMIN DASHBOARD").withStyle(ChatFormatting.BOLD), panelLeft + 22, panelTop + 22, 0xFFFFAA00, false);
        graphics.drawString(font, Component.translatable(adminSection.label), panelLeft + 22, panelTop + 40, 0xFFE0E0E0, false);
        String generated = adminDashboard().generatedAt().isBlank() ? "" : Component.translatable("Updated {0}", readableDateTime(adminDashboard().generatedAt())).getString();
        graphics.drawString(font, Component.literal(trimToWidth(generated, Math.max(80, panelWidth / 3))), panelLeft + Math.max(210, panelWidth / 3), panelTop + 40, 0xFF9E9E9E, false);
        if (!payload.message().isBlank()) {
            String message = trimToWidth(net.austizz.ultimate_auction_system.i18n.UasTranslations.literal(payload.message()).getString(), panelWidth - 40);
            graphics.drawString(font, Component.literal(message), panelLeft + 22, contentTop - 12, payload.success() ? 0xFF55FF55 : 0xFFFF5555, false);
        }
    }

    private void renderAdminContent(GuiGraphics graphics, int mouseX, int mouseY) {
        if (adminWideNav()) {
            graphics.fill(panelLeft + 14, contentTop - 4, panelLeft + 144, panelTop + panelHeight - 18, 0xFF191919);
        }
        graphics.fill(contentLeft - 4, contentTop - 4, contentLeft + contentWidth + 4, contentTop + contentHeight + 4, 0xFF242424);
        graphics.fill(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight, 0xFF303030);

        switch (adminSection) {
            case OVERVIEW -> renderAdminOverview(graphics, mouseX, mouseY);
            case AUCTIONS -> renderAdminAuctionList(graphics, mouseX, mouseY, "All Auctions");
            case PLAYERS -> renderAdminPlayers(graphics);
            case ECONOMY -> renderAdminEconomy(graphics, mouseX, mouseY);
            case MODERATION -> renderAdminModeration(graphics, mouseX, mouseY);
            case SUSPICION -> renderAdminSuspicion(graphics);
            case BANNED_ITEMS -> renderAdminBannedItems(graphics);
            case RECOVERY -> renderAdminRecovery(graphics);
            case AUDIT -> renderAdminAudit(graphics);
        }
    }

    private void renderAdminOverview(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = contentTop + 14 - adminScroll;
        String hoverText = null;
        graphics.enableScissor(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);
        renderAdminStatsGrid(graphics, contentLeft + 12, y, contentWidth - 24, true);
        y += adminStatsGridHeight(true) + 18;
        hoverText = renderAdminEconomyChart(graphics, contentLeft + 12, y, contentWidth - 24, 156, mouseX, mouseY);
        y += 174;
        graphics.drawString(font, Component.translatable("Moderation Queues").withStyle(ChatFormatting.BOLD), contentLeft + 12, y, 0xFFFFFFFF, false);
        y += 20;
        y = renderAdminQueuePreview(graphics, "Now Restricted", adminDashboard().restrictedListings(), y);
        y = renderAdminQueuePreview(graphics, "Failed Settlements", adminDashboard().failedSettlements(), y + 8);
        graphics.disableScissor();
        if (hoverText != null) {
            graphics.renderTooltip(font, Component.literal(hoverText), mouseX, mouseY);
        }
        renderScrollBar(graphics, contentLeft + contentWidth - 6, contentTop, contentTop + contentHeight, adminScroll, adminContentHeight(), contentHeight);
    }

    private void renderAdminEconomy(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = contentTop + 14 - adminScroll;
        String hoverText = null;
        graphics.enableScissor(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);
        hoverText = renderAdminEconomyChart(graphics, contentLeft + 12, y, contentWidth - 24, 176, mouseX, mouseY);
        y += 196;
        renderAdminStatsGrid(graphics, contentLeft + 12, y, contentWidth - 24, false);
        y += adminStatsGridHeight(false) + 12;
        renderAdminEconomyReports(graphics, contentLeft + 12, y, contentWidth - 24);
        graphics.disableScissor();
        if (hoverText != null) {
            graphics.renderTooltip(font, Component.literal(hoverText), mouseX, mouseY);
        }
        renderScrollBar(graphics, contentLeft + contentWidth - 6, contentTop, contentTop + contentHeight, adminScroll, adminContentHeight(), contentHeight);
    }

    private String renderAdminEconomyChart(GuiGraphics graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        renderAdminCard(graphics, x, y, w, h);
        graphics.drawString(font, Component.translatable("Economy Flow").withStyle(ChatFormatting.BOLD), x + 10, y + 10, 0xFFFFAA00, false);
        List<AuctionAdminDashboardPayload.Stats> stats = adminDashboard().stats();
        if (stats.isEmpty()) {
            graphics.drawString(font, Component.translatable("No economy data yet"), x + 10, y + 34, 0xFFBDBDBD, false);
            return null;
        }

        int legendY = y + 28;
        int legendX = x + 10;
        legendX = renderChartLegend(graphics, legendX, legendY, "Bids", 0xFFFFD700);
        legendX = renderChartLegend(graphics, legendX + 10, legendY, "Sales", 0xFF55FF55);
        legendX = renderChartLegend(graphics, legendX + 10, legendY, "Fees", 0xFFFFAA00);
        renderChartLegend(graphics, legendX + 10, legendY, "Tax", 0xFFFF6666);

        BigDecimal max = BigDecimal.ONE;
        for (AuctionAdminDashboardPayload.Stats stat : stats) {
            for (ChartMetric metric : economyMetrics(stat)) {
                max = max.max(metric.value());
            }
        }

        int rowTop = y + 48;
        int rowH = Math.max(28, (h - 58) / Math.max(1, stats.size()));
        int labelW = Math.min(82, Math.max(46, w / 5));
        int barX = x + labelW + 16;
        int barW = Math.max(36, w - labelW - 34);
        String hoverText = null;
        for (int i = 0; i < stats.size(); i++) {
            AuctionAdminDashboardPayload.Stats stat = stats.get(i);
            int statY = rowTop + i * rowH;
            graphics.drawString(font, Component.literal(trimToWidth(Component.translatable(stat.label()).getString(), labelW)), x + 10, statY + 8, 0xFFE0E0E0, false);
            List<ChartMetric> metrics = economyMetrics(stat);
            int barH = Math.max(3, Math.min(5, (rowH - 8) / Math.max(1, metrics.size())));
            int gap = Math.max(2, (rowH - 8 - metrics.size() * barH) / Math.max(1, metrics.size()));
            int metricY = statY + 4;
            for (ChartMetric metric : metrics) {
                int valueW = metric.value().compareTo(BigDecimal.ZERO) <= 0
                        ? 1
                        : metric.value()
                        .multiply(BigDecimal.valueOf(barW))
                        .divide(max, 0, RoundingMode.HALF_UP)
                        .intValue();
                graphics.fill(barX, metricY, barX + barW, metricY + barH, 0xFF151515);
                graphics.fill(barX, metricY, barX + Math.min(barW, valueW), metricY + barH, metric.color());
                if (mouseX >= barX && mouseX <= barX + barW && mouseY >= metricY && mouseY <= metricY + barH
                        && mouseY >= contentTop && mouseY <= contentTop + contentHeight) {
                    hoverText = Component.translatable("{0} {1}: {2}", Component.translatable(stat.label()), Component.translatable(metric.label()), metric.display()).getString();
                }
                metricY += barH + gap;
            }
        }
        return hoverText;
    }

    private int renderChartLegend(GuiGraphics graphics, int x, int y, String label, int color) {
        graphics.fill(x, y + 2, x + 8, y + 10, color);
        String translated = Component.translatable(label).getString();
        graphics.drawString(font, Component.literal(translated), x + 12, y + 2, 0xFFBDBDBD, false);
        return x + 12 + font.width(translated);
    }

    private List<ChartMetric> economyMetrics(AuctionAdminDashboardPayload.Stats stat) {
        return List.of(
                new ChartMetric("Bids", 0xFFFFD700, moneyDraft(stat.bidVolume()), stat.bidVolume()),
                new ChartMetric("Sales", 0xFF55FF55, moneyDraft(stat.soldValue()), stat.soldValue()),
                new ChartMetric("Fees", 0xFFFFAA00, moneyDraft(stat.estimatedListingFees()), stat.estimatedListingFees()),
                new ChartMetric("Tax", 0xFFFF6666, moneyDraft(stat.estimatedSalesTax()), stat.estimatedSalesTax())
        );
    }

    private void renderAdminStatsGrid(GuiGraphics graphics, int x, int y, int w, boolean compact) {
        int columns = w >= 620 ? 3 : 1;
        int cardW = columns == 1 ? w : (w - 16) / 3;
        int cardH = compact ? 100 : 136;
        List<AuctionAdminDashboardPayload.Stats> stats = adminDashboard().stats();
        for (int i = 0; i < stats.size(); i++) {
            AuctionAdminDashboardPayload.Stats stat = stats.get(i);
            int col = i % columns;
            int row = i / columns;
            int cardX = x + col * (cardW + 8);
            int cardY = y + row * (cardH + 10);
            renderAdminCard(graphics, cardX, cardY, cardW, cardH);
            graphics.drawString(font, Component.translatable(stat.label()).withStyle(ChatFormatting.BOLD), cardX + 10, cardY + 10, 0xFFFFAA00, false);
            graphics.drawString(font, Component.translatable("Created {0}  Active {1}", stat.auctionsCreated(), stat.activeAuctions()), cardX + 10, cardY + 28, 0xFFE0E0E0, false);
            graphics.drawString(font, Component.translatable("Sold {0}  Cancelled {1}", stat.soldAuctions(), stat.cancelledAuctions()), cardX + 10, cardY + 42, 0xFFBDBDBD, false);
            graphics.drawString(font, Component.translatable("Bids {0}", stat.bidVolume()), cardX + 10, cardY + 58, 0xFFFFD700, false);
            graphics.drawString(font, Component.translatable("Sales {0}", stat.soldValue()), cardX + 10, cardY + 72, 0xFF55FF55, false);
            if (!compact) {
                graphics.drawString(font, Component.translatable("Fees {0}", stat.estimatedListingFees()), cardX + 10, cardY + 90, 0xFFFFD700, false);
                graphics.drawString(font, Component.translatable("Tax {0}", stat.estimatedSalesTax()), cardX + 10, cardY + 104, 0xFFFFD700, false);
                graphics.drawString(font, Component.translatable("Avg {0}", stat.averageSale()), cardX + 10, cardY + 118, 0xFFE0E0E0, false);
            }
        }
    }

    private int adminStatsGridHeight(boolean compact) {
        int columns = contentWidth >= 620 ? 3 : 1;
        int rows = Math.max(1, (int) Math.ceil(adminDashboard().stats().size() / (double) columns));
        return rows * (compact ? 110 : 146);
    }

    private int renderAdminQueuePreview(GuiGraphics graphics, String title, List<AuctionEntrySummary> entries, int y) {
        renderAdminCard(graphics, contentLeft + 12, y, contentWidth - 24, 58);
        graphics.drawString(font, Component.translatable(title).withStyle(ChatFormatting.BOLD), contentLeft + 22, y + 10, 0xFFFFFFFF, false);
        String detail = entries.isEmpty()
                ? Component.translatable("No auctions in this queue").getString()
                : Component.translatable("{0} auction(s), first: {1}", entries.size(), entries.getFirst().itemName()).getString();
        graphics.drawString(font, Component.literal(trimToWidth(detail, contentWidth - 56)), contentLeft + 22, y + 30, entries.isEmpty() ? 0xFF9E9E9E : 0xFFFFD700, false);
        return y + 66;
    }

    private void renderAdminEconomyReports(GuiGraphics graphics, int x, int y, int w) {
        List<AuctionAdminDashboardPayload.EconomyReport> reports = adminDashboard().economyReports();
        if (reports.isEmpty()) {
            return;
        }
        int columns = w >= 620 ? 3 : 1;
        int cardW = columns == 1 ? w : (w - 16) / 3;
        int cardH = 116;
        for (int i = 0; i < reports.size(); i++) {
            AuctionAdminDashboardPayload.EconomyReport report = reports.get(i);
            int col = i % columns;
            int row = i / columns;
            int cardX = x + col * (cardW + 8);
            int cardY = y + row * (cardH + 10);
            renderAdminCard(graphics, cardX, cardY, cardW, cardH);
            graphics.drawString(font, Component.translatable("Economy Report: {0}", Component.translatable(report.label())).withStyle(ChatFormatting.BOLD), cardX + 10, cardY + 10, 0xFFFFAA00, false);
            graphics.drawString(font, Component.translatable("Active {0}  Sold {1}  Failed {2}", report.activeListings(), report.completedSales(), report.failedSettlements()), cardX + 10, cardY + 28, 0xFFE0E0E0, false);
            graphics.drawString(font, Component.translatable("Gross {0}  Fees {1}", report.grossVolume(), report.fees()), cardX + 10, cardY + 42, 0xFFFFD700, false);
            graphics.drawString(font, Component.translatable("Taxes {0}", report.taxes()), cardX + 10, cardY + 56, 0xFFFF6666, false);
            renderTopEconomyRow(graphics, "Top seller: {0}", report.topSellers(), cardX + 10, cardY + 72, cardW - 20);
            renderTopEconomyRow(graphics, "Top category: {0}", report.topCategories(), cardX + 10, cardY + 86, cardW - 20);
            renderTopEconomyRow(graphics, "Top item: {0}", report.topItems(), cardX + 10, cardY + 100, cardW - 20);
        }
    }

    private void renderTopEconomyRow(GuiGraphics graphics,
                                     String labelKey,
                                     List<AuctionAdminDashboardPayload.EconomyRow> rows,
                                     int x,
                                     int y,
                                     int w) {
        String value = rows.isEmpty()
                ? Component.translatable("None").getString()
                : Component.translatable("{0} ({1}, {2})", rows.getFirst().label(), rows.getFirst().count(), rows.getFirst().amount()).getString();
        graphics.drawString(font, Component.literal(trimToWidth(Component.translatable(labelKey, value).getString(), w)), x, y, 0xFFBDBDBD, false);
    }

    private void renderAdminAuctionList(GuiGraphics graphics, int mouseX, int mouseY, String title) {
        graphics.drawString(font, Component.translatable(title).withStyle(ChatFormatting.BOLD), contentLeft + 10, contentTop + 8, 0xFFFFFFFF, false);
        renderAdminAuctionRows(graphics, mouseX, mouseY, contentTop + 30);
    }

    private void renderAdminModeration(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("Flagged Auctions").withStyle(ChatFormatting.BOLD), contentLeft + 10, contentTop + 8, 0xFFFFFFFF, false);
        renderAdminAuctionRows(graphics, mouseX, mouseY, contentTop + 30);
    }

    private void renderAdminSuspicion(GuiGraphics graphics) {
        int listTop = contentTop + 30;
        int rowH = 58;
        List<AuctionAdminDashboardPayload.Suspicion> signals = adminDashboard().suspicionSignals();
        graphics.drawString(font, Component.translatable("Suspicious Bid Patterns").withStyle(ChatFormatting.BOLD), contentLeft + 10, contentTop + 8, 0xFFFFFFFF, false);
        if (signals.isEmpty()) {
            graphics.drawString(font, Component.translatable("No suspicion signals."), contentLeft + 10, listTop + 16, 0xFFBDBDBD, false);
            return;
        }

        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - listTop) / rowH);
        int end = Math.min(signals.size(), start + rows + 1);
        graphics.enableScissor(contentLeft, listTop, contentLeft + contentWidth, adminListBottom());
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.Suspicion signal = signals.get(i);
            int rowY = listTop + i * rowH - adminScroll;
            if (rowY + rowH < listTop || rowY > adminListBottom()) {
                continue;
            }
            graphics.fill(contentLeft, rowY, contentLeft + contentWidth - 10, rowY + rowH - 4, 0xFF000000);
            graphics.fill(contentLeft + 2, rowY + 2, contentLeft + contentWidth - 12, rowY + rowH - 6, 0xFF241F16);
            int textW = Math.max(80, contentWidth - 130);
            graphics.drawString(font, suspicionTypeLabel(signal.type()).withStyle(ChatFormatting.BOLD), contentLeft + 10, rowY + 7, 0xFFFFD700, false);
            String target = signal.itemName().isBlank()
                    ? Component.translatable("Player: {0}", signal.primaryPlayerName()).getString()
                    : signal.itemName();
            graphics.drawString(font, Component.literal(trimToWidth(target, textW)), contentLeft + 10, rowY + 21, 0xFFE0E0E0, false);
            graphics.drawString(font, Component.literal(trimToWidth(suspicionDetail(signal).getString(), textW)), contentLeft + 10, rowY + 35, 0xFFBDBDBD, false);
            graphics.drawString(font, Component.literal(readableDateTime(signal.observedAt())), contentLeft + contentWidth - 146, rowY + 7, 0xFF9E9E9E, false);
        }
        graphics.disableScissor();
        renderScrollBar(graphics, contentLeft + contentWidth - 6, listTop, adminListBottom(), adminScroll, adminContentHeight(), Math.max(1, adminListBottom() - listTop));
    }

    private void renderAdminAuctionRows(GuiGraphics graphics, int mouseX, int mouseY, int listTop) {
        List<AuctionEntrySummary> entries = visibleEntries();
        int rowHeight = auctionRowHeight();
        int listBottom = adminListBottom();
        int start = Math.max(0, auctionScroll / rowHeight);
        int visibleRows = Math.max(1, (listBottom - listTop) / rowHeight);
        int end = Math.min(entries.size(), start + visibleRows + 2);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("No admin auctions to show"), contentLeft + contentWidth / 2, listTop + 60, 0xFFDDDDDD);
            return;
        }
        graphics.enableScissor(contentLeft, listTop, contentLeft + contentWidth, listBottom);
        for (int i = start; i < end; i++) {
            AuctionEntrySummary entry = entries.get(i);
            int y = listTop + i * rowHeight - auctionScroll;
            if (y + rowHeight < listTop || y > listBottom) {
                continue;
            }
            renderAuctionRow(graphics, entry, contentLeft, y, contentWidth, rowHeight - 8, mouseX, mouseY);
        }
        graphics.disableScissor();
        renderScrollBar(graphics, contentLeft + contentWidth - 6, listTop, listBottom, auctionScroll, auctionListContentHeight(), Math.max(1, listBottom - listTop));
    }

    private void renderAdminPlayers(GuiGraphics graphics) {
        int y = adminPlayerListTop();
        AuctionAdminDashboardPayload.Player selected = selectedAdminPlayer();
        if (selected != null) {
            renderAdminCard(graphics, contentLeft, contentTop + 38, contentWidth - 10, 232);
            graphics.drawString(font, Component.translatable("Inspecting {0}", selected.playerName()).withStyle(ChatFormatting.BOLD), contentLeft + 10, contentTop + 48, 0xFFFFAA00, false);
            graphics.drawString(font, Component.translatable("Active {0}  Bids {1}  Sold {2}  Bought {3}", selected.activeListings(), selected.bidCount(), selected.soldCount(), selected.boughtCount()), contentLeft + 10, contentTop + 64, 0xFFE0E0E0, false);
            graphics.drawString(font, Component.translatable("Bid volume {0}  Sold value {1}", selected.bidVolume(), selected.soldValue()), contentLeft + 10, contentTop + 80, 0xFFBDBDBD, false);
            int maxListings = Math.max(1, selected.maxActiveListings());
            int limitColor = selected.activeListings() >= maxListings ? 0xFFFF6666 : selected.activeListings() >= Math.max(1, maxListings * 8 / 10) ? 0xFFFFD700 : 0xFF55FF55;
            graphics.drawString(font, Component.translatable("Listing limit {0} / {1} active", selected.activeListings(), maxListings), contentLeft + 10, contentTop + 96, limitColor, false);
            graphics.drawString(font, Component.translatable("Delivery storage {0} item(s)", selected.deliveryCount()), contentLeft + 10, contentTop + 112, selected.deliveryCount() > 0 ? 0xFFFFD700 : 0xFFBDBDBD, false);
            if (!selected.deliveryPreview().isBlank()) {
                graphics.drawString(font, Component.literal(trimToWidth(selected.deliveryPreview(), contentWidth - 36)), contentLeft + 10, contentTop + 128, 0xFFE0E0E0, false);
            }
            graphics.drawString(font, Component.translatable("Blocked Actions"), contentLeft + 10, contentTop + 144, 0xFFBDBDBD, false);
        }

        List<AuctionAdminDashboardPayload.Player> players = filteredAdminPlayers();
        int rowH = 44;
        int listBottom = adminListBottom();
        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (listBottom - y) / rowH);
        int end = Math.min(players.size(), start + rows + 1);
        graphics.enableScissor(contentLeft, y, contentLeft + contentWidth, listBottom);
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.Player player = players.get(i);
            int rowY = y + i * rowH - adminScroll;
            if (rowY + rowH < y || rowY > listBottom) {
                continue;
            }
            boolean selectedRow = player.playerId().equals(selectedAdminPlayerId);
            int fill = selectedRow ? 0xFF2F6F35 : 0xFF242424;
            graphics.fill(contentLeft, rowY, contentLeft + contentWidth - 10, rowY + rowH - 4, 0xFF000000);
            graphics.fill(contentLeft + 2, rowY + 2, contentLeft + contentWidth - 12, rowY + rowH - 6, fill);
            graphics.drawString(font, Component.literal(trimToWidth(player.playerName(), contentWidth / 2)).withStyle(ChatFormatting.BOLD), contentLeft + 10, rowY + 8, selectedRow ? 0xFFFFFFFF : 0xFFE0E0E0, false);
            graphics.drawString(font, Component.translatable("Active {0} | Bids {1} | Sold {2}", player.activeListings(), player.bidCount(), player.soldCount()), contentLeft + 10, rowY + 24, 0xFFBDBDBD, false);
            if (player.banActive()) {
                graphics.drawString(font, Component.translatable("BANNED"), contentLeft + contentWidth - 76, rowY + 14, 0xFFFF5555, false);
            }
        }
        graphics.disableScissor();
        renderScrollBar(graphics, contentLeft + contentWidth - 6, y, listBottom, adminScroll, adminContentHeight(), Math.max(1, listBottom - y));
    }

    private void renderAdminBannedItems(GuiGraphics graphics) {
        int listTop = contentTop + 44;
        int rowH = 36;
        List<AuctionAdminDashboardPayload.BannedEntry> entries = adminDashboard().bannedEntries();
        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - listTop) / rowH);
        int end = Math.min(entries.size(), start + rows + 1);
        graphics.drawString(font, Component.translatable("Item id, #tag, or @modid").withStyle(ChatFormatting.BOLD), contentLeft, contentTop + 34, 0xFFBDBDBD, false);
        graphics.enableScissor(contentLeft, listTop, contentLeft + contentWidth, adminListBottom());
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.BannedEntry entry = entries.get(i);
            int rowY = listTop + i * rowH - adminScroll;
            if (rowY + rowH < listTop || rowY > adminListBottom()) {
                continue;
            }
            graphics.fill(contentLeft, rowY, contentLeft + contentWidth - 10, rowY + rowH - 4, 0xFF000000);
            graphics.fill(contentLeft + 2, rowY + 2, contentLeft + contentWidth - 12, rowY + rowH - 6, 0xFF242424);
            graphics.drawString(font, Component.literal(entry.type()).append(Component.translatable(": ")).append(Component.literal(trimToWidth(entry.label(), contentWidth - 160))).withStyle(ChatFormatting.BOLD), contentLeft + 10, rowY + 7, 0xFFE0E0E0, false);
            graphics.drawString(font, Component.translatable("{0} active match(es)", entry.matchingActiveAuctions()), contentLeft + 10, rowY + 21, entry.matchingActiveAuctions() > 0 ? 0xFFFFD700 : 0xFF9E9E9E, false);
        }
        graphics.disableScissor();
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("No banned auction entries configured."), contentLeft + 10, listTop + 12, 0xFFBDBDBD, false);
        }
        renderScrollBar(graphics, contentLeft + contentWidth - 6, listTop, adminListBottom(), adminScroll, adminContentHeight(), Math.max(1, adminListBottom() - listTop));
    }

    private void renderAdminRecovery(GuiGraphics graphics) {
        int listTop = contentTop + 30;
        int rowH = 62;
        List<AuctionAdminDashboardPayload.Recovery> entries = adminDashboard().recoveryEntries();
        graphics.drawString(font, Component.translatable("Admin Recovery Storage").withStyle(ChatFormatting.BOLD), contentLeft + 10, contentTop + 8, 0xFFFFFFFF, false);
        if (entries.isEmpty()) {
            graphics.drawString(font, Component.translatable("No recovered items in storage."), contentLeft + 10, listTop + 16, 0xFFBDBDBD, false);
            return;
        }

        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - listTop) / rowH);
        int end = Math.min(entries.size(), start + rows + 1);
        graphics.enableScissor(contentLeft, listTop, contentLeft + contentWidth, adminListBottom());
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.Recovery entry = entries.get(i);
            int rowY = listTop + i * rowH - adminScroll;
            if (rowY + rowH < listTop || rowY > adminListBottom()) {
                continue;
            }
            int fill = entry.active() ? 0xFF242424 : 0xFF1D301F;
            graphics.fill(contentLeft, rowY, contentLeft + contentWidth - 10, rowY + rowH - 4, 0xFF000000);
            graphics.fill(contentLeft + 2, rowY + 2, contentLeft + contentWidth - 12, rowY + rowH - 6, fill);
            int textW = Math.max(80, contentWidth - 150);
            graphics.drawString(font, Component.literal(trimToWidth(entry.itemName(), textW)).withStyle(ChatFormatting.BOLD), contentLeft + 10, rowY + 8, 0xFFFFAA00, false);
            graphics.drawString(font, Component.translatable("Seller: {0}", entry.sellerName()), contentLeft + 10, rowY + 22, 0xFFBDBDBD, false);
            graphics.drawString(font, Component.translatable("Recovered by {0}", entry.adminName()), contentLeft + 10, rowY + 36, 0xFFE0E0E0, false);
            String reason = Component.translatable("Reason: {0}", entry.reason()).getString();
            graphics.drawString(font, Component.literal(trimToWidth(reason, textW)), contentLeft + Math.min(260, contentWidth / 2), rowY + 22, 0xFFBDBDBD, false);
            String status = entry.active()
                    ? Component.translatable("Active recovery item").getString()
                    : Component.translatable("Released by {0}", entry.releasedByName()).getString();
            graphics.drawString(font, Component.literal(trimToWidth(status, Math.max(80, textW / 2))), contentLeft + Math.min(260, contentWidth / 2), rowY + 36, entry.active() ? 0xFFFFD700 : 0xFF55FF55, false);
        }
        graphics.disableScissor();
        renderScrollBar(graphics, contentLeft + contentWidth - 6, listTop, adminListBottom(), adminScroll, adminContentHeight(), Math.max(1, adminListBottom() - listTop));
    }

    private void renderAdminAudit(GuiGraphics graphics) {
        int y = contentTop + 12;
        int rowH = 46;
        List<AuctionAdminDashboardPayload.Audit> audit = adminDashboard().auditLog();
        int start = Math.max(0, adminScroll / rowH);
        int rows = Math.max(1, (adminListBottom() - y) / rowH);
        int end = Math.min(audit.size(), start + rows + 1);
        graphics.enableScissor(contentLeft, y, contentLeft + contentWidth, adminListBottom());
        for (int i = start; i < end; i++) {
            AuctionAdminDashboardPayload.Audit entry = audit.get(i);
            int rowY = y + i * rowH - adminScroll;
            if (rowY + rowH < y || rowY > adminListBottom()) {
                continue;
            }
            graphics.fill(contentLeft, rowY, contentLeft + contentWidth - 10, rowY + rowH - 4, 0xFF000000);
            graphics.fill(contentLeft + 2, rowY + 2, contentLeft + contentWidth - 12, rowY + rowH - 6, entry.success() ? 0xFF242424 : 0xFF3A1C1C);
            graphics.drawString(font, Component.translatable("{0} by {1}", auditActionLabel(entry.action()), auditActorLabel(entry.adminName())).withStyle(ChatFormatting.BOLD), contentLeft + 10, rowY + 7, entry.success() ? 0xFFE0E0E0 : 0xFFFF7777, false);
            graphics.drawString(font, Component.literal(trimToWidth(entry.target(), contentWidth - 40)), contentLeft + 10, rowY + 21, 0xFFBDBDBD, false);
            graphics.drawString(font, Component.literal(readableDateTime(entry.createdAt())), contentLeft + contentWidth - 150, rowY + 7, 0xFF9E9E9E, false);
        }
        graphics.disableScissor();
        if (audit.isEmpty()) {
            graphics.drawString(font, Component.translatable("No admin audit entries yet."), contentLeft + 10, y + 12, 0xFFBDBDBD, false);
        }
        renderScrollBar(graphics, contentLeft + contentWidth - 6, y, adminListBottom(), adminScroll, adminContentHeight(), Math.max(1, adminListBottom() - y));
    }

    private Component auditActionLabel(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PLAYER_BAN" -> Component.translatable("Player banned");
            case "PLAYER_UNBAN" -> Component.translatable("Player unbanned");
            case "ADMIN_FORCE_CANCEL" -> Component.translatable("Admin force-cancelled auction");
            case "ADMIN_RETRY_SETTLEMENT" -> Component.translatable("Admin retried settlement");
            case "ADMIN_RELEASE_RECOVERY" -> Component.translatable("Admin released recovery item");
            case "SUSPICIOUS_BID_PATTERN" -> Component.translatable("Suspicious bid pattern");
            case "BANNED_ENTRY_ADD" -> Component.translatable("Banned item added");
            case "BANNED_ENTRY_REMOVE" -> Component.translatable("Banned item removed");
            default -> Component.literal(action == null || action.isBlank() ? "-" : action);
        };
    }

    private Component auditActorLabel(String actorName) {
        if ("UAS Audit".equals(actorName)) {
            return Component.translatable("UAS Audit");
        }
        return Component.literal(actorName == null || actorName.isBlank() ? "-" : actorName);
    }

    private MutableComponent suspicionTypeLabel(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RAPID_BID_ESCALATION" -> Component.translatable("Rapid bid escalation");
            case "REPEATED_BIDDER_PAIR" -> Component.translatable("Repeated bidder pair");
            case "SELLER_SELF_BID" -> Component.translatable("Seller self-bid signal");
            case "REPEATED_CANCELLED_LISTINGS" -> Component.translatable("Repeated cancelled listings");
            default -> Component.translatable("Unknown suspicion signal");
        };
    }

    private Component suspicionDetail(AuctionAdminDashboardPayload.Suspicion signal) {
        String type = signal.type();
        if ("RAPID_BID_ESCALATION".equals(type)) {
            return Component.translatable(
                    "Rapid price movement: {0} bids in {1} minutes, {2} to {3}",
                    signal.evidenceCount(),
                    Math.max(1, signal.windowSeconds() / 60),
                    signal.startAmount(),
                    signal.endAmount()
            );
        }
        if ("REPEATED_BIDDER_PAIR".equals(type)) {
            return Component.translatable(
                    "Repeated outbid pair: {0} and {1}, {2} turns",
                    signal.primaryPlayerName().isBlank() ? Component.translatable("Unknown") : signal.primaryPlayerName(),
                    signal.secondaryPlayerName().isBlank() ? Component.translatable("Unknown") : signal.secondaryPlayerName(),
                    signal.evidenceCount()
            );
        }
        if ("SELLER_SELF_BID".equals(type)) {
            return Component.translatable(
                    "Seller self-bid evidence: {0} event(s) for {1}",
                    signal.evidenceCount(),
                    signal.primaryPlayerName().isBlank() ? Component.translatable("Unknown") : signal.primaryPlayerName()
            );
        }
        if ("REPEATED_CANCELLED_LISTINGS".equals(type)) {
            return Component.translatable(
                    "Repeated cancellations: {0} listings in {1} hours by {2}",
                    signal.evidenceCount(),
                    Math.max(1, signal.windowSeconds() / 3600),
                    signal.primaryPlayerName().isBlank() ? Component.translatable("Unknown") : signal.primaryPlayerName()
            );
        }
        return Component.translatable("Evidence count: {0}", signal.evidenceCount());
    }

    private void renderAdminCard(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF191919);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 4, 0xFF555555);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        if (payload.adminMode()) {
            renderAdminContent(graphics, mouseX, mouseY);
            return;
        }
        if (activeTab == Tab.DASHBOARD) {
            renderDashboardContent(graphics, mouseX, mouseY);
            return;
        }
        List<AuctionEntrySummary> entries = visibleEntries();
        int rowHeight = auctionRowHeight();
        int listTop = auctionListTop();
        int listBottom = auctionListBottom();
        int start = Math.max(0, auctionScroll / rowHeight);
        int visibleRows = Math.max(1, auctionListViewportHeight() / rowHeight);
        int end = Math.min(entries.size(), start + visibleRows + 2);

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("No auctions to show"), contentLeft + contentWidth / 2, contentTop + 70, 0xFFDDDDDD);
            return;
        }

        graphics.enableScissor(contentLeft, listTop, contentLeft + contentWidth, listBottom);
        for (int i = start; i < end; i++) {
            AuctionEntrySummary entry = entries.get(i);
            int y = listTop + i * rowHeight - auctionScroll;
            if (y + rowHeight < listTop || y > listBottom) {
                continue;
            }
            renderAuctionRow(graphics, entry, contentLeft, y, contentWidth, rowHeight - 8, mouseX, mouseY);
        }
        graphics.disableScissor();
        renderScrollBar(graphics, contentLeft + contentWidth - 6, listTop, listBottom, auctionScroll, auctionListContentHeight(), auctionListViewportHeight());
    }

    private void renderDashboardContent(GuiGraphics graphics, int mouseX, int mouseY) {
        renderDashboardSummary(graphics);
        int listTop = auctionListTop();
        int listBottom = auctionListBottom();
        int rowHeight = auctionRowHeight();
        int y = listTop - auctionScroll;
        List<DashboardSection> sections = dashboardSections();

        graphics.enableScissor(contentLeft, listTop, contentLeft + contentWidth, listBottom);
        for (DashboardSection section : sections) {
            int headerY = y;
            if (headerY + 22 >= listTop && headerY <= listBottom) {
                graphics.fill(contentLeft, headerY, contentLeft + contentWidth - 10, headerY + 20, 0xFF1F1F1F);
                graphics.fill(contentLeft, headerY, contentLeft + 4, headerY + 20, section.color());
                graphics.drawString(font, dashboardSectionHeaderLabel(section, contentWidth - 58), contentLeft + 10, headerY + 6, section.color(), false);
            }
            y += DASHBOARD_SECTION_HEADER_HEIGHT;
            if (dashboardSectionExpanded(section)) {
                if (section.entries().isEmpty()) {
                    if (y + 20 >= listTop && y <= listBottom) {
                        graphics.drawString(font, Component.translatable("No auctions in this section"), contentLeft + 12, y + 6, 0xFF9E9E9E, false);
                    }
                    y += DASHBOARD_SECTION_EMPTY_HEIGHT;
                } else {
                    for (AuctionEntrySummary entry : section.entries()) {
                        if (y + rowHeight >= listTop && y <= listBottom) {
                            renderAuctionRow(graphics, entry, contentLeft, y, contentWidth, rowHeight - 8, mouseX, mouseY);
                        }
                        y += rowHeight;
                    }
                }
            }
            y += DASHBOARD_SECTION_GAP;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, contentLeft + contentWidth - 6, listTop, listBottom, auctionScroll, auctionListContentHeight(), auctionListViewportHeight());
    }

    private void renderDashboardSummary(GuiGraphics graphics) {
        List<DashboardSection> sections = dashboardSections();
        int cardGap = 6;
        int cardCount = Math.min(5, sections.size());
        int cardW = Math.max(64, (contentWidth - cardGap * Math.max(0, cardCount - 1)) / Math.max(1, cardCount));
        int x = contentLeft;
        int y = contentTop + 8;
        for (int i = 0; i < cardCount; i++) {
            DashboardSection section = sections.get(i);
            int w = i == cardCount - 1 ? contentLeft + contentWidth - x : cardW;
            graphics.fill(x, y, x + w, y + 54, 0xFF000000);
            graphics.fill(x + 2, y + 2, x + w - 2, y + 52, 0xFF191919);
            graphics.fill(x + 2, y + 2, x + w - 2, y + 4, section.color());
            graphics.drawString(font, Component.literal(trimToWidth(Component.translatable(section.title()).getString(), w - 12)), x + 8, y + 12, section.color(), false);
            graphics.drawString(font, Component.literal(String.valueOf(section.entries().size())).withStyle(ChatFormatting.BOLD), x + 8, y + 30, 0xFFFFFFFF, false);
            x += w + cardGap;
        }
    }

    private void renderAuctionRow(GuiGraphics graphics, AuctionEntrySummary entry, int x, int y, int w, int h, int mouseX, int mouseY) {
        graphics.fill(x, y, x + w, y + h, 0xFF2B2B2B);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF414141);
        int rarityColor = rarityColor(entry.rarity());
        int actionColumnW = rowActionColumnWidth(entry);
        int textX = x + 64;
        String status = isClaimed(entry) ? "CLAIMED" : entry.viewerIsHighestBidder() ? "WINNING" : entry.state();
        Component statusComponent = Component.translatable(status);
        int statusWidth = font.width(statusComponent);
        int statusX = Math.max(textX, x + w - actionColumnW - statusWidth - 8);
        int textW = Math.max(80, statusX - textX - 8);
        graphics.fill(x + 10, y + 10, x + 54, y + 54, 0xFF1C1C1C);
        graphics.fill(x + 13, y + 13, x + 51, y + 51, 0x33000000 | (rarityColor & 0x00FFFFFF));
        renderBundlePreview(graphics, entry.contents(), x + 13, y + 13, 38, 38);

        String itemTitle = entry.bundle() ? entry.itemName() : (entry.item().getCount() > 1 ? entry.item().getCount() + "x " : "") + entry.itemName();
        graphics.drawString(font, Component.literal(trimToWidth(itemTitle, textW)), textX, y + 12, rarityColor, false);
        graphics.drawString(font, Component.literal(Component.translatable("Seller").getString() + ": " + trimToWidth(entry.sellerName(), textW - 40)), textX, y + 28, 0xFFBDBDBD, false);
        graphics.drawString(font, Component.literal(Component.translatable("Bid").getString() + ": " + currentBidText(entry)), textX, y + 44, 0xFFFFD966, false);
        int metaY = y + 44;
        if (textW >= 230) {
            graphics.drawString(font, Component.literal(Component.translatable("Time").getString() + ": " + timeLeft(entry.endsAt(), entry.state())), textX + 108, metaY, 0xFFA5D6A7, false);
        } else {
            metaY += 12;
            graphics.drawString(font, Component.literal(Component.translatable("Time").getString() + ": " + timeLeft(entry.endsAt(), entry.state())), textX, metaY, 0xFFA5D6A7, false);
        }
        int buyoutY = metaY + 16;
        boolean hasBuyout = !isZeroMoneyLabel(entry.buyoutPrice());
        if (hasBuyout) {
            graphics.drawString(font, Component.literal(Component.translatable("Buyout").getString() + ": " + entry.buyoutPrice()), textX, buyoutY, 0xFF55FF55, false);
        }
        int descriptionY = hasBuyout ? buyoutY + 16 : buyoutY;
        if (entry.reserveActive()) {
            graphics.drawString(font, Component.literal(trimToWidth(reserveStatusText(entry), textW)), textX, descriptionY, reserveStatusColor(entry), false);
            descriptionY += 12;
        }
        if (entry.sealedBid()) {
            String sealedText = entry.sealedBidRevealed()
                    ? Component.translatable("Sealed bids revealed").getString()
                    : Component.translatable("Sealed bids hidden until close").getString();
            graphics.drawString(font, Component.literal(trimToWidth(sealedText, textW)), textX, descriptionY, entry.sealedBidRevealed() ? 0xFFBDBDBD : 0xFFE1BEE7, false);
            descriptionY += 12;
            if (!entry.sealedBidRevealed() && entry.viewerBid() != null && !entry.viewerBid().isBlank()) {
                String viewerBid = Component.translatable("Your sealed bid: {0}", entry.viewerBid()).getString();
                graphics.drawString(font, Component.literal(trimToWidth(viewerBid, textW)), textX, descriptionY, 0xFF55FF55, false);
                descriptionY += 12;
            }
        }
        if (entry.bundle()) {
            graphics.drawString(font, Component.translatable("Bundle - {0} stacks / {1} items", entry.contents().size(), entry.totalItemCount()), textX, descriptionY, 0xFF55FF55, false);
            descriptionY += 12;
        }
        String description = entry.description() == null || entry.description().isBlank() ? Component.translatable("No description").getString() : entry.description();
        for (String line : wrapText(description, textW, 1)) {
            graphics.drawString(font, Component.literal(line), textX, descriptionY, 0xFFDDDDDD, false);
            descriptionY += 12;
        }
        for (String line : itemMetadataLines(entry.item(), textW, 2)) {
            graphics.drawString(font, Component.literal(line), textX, descriptionY, 0xFF9E9E9E, false);
            descriptionY += 12;
        }

        int statusColor = isClaimed(entry) ? 0xFF9DDBA2 : entry.viewerIsHighestBidder() ? 0xFF55FF55 : 0xFFE0E0E0;
        graphics.drawString(font, statusComponent, statusX, y + 12, statusColor, false);
        if (mouseX >= x + 10 && mouseX <= x + 54 && mouseY >= y + 10 && mouseY <= y + 54) {
            graphics.renderTooltip(font, entry.item(), mouseX, mouseY);
        }
    }

    private int auctionRowHeight() {
        return 142;
    }

    private String reserveStatusText(AuctionEntrySummary entry) {
        String status = Component.translatable(entry.reserveMet() ? "Met" : "Not met").getString();
        if (entry.reservePriceVisible() && entry.reservePrice() != null && !entry.reservePrice().isBlank()) {
            return Component.translatable("Reserve: {0} ({1})", entry.reservePrice(), status).getString();
        }
        return Component.translatable("Reserve: {0}", status).getString();
    }

    private int reserveStatusColor(AuctionEntrySummary entry) {
        return entry.reserveMet() ? 0xFF55FF55 : 0xFFFFD966;
    }

    private int rowActionColumnWidth(AuctionEntrySummary entry) {
        if (contentWidth < 760) {
            return 108;
        }
        if (payload.adminMode()) {
            int count = adminCanForceCancel(entry) ? 2 : 1;
            if ("FAILED_SETTLEMENT".equals(normalizedState(entry))) {
                count++;
            }
            return count * 86 + Math.max(0, count - 1) * 6 + 36;
        }
        int count = 2; // notifications + view bids
        count += entry.bundle() ? 1 : 0;
        if (activeTab == Tab.BROWSE || activeTab == Tab.DASHBOARD) {
            count += entry.canBid() ? 1 : 0;
            count += entry.canBuyout() ? 1 : 0;
            count += entry.canCancel() ? 1 : 0;
            count += entry.canClaim() || isClaimed(entry) ? 1 : 0;
        } else if (activeTab == Tab.MY_BIDS) {
            count += entry.canBid() ? 1 : 0;
            count += entry.canClaim() || isClaimed(entry) ? 1 : 0;
        } else if (entry.canCancel() || entry.canClaim() || isClaimed(entry)) {
            count++;
        }
        return count * 86 + Math.max(0, count - 1) * 6 + 28;
    }

    private void renderModal(GuiGraphics graphics, int mouseX, int mouseY) {
        int modalW = modalWidth();
        int modalH = modalHeight();
        int x = modalX(modalW);
        int y = modalY(modalH);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0x99000000);
        graphics.fill(x, y, x + modalW, y + modalH, 0xFF1F1F1F);
        graphics.fill(x + 3, y + 3, x + modalW - 3, y + modalH - 3, 0xFF4A4A4A);
        graphics.fill(x + 3, y + 3, x + modalW - 3, y + 42, 0xFF565656);
        graphics.drawString(font, Component.translatable(modalTitle()).withStyle(ChatFormatting.BOLD), x + 18, y + 18, 0xFFFFAA00, false);

        if (modal == Modal.BID && selectedAuction != null) {
            renderBidModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.CREATE) {
            renderCreateModal(graphics, x, y, modalW, mouseX, mouseY);
        } else if (modal == Modal.RELIST && selectedAuction != null) {
            renderRelistModal(graphics, x, y, modalW, mouseX, mouseY);
        } else if (modal == Modal.CONFIRM_CREATE) {
            renderConfirmCreateModal(graphics, x, y, modalW, modalH, mouseX, mouseY);
        } else if (modal == Modal.DATE_PICKER) {
            renderDatePickerModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.BIDS && selectedAuction != null) {
            renderBidsModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.DELIVERY) {
            int rowY = y + 58;
            for (AuctionDeliverySummary delivery : payload.deliveries().stream().limit(6).toList()) {
                graphics.fill(x + 20, rowY - 4, x + 42, rowY + 18, 0xFF0B0B0B);
                renderBundlePreview(graphics, delivery.contents(), x + 22, rowY - 2, 18, 18);
                String title = delivery.bundle()
                        ? "Bundle - " + delivery.contents().size() + " stacks / " + delivery.totalItemCount() + " items"
                        : delivery.item().getCount() + "x " + delivery.item().getHoverName().getString();
                graphics.drawString(font, Component.literal(trimToWidth(title, modalW - 160)), x + 48, rowY, 0xFFE0E0E0, false);
                graphics.drawString(font, Component.literal(delivery.reason()), x + 48, rowY + 12, 0xFFBDBDBD, false);
                rowY += 34;
            }
            if (payload.deliveries().isEmpty()) {
                graphics.drawString(font, Component.translatable("No delivery items"), x + 20, rowY, 0xFFE0E0E0, false);
            }
        } else if (modal == Modal.FILTER) {
            renderFilterModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.MOD_FILTER) {
            renderModFilterModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.CONTENTS && selectedAuction != null) {
            renderContentsModal(graphics, x, y, modalW, modalH, selectedAuction.contents());
        } else if (modal == Modal.ACCOUNT_SELECTOR) {
            renderAccountSelectorModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.ADMIN_FORCE_CANCEL && selectedAuction != null) {
            renderAdminForceCancelModal(graphics, x, y, modalW);
        }
    }

    private void renderAdminForceCancelModal(GuiGraphics graphics, int x, int y, int modalW) {
        int rarityColor = rarityColor(selectedAuction.rarity());
        graphics.fill(x + 20, y + 54, x + modalW - 20, y + 98, 0xFF000000);
        graphics.fill(x + 22, y + 56, x + modalW - 22, y + 96, 0xFF191919);
        graphics.drawString(font, Component.literal(trimToWidth(selectedAuction.itemName(), modalW - 56)).withStyle(ChatFormatting.BOLD), x + 32, y + 64, rarityColor, false);
        graphics.drawString(font, Component.translatable("Seller: {0}", selectedAuction.sellerName()), x + 32, y + 80, 0xFFBDBDBD, false);
        String refundLine = Component.translatable("Force-cancel refunds the current highest bidder before changing auction state.").getString();
        String recoveryLine = Component.translatable("Return sends contents back to the seller; recovery stores them for admin release.").getString();
        graphics.drawString(font, Component.literal(trimToWidth(refundLine, modalW - 40)), x + 20, y + 146, 0xFFFFD700, false);
        graphics.drawString(font, Component.literal(trimToWidth(recoveryLine, modalW - 40)), x + 20, y + 162, 0xFFE0E0E0, false);
    }

    private void renderBidModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        int actionY = y + modalH - 38;
        int quickY = actionY - 42;
        int inputY = quickY - 52;
        int accountPanelH = modalH < 360 ? 56 : 68;
        int currentY = inputY - accountPanelH - 14;
        int previewSize = modalH < 360 ? 56 : 86;
        int previewX = x + 22;
        int previewY = y + 58;
        int rarityColor = rarityColor(selectedAuction.rarity());

        graphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000);
        graphics.fill(previewX + 2, previewY + 2, previewX + previewSize - 2, previewY + previewSize - 2, 0xFF1A1A1A);
        graphics.fill(previewX + 4, previewY + 4, previewX + previewSize - 4, previewY + previewSize - 4, 0x33000000 | (rarityColor & 0x00FFFFFF));
        renderBundlePreview(graphics, selectedAuction.contents(), previewX + 4, previewY + 4, previewSize - 8, previewSize - 8);

        int detailX = previewX + previewSize + 16;
        int detailW = Math.max(80, x + modalW - 22 - detailX);
        graphics.drawString(font, Component.literal(trimToWidth(selectedAuction.itemName(), detailW)).withStyle(ChatFormatting.BOLD), detailX, previewY + 8, rarityColor, false);
        String sellerLine = Component.translatable("Seller").getString() + ": " + trimToWidth(selectedAuction.sellerName(), detailW - 40);
        graphics.drawString(font, Component.literal(sellerLine), detailX, previewY + 26, 0xFFBDBDBD, false);
        if (selectedAuction.bundle()) {
            graphics.drawString(font, Component.translatable("Bundle - {0} stacks", selectedAuction.contents().size()), detailX, previewY + 38, 0xFF55FF55, false);
        }
        graphics.fill(detailX, previewY + 44, detailX + Math.min(detailW, 128), previewY + 66, 0xFF000000);
        graphics.fill(detailX + 2, previewY + 46, detailX + Math.min(detailW, 128) - 2, previewY + 64, 0xFF191919);
        graphics.drawString(font, Component.literal(timeLeft(selectedAuction.endsAt(), selectedAuction.state()) + " " + Component.translatable("remaining").getString()), detailX + 8, previewY + 52, 0xFFFF6666, false);

        graphics.fill(x + 20, currentY, x + modalW - 20, currentY + accountPanelH, 0xFF000000);
        graphics.fill(x + 22, currentY + 2, x + modalW - 22, currentY + accountPanelH - 2, 0xFF191919);
        graphics.drawString(font, Component.translatable("Current Bid"), x + 34, currentY + 10, 0xFFBDBDBD, false);
        graphics.drawString(font, Component.literal(currentBidText(selectedAuction)).withStyle(ChatFormatting.BOLD), x + 34, currentY + 26, 0xFFFFD700, false);
        if (selectedAuction.sealedBid() && !selectedAuction.sealedBidRevealed() && selectedAuction.viewerBid() != null && !selectedAuction.viewerBid().isBlank()) {
            graphics.drawString(font, Component.translatable("Your sealed bid: {0}", selectedAuction.viewerBid()), x + 128, currentY + 26, 0xFF55FF55, false);
        }
        int accountX = x + modalW / 2;
        int accountW = x + modalW - 126 - accountX;
        AuctionAccountSummary account = selectedAccount();
        graphics.drawString(font, Component.literal(trimToWidth(Component.translatable("Account: {0}", bidAccountLabel()).getString(), accountW)), accountX, currentY + 10, account.present() ? 0xFFBDBDBD : 0xFFFF6666, false);
        graphics.drawString(font, Component.literal(trimToWidth(Component.translatable("Balance: {0}", account.balance()).getString(), accountW)).withStyle(ChatFormatting.BOLD), accountX, currentY + 26, account.frozen() ? 0xFFFF6666 : 0xFF55FF55, false);
        if (accountPanelH >= 64) {
            graphics.drawString(font, Component.translatable("Bid: {0}", moneyDisplay(moneyDraft(bidDraft))), x + 34, currentY + 46, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("After: {0}", moneyDisplay(bidRemainingBalance())).withStyle(ChatFormatting.BOLD), accountX, currentY + 46, bidRemainingBalance().compareTo(BigDecimal.ZERO) < 0 ? 0xFFFF6666 : 0xFF55FF55, false);
        }
        graphics.drawString(font, Component.literal(trimToWidth(bidReviewStatus(), modalW - 40)), x + 20, currentY + accountPanelH + 4, bidReviewStatusColor(), false);

        graphics.drawString(font, Component.translatable("Your Bid").append(Component.literal(" (" + Component.translatable("Minimum").getString() + ": " + moneyDisplay(moneyDraft(nextBidValue(selectedAuction))) + ")")), x + 20, inputY + 2, 0xFFFFFFFF, false);
    }

    private void renderAccountSelectorModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        List<AuctionAccountSummary> accounts = accountOptions();
        int listTop = y + 60;
        int listBottom = y + modalH - 58;
        graphics.fill(x + 18, listTop, x + modalW - 18, listBottom, 0xFF000000);
        graphics.fill(x + 20, listTop + 2, x + modalW - 20, listBottom - 2, 0xFF191919);
        if (accounts.isEmpty()) {
            graphics.drawString(font, Component.translatable("No UBS account"), x + 28, listTop + 18, 0xFFFF6666, false);
            return;
        }
        graphics.enableScissor(x + 20, listTop + 2, x + modalW - 20, listBottom - 2);
        int rowH = 52;
        int rowY = listTop + 4 - accountScroll;
        for (AuctionAccountSummary account : accounts) {
            boolean selected = account.accountId().equals(selectedAccountId);
            int rowColor = selected ? 0xFF1F6F2B : 0xFF252525;
            if (account.frozen()) {
                rowColor = 0xFF351818;
            }
            graphics.fill(x + 24, rowY, x + modalW - 24, rowY + rowH - 4, 0xFF000000);
            graphics.fill(x + 26, rowY + 2, x + modalW - 26, rowY + rowH - 6, rowColor);
            graphics.drawString(font, Component.literal(trimToWidth(accountSelectorRowTitle(account), modalW - 82)).withStyle(ChatFormatting.BOLD), x + 36, rowY + 10, account.frozen() ? 0xFFFF7777 : 0xFFFFFFFF, false);
            graphics.drawString(font, Component.literal(trimToWidth(accountDetailLine(account), modalW - 82)), x + 36, rowY + 26, 0xFFBDBDBD, false);
            rowY += rowH;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 14, listTop + 2, listBottom - 2, accountScroll, accountContentHeight(), Math.max(1, listBottom - listTop - 4));
    }

    private void renderBidsModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        List<AuctionBidSummary> bids = acceptedBids(selectedAuction);
        int summaryTop = y + 58;
        int summaryH = modalH < 360 ? 64 : 82;
        int rarityColor = rarityColor(selectedAuction.rarity());

        graphics.fill(x + 20, summaryTop, x + modalW - 20, summaryTop + summaryH, 0xFF000000);
        graphics.fill(x + 22, summaryTop + 2, x + modalW - 22, summaryTop + summaryH - 2, 0xFF191919);
        int itemBox = Math.min(58, summaryH - 16);
        graphics.fill(x + 34, summaryTop + 8, x + 34 + itemBox, summaryTop + 8 + itemBox, 0xFF0B0B0B);
        graphics.fill(x + 36, summaryTop + 10, x + 32 + itemBox, summaryTop + 6 + itemBox, 0x33000000 | (rarityColor & 0x00FFFFFF));
        renderBundlePreview(graphics, selectedAuction.contents(), x + 38, summaryTop + 12, itemBox - 8, itemBox - 8);

        int detailX = x + 48 + itemBox;
        int detailW = Math.max(80, modalW - 96 - itemBox);
        graphics.drawString(font, Component.literal(trimToWidth(selectedAuction.itemName(), detailW)).withStyle(ChatFormatting.BOLD), detailX, summaryTop + 16, rarityColor, false);
        if (selectedAuction.bundle()) {
            graphics.drawString(font, Component.translatable("Bundle - {0} stacks", selectedAuction.contents().size()), detailX, summaryTop + 30, 0xFF55FF55, false);
        }
        graphics.fill(detailX, summaryTop + 40, detailX + 84, summaryTop + 62, 0xFF000000);
        graphics.fill(detailX + 2, summaryTop + 42, detailX + 82, summaryTop + 60, 0xFF191919);
        graphics.drawString(font, Component.literal(currentBidText(selectedAuction)).withStyle(ChatFormatting.BOLD), detailX + 8, summaryTop + 48, 0xFFFFD700, false);
        graphics.fill(detailX + 96, summaryTop + 40, detailX + 190, summaryTop + 62, 0xFF000000);
        graphics.fill(detailX + 98, summaryTop + 42, detailX + 188, summaryTop + 60, 0xFF191919);
        graphics.drawString(font, Component.literal(timeLeft(selectedAuction.endsAt(), selectedAuction.state())), detailX + 104, summaryTop + 48, 0xFFFF6666, false);

        int titleY = summaryTop + summaryH + 16;
        int visibleBidCount = selectedAuction.sealedBid() && !selectedAuction.sealedBidRevealed() ? selectedAuction.bidCount() : bids.size();
        graphics.drawString(font, Component.translatable(selectedAuction.sealedBid() ? "Sealed Bids" : "Bid History").append(Component.literal(" (" + visibleBidCount + ")")).withStyle(ChatFormatting.BOLD), x + 20, titleY, 0xFFFFFFFF, false);

        int listTop = titleY + 18;
        int listBottom = Math.max(listTop + 24, y + modalH - 58);
        graphics.fill(x + 20, listTop, x + modalW - 20, listBottom, 0xFF000000);
        graphics.fill(x + 22, listTop + 2, x + modalW - 22, listBottom - 2, 0xFF191919);

        if (bids.isEmpty()) {
            if (selectedAuction.sealedBid() && !selectedAuction.sealedBidRevealed()) {
                graphics.drawString(font, Component.translatable("Sealed bids are hidden until this auction ends."), x + 34, listTop + 18, 0xFFE1BEE7, false);
                if (selectedAuction.viewerBid() != null && !selectedAuction.viewerBid().isBlank()) {
                    graphics.drawString(font, Component.translatable("Your sealed bid: {0}", selectedAuction.viewerBid()), x + 34, listTop + 34, 0xFF55FF55, false);
                }
            } else {
                graphics.drawString(font, Component.translatable("No bids yet"), x + 34, listTop + 18, 0xFFBDBDBD, false);
            }
            return;
        }

        int rowH = 50;
        graphics.enableScissor(x + 22, listTop + 2, x + modalW - 22, listBottom - 2);
        for (int i = 0; i < bids.size(); i++) {
            AuctionBidSummary bid = bids.get(i);
            int rowY = listTop + 2 + i * rowH - bidsScroll;
            if (rowY + rowH < listTop || rowY > listBottom) {
                continue;
            }
            boolean winning = i == 0;
            int rowFill = winning ? 0xFF214A22 : 0xFF191919;
            graphics.fill(x + 22, rowY, x + modalW - 22, rowY + rowH - 1, rowFill);
            graphics.fill(x + 22, rowY + rowH - 1, x + modalW - 22, rowY + rowH, 0xFF555555);
            graphics.drawString(font, Component.literal((winning ? "* " : "") + trimToWidth(bid.bidderName(), modalW - 180)).withStyle(ChatFormatting.BOLD), x + 34, rowY + 12, winning ? 0xFF55FF55 : 0xFFFFFFFF, false);
            graphics.drawString(font, Component.literal(timeAgo(bid.timestamp())), x + 34, rowY + 28, 0xFFBDBDBD, false);
            graphics.drawString(font, Component.literal(bid.amount()).withStyle(ChatFormatting.BOLD), x + modalW - 112, rowY + 12, 0xFFFFD700, false);
            if (winning) {
                graphics.drawString(font, Component.translatable("Winning Bid").withStyle(ChatFormatting.BOLD), x + modalW - 112, rowY + 28, 0xFF55FF55, false);
            }
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 14, listTop + 2, listBottom - 2, bidsScroll, bidsContentHeight(), Math.max(1, listBottom - listTop - 4));
    }

    private void renderContentsModal(GuiGraphics graphics, int x, int y, int modalW, int modalH, List<ItemStack> contents) {
        List<ItemStack> safeContents = contents == null ? List.of() : contents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .toList();
        int summaryTop = y + 54;
        int listTop = y + 96;
        int listBottom = y + modalH - 54;
        graphics.drawString(font, Component.literal(trimToWidth(selectedAuction.itemName(), modalW - 40)).withStyle(ChatFormatting.BOLD), x + 20, summaryTop, 0xFFFFAA00, false);
        graphics.drawString(font, Component.translatable("{0} stacks / {1} items", safeContents.size(), safeContents.stream().mapToInt(ItemStack::getCount).sum()), x + 20, summaryTop + 16, 0xFF55FF55, false);

        graphics.fill(x + 20, listTop, x + modalW - 20, listBottom, 0xFF000000);
        graphics.fill(x + 22, listTop + 2, x + modalW - 22, listBottom - 2, 0xFF191919);
        if (safeContents.isEmpty()) {
            graphics.drawString(font, Component.translatable("No contents to show"), x + 34, listTop + 18, 0xFFBDBDBD, false);
            return;
        }

        graphics.enableScissor(x + 22, listTop + 2, x + modalW - 22, listBottom - 2);
        int rowY = listTop + 2 - contentsScroll;
        int textW = modalW - 116;
        for (ItemStack stack : safeContents) {
            List<String> metadataLines = fullItemMetadataLines(stack, textW);
            int rowH = contentsRowHeight(metadataLines);
            if (rowY + rowH < listTop || rowY > listBottom) {
                rowY += rowH;
                continue;
            }
            int textX = x + 82;
            graphics.fill(x + 22, rowY, x + modalW - 22, rowY + rowH - 1, 0xFF242424);
            graphics.fill(x + 22, rowY + rowH - 1, x + modalW - 22, rowY + rowH, 0xFF555555);
            graphics.fill(x + 34, rowY + 9, x + 68, rowY + 43, 0xFF0B0B0B);
            graphics.renderItem(stack, x + 43, rowY + 18);
            graphics.renderItemDecorations(font, stack, x + 43, rowY + 18);
            graphics.drawString(font, Component.literal(stack.getCount() + "x " + trimToWidth(stack.getHoverName().getString(), textW - 32)).withStyle(ChatFormatting.BOLD), textX, rowY + 9, 0xFFE0E0E0, false);
            int metaY = rowY + 25;
            for (String line : metadataLines) {
                graphics.drawString(font, Component.literal(line), textX, metaY, 0xFF9E9E9E, false);
                metaY += 11;
            }
            rowY += rowH;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 14, listTop + 2, listBottom - 2, contentsScroll, contentsContentHeight(safeContents, modalW - 116), Math.max(1, listBottom - listTop - 4));
    }

    private void renderCreateModal(GuiGraphics graphics, int x, int y, int modalW, int mouseX, int mouseY) {
        int modalH = modalHeight();
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        boolean compact = compactCreateModal(modalW);
        int scroll = createScroll;
        int bundleOffset = createSelectionIsBundle() ? 48 : 0;
        int startingY = y + (compact ? 274 : 190) + bundleOffset - scroll;
        int buyoutY = y + (compact ? 322 : 190) + bundleOffset - scroll;
        int reserveY = y + (compact ? 370 : 190) + bundleOffset - scroll;
        int formatY = y + (compact ? 418 : 238) + bundleOffset - scroll;
        int endY = y + (compact ? 466 : 286) + bundleOffset - scroll;
        int descriptionY = y + (compact ? 514 : 334) + bundleOffset - scroll;
        int formatX = x + 20;
        int formatW = modalW - 40;
        int descriptionX = x + 20;
        int descriptionW = modalW - 40;
        int descriptionH = compact ? COMPACT_DESCRIPTION_FIELD_HEIGHT : DESCRIPTION_FIELD_HEIGHT;
        if (!compact) {
            formatW = Math.max(170, Math.min(220, (modalW - 60) / 2));
            descriptionX = formatX + formatW + 18;
            descriptionW = Math.max(160, x + modalW - 20 - descriptionX);
            descriptionY = formatY;
        }
        int fieldW = compact ? modalW - 40 : 138;
        int startingX = x + 20;
        int buyoutX = compact ? x + 20 : x + 170;
        int buyoutW = compact ? modalW - 40 : 128;
        int reserveX = compact ? x + 20 : x + 310;
        int reserveW = compact ? modalW - 40 : 128;

        graphics.enableScissor(x + 6, bodyTop, x + modalW - 6, bodyBottom);
        String selectionTitle = createSelectionIsBundle() ? "Select Bundle Items from Inventory" : "Select Item from Inventory";
        graphics.drawString(font, Component.translatable(selectionTitle).withStyle(ChatFormatting.BOLD), x + 20, y + 54 - scroll, 0xFFFFFFFF, false);
        graphics.fill(inventoryGridLeft - 4, inventoryGridTop - 4, inventoryGridLeft + 202, inventoryGridTop + 92, 0xFF111111);
        graphics.fill(inventoryGridLeft - 2, inventoryGridTop - 2, inventoryGridLeft + 200, inventoryGridTop + 90, 0xFF2C2C2C);
        renderInventoryGrid(graphics, mouseX, mouseY);
        if (compact) {
            renderSelectedItemPreview(graphics, x + 20, y + 174 - scroll, modalW - 40, 74);
            if (createSelectionIsBundle()) {
                int titleY = y + 274 - scroll;
                drawCreateHelpLabel(graphics, "Bundle Title", x + 20, titleY - 12, modalW - 40, x + 20, titleY, modalW - 40, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Bundle Title", "Help: Bundle Title Body");
            }
        } else {
            renderSelectedItemPreview(graphics, x + 236, y + 72 - scroll, modalW - 256, 88);
            if (createSelectionIsBundle()) {
                int titleY = y + 190 - scroll;
                drawCreateHelpLabel(graphics, "Bundle Title", x + 20, titleY - 12, modalW - 40, x + 20, titleY, modalW - 40, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Bundle Title", "Help: Bundle Title Body");
            }
        }
        drawCreateHelpLabel(graphics, "Starting Bid (dollars)", startingX, startingY - 12, fieldW, startingX, startingY, fieldW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Starting Bid", "Help: Starting Bid Body");
        drawCreateHelpLabel(graphics, "Buyout (dollars)", buyoutX, buyoutY - 12, buyoutW, buyoutX, buyoutY, buyoutW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Buyout", "Help: Buyout Body");
        drawCreateHelpLabel(graphics, "Reserve (dollars)", reserveX, reserveY - 12, reserveW, reserveX, reserveY, reserveW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Reserve", "Help: Reserve Body");
        drawCreateHelpLabel(graphics, "Auction Format", formatX, formatY - 12, formatW, formatX, formatY, formatW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Auction Format", "Help: Auction Format Body");
        drawCreateHelpLabel(graphics, "Auction End Date & Time", formatX, endY - 12, formatW, formatX, endY, formatW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: End Date", "Help: End Date Body");
        drawCreateHelpLabel(graphics, "Description", descriptionX, descriptionY - 12, descriptionW, descriptionX, descriptionY, descriptionW, descriptionH, mouseX, mouseY, bodyTop, bodyBottom, "Help: Description", "Help: Description Body");

        int feeY = y + (compact ? 590 : 334) + bundleOffset - scroll;
        graphics.fill(x + 20, feeY, x + modalW - 20, feeY + LISTING_FEE_PANEL_HEIGHT, 0xFF000000);
        graphics.fill(x + 22, feeY + 2, x + modalW - 22, feeY + LISTING_FEE_PANEL_HEIGHT - 2, 0xFF191919);
        Component feeLabel = Component.translatable("Listing Fee").append(Component.literal(" (" + listingFeeRateLabel() + "%)"));
        graphics.drawString(font, feeLabel, x + 34, feeY + 12, 0xFFE0E0E0, false);
        registerCreateHelpZone(x + 20, feeY, modalW - 40, LISTING_FEE_PANEL_HEIGHT, mouseX, mouseY, bodyTop, bodyBottom, "Help: Listing Fee", "Help: Listing Fee Body");
        graphics.drawString(font, Component.literal(moneyDisplay(listingFeePreview())).withStyle(ChatFormatting.BOLD), x + 34, feeY + 30, 0xFFFFD700, false);
        int accountTextW = Math.max(80, modalW - 190);
        graphics.drawString(font, Component.literal(trimToWidth(accountSelectorButtonLabel(), accountTextW)), x + 34, feeY + 48, selectedAccount().frozen() ? 0xFFFF6666 : 0xFFBDBDBD, false);
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 12, bodyTop, bodyBottom, createScroll, createContentHeight(modalW), bodyBottom - bodyTop);
    }

    private void drawCreateHelpLabel(GuiGraphics graphics, String key, int x, int y, int maxWidth, int fieldX, int fieldY, int fieldW, int fieldH, int mouseX, int mouseY, int bodyTop, int bodyBottom, String helpTitleKey, String helpBodyKey) {
        int labelWidth = Math.max(24, maxWidth);
        String label = trimToWidth(Component.translatable(key).getString(), labelWidth);
        graphics.drawString(font, Component.literal(label).withStyle(ChatFormatting.BOLD), x, y, 0xFFFFFFFF, false);
        registerCreateHelpZone(x, y - 2, Math.min(maxWidth, font.width(label)), 14, mouseX, mouseY, bodyTop, bodyBottom, helpTitleKey, helpBodyKey);
        registerCreateHelpZone(fieldX, fieldY, fieldW, fieldH, mouseX, mouseY, bodyTop, bodyBottom, helpTitleKey, helpBodyKey);
    }

    private void registerCreateHelpZone(int x, int y, int width, int height, int mouseX, int mouseY, int bodyTop, int bodyBottom, String titleKey, String bodyKey) {
        if (width <= 0 || height <= 0 || y + height <= bodyTop || y >= bodyBottom) {
            return;
        }
        int visibleTop = Math.max(y, bodyTop);
        int visibleBottom = Math.min(y + height, bodyBottom);
        if (mouseX >= x && mouseX < x + width && mouseY >= visibleTop && mouseY < visibleBottom) {
            hoveredCreateHelpTitleKey = titleKey;
            hoveredCreateHelpBodyKey = bodyKey;
        }
    }

    private void clearCreateHelpHover() {
        hoveredCreateHelpTitleKey = "";
        hoveredCreateHelpBodyKey = "";
    }

    private void renderCreateHelpTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if ((modal != Modal.CREATE && modal != Modal.RELIST) || hoveredCreateHelpTitleKey.isBlank() || hoveredCreateHelpBodyKey.isBlank()) {
            return;
        }
        String title = Component.translatable(hoveredCreateHelpTitleKey).getString();
        String body = Component.translatable(hoveredCreateHelpBodyKey).getString();
        int textW = Math.min(HELP_TOOLTIP_TEXT_WIDTH, Math.max(100, width - 32));
        List<String> lines = wrapText(body, textW, 6);
        int contentW = font.width(title);
        for (String line : lines) {
            contentW = Math.max(contentW, font.width(line));
        }
        int tooltipW = Math.min(width - 12, contentW + 18);
        int tooltipH = 25 + lines.size() * 10;
        int tooltipX = clamp(mouseX + 12, 6, Math.max(6, width - tooltipW - 6));
        int tooltipY = clamp(mouseY + 12, 6, Math.max(6, height - tooltipH - 6));

        graphics.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xFF000000);
        graphics.fill(tooltipX + 2, tooltipY + 2, tooltipX + tooltipW - 2, tooltipY + tooltipH - 2, 0xFF1A1A1A);
        graphics.fill(tooltipX + 2, tooltipY + 2, tooltipX + tooltipW - 2, tooltipY + 5, 0xFFFFAA00);
        graphics.drawString(font, Component.literal(trimToWidth(title, tooltipW - 12)).withStyle(ChatFormatting.BOLD), tooltipX + 8, tooltipY + 9, 0xFFFFAA00, false);
        int lineY = tooltipY + 21;
        for (String line : lines) {
            graphics.drawString(font, Component.literal(line), tooltipX + 8, lineY, 0xFFE0E0E0, false);
            lineY += 10;
        }
    }

    private String sealedBidToggleLabel() {
        return sealedBidDraft
                ? Component.translatable("Sealed Bids: On").getString()
                : Component.translatable("Sealed Bids: Off").getString();
    }

    private void renderRelistModal(GuiGraphics graphics, int x, int y, int modalW, int mouseX, int mouseY) {
        int modalH = modalHeight();
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        boolean compact = compactCreateModal(modalW);
        int scroll = createScroll;
        int startingY = y + (compact ? 190 : 166) - scroll;
        int buyoutY = y + (compact ? 238 : 166) - scroll;
        int reserveY = y + (compact ? 286 : 166) - scroll;
        int formatY = y + (compact ? 334 : 214) - scroll;
        int endY = y + (compact ? 382 : 262) - scroll;
        int descriptionY = y + (compact ? 430 : 310) - scroll;
        int formatX = x + 20;
        int formatW = modalW - 40;
        int descriptionX = x + 20;
        int descriptionW = modalW - 40;
        int descriptionH = compact ? COMPACT_DESCRIPTION_FIELD_HEIGHT : DESCRIPTION_FIELD_HEIGHT;
        if (!compact) {
            formatW = Math.max(170, Math.min(220, (modalW - 60) / 2));
            descriptionX = formatX + formatW + 18;
            descriptionW = Math.max(160, x + modalW - 20 - descriptionX);
            descriptionY = formatY;
        }
        int fieldW = compact ? modalW - 40 : 138;
        int startingX = x + 20;
        int buyoutX = compact ? x + 20 : x + 170;
        int buyoutW = compact ? modalW - 40 : 128;
        int reserveX = compact ? x + 20 : x + 310;
        int reserveW = compact ? modalW - 40 : 128;

        graphics.enableScissor(x + 6, bodyTop, x + modalW - 6, bodyBottom);
        renderRelistItemPreview(graphics, x + 20, y + 58 - scroll, modalW - 40, 82);
        drawCreateHelpLabel(graphics, "Starting Bid (dollars)", startingX, startingY - 12, fieldW, startingX, startingY, fieldW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Starting Bid", "Help: Starting Bid Body");
        drawCreateHelpLabel(graphics, "Buyout (dollars)", buyoutX, buyoutY - 12, buyoutW, buyoutX, buyoutY, buyoutW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Buyout", "Help: Buyout Body");
        drawCreateHelpLabel(graphics, "Reserve (dollars)", reserveX, reserveY - 12, reserveW, reserveX, reserveY, reserveW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Reserve", "Help: Reserve Body");
        drawCreateHelpLabel(graphics, "Auction Format", formatX, formatY - 12, formatW, formatX, formatY, formatW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: Auction Format", "Help: Auction Format Body");
        drawCreateHelpLabel(graphics, "Auction End Date & Time", formatX, endY - 12, formatW, formatX, endY, formatW, 22, mouseX, mouseY, bodyTop, bodyBottom, "Help: End Date", "Help: End Date Body");
        drawCreateHelpLabel(graphics, "Description", descriptionX, descriptionY - 12, descriptionW, descriptionX, descriptionY, descriptionW, descriptionH, mouseX, mouseY, bodyTop, bodyBottom, "Help: Description", "Help: Description Body");

        int feeY = y + (compact ? 506 : 310) - scroll;
        graphics.fill(x + 20, feeY, x + modalW - 20, feeY + LISTING_FEE_PANEL_HEIGHT, 0xFF000000);
        graphics.fill(x + 22, feeY + 2, x + modalW - 22, feeY + LISTING_FEE_PANEL_HEIGHT - 2, 0xFF191919);
        Component feeLabel = Component.translatable("Listing Fee").append(Component.literal(" (" + listingFeeRateLabel() + "%)"));
        graphics.drawString(font, feeLabel, x + 34, feeY + 12, 0xFFE0E0E0, false);
        registerCreateHelpZone(x + 20, feeY, modalW - 40, LISTING_FEE_PANEL_HEIGHT, mouseX, mouseY, bodyTop, bodyBottom, "Help: Listing Fee", "Help: Listing Fee Body");
        graphics.drawString(font, Component.literal(moneyDisplay(listingFeePreview())).withStyle(ChatFormatting.BOLD), x + 34, feeY + 30, 0xFFFFD700, false);
        int accountTextW = Math.max(80, modalW - 190);
        graphics.drawString(font, Component.literal(trimToWidth(accountSelectorButtonLabel(), accountTextW)), x + 34, feeY + 48, selectedAccount().frozen() ? 0xFFFF6666 : 0xFFBDBDBD, false);
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 12, bodyTop, bodyBottom, createScroll, createContentHeight(modalW), bodyBottom - bodyTop);
    }

    private void renderRelistItemPreview(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF191919);
        if (selectedAuction == null) {
            graphics.drawString(font, Component.translatable("Auction not found."), x + 18, y + 28, 0xFFFF6666, false);
            return;
        }

        int previewSize = Math.min(58, h - 16);
        int previewX = x + 16;
        int previewY = y + (h - previewSize) / 2;
        int rarityColor = rarityColor(selectedAuction.rarity());
        graphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF050505);
        graphics.fill(previewX + 2, previewY + 2, previewX + previewSize - 2, previewY + previewSize - 2, 0x33000000 | (rarityColor & 0x00FFFFFF));
        renderBundlePreview(graphics, selectedAuction.contents(), previewX + 4, previewY + 4, previewSize - 8, previewSize - 8);

        int textX = previewX + previewSize + 16;
        int textW = Math.max(80, x + w - 18 - textX);
        graphics.drawString(font, Component.literal(trimToWidth(selectedAuction.itemName(), textW)).withStyle(ChatFormatting.BOLD), textX, y + 14, rarityColor, false);
        String sourceLine = Component.translatable("Previous auction: {0}", selectedAuction.auctionId()).getString();
        graphics.drawString(font, Component.literal(trimToWidth(sourceLine, textW)), textX, y + 30, 0xFFBDBDBD, false);
        if (selectedAuction.bundle()) {
            graphics.drawString(font, Component.translatable("Bundle - {0} stacks / {1} items", selectedAuction.contents().size(), selectedAuction.totalItemCount()), textX, y + 46, 0xFF55FF55, false);
        } else {
            graphics.drawString(font, Component.translatable("Reuses the expired unsold item."), textX, y + 46, 0xFF55FF55, false);
        }
    }

    private void renderConfirmCreateModal(GuiGraphics graphics, int x, int y, int modalW, int modalH, int mouseX, int mouseY) {
        AuctionPendingListingSummary pending = payload.pendingListing();
        int previewTop = y + 58;
        int previewH = modalH < 360 ? 86 : 104;
        int itemBox = Math.min(72, previewH - 22);
        int itemX = x + 34;
        int itemY = previewTop + 12;

        if (!pending.present()) {
            graphics.drawString(font, Component.translatable("No pending auction"), x + 20, previewTop, 0xFFE0E0E0, false);
            return;
        }

        int rarityColor = rarityColor(pending.item().getRarity().name());
        graphics.fill(x + 20, previewTop, x + modalW - 20, previewTop + previewH, 0xFF000000);
        graphics.fill(x + 22, previewTop + 2, x + modalW - 22, previewTop + previewH - 2, 0xFF191919);
        graphics.fill(itemX, itemY, itemX + itemBox, itemY + itemBox, 0xFF0B0B0B);
        graphics.fill(itemX + 2, itemY + 2, itemX + itemBox - 2, itemY + itemBox - 2, 0x33000000 | (rarityColor & 0x00FFFFFF));
        renderBundlePreview(graphics, pending.contents(), itemX + 4, itemY + 4, itemBox - 8, itemBox - 8);

        int detailX = x + 48 + itemBox;
        int detailW = Math.max(100, modalW - 92 - itemBox);
        String title = pending.bundle() ? pending.itemName() : (pending.itemCount() > 1 ? pending.itemCount() + "x " : "") + pending.itemName();
        graphics.drawString(font, Component.literal(trimToWidth(title, detailW)).withStyle(ChatFormatting.BOLD), detailX, previewTop + 14, rarityColor, false);
        graphics.drawString(font, Component.literal(trimToWidth(pending.sourceLabel(), detailW)), detailX, previewTop + 30, 0xFFBDBDBD, false);
        graphics.drawString(font, Component.translatable("Format: {0}", auctionFormatLabel(pending.sealedBid())), detailX, previewTop + 46, pending.sealedBid() ? 0xFFE1BEE7 : 0xFFBDBDBD, false);
        int metadataY = previewTop + 60;
        if (pending.bundle()) {
            graphics.drawString(font, Component.translatable("Bundle - {0} stacks / {1} items", pending.contents().size(), pending.itemCount()), detailX, metadataY, 0xFF55FF55, false);
            metadataY += 12;
        }
        for (String line : itemMetadataLines(pending.item(), detailW, 2)) {
            graphics.drawString(font, Component.literal(line), detailX, metadataY, 0xFF9E9E9E, false);
            metadataY += 12;
        }

        int infoTop = y + (modalH < 360 ? 154 : 178);
        int columnW = Math.max(90, (modalW - 56) / 3);
        renderInfoChip(graphics, x + 20, infoTop, columnW, "Starting Bid", pending.startingBid(), 0xFFFFD700);
        renderInfoChip(graphics, x + 28 + columnW, infoTop, columnW, "Buyout", isZeroMoneyLabel(pending.buyoutPrice()) ? Component.translatable("None").getString() : pending.buyoutPrice(), 0xFF55FF55);
        renderInfoChip(graphics, x + 36 + columnW * 2, infoTop, columnW, "Reserve", isZeroMoneyLabel(pending.reservePrice()) ? Component.translatable("None").getString() : pending.reservePrice(), 0xFFFFAA00);

        int accountY = infoTop + 68;
        graphics.drawString(font, Component.translatable("Listing Fee").append(Component.literal(": " + pending.listingFee())), x + 20, accountY - 14, 0xFFFFD700, false);
        graphics.drawString(font, Component.literal(trimToWidth(accountSelectorButtonLabel(), modalW - 154)), x + 20, accountY, selectedAccount().frozen() ? 0xFFFF6666 : 0xFFBDBDBD, false);

        int descTop = accountY + 18;
        graphics.drawString(font, Component.translatable("Description").withStyle(ChatFormatting.BOLD), x + 20, descTop, 0xFFFFFFFF, false);
        String description = pending.description() == null || pending.description().isBlank() ? Component.translatable("No description").getString() : pending.description();
        int lineY = descTop + 16;
        for (String line : wrapText(description, modalW - 40, 2)) {
            graphics.drawString(font, Component.literal(line), x + 20, lineY, 0xFFE0E0E0, false);
            lineY += 12;
        }
        graphics.drawString(font, Component.translatable("Ends: {0}", readableDateTime(pending.endsAt())), x + 20, lineY + 8, 0xFFA5D6A7, false);
        graphics.drawString(font, Component.translatable("Duration: {0}", durationFromNow(pending.endsAt())), x + 20, lineY + 22, 0xFFA5D6A7, false);
        graphics.drawString(font, Component.translatable("Confirm by: {0}", readableDateTime(pending.expiresAt())), x + 20, lineY + 36, 0xFFFFAA00, false);

        if (mouseX >= itemX && mouseX <= itemX + itemBox && mouseY >= itemY && mouseY <= itemY + itemBox) {
            graphics.renderTooltip(font, pending.item(), mouseX, mouseY);
        }
    }

    private void renderInfoChip(GuiGraphics graphics, int x, int y, int w, String label, String value, int valueColor) {
        graphics.fill(x, y, x + w, y + 40, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + w - 2, y + 38, 0xFF191919);
        graphics.drawString(font, Component.literal(trimToWidth(Component.translatable(label).getString(), w - 16)), x + 8, y + 8, 0xFFBDBDBD, false);
        graphics.drawString(font, Component.literal(trimToWidth(value, w - 16)).withStyle(ChatFormatting.BOLD), x + 8, y + 24, valueColor, false);
    }

    private void renderFilterModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        int scroll = filterScroll;
        int labelX = x + 18;

        graphics.enableScissor(x + 6, bodyTop, x + modalW - 6, bodyBottom);
        graphics.drawString(font, Component.translatable("Categories"), labelX, y + 58 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Mod"), labelX, y + 114 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Price Range"), labelX, y + 170 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Closing Time"), labelX, y + 260 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Sort By"), labelX, y + 322 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Saved Searches"), labelX, y + 384 - scroll, 0xFFE0E0E0, false);

        List<AuctionSavedSearchPayload> savedSearches = savedSearches();
        int rowY = y + 436 - scroll;
        int rowH = 56;
        if (savedSearches.isEmpty()) {
            graphics.drawString(font, Component.translatable("No saved searches"), labelX, rowY + 10, 0xFF9E9E9E, false);
        }
        for (AuctionSavedSearchPayload savedSearch : savedSearches) {
            int rowBottom = rowY + rowH - 4;
            if (rowBottom >= bodyTop && rowY <= bodyBottom) {
                int clampedTop = Math.max(rowY, bodyTop);
                int clampedBottom = Math.min(rowBottom, bodyBottom);
                graphics.fill(labelX, clampedTop, x + modalW - 18, clampedBottom, 0xFF000000);
                graphics.fill(labelX + 2, clampedTop + 2, x + modalW - 20, clampedBottom - 2, 0xFF191919);
                int textW = Math.max(60, modalW - 274);
                graphics.drawString(font, Component.literal(trimToWidth(savedSearch.name(), textW)).withStyle(ChatFormatting.BOLD), labelX + 10, rowY + 7, 0xFFFFFFFF, false);
                graphics.drawString(font, Component.literal(trimToWidth(savedSearchDetail(savedSearch), textW)), labelX + 10, rowY + 22, 0xFFBDBDBD, false);
            }
            rowY += rowH;
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 12, bodyTop, bodyBottom, filterScroll, filterContentHeight(), bodyBottom - bodyTop);
    }

    private void renderModFilterModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        int listTop = y + 88;
        int listBottom = y + modalH - 52;
        int rowH = 32;
        List<ModOption> options = modOptions();

        graphics.fill(x + 18, listTop, x + modalW - 18, listBottom, 0xFF111111);
        graphics.fill(x + 20, listTop + 2, x + modalW - 20, listBottom - 2, 0xFF191919);
        if (options.isEmpty()) {
            graphics.drawString(font, Component.translatable("No mods found"), x + 30, listTop + 18, 0xFFBDBDBD, false);
            return;
        }

        graphics.enableScissor(x + 20, listTop + 2, x + modalW - 20, listBottom - 2);
        for (int i = 0; i < options.size(); i++) {
            ModOption option = options.get(i);
            int rowY = listTop + 2 + i * rowH - modScroll;
            if (rowY + rowH < listTop || rowY > listBottom) {
                continue;
            }
            boolean selected = option.modId().equals(pendingModId);
            int fill = selected ? 0xFF2F6F35 : 0xFF2A2A2A;
            int border = selected ? 0xFF55FF55 : 0xFF000000;
            graphics.fill(x + 20, rowY, x + modalW - 20, rowY + rowH - 3, border);
            graphics.fill(x + 22, rowY + 2, x + modalW - 22, rowY + rowH - 5, fill);
            String displayName = option.modId().isBlank()
                    ? Component.translatable("All Mods").getString()
                    : option.displayName();
            Component title = Component.literal(trimToWidth(displayName, modalW - 82))
                    .withStyle(selected ? ChatFormatting.BOLD : ChatFormatting.WHITE);
            graphics.drawString(font, title, x + 30, rowY + 7, selected ? 0xFFFFFFFF : 0xFFE0E0E0, false);
            String detail = option.modId().isBlank()
                    ? Component.translatable("All listed mods").getString()
                    : Component.translatable("{0} - {1} auctions", option.modId(), option.activeAuctionCount()).getString();
            graphics.drawString(font, Component.literal(trimToWidth(detail, modalW - 82)), x + 30, rowY + 19, selected ? 0xFFD7FFD7 : 0xFFBDBDBD, false);
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 14, listTop + 2, listBottom - 2, modScroll, modContentHeight(), Math.max(1, listBottom - listTop - 4));
    }

    private void renderSelectedItemPreview(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF191919);
        List<ItemStack> stacks = selectedInventoryStacks();
        ItemStack stack = stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst();
        graphics.fill(x + 16, y + 18, x + 66, y + 68, 0xFF050505);
        graphics.fill(x + 18, y + 20, x + 64, y + 66, stack.isEmpty() ? 0xFF333333 : createSelectionIsBundle() ? 0xFF2C3A18 : 0xFF1B1B62);
        if (stack.isEmpty()) {
            graphics.drawString(font, Component.translatable("No item selected"), x + 80, y + 28, 0xFFBDBDBD, false);
            graphics.drawString(font, Component.translatable("Select a slot below"), x + 80, y + 42, 0xFF8E8E8E, false);
            return;
        }

        renderBundlePreview(graphics, stacks, x + 18, y + 20, 46, 46);
        if (createSelectionIsBundle()) {
            graphics.drawString(font, Component.literal(trimToWidth(selectedBundlePreviewTitle(), w - 92)).withStyle(ChatFormatting.BOLD), x + 80, y + 18, 0xFFFFAA00, false);
            graphics.drawString(font, Component.translatable("{0} stacks, {1} items", stacks.size(), stacks.stream().mapToInt(ItemStack::getCount).sum()), x + 80, y + 34, 0xFFE0E0E0, false);
            graphics.drawString(font, Component.translatable("Bundle"), x + 80, y + 50, 0xFF55FF55, false);
        } else {
            graphics.drawString(font, stack.getHoverName(), x + 80, y + 24, 0xFF5F6BFF, false);
            graphics.drawString(font, Component.translatable("{0}x Inventory Slot {1}", stack.getCount(), selectedInventorySlot + 1), x + 80, y + 42, 0xFFE0E0E0, false);
        }
    }

    private void renderBundlePreview(GuiGraphics graphics, List<ItemStack> contents, int x, int y, int w, int h) {
        List<ItemStack> safeContents = contents == null ? List.of() : contents.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .toList();
        if (safeContents.isEmpty()) {
            return;
        }
        int count = Math.min(4, safeContents.size());
        int left = x + Math.max(1, Math.min(6, (w - 32) / 3));
        int right = x + w - 16 - Math.max(1, Math.min(6, (w - 32) / 3));
        int top = y + Math.max(1, Math.min(6, (h - 32) / 3));
        int bottom = y + h - 16 - Math.max(1, Math.min(6, (h - 32) / 3));
        int centerX = x + (w - 16) / 2;
        int centerY = y + (h - 16) / 2;
        int[][] positions = switch (count) {
            case 1 -> new int[][]{{centerX, centerY}};
            case 2 -> new int[][]{{left, centerY}, {right, centerY}};
            case 3 -> new int[][]{{centerX, top}, {left, bottom}, {right, bottom}};
            default -> new int[][]{{left, top}, {right, top}, {left, bottom}, {right, bottom}};
        };
        for (int i = 0; i < count; i++) {
            ItemStack stack = safeContents.get(i);
            graphics.renderItem(stack, positions[i][0], positions[i][1]);
            graphics.renderItemDecorations(font, stack, positions[i][0], positions[i][1]);
        }
        int remaining = safeContents.size() - 4;
        if (remaining > 0) {
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 250.0F);
            int badgeX = x + w - 18;
            int badgeY = y + 2;
            graphics.fill(badgeX, badgeY, badgeX + 18, badgeY + 14, 0xFFFF3333);
            graphics.fill(badgeX + 1, badgeY + 1, badgeX + 17, badgeY + 13, 0xFF9D1010);
            graphics.drawCenteredString(font, Component.literal("+" + remaining).withStyle(ChatFormatting.BOLD), badgeX + 9, badgeY + 3, 0xFFFFFFFF);
            graphics.pose().popPose();
        }
    }

    private String selectedBundlePreviewTitle() {
        String draft = bundleTitleDraft == null ? "" : bundleTitleDraft.trim();
        return draft.isBlank() ? generatedSelectedBundleTitle() : draft;
    }

    private String generatedSelectedBundleTitle() {
        List<ItemStack> stacks = selectedInventoryStacks();
        if (stacks.isEmpty()) {
            return Component.translatable("Bundle").getString();
        }
        if (stacks.size() == 1) {
            return stacks.getFirst().getHoverName().getString();
        }
        return Component.translatable("Bundle: {0} + {1} more", stacks.getFirst().getHoverName().getString(), stacks.size() - 1).getString();
    }

    private void renderDatePickerModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        DatePickerLayout layout = datePickerLayout(x, y, modalW, modalH);

        graphics.drawString(font, Component.translatable("Select Date (up to 30 days)").withStyle(ChatFormatting.BOLD), layout.calendarX(), y + 52, 0xFFFFFFFF, false);
        graphics.drawCenteredString(font, Component.translatable("{0} {1}", Component.translatable(calendarMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ROOT)), calendarMonth.getYear()), layout.calendarX() + layout.calendarWidth() / 2, layout.monthY() + 7, 0xFFFFFFFF);

        String[] weekdays = {"Sunday initial", "Monday initial", "Tuesday initial", "Wednesday initial", "Thursday initial", "Friday initial", "Saturday initial"};
        for (int col = 0; col < weekdays.length; col++) {
            graphics.drawCenteredString(font, Component.translatable(weekdays[col]), layout.calendarX() + col * layout.cell() + (layout.cell() - 2) / 2, layout.weekdayY(), 0xFFE0E0E0);
        }

        graphics.drawString(font, Component.translatable("Select Time").withStyle(ChatFormatting.BOLD), layout.timeX(), layout.timeY(), 0xFFFFFFFF, false);
        int panelTop = layout.timeY() + 14;
        int panelBottom = layout.timeY() + 112;
        graphics.fill(layout.timeX(), panelTop, layout.timeX() + layout.timeWidth(), panelBottom, 0xFF111111);
        graphics.fill(layout.timeX() + 2, panelTop + 2, layout.timeX() + layout.timeWidth() - 2, panelBottom - 2, 0xFF2F2F2F);

        int controlsW = 164;
        int controlsX = layout.timeX() + Math.max(0, (layout.timeWidth() - controlsW) / 2);
        int colonX = controlsX + 52;
        int valueY = layout.timeY() + 48;
        graphics.drawCenteredString(font, Component.literal(":"), colonX, valueY + 7, 0xFFFFFFFF);
        renderPeriodDot(graphics, controlsX + 124, valueY - 6, !selectedEndPm());
        renderPeriodDot(graphics, controlsX + 124, valueY + 24, selectedEndPm());
        graphics.drawString(font, Component.translatable("AM"), controlsX + 140, valueY - 3, selectedEndPm() ? 0xFFBDBDBD : 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("PM"), controlsX + 140, valueY + 27, selectedEndPm() ? 0xFFFFFFFF : 0xFFBDBDBD, false);
    }

    private void renderPeriodDot(GuiGraphics graphics, int x, int y, boolean selected) {
        graphics.fill(x, y, x + 10, y + 10, 0xFFE0E0E0);
        graphics.fill(x + 1, y + 1, x + 9, y + 9, 0xFF2F2F2F);
        if (selected) {
            graphics.fill(x + 3, y + 3, x + 7, y + 7, 0xFF55FF55);
        }
    }

    private void renderScrollBar(GuiGraphics graphics, int x, int top, int bottom, int scroll, int contentHeight, int viewportHeight) {
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = Math.max(1, bottom - top);
        int thumbHeight = Math.max(20, trackHeight * viewportHeight / Math.max(viewportHeight, contentHeight));
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + travel * scroll / maxScroll;
        graphics.fill(x, top, x + 4, bottom, 0xFF202020);
        graphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, 0xFFE0E0E0);
    }

    private boolean compactCreateModal(int modalW) {
        return modalW < 470;
    }

    private int createContentHeight(int modalW) {
        if (modal == Modal.RELIST) {
            return compactCreateModal(modalW) ? 532 : 336;
        }
        return (compactCreateModal(modalW) ? 616 : 360) + (createSelectionIsBundle() ? 48 : 0);
    }

    private int filterContentHeight() {
        return 468 + savedSearches().size() * 56;
    }

    private int modContentHeight() {
        return modOptions().size() * 32 + 4;
    }

    private int bidsContentHeight() {
        return acceptedBids(selectedAuction).size() * 50 + 4;
    }

    private int contentsContentHeight(List<ItemStack> contents, int textWidth) {
        if (contents == null || contents.isEmpty()) {
            return 4;
        }
        int height = 4;
        for (ItemStack stack : contents) {
            height += contentsRowHeight(stack, textWidth);
        }
        return height;
    }

    private int contentsRowHeight(ItemStack stack, int textWidth) {
        return contentsRowHeight(fullItemMetadataLines(stack, textWidth));
    }

    private int contentsRowHeight(List<String> metadataLines) {
        int lineCount = metadataLines == null ? 0 : metadataLines.size();
        return Math.max(54, 36 + lineCount * 11);
    }

    private int accountContentHeight() {
        return accountOptions().size() * 52 + 8;
    }

    private void clampModalScrolls() {
        int modalW = modalWidth();
        int viewport = Math.max(1, modalHeight() - 92);
        createScroll = clamp(createScroll, 0, Math.max(0, createContentHeight(modalW) - viewport));
        filterScroll = clamp(filterScroll, 0, Math.max(0, filterContentHeight() - viewport));
        int bidsViewport = Math.max(1, modalHeight() - (modalHeight() < 360 ? 206 : 224));
        bidsScroll = clamp(bidsScroll, 0, Math.max(0, bidsContentHeight() - bidsViewport));
        int modViewport = Math.max(1, modalHeight() - 140);
        modScroll = clamp(modScroll, 0, Math.max(0, modContentHeight() - modViewport));
        int contentsViewport = Math.max(1, modalHeight() - 154);
        List<ItemStack> contents = selectedAuction == null ? List.of() : selectedAuction.contents();
        contentsScroll = clamp(contentsScroll, 0, Math.max(0, contentsContentHeight(contents, modalWidth() - 116) - contentsViewport));
        int accountViewport = Math.max(1, modalHeight() - 120);
        accountScroll = clamp(accountScroll, 0, Math.max(0, accountContentHeight() - accountViewport));
    }

    private void setVisibleInModalBody(AbstractWidget widget, int bodyTop, int bodyBottom) {
        boolean visible = widget.getY() + widget.getHeight() > bodyTop && widget.getY() < bodyBottom;
        widget.visible = visible;
        if (!visible) {
            widget.active = false;
        }
    }

    private void setVisibleInAuctionList(AbstractWidget widget) {
        int top = auctionListTop();
        int bottom = auctionListBottom();
        boolean visible = widget.getY() >= top && widget.getY() + widget.getHeight() <= bottom;
        widget.visible = visible;
        if (!visible) {
            widget.active = false;
        }
    }

    private int auctionListTop() {
        if (payload.adminMode()) {
            return contentTop + 30;
        }
        if (activeTab == Tab.DASHBOARD) {
            return contentTop + 76;
        }
        return contentTop + 8;
    }

    private int auctionListBottom() {
        if (payload.adminMode()) {
            return adminListBottom();
        }
        return contentTop + contentHeight - 8;
    }

    private int auctionListViewportHeight() {
        return Math.max(1, auctionListBottom() - auctionListTop());
    }

    private int auctionListContentHeight() {
        if (activeTab == Tab.DASHBOARD && !payload.adminMode()) {
            return dashboardContentHeight();
        }
        return visibleEntries().size() * auctionRowHeight();
    }

    private void clampAuctionScroll() {
        auctionScroll = clamp(auctionScroll, 0, Math.max(0, auctionListContentHeight() - auctionListViewportHeight()));
    }

    private boolean inModalBody(double mouseY) {
        int modalH = modalHeight();
        int y = modalY(modalH);
        return mouseY >= modalBodyTop(y) && mouseY <= modalBodyBottom(y, modalH);
    }

    private boolean filtersActive() {
        return category != AuctionCategory.ALL
                || sort != AuctionSort.ENDING_SOON
                || maxHoursLeft > 0L
                || !minPriceDraft.isBlank()
                || !maxPriceDraft.isBlank()
                || !selectedModId.isBlank();
    }

    private AuctionAdminDashboardPayload adminDashboard() {
        return payload == null || payload.adminDashboard() == null ? AuctionAdminDashboardPayload.EMPTY : payload.adminDashboard();
    }

    private List<AuctionAdminDashboardPayload.Player> filteredAdminPlayers() {
        String search = adminSearchDraft == null ? "" : adminSearchDraft.trim().toLowerCase(Locale.ROOT);
        return adminDashboard().players().stream()
                .filter(player -> {
                    if (search.isBlank()) {
                        return true;
                    }
                    String haystack = (player.playerName() + " " + player.playerId()).toLowerCase(Locale.ROOT);
                    return haystack.contains(search);
                })
                .toList();
    }

    private List<DashboardSection> dashboardSections() {
        List<AuctionEntrySummary> entries = payload == null || payload.dashboardListings() == null ? List.of() : payload.dashboardListings();
        List<AuctionEntrySummary> active = entries.stream()
                .filter(entry -> "ACTIVE".equals(normalizedState(entry)))
                .filter(entry -> entry.viewerHasBid() || entry.viewerIsSeller() || entry.viewerIsHighestBidder())
                .toList();
        List<AuctionEntrySummary> endingSoon = entries.stream()
                .filter(entry -> "ACTIVE".equals(normalizedState(entry)))
                .filter(this::entryEndingSoon)
                .toList();
        List<AuctionEntrySummary> claimable = entries.stream()
                .filter(AuctionEntrySummary::canClaim)
                .toList();
        List<AuctionEntrySummary> watching = entries.stream()
                .filter(AuctionEntrySummary::viewerReceivesNotifications)
                .toList();
        List<AuctionEntrySummary> history = entries.stream()
                .filter(entry -> !"ACTIVE".equals(normalizedState(entry)) || isClaimed(entry))
                .toList();
        return List.of(
                new DashboardSection("Active", active, 0xFF55FF55),
                new DashboardSection("Ending Soon", endingSoon, 0xFFFFD966),
                new DashboardSection("Claimable", claimable, 0xFF3EFF47),
                new DashboardSection("Watching", watching, 0xFF66CCFF),
                new DashboardSection("History", history, 0xFFFFAA00)
        );
    }

    private Component dashboardSectionHeaderLabel(DashboardSection section, int maxWidth) {
        String label = Component.translatable(section.title()).getString() + " (" + section.entries().size() + ")";
        return Component.literal(trimToWidth(label, maxWidth)).withStyle(ChatFormatting.BOLD);
    }

    private boolean dashboardSectionExpanded(DashboardSection section) {
        return expandedDashboardSections.contains(section.title());
    }

    private void toggleDashboardSection(DashboardSection section) {
        if (!expandedDashboardSections.remove(section.title())) {
            expandedDashboardSections.add(section.title());
        }
        clampAuctionScroll();
        rebuildWidgets();
    }

    private int dashboardContentHeight() {
        int height = 0;
        for (DashboardSection section : dashboardSections()) {
            height += DASHBOARD_SECTION_HEADER_HEIGHT;
            if (dashboardSectionExpanded(section)) {
                height += section.entries().isEmpty()
                        ? DASHBOARD_SECTION_EMPTY_HEIGHT
                        : section.entries().size() * auctionRowHeight();
            }
            height += DASHBOARD_SECTION_GAP;
        }
        return height;
    }

    private boolean entryEndingSoon(AuctionEntrySummary entry) {
        if (entry == null || entry.endsAt() == null || entry.endsAt().isBlank()) {
            return false;
        }
        try {
            Duration duration = Duration.between(LocalDateTime.now(), LocalDateTime.parse(entry.endsAt()));
            return !duration.isNegative() && duration.toHours() <= 24L;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private AuctionAdminDashboardPayload.Player selectedAdminPlayer() {
        if (selectedAdminPlayerId == null) {
            return null;
        }
        return adminDashboard().players().stream()
                .filter(player -> selectedAdminPlayerId.equals(player.playerId()))
                .findFirst()
                .orElse(null);
    }

    private void selectAdminPlayer(AuctionAdminDashboardPayload.Player player) {
        selectedAdminPlayerId = player.playerId();
        adminBlockCreate = player.blockCreate() || !player.banActive();
        adminBlockBid = player.blockBid() || !player.banActive();
        adminBlockBuyout = player.blockBuyout() || !player.banActive();
        adminBlockWatch = player.blockWatch() || !player.banActive();
        adminBanReasonDraft = player.banActive() ? player.banReason() : "";
        adminBanExpiryDraft = player.banActive() && !"Never".equalsIgnoreCase(player.banExpiresAt()) ? player.banExpiresAt() : "";
        rebuildWidgets();
    }

    private int adminListBottom() {
        return contentTop + contentHeight - 8;
    }

    private int adminPlayerListTop() {
        return selectedAdminPlayer() == null ? contentTop + 42 : contentTop + 282;
    }

    private int adminContentHeight() {
        return switch (adminSection) {
            case OVERVIEW -> adminStatsGridHeight(true) + 382;
            case ECONOMY -> adminStatsGridHeight(false) + adminEconomyReportHeight() + 244;
            case PLAYERS -> adminPlayerListTop() - contentTop + filteredAdminPlayers().size() * 44 + 12;
            case SUSPICION -> 38 + adminDashboard().suspicionSignals().size() * 58;
            case BANNED_ITEMS -> 52 + adminDashboard().bannedEntries().size() * 36;
            case RECOVERY -> 38 + adminDashboard().recoveryEntries().size() * 62;
            case AUDIT -> adminDashboard().auditLog().size() * 46 + 24;
            case AUCTIONS, MODERATION -> auctionListContentHeight() + 40;
        };
    }

    private int adminEconomyReportHeight() {
        int reportCount = adminDashboard().economyReports().size();
        if (reportCount <= 0) {
            return 0;
        }
        int columns = contentWidth >= 620 ? 3 : 1;
        int rows = Math.max(1, (int) Math.ceil(reportCount / (double) columns));
        return rows * 126;
    }

    private void clampAdminScroll() {
        adminScroll = clamp(adminScroll, 0, Math.max(0, adminContentHeight() - contentHeight));
    }

    private void setVisibleInAdminList(AbstractWidget widget) {
        int bottom = adminListBottom();
        boolean visible = widget.getY() >= contentTop && widget.getY() + widget.getHeight() <= bottom;
        widget.visible = visible;
        if (!visible) {
            widget.active = false;
        }
    }

    private String selectedModLabel() {
        if (selectedModId == null || selectedModId.isBlank()) {
            return Component.translatable("All Mods").getString();
        }
        return modDisplayName(selectedModId);
    }

    private String modDisplayName(String modId) {
        if (modId == null || modId.isBlank()) {
            return Component.translatable("All Mods").getString();
        }
        for (AuctionModFilterSummaryPayload summary : modFilterSummaries()) {
            if (modId.equals(summary.modId())) {
                return summary.displayName();
            }
        }
        return modId;
    }

    private List<ModOption> modOptions() {
        String search = modSearchDraft == null ? "" : modSearchDraft.trim().toLowerCase(Locale.ROOT);
        List<ModOption> options = new ArrayList<>();
        options.add(new ModOption("", "All Mods", totalListedModAuctions()));
        for (AuctionModFilterSummaryPayload summary : modFilterSummaries()) {
            if (summary == null || summary.modId() == null || summary.modId().isBlank()) {
                continue;
            }
            String haystack = (summary.displayName() + " " + summary.modId()).toLowerCase(Locale.ROOT);
            if (!search.isBlank() && !haystack.contains(search)) {
                continue;
            }
            options.add(new ModOption(summary.modId(), summary.displayName(), summary.activeAuctionCount()));
        }
        return options;
    }

    private int totalListedModAuctions() {
        int total = 0;
        for (AuctionModFilterSummaryPayload summary : modFilterSummaries()) {
            if (summary != null) {
                total += Math.max(0, summary.activeAuctionCount());
            }
        }
        return total;
    }

    private List<AuctionModFilterSummaryPayload> modFilterSummaries() {
        return payload == null || payload.modFilters() == null ? List.of() : payload.modFilters();
    }

    private List<AuctionSavedSearchPayload> savedSearches() {
        return payload == null || payload.savedSearches() == null ? List.of() : payload.savedSearches();
    }

    private void runSavedSearch(AuctionSavedSearchPayload savedSearch) {
        if (savedSearch == null) {
            return;
        }
        searchDraft = savedSearch.search();
        category = AuctionCategory.fromToken(savedSearch.category());
        sort = AuctionSort.fromToken(savedSearch.sort());
        minPriceDraft = sanitizeMoneyInput(savedSearch.minimumPrice());
        maxPriceDraft = sanitizeMoneyInput(savedSearch.maximumPrice());
        maxHoursLeft = Math.max(0L, savedSearch.maximumHoursLeft());
        selectedModId = savedSearch.modId() == null ? "" : savedSearch.modId();
        if (searchBox != null) {
            searchBox.setValue(searchDraft);
        }
        if (minPriceBox != null) {
            minPriceBox.setValue(minPriceDraft);
        }
        if (maxPriceBox != null) {
            maxPriceBox.setValue(maxPriceDraft);
        }
        auctionScroll = 0;
        modal = Modal.NONE;
        refreshFromServer();
    }

    private void sendSavedSearchAction(String action, AuctionSavedSearchPayload savedSearch) {
        String name = value(savedSearchNameBox, savedSearchNameDraft).trim();
        if ("RENAME_SEARCH".equals(action) && name.isBlank() && savedSearch != null) {
            savedSearchNameDraft = savedSearch.name();
            rebuildWidgets();
            return;
        }
        PacketDistributor.sendToServer(new AuctionActionPayload(
                action,
                savedSearch == null ? null : savedSearch.searchId(),
                null,
                -1,
                List.of(),
                "DELETE_SEARCH".equals(action) ? "" : name,
                "",
                "",
                "",
                "",
                "",
                0,
                "",
                "",
                searchValue(),
                category.name(),
                sort.name(),
                minPriceValue(),
                maxPriceValue(),
                maxHoursLeft,
                selectedModId,
                selectedAccountIdForAction(),
                payload.adminMode()
        ));
    }

    private String savedSearchDetail(AuctionSavedSearchPayload savedSearch) {
        if (savedSearch == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (savedSearch.search() != null && !savedSearch.search().isBlank()) {
            parts.add(Component.translatable("Search: {0}", savedSearch.search()).getString());
        }
        String categoryLabel = Component.translatable(AuctionCategory.fromToken(savedSearch.category()).label()).getString();
        parts.add(Component.translatable("Category: {0}", categoryLabel).getString());
        String mod = savedSearch.modId() == null ? "" : savedSearch.modId().trim();
        parts.add(mod.isBlank()
                ? Component.translatable("Any mod").getString()
                : Component.translatable("Mod: {0}", modDisplayName(mod)).getString());
        String price = savedSearchPrice(savedSearch);
        if (!price.isBlank()) {
            parts.add(price);
        }
        long hoursLeft = Math.max(0L, savedSearch.maximumHoursLeft());
        if (hoursLeft > 0L) {
            parts.add(timeFilterLabel(hoursLeft));
        }
        parts.add(Component.translatable(AuctionSort.fromToken(savedSearch.sort()).label()).getString());
        return String.join(Component.translatable(" | ").getString(), parts);
    }

    private String savedSearchPrice(AuctionSavedSearchPayload savedSearch) {
        String min = savedSearch.minimumPrice() == null ? "" : savedSearch.minimumPrice().trim();
        String max = savedSearch.maximumPrice() == null ? "" : savedSearch.maximumPrice().trim();
        if (min.isBlank() && max.isBlank()) {
            return "";
        }
        if (min.isBlank()) {
            return Component.translatable("Price: up to {0}", moneyDisplay(moneyDraft(max))).getString();
        }
        if (max.isBlank()) {
            return Component.translatable("Price: from {0}", moneyDisplay(moneyDraft(min))).getString();
        }
        return Component.translatable("Price: {0} - {1}", moneyDisplay(moneyDraft(min)), moneyDisplay(moneyDraft(max))).getString();
    }

    private void applyFilters() {
        minPriceDraft = sanitizeMoneyInput(value(minPriceBox, minPriceDraft));
        maxPriceDraft = sanitizeMoneyInput(value(maxPriceBox, maxPriceDraft));
        modal = Modal.NONE;
        refreshFromServer();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void renderInventoryGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        Inventory inventory = minecraft.player.getInventory();
        int columns = 9;
        int size = Math.min(inventory.getContainerSize(), 36);
        for (int slot = 0; slot < size; slot++) {
            int col = slot % columns;
            int row = slot / columns;
            int x = inventoryGridLeft + col * 22;
            int y = inventoryGridTop + row * 22;
            boolean selected = selectedInventorySlots.contains(slot);
            int color = selected ? 0xFF3EFF47 : 0xFF202020;
            graphics.fill(x, y, x + 20, y + 20, color);
            graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFF555555);
            if (selected) {
                graphics.fill(x + 2, y + 2, x + 18, y + 4, 0xFFFFFFFF);
            }
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, y + 2);
                graphics.renderItemDecorations(font, stack, x + 2, y + 2);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modal == Modal.NONE) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (modal == Modal.CREATE && clickInventoryGrid(mouseX, mouseY)) {
            return true;
        }
        if (modal == Modal.MOD_FILTER && modSearchBox != null && modSearchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(modSearchBox);
            modSearchBox.setFocused(true);
            return true;
        }

        List<? extends GuiEventListener> listeners = children();
        int start = Math.max(0, Math.min(modalChildStart, listeners.size()));
        for (int i = listeners.size() - 1; i >= start; i--) {
            GuiEventListener listener = listeners.get(i);
            if (listener.mouseClicked(mouseX, mouseY, button)) {
                setFocused(listener);
                if (button == 0) {
                    setDragging(true);
                }
                return true;
            }
        }
        clearFocus();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modal == Modal.NONE) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        GuiEventListener focused = getFocused();
        if (isModalChild(focused) && focused.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        setDragging(false);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modal == Modal.NONE) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        GuiEventListener focused = getFocused();
        return isModalChild(focused) && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal == Modal.NONE) {
            if (payload.adminMode()) {
                if ((adminSection == AdminSection.AUCTIONS || adminSection == AdminSection.MODERATION)
                        && mouseY >= auctionListTop() && mouseY <= auctionListBottom()
                        && auctionListContentHeight() > auctionListViewportHeight()) {
                    int rowDelta = (int) Math.signum(scrollY);
                    if (rowDelta == 0) {
                        rowDelta = scrollY > 0.0D ? 1 : -1;
                    }
                    auctionScroll -= rowDelta * auctionRowHeight();
                    clampAuctionScroll();
                    rebuildWidgets();
                    return true;
                }
                if (mouseY >= contentTop && mouseY <= adminListBottom() && adminContentHeight() > contentHeight) {
                    adminScroll -= (int) Math.round(scrollY * 24.0D);
                    clampAdminScroll();
                    rebuildWidgets();
                    return true;
                }
                return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            if (mouseY >= auctionListTop() && mouseY <= auctionListBottom() && auctionListContentHeight() > auctionListViewportHeight()) {
                int rowDelta = (int) Math.signum(scrollY);
                if (rowDelta == 0) {
                    rowDelta = scrollY > 0.0D ? 1 : -1;
                }
                auctionScroll -= rowDelta * auctionRowHeight();
                clampAuctionScroll();
                rebuildWidgets();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (modal == Modal.CREATE || modal == Modal.RELIST || modal == Modal.FILTER || modal == Modal.BIDS || modal == Modal.MOD_FILTER || modal == Modal.CONTENTS || modal == Modal.ACCOUNT_SELECTOR) {
            int delta = (int) Math.round(scrollY * 22.0D);
            if (modal == Modal.CREATE || modal == Modal.RELIST) {
                createScroll -= delta;
            } else if (modal == Modal.FILTER) {
                filterScroll -= delta;
            } else if (modal == Modal.MOD_FILTER) {
                modScroll -= delta;
            } else if (modal == Modal.CONTENTS) {
                contentsScroll -= delta;
            } else if (modal == Modal.ACCOUNT_SELECTOR) {
                accountScroll -= delta;
            } else {
                bidsScroll -= delta;
            }
            clampModalScrolls();
            if (modal == Modal.CREATE || modal == Modal.RELIST || modal == Modal.FILTER || modal == Modal.MOD_FILTER) {
                rebuildWidgets();
            }
            return true;
        }
        GuiEventListener focused = getFocused();
        if (isModalChild(focused) && focused.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (modal == Modal.NONE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 256) {
            closeModal();
            return true;
        }
        if (modal == Modal.MOD_FILTER && modSearchBox != null && modSearchBox.isFocused() && modSearchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        GuiEventListener focused = getFocused();
        if (isModalChild(focused) && focused.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (modal == Modal.NONE) {
            return super.keyReleased(keyCode, scanCode, modifiers);
        }
        GuiEventListener focused = getFocused();
        return isModalChild(focused) && focused.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (modal == Modal.NONE) {
            return super.charTyped(codePoint, modifiers);
        }
        if (modal == Modal.MOD_FILTER && modSearchBox != null && modSearchBox.isFocused() && modSearchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        GuiEventListener focused = getFocused();
        if (isModalChild(focused) && focused.charTyped(codePoint, modifiers)) {
            return true;
        }
        return true;
    }

    private boolean clickInventoryGrid(double mouseX, double mouseY) {
        if (!inModalBody(mouseY)) {
            return false;
        }
        if (minecraft != null && minecraft.player != null) {
            int columns = 9;
            int size = Math.min(minecraft.player.getInventory().getContainerSize(), 36);
            for (int slot = 0; slot < size; slot++) {
                int col = slot % columns;
                int row = slot / columns;
                int x = inventoryGridLeft + col * 22;
                int y = inventoryGridTop + row * 22;
                if (mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + 20) {
                    int clickedSlot = slot;
                    ItemStack stack = minecraft.player.getInventory().getItem(clickedSlot);
                    if (stack.isEmpty()) {
                        return true;
                    }
                    if (selectedInventorySlots.contains(clickedSlot)) {
                        selectedInventorySlots = selectedInventorySlots.stream()
                                .filter(selected -> selected != clickedSlot)
                                .toList();
                    } else if (selectedInventorySlots.size() < 18) {
                        selectedInventorySlots = new ArrayList<>(selectedInventorySlots);
                        selectedInventorySlots.add(clickedSlot);
                    }
                    selectedInventorySlot = selectedInventorySlots.isEmpty() ? -1 : selectedInventorySlots.getFirst();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isModalChild(GuiEventListener listener) {
        if (listener == null || modal == Modal.NONE) {
            return false;
        }
        List<? extends GuiEventListener> listeners = children();
        int start = Math.max(0, Math.min(modalChildStart, listeners.size()));
        for (int i = start; i < listeners.size(); i++) {
            if (listeners.get(i) == listener) {
                return true;
            }
        }
        return false;
    }

    private List<AuctionEntrySummary> visibleEntries() {
        if (payload.adminMode()) {
            if (adminSection == AdminSection.MODERATION) {
                List<AuctionEntrySummary> entries = new ArrayList<>();
                entries.addAll(adminDashboard().failedSettlements());
                entries.addAll(adminDashboard().restrictedListings().stream()
                        .filter(entry -> entries.stream().noneMatch(existing -> existing.auctionId().equals(entry.auctionId())))
                        .toList());
                return entries;
            }
            return adminSection == AdminSection.AUCTIONS ? payload.browseListings() : List.of();
        }
        return switch (activeTab) {
            case DASHBOARD -> payload.dashboardListings();
            case BROWSE -> payload.browseListings();
            case MY_BIDS -> payload.myBids();
            case MY_AUCTIONS -> payload.myAuctions();
        };
    }

    private String modalTitle() {
        return switch (modal) {
            case BID -> bidActionLabel(selectedAuction);
            case CREATE -> "Create Auction";
            case RELIST -> "Relist Auction";
            case CONFIRM_CREATE -> "Confirm Auction";
            case DATE_PICKER -> "Select End Date & Time";
            case BIDS -> "Auction Bids";
            case DELIVERY -> "Delivery Storage";
            case FILTER -> "Filters";
            case MOD_FILTER -> "Choose Mod";
            case CONTENTS -> "Auction Contents";
            case ACCOUNT_SELECTOR -> "Choose Account";
            case ADMIN_FORCE_CANCEL -> "Force Cancel Auction";
            case NONE -> "";
        };
    }

    private String nextBidValue(AuctionEntrySummary entry) {
        if (entry == null) {
            return "";
        }
        try {
            if (entry.sealedBid() && !entry.sealedBidRevealed()) {
                return moneyDraft(entry.startingBid()).stripTrailingZeros().toPlainString();
            }
            return moneyDraft(entry.currentBid()).add(BigDecimal.ONE).stripTrailingZeros().toPlainString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String bidActionLabel(AuctionEntrySummary entry) {
        if (entry != null && entry.sealedBid()) {
            return entry.viewerHasBid() ? "Raise Sealed Bid" : "Place Sealed Bid";
        }
        return entry != null && entry.viewerHasBid() ? "Raise Bid" : "Place Bid";
    }

    private String currentBidText(AuctionEntrySummary entry) {
        if (entry == null) {
            return "";
        }
        if (entry.sealedBid() && !entry.sealedBidRevealed()) {
            return Component.translatable("Sealed").getString();
        }
        return entry.currentBid();
    }

    private String auctionFormatLabel(boolean sealedBid) {
        return Component.translatable(sealedBid ? "Sealed Bid" : "Normal Auction").getString();
    }

    private String timeFilterLabel() {
        return timeFilterLabel(maxHoursLeft);
    }

    private String timeFilterLabel(long hoursLeft) {
        if (hoursLeft == 0L) {
            return Component.translatable("Any Time").getString();
        }
        if (hoursLeft == 1L) {
            return Component.translatable("Under 1 Hour").getString();
        }
        if (hoursLeft == 24L) {
            return Component.translatable("Under 24 Hours").getString();
        }
        return Component.translatable("Under 7 Days").getString();
    }

    private String timeLeft(String rawEnd, String state) {
        if (!"ACTIVE".equals(state)) {
            return Component.translatable(state).getString();
        }
        try {
            Duration duration = Duration.between(LocalDateTime.now(), LocalDateTime.parse(rawEnd));
            if (duration.isNegative() || duration.isZero()) {
                return Component.translatable("Ended").getString();
            }
            long days = duration.toDays();
            long hours = duration.toHoursPart();
            long minutes = duration.toMinutesPart();
            if (days > 0) {
                return Component.translatable("{0}d {1}h", days, hours).getString();
            }
            if (hours > 0) {
                return Component.translatable("{0}h {1}m", hours, minutes).getString();
            }
            return Component.translatable("{0}m", Math.max(1, minutes)).getString();
        } catch (DateTimeParseException exception) {
            return "";
        }
    }

    private String timeAgo(String rawTimestamp) {
        try {
            Duration duration = Duration.between(LocalDateTime.parse(rawTimestamp), LocalDateTime.now());
            if (duration.isNegative()) {
                duration = Duration.ZERO;
            }
            long days = duration.toDays();
            if (days > 0) {
                return Component.translatable("{0}d ago", days).getString();
            }
            long hours = duration.toHours();
            if (hours > 0) {
                return Component.translatable("{0}h ago", hours).getString();
            }
            long minutes = duration.toMinutes();
            if (minutes > 0) {
                return Component.translatable("{0}m ago", minutes).getString();
            }
            return Component.translatable("just now").getString();
        } catch (DateTimeParseException exception) {
            return rawTimestamp == null ? "" : rawTimestamp;
        }
    }

    private boolean adminCanForceCancel(AuctionEntrySummary entry) {
        if (entry == null) {
            return false;
        }
        String state = normalizedState(entry);
        return !"CLAIMED".equals(state) && !"CANCELLED".equals(state);
    }

    private boolean isClaimed(AuctionEntrySummary entry) {
        return "CLAIMED".equals(normalizedState(entry));
    }

    private String normalizedState(AuctionEntrySummary entry) {
        return entry == null || entry.state() == null ? "" : entry.state().trim().toUpperCase(Locale.ROOT);
    }

    private List<String> itemMetadataLines(ItemStack stack, int maxWidth, int maxLines) {
        if (stack == null || stack.isEmpty() || minecraft == null || maxLines <= 0) {
            return List.of();
        }
        Item.TooltipContext context = minecraft.level == null ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(minecraft.level);
        return stack.getTooltipLines(context, minecraft.player, TooltipFlag.Default.NORMAL).stream()
                .skip(1)
                .map(Component::getString)
                .filter(line -> line != null && !line.isBlank())
                .limit(maxLines)
                .map(line -> trimToWidth(line, maxWidth))
                .toList();
    }

    private List<String> fullItemMetadataLines(ItemStack stack, int maxWidth) {
        return itemMetadataLines(stack, maxWidth, Integer.MAX_VALUE);
    }

    private String readableDateTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return "";
        }
        try {
            LocalDateTime time = LocalDateTime.parse(rawTime);
            String month = time.getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT);
            return Component.translatable("{0} {1}, {2}", Component.translatable(month), time.getDayOfMonth(), timeLabel(time.getHour(), time.getMinute())).getString();
        } catch (DateTimeParseException exception) {
            return rawTime;
        }
    }

    private String durationFromNow(String rawTime) {
        try {
            Duration duration = Duration.between(LocalDateTime.now(), LocalDateTime.parse(rawTime));
            if (duration.isNegative() || duration.isZero()) {
                return Component.translatable("now").getString();
            }
            long days = duration.toDays();
            long hours = duration.toHoursPart();
            long minutes = duration.toMinutesPart();
            if (days > 0) {
                return Component.translatable("{0}d {1}h", days, hours).getString();
            }
            if (hours > 0) {
                return Component.translatable("{0}h {1}m", hours, minutes).getString();
            }
            return Component.translatable("{0}m", Math.max(1, duration.toMinutes())).getString();
        } catch (DateTimeParseException exception) {
            return "";
        }
    }

    private List<AuctionBidSummary> acceptedBids(AuctionEntrySummary entry) {
        if (entry == null) {
            return List.of();
        }
        return entry.bidHistory().stream()
                .filter(AuctionBidSummary::accepted)
                .sorted(this::compareBids)
                .toList();
    }

    private int compareBids(AuctionBidSummary left, AuctionBidSummary right) {
        int amount = moneyDraft(right.amount()).compareTo(moneyDraft(left.amount()));
        if (amount != 0) {
            return amount;
        }
        String rightTime = right.timestamp() == null ? "" : right.timestamp();
        String leftTime = left.timestamp() == null ? "" : left.timestamp();
        return rightTime.compareTo(leftTime);
    }

    private int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toLowerCase()) {
            case "uncommon" -> 0xFF55FF55;
            case "rare" -> 0xFF5555FF;
            case "epic" -> 0xFFAA00AA;
            case "legendary" -> 0xFFFFAA00;
            default -> 0xFFAAAAAA;
        };
    }

    private String labelCount(int count) {
        return count <= 0 ? "" : " (" + count + ")";
    }

    private BigDecimal listingFeePreview() {
        return moneyDraft(startingBidDraft)
                .multiply(BigDecimal.valueOf(Math.max(0.0D, payload.listingFeeRate())))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String listingFeeRateLabel() {
        return BigDecimal.valueOf(Math.max(0.0D, payload.listingFeeRate()))
                .multiply(BigDecimal.valueOf(100L))
                .stripTrailingZeros()
                .toPlainString();
    }

    private BigDecimal moneyDraft(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            String cleaned = raw.trim().replace(",", "");
            BigDecimal multiplier = BigDecimal.ONE;
            if (!cleaned.isEmpty()) {
                char suffix = Character.toUpperCase(cleaned.charAt(cleaned.length() - 1));
                multiplier = switch (suffix) {
                    case 'K' -> BigDecimal.valueOf(1_000L);
                    case 'M' -> BigDecimal.valueOf(1_000_000L);
                    case 'B' -> BigDecimal.valueOf(1_000_000_000L);
                    case 'T' -> BigDecimal.valueOf(1_000_000_000_000L);
                    default -> BigDecimal.ONE;
                };
                if (multiplier.compareTo(BigDecimal.ONE) > 0) {
                    cleaned = cleaned.substring(0, cleaned.length() - 1);
                }
            }
            cleaned = cleaned.replaceAll("[^0-9+\\-.]", "");
            if (cleaned.isBlank() || "-".equals(cleaned) || "+".equals(cleaned) || ".".equals(cleaned)) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(cleaned).multiply(multiplier).max(BigDecimal.ZERO);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String moneyDisplay(BigDecimal amount) {
        return UasMoneyFormatter.display(amount);
    }

    private boolean isZeroMoneyLabel(String label) {
        return moneyDraft(label).compareTo(BigDecimal.ZERO) <= 0;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null || text.isBlank() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int widthWithoutEllipsis = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(text, widthWithoutEllipsis) + ellipsis;
    }

    private List<String> wrapText(String text, int maxWidth, int maxLines) {
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) {
            return List.of();
        }
        String remaining = text.trim().replace('\n', ' ');
        List<String> lines = new ArrayList<>();
        while (!remaining.isBlank() && lines.size() < maxLines) {
            String line = font.plainSubstrByWidth(remaining, maxWidth);
            if (line.length() < remaining.length()) {
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace > 8) {
                    line = line.substring(0, lastSpace);
                }
            }
            line = line.trim();
            if (line.isEmpty()) {
                break;
            }
            lines.add(line);
            remaining = remaining.substring(Math.min(line.length(), remaining.length())).trim();
        }
        if (!remaining.isBlank() && !lines.isEmpty()) {
            String last = lines.remove(lines.size() - 1);
            lines.add(trimToWidth(last + "...", maxWidth));
        }
        return lines;
    }

    private String searchValue() {
        return value(searchBox);
    }

    private String minPriceValue() {
        return sanitizeMoneyInput(value(minPriceBox, minPriceDraft));
    }

    private String maxPriceValue() {
        return sanitizeMoneyInput(value(maxPriceBox, maxPriceDraft));
    }

    private String value(EditBox box) {
        return box == null ? "" : box.getValue();
    }

    private String value(EditBox box, String fallback) {
        if (box == null) {
            return fallback == null ? "" : fallback;
        }
        return box.getValue();
    }

    private String value(AuctionTextArea box, String fallback) {
        if (box == null) {
            return fallback == null ? "" : fallback;
        }
        return box.getValue();
    }

}
