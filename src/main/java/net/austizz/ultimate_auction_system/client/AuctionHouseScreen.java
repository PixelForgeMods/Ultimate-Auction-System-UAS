package net.austizz.ultimate_auction_system.client;

import net.austizz.ultimate_auction_system.AuctionCategory;
import net.austizz.ultimate_auction_system.AuctionSort;
import net.austizz.ultimate_auction_system.network.AuctionActionPayload;
import net.austizz.ultimate_auction_system.network.AuctionBidSummary;
import net.austizz.ultimate_auction_system.network.AuctionDeliverySummary;
import net.austizz.ultimate_auction_system.network.AuctionEntrySummary;
import net.austizz.ultimate_auction_system.network.AuctionSnapshotPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public class AuctionHouseScreen extends Screen {
    private record DatePickerLayout(int cell, int calendarWidth, int calendarX, int monthY, int weekdayY, int dayTop, int hourY, int actionY) {
    }

    private record RowAction(String label, AuctionButton.Style style, Consumer<AuctionButton> onPress) {
    }

    private enum Tab {
        BROWSE("Browse"),
        MY_BIDS("My Bids"),
        MY_AUCTIONS("My Auctions");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum Modal {
        NONE,
        BID,
        CREATE,
        DATE_PICKER,
        BIDS,
        DELIVERY,
        FILTER
    }

    private AuctionSnapshotPayload payload;
    private Tab activeTab = Tab.BROWSE;
    private Modal modal = Modal.NONE;
    private AuctionEntrySummary selectedAuction;
    private int selectedInventorySlot = -1;
    private int page = 0;
    private AuctionCategory category = AuctionCategory.ALL;
    private AuctionSort sort = AuctionSort.ENDING_SOON;
    private long maxHoursLeft = 0L;
    private String searchDraft = "";
    private String minPriceDraft = "";
    private String maxPriceDraft = "";
    private String startingBidDraft = "";
    private String buyoutDraft = "";
    private String descriptionDraft = "";
    private LocalDate selectedEndDate = LocalDate.now().plusDays(1);
    private int selectedEndHour = 12;
    private YearMonth calendarMonth = YearMonth.now();

    private EditBox searchBox;
    private EditBox minPriceBox;
    private EditBox maxPriceBox;
    private EditBox bidBox;
    private EditBox startingBidBox;
    private EditBox buyoutBox;
    private EditBox descriptionBox;

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
    private int modalRenderableStart = 0;
    private int modalChildStart = 0;

    public AuctionHouseScreen(AuctionSnapshotPayload payload) {
        super(Component.translatable("Auction House"));
        this.payload = payload;
    }

    public void refresh(AuctionSnapshotPayload updated) {
        this.payload = updated;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        searchDraft = value(searchBox, searchDraft);
        minPriceDraft = value(minPriceBox, minPriceDraft);
        maxPriceDraft = value(maxPriceBox, maxPriceDraft);
        clearWidgets();
        panelWidth = Math.min(1000, Math.max(360, width - 22));
        panelHeight = Math.min(700, Math.max(260, height - 22));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        boolean compactHeader = panelWidth < 760;
        boolean narrowHeader = panelWidth < 560;
        boolean stackedTabs = panelWidth < 560;
        int tabY = panelTop + (stackedTabs ? 122 : compactHeader ? 78 : 54);
        headerHeight = stackedTabs ? 162 : compactHeader ? 118 : 92;
        contentLeft = panelLeft + 16;
        contentTop = panelTop + headerHeight + 12;
        contentWidth = panelWidth - 32;
        contentHeight = Math.max(48, panelHeight - headerHeight - 34);
        if (activeTab == Tab.MY_AUCTIONS && stackedTabs) {
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
        searchBox.setResponder(value -> searchDraft = value);
        addRenderableWidget(searchBox);

        int tabX = panelLeft + 16;
        addAuctionButton(tabX, stackedTabs ? tabY - 30 : tabY, 74, 24, "Filters", filtersActive() ? AuctionButton.Style.TAB_ACTIVE : AuctionButton.Style.GRAY, button -> {
            modal = Modal.FILTER;
            filterScroll = 0;
            rebuildWidgets();
        });
        int tabsX = stackedTabs ? tabX : tabX + 82;
        addTabButton(tabsX, tabY, 86, Tab.BROWSE);
        addTabButton(tabsX + 92, tabY, 88, Tab.MY_BIDS);
        addTabButton(tabsX + 186, tabY, 104, Tab.MY_AUCTIONS);

        if (activeTab == Tab.MY_AUCTIONS) {
            int createY = stackedTabs ? tabY + 30 : tabY;
            addAuctionButton(panelLeft + panelWidth - 170, createY, 154, 24, "Create Auction", AuctionButton.Style.GRAY, button -> {
                modal = Modal.CREATE;
                createScroll = 0;
                resetCreateForm();
                rebuildWidgets();
            });
        }

        addContentButtons();

        modalRenderableStart = renderables.size();
        modalChildStart = children().size();
        if (modal != Modal.NONE) {
            clearFocus();
            clampModalScrolls();
            addModalWidgets();
        }
    }

    private void addTabButton(int x, int y, int w, Tab tab) {
        AuctionButton button = addAuctionButton(x, y, w, 24, tab.label, activeTab == tab ? AuctionButton.Style.TAB_ACTIVE : AuctionButton.Style.DARK, ignored -> {
            activeTab = tab;
            page = 0;
            modal = Modal.NONE;
            rebuildWidgets();
        });
        button.active = activeTab != tab;
    }

    private AuctionButton addAuctionButton(int x, int y, int w, int h, String label, AuctionButton.Style style, Consumer<AuctionButton> onPress) {
        return addRenderableWidget(new AuctionButton(x, y, w, h, Component.translatable(label), style, onPress));
    }

    private AuctionButton addAuctionButton(int x, int y, int w, int h, Component label, AuctionButton.Style style, Consumer<AuctionButton> onPress) {
        return addRenderableWidget(new AuctionButton(x, y, w, h, label, style, onPress));
    }

    private void addContentButtons() {
        List<AuctionEntrySummary> entries = visibleEntries();
        int rowHeight = auctionRowHeight();
        int perPage = Math.max(1, contentHeight / rowHeight);
        int start = page * perPage;
        int end = Math.min(entries.size(), start + perPage);
        int y = contentTop + 8;

        for (int i = start; i < end; i++) {
            AuctionEntrySummary entry = entries.get(i);
            int rowTop = y + (i - start) * rowHeight;
            addRowActionButtons(entry, rowTop, rowHeight);
        }

        int pagerY = panelTop + panelHeight - 28;
        AuctionButton prev = addAuctionButton(contentLeft, pagerY, 58, 20, "Prev", AuctionButton.Style.GRAY, button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        });
        prev.active = page > 0;

        AuctionButton next = addAuctionButton(contentLeft + 64, pagerY, 58, 20, "Next", AuctionButton.Style.GRAY, button -> {
            page++;
            rebuildWidgets();
        });
        next.active = end < entries.size();
    }

    private void addRowActionButtons(AuctionEntrySummary entry, int rowTop, int rowHeight) {
        List<RowAction> actions = new ArrayList<>();
        actions.add(new RowAction(entry.viewerReceivesNotifications() ? "Watching" : "Notify", entry.viewerReceivesNotifications() ? AuctionButton.Style.GREEN : AuctionButton.Style.GRAY, button -> sendAuctionAction("TOGGLE_NOTIFICATIONS", entry, "", null)));
        actions.add(new RowAction("View Bids", AuctionButton.Style.GRAY, button -> openBids(entry)));

        if (activeTab == Tab.BROWSE) {
            if (entry.canBid()) {
                actions.add(new RowAction(entry.viewerHasBid() ? "Raise Bid" : "Place Bid", AuctionButton.Style.GREEN, button -> openBid(entry)));
            }
            if (entry.canBuyout()) {
                actions.add(new RowAction("Buyout", AuctionButton.Style.GRAY, button -> sendAuctionAction("BUYOUT", entry, "", null)));
            }
        } else if (activeTab == Tab.MY_BIDS) {
            if (entry.canBid()) {
                actions.add(new RowAction("Raise Bid", AuctionButton.Style.GREEN, button -> openBid(entry)));
            }
            if (entry.canClaim()) {
                actions.add(new RowAction("Claim", AuctionButton.Style.GREEN, button -> sendAuctionAction("CLAIM", entry, "", null)));
            }
        } else {
            if (entry.canCancel()) {
                actions.add(new RowAction("Cancel", AuctionButton.Style.RED, button -> sendAuctionAction("CANCEL", entry, "", null)));
            } else if (entry.canClaim()) {
                actions.add(new RowAction("Claim", AuctionButton.Style.GREEN, button -> sendAuctionAction("CLAIM", entry, "", null)));
            }
        }

        int buttonH = 20;
        int gap = 6;
        int buttonW = 86;
        int totalW = actions.size() * buttonW + Math.max(0, actions.size() - 1) * gap;
        if (contentWidth >= 760 && totalW <= contentWidth - 260) {
            int x = contentLeft + contentWidth - totalW - 14;
            int y = rowTop + rowHeight - 36;
            for (RowAction action : actions) {
                addAuctionButton(x, y, buttonW, buttonH, action.label(), action.style(), action.onPress());
                x += buttonW + gap;
            }
            return;
        }

        int stackedButtonW = 88;
        int x = contentLeft + contentWidth - stackedButtonW - 14;
        int y = rowTop + 12;
        for (RowAction action : actions) {
            addAuctionButton(x, y, stackedButtonW, buttonH, action.label(), action.style(), action.onPress());
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
        }
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
        bidBox.setValue(nextBidValue(selectedAuction));
        addRenderableWidget(bidBox);

        addAuctionButton(x + modalW - 20 - minW, inputY + 16, minW, 24, Component.literal("MIN"), AuctionButton.Style.GRAY, button -> bidBox.setValue(nextBidValue(selectedAuction)));

        int quickW = Math.max(52, (modalW - 64) / 4);
        int quickX = x + 20;
        addAuctionButton(quickX, quickY, quickW, 24, Component.literal("+$100"), AuctionButton.Style.GRAY, button -> addBidIncrement("100"));
        addAuctionButton(quickX + quickW + gap, quickY, quickW, 24, Component.literal("+$500"), AuctionButton.Style.GRAY, button -> addBidIncrement("500"));
        addAuctionButton(quickX + (quickW + gap) * 2, quickY, quickW, 24, Component.literal("+$1000"), AuctionButton.Style.GRAY, button -> addBidIncrement("1000"));
        addAuctionButton(quickX + (quickW + gap) * 3, quickY, quickW, 24, Component.literal("+$5000"), AuctionButton.Style.GRAY, button -> addBidIncrement("5000"));

        int actionW = Math.max(110, (modalW - 52) / 2);
        addAuctionButton(x + 20, actionY, actionW, 26, "Cancel", AuctionButton.Style.GRAY, button -> closeModal());
        addAuctionButton(x + modalW - actionW - 20, actionY, actionW, 26, "Confirm Bid", AuctionButton.Style.GREEN, button -> sendAuctionAction("BID", selectedAuction, bidBox.getValue(), null));
    }

    private void addCreateModalWidgets(int x, int y, int modalW, int modalH) {
        boolean compact = compactCreateModal(modalW);
        int scroll = createScroll;
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        int startingY = y + (compact ? 274 : 190) - scroll;
        int buyoutY = y + (compact ? 322 : 190) - scroll;
        int endY = y + (compact ? 370 : 190) - scroll;
        int descriptionY = y + (compact ? 418 : 238) - scroll;
        int fieldW = compact ? modalW - 40 : 138;

        startingBidBox = new AuctionEditBox(font, x + 20, startingY, fieldW, 22, Component.translatable("Starting Bid"));
        startingBidBox.setHint(Component.translatable("Enter starting bid"));
        startingBidBox.setValue(startingBidDraft);
        startingBidBox.setResponder(value -> startingBidDraft = value);
        setVisibleInModalBody(startingBidBox, bodyTop, bodyBottom);
        addRenderableWidget(startingBidBox);

        buyoutBox = new AuctionEditBox(font, compact ? x + 20 : x + 170, buyoutY, compact ? modalW - 40 : 128, 22, Component.translatable("Buyout"));
        buyoutBox.setHint(Component.translatable("Optional buyout"));
        buyoutBox.setValue(buyoutDraft);
        buyoutBox.setResponder(value -> buyoutDraft = value);
        setVisibleInModalBody(buyoutBox, bodyTop, bodyBottom);
        addRenderableWidget(buyoutBox);

        AuctionButton endDateButton = addAuctionButton(compact ? x + 20 : x + 310, endY, compact ? modalW - 40 : Math.max(90, modalW - 330), 22, Component.literal(endDateDisplay()), AuctionButton.Style.DARK, button -> {
            modal = Modal.DATE_PICKER;
            calendarMonth = YearMonth.from(selectedEndDate);
            rebuildWidgets();
        });
        setVisibleInModalBody(endDateButton, bodyTop, bodyBottom);

        descriptionBox = new AuctionEditBox(font, x + 20, descriptionY, modalW - 40, 22, Component.translatable("Description"));
        descriptionBox.setHint(Component.translatable("Describe the auction"));
        descriptionBox.setValue(descriptionDraft);
        descriptionBox.setResponder(value -> descriptionDraft = value);
        setVisibleInModalBody(descriptionBox, bodyTop, bodyBottom);
        addRenderableWidget(descriptionBox);

        inventoryGridLeft = x + 20;
        inventoryGridTop = y + 72 - scroll;

        int actionY = y + modalH - 36;
        int cancelW = modalW < 360 ? 112 : 150;
        int createW = modalW < 360 ? 142 : 160;
        addAuctionButton(x + 20, actionY, cancelW, 26, "Cancel", AuctionButton.Style.GRAY, button -> closeModal());
        AuctionButton createButton = addAuctionButton(x + modalW - createW - 20, actionY, createW, 26, "Create Auction", AuctionButton.Style.GREEN, button -> sendCreateAction());
        createButton.active = !selectedInventoryStack().isEmpty();
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
            page = 0;
            rebuildWidgets();
        });
        setVisibleInModalBody(categoryButton, bodyTop, bodyBottom);

        minPriceBox = new AuctionEditBox(font, fieldX, y + 134 - scroll, fieldW, 20, Component.translatable("Min Price"));
        minPriceBox.setHint(Component.translatable("Min Price"));
        minPriceBox.setValue(minPriceDraft);
        minPriceBox.setResponder(value -> minPriceDraft = value);
        setVisibleInModalBody(minPriceBox, bodyTop, bodyBottom);
        addRenderableWidget(minPriceBox);

        maxPriceBox = new AuctionEditBox(font, fieldX, y + 162 - scroll, fieldW, 20, Component.translatable("Max Price"));
        maxPriceBox.setHint(Component.translatable("Max Price"));
        maxPriceBox.setValue(maxPriceDraft);
        maxPriceBox.setResponder(value -> maxPriceDraft = value);
        setVisibleInModalBody(maxPriceBox, bodyTop, bodyBottom);
        addRenderableWidget(maxPriceBox);

        AuctionButton timeButton = addAuctionButton(fieldX, y + 224 - scroll, fieldW, 22, timeFilterLabel(), AuctionButton.Style.GRAY, button -> {
            if (maxHoursLeft == 0L) {
                maxHoursLeft = 1L;
            } else if (maxHoursLeft == 1L) {
                maxHoursLeft = 24L;
            } else if (maxHoursLeft == 24L) {
                maxHoursLeft = 168L;
            } else {
                maxHoursLeft = 0L;
            }
            page = 0;
            rebuildWidgets();
        });
        setVisibleInModalBody(timeButton, bodyTop, bodyBottom);

        AuctionButton sortButton = addAuctionButton(fieldX, y + 286 - scroll, fieldW, 22, sort.label(), AuctionButton.Style.GRAY, button -> {
            AuctionSort[] values = AuctionSort.values();
            sort = values[(sort.ordinal() + 1) % values.length];
            page = 0;
            rebuildWidgets();
        });
        setVisibleInModalBody(sortButton, bodyTop, bodyBottom);

        addAuctionButton(x + 18, y + modalH - 36, modalW - 118, 26, "Apply Filters", AuctionButton.Style.GREEN, button -> applyFilters());
        addAuctionButton(x + modalW - 88, y + modalH - 36, 70, 26, "Close", AuctionButton.Style.GRAY, button -> closeModal());
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

        addAuctionButton(x + 22, layout.hourY(), 30, 24, Component.literal("<"), AuctionButton.Style.GRAY, button -> {
            selectedEndHour = Math.floorMod(selectedEndHour - 1, 24);
            rebuildWidgets();
        });
        addAuctionButton(x + 58, layout.hourY(), modalW - 116, 24, Component.literal(hourLabel(selectedEndHour)), AuctionButton.Style.DARK, button -> {
            selectedEndHour = Math.floorMod(selectedEndHour + 1, 24);
            rebuildWidgets();
        });
        addAuctionButton(x + modalW - 52, layout.hourY(), 30, 24, Component.literal(">"), AuctionButton.Style.GRAY, button -> {
            selectedEndHour = Math.floorMod(selectedEndHour + 1, 24);
            rebuildWidgets();
        });

        addAuctionButton(x + 22, layout.actionY(), 120, 26, "Cancel", AuctionButton.Style.GRAY, button -> {
            modal = Modal.CREATE;
            rebuildWidgets();
        });
        AuctionButton confirm = addAuctionButton(x + modalW - 142, layout.actionY(), 120, 26, "Confirm", AuctionButton.Style.GREEN, button -> {
            modal = Modal.CREATE;
            rebuildWidgets();
        });
        confirm.active = selectedEndDateTime().isAfter(LocalDateTime.now());
    }

    private DatePickerLayout datePickerLayout(int x, int y, int modalW, int modalH) {
        int monthY = y + (modalH < 390 ? 62 : 74);
        int dayTop = y + (modalH < 390 ? 110 : 128);
        int actionY = y + modalH - 38;
        int hourY = actionY - 58;
        int availableHeight = Math.max(18, hourY - 24 - dayTop);
        int cellByHeight = Math.max(18, availableHeight / 6);
        int cellByWidth = Math.max(18, (modalW - 80) / 7);
        int cell = Math.max(18, Math.min(30, Math.min(cellByHeight, cellByWidth)));
        int calendarWidth = cell * 7;
        int calendarX = x + (modalW - calendarWidth) / 2;
        int weekdayY = dayTop - 18;
        return new DatePickerLayout(cell, calendarWidth, calendarX, monthY, weekdayY, dayTop, hourY, actionY);
    }

    private int modalWidth() {
        if (modal == Modal.FILTER) {
            return Math.min(280, panelWidth - 32);
        }
        if (modal == Modal.DATE_PICKER) {
            return Math.min(390, panelWidth - 44);
        }
        if (modal == Modal.BID || modal == Modal.BIDS) {
            return Math.min(560, panelWidth - 44);
        }
        return Math.min(520, panelWidth - 44);
    }

    private int modalHeight() {
        if (modal == Modal.FILTER) {
            return Math.max(220, panelHeight - 34);
        }
        if (modal == Modal.CREATE || modal == Modal.DATE_PICKER || modal == Modal.BID || modal == Modal.BIDS) {
            return Math.min(430, panelHeight - 48);
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
        modal = modal == Modal.DATE_PICKER ? Modal.CREATE : Modal.NONE;
        rebuildWidgets();
    }

    private void resetCreateForm() {
        selectedInventorySlot = -1;
        startingBidDraft = "";
        buyoutDraft = "";
        descriptionDraft = "";
        selectedEndDate = LocalDate.now().plusDays(1);
        selectedEndHour = 12;
        calendarMonth = YearMonth.from(selectedEndDate);
    }

    private boolean selectableEndDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        return !date.isBefore(today) && !date.isAfter(today.plusDays(30));
    }

    private LocalDateTime selectedEndDateTime() {
        return selectedEndDate.atTime(selectedEndHour, 0);
    }

    private String endDateDisplay() {
        LocalDateTime end = selectedEndDateTime();
        String month = end.getMonth().getDisplayName(TextStyle.SHORT, Locale.ROOT);
        return month + " " + end.getDayOfMonth() + ", " + hourLabel(end.getHour());
    }

    private String hourLabel(int hour) {
        int displayHour = hour % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }
        return displayHour + ":00 " + (hour < 12 ? "AM" : "PM");
    }

    private ItemStack selectedInventoryStack() {
        if (minecraft == null || minecraft.player == null || selectedInventorySlot < 0) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = minecraft.player.getInventory();
        if (selectedInventorySlot >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(selectedInventorySlot);
    }

    private void openBid(AuctionEntrySummary entry) {
        selectedAuction = entry;
        modal = Modal.BID;
        rebuildWidgets();
    }

    private void openBids(AuctionEntrySummary entry) {
        selectedAuction = entry;
        modal = Modal.BIDS;
        bidsScroll = 0;
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
        bidBox.setValue(current.add(moneyDraft(rawIncrement)).stripTrailingZeros().toPlainString());
    }

    private void sendCreateAction() {
        startingBidDraft = value(startingBidBox, startingBidDraft);
        buyoutDraft = value(buyoutBox, buyoutDraft);
        descriptionDraft = value(descriptionBox, descriptionDraft);
        PacketDistributor.sendToServer(new AuctionActionPayload(
                "CREATE",
                null,
                null,
                selectedInventorySlot,
                "",
                startingBidDraft,
                buyoutDraft,
                Math.max(1, (int) Duration.between(LocalDateTime.now(), selectedEndDateTime()).toHours()),
                selectedEndDateTime().toString(),
                descriptionDraft,
                searchValue(),
                category.name(),
                sort.name(),
                minPriceValue(),
                maxPriceValue(),
                maxHoursLeft
        ));
        modal = Modal.NONE;
    }

    private void sendAuctionAction(String action, AuctionEntrySummary entry, String amount, UUID deliveryId) {
        PacketDistributor.sendToServer(new AuctionActionPayload(
                action,
                entry == null ? null : entry.auctionId(),
                deliveryId,
                -1,
                amount == null ? "" : amount,
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
                maxHoursLeft
        ));
        modal = Modal.NONE;
    }

    private void refreshFromServer() {
        PacketDistributor.sendToServer(AuctionActionPayload.refresh(
                searchValue(),
                category.name(),
                sort.name(),
                minPriceValue(),
                maxPriceValue(),
                maxHoursLeft
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderAuctionBackdrop(graphics);
        renderPanel(graphics);
        renderHeader(graphics);
        renderSidebar(graphics);
        renderContent(graphics);
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
        graphics.fill(panelLeft + 10, panelTop + 10, panelLeft + panelWidth - 10, panelTop + headerHeight - 10, 0xFF565656);
        graphics.drawString(font, Component.translatable("AUCTION HOUSE").withStyle(ChatFormatting.BOLD), panelLeft + 18, panelTop + 20, 0xFFFFAA00, false);
        int accountY = panelWidth < 760 ? panelTop + 31 : panelTop + 42;
        if (payload.account().present()) {
            graphics.drawString(font, Component.literal(payload.account().accountTypeLabel() + " $" + payload.account().balance()), panelLeft + 18, accountY, 0xFF55FF55, false);
        } else {
            graphics.drawString(font, Component.translatable("No UBS account"), panelLeft + 18, accountY, 0xFFFF5555, false);
        }
        if (!payload.message().isBlank()) {
            String message = trimToWidth(Component.translatable(payload.message()).getString(), panelWidth - 36);
            graphics.drawString(font, Component.literal(message), panelLeft + 18, contentTop - 12, payload.success() ? 0xFF55FF55 : 0xFFFF5555, false);
        }
    }

    private void renderSidebar(GuiGraphics graphics) {
        // Filters are opened from the header as a scrollable side modal.
    }

    private void renderContent(GuiGraphics graphics) {
        List<AuctionEntrySummary> entries = visibleEntries();
        int rowHeight = auctionRowHeight();
        int perPage = Math.max(1, contentHeight / rowHeight);
        int start = page * perPage;
        int end = Math.min(entries.size(), start + perPage);

        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("No auctions to show"), contentLeft + contentWidth / 2, contentTop + 70, 0xFFDDDDDD);
            return;
        }

        for (int i = start; i < end; i++) {
            AuctionEntrySummary entry = entries.get(i);
            int y = contentTop + 8 + (i - start) * rowHeight;
            renderAuctionRow(graphics, entry, contentLeft, y, contentWidth, rowHeight - 8);
        }
        graphics.drawString(font, Component.literal((page + 1) + " / " + Math.max(1, (int) Math.ceil(entries.size() / (double) perPage))), contentLeft + 130, panelTop + panelHeight - 23, 0xFFE0E0E0, false);
    }

    private void renderAuctionRow(GuiGraphics graphics, AuctionEntrySummary entry, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF2B2B2B);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF414141);
        int rarityColor = rarityColor(entry.rarity());
        int actionColumnW = rowActionColumnWidth(entry);
        int textX = x + 64;
        int textW = Math.max(80, w - actionColumnW - 80);
        graphics.fill(x + 10, y + 10, x + 54, y + 54, 0xFF1C1C1C);
        graphics.fill(x + 13, y + 13, x + 51, y + 51, 0x33000000 | (rarityColor & 0x00FFFFFF));
        graphics.renderItem(entry.item(), x + 24, y + 24);
        graphics.renderItemDecorations(font, entry.item(), x + 24, y + 24);

        graphics.drawString(font, Component.literal(trimToWidth(entry.itemName(), textW)), textX, y + 12, rarityColor, false);
        graphics.drawString(font, Component.literal(Component.translatable("Seller").getString() + ": " + trimToWidth(entry.sellerName(), textW - 40)), textX, y + 28, 0xFFBDBDBD, false);
        graphics.drawString(font, Component.literal(Component.translatable("Bid").getString() + ": $" + entry.currentBid()), textX, y + 44, 0xFFFFD966, false);
        int metaY = y + 44;
        if (textW >= 230) {
            graphics.drawString(font, Component.literal(Component.translatable("Time").getString() + ": " + timeLeft(entry.endsAt(), entry.state())), textX + 108, metaY, 0xFFA5D6A7, false);
        } else {
            metaY += 12;
            graphics.drawString(font, Component.literal(Component.translatable("Time").getString() + ": " + timeLeft(entry.endsAt(), entry.state())), textX, metaY, 0xFFA5D6A7, false);
        }
        int buyoutY = metaY + 16;
        if (!entry.buyoutPrice().equals("0")) {
            graphics.drawString(font, Component.literal(Component.translatable("Buyout").getString() + ": $" + entry.buyoutPrice()), textX, buyoutY, 0xFF55FF55, false);
        }
        int descriptionY = entry.buyoutPrice().equals("0") ? buyoutY : buyoutY + 16;
        String description = entry.description() == null || entry.description().isBlank() ? Component.translatable("No description").getString() : entry.description();
        for (String line : wrapText(description, textW, 2)) {
            graphics.drawString(font, Component.literal(line), textX, descriptionY, 0xFFDDDDDD, false);
            descriptionY += 12;
        }

        String status = entry.viewerIsHighestBidder() ? "WINNING" : entry.state();
        graphics.drawString(font, Component.translatable(status), x + w - actionColumnW, y + 12, entry.viewerIsHighestBidder() ? 0xFF55FF55 : 0xFFE0E0E0, false);
    }

    private int auctionRowHeight() {
        return 128;
    }

    private int rowActionColumnWidth(AuctionEntrySummary entry) {
        if (contentWidth < 760) {
            return 108;
        }
        int count = 2; // notifications + view bids
        if (activeTab == Tab.BROWSE) {
            count += entry.canBid() ? 1 : 0;
            count += entry.canBuyout() ? 1 : 0;
        } else if (activeTab == Tab.MY_BIDS) {
            count += entry.canBid() ? 1 : 0;
            count += entry.canClaim() ? 1 : 0;
        } else if (entry.canCancel() || entry.canClaim()) {
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
        } else if (modal == Modal.DATE_PICKER) {
            renderDatePickerModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.BIDS && selectedAuction != null) {
            renderBidsModal(graphics, x, y, modalW, modalH);
        } else if (modal == Modal.DELIVERY) {
            int rowY = y + 58;
            for (AuctionDeliverySummary delivery : payload.deliveries().stream().limit(6).toList()) {
                graphics.renderItem(delivery.item(), x + 22, rowY - 2);
                graphics.drawString(font, Component.literal(delivery.item().getCount() + "x " + delivery.item().getHoverName().getString()), x + 48, rowY, 0xFFE0E0E0, false);
                graphics.drawString(font, Component.literal(delivery.reason()), x + 48, rowY + 12, 0xFFBDBDBD, false);
                rowY += 34;
            }
            if (payload.deliveries().isEmpty()) {
                graphics.drawString(font, Component.translatable("No delivery items"), x + 20, rowY, 0xFFE0E0E0, false);
            }
        } else if (modal == Modal.FILTER) {
            renderFilterModal(graphics, x, y, modalW, modalH);
        }
    }

    private void renderBidModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        int actionY = y + modalH - 38;
        int quickY = actionY - 42;
        int inputY = quickY - 52;
        int currentY = inputY - 62;
        int previewSize = modalH < 360 ? 56 : 86;
        int previewX = x + 22;
        int previewY = y + 58;
        int rarityColor = rarityColor(selectedAuction.rarity());

        graphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000);
        graphics.fill(previewX + 2, previewY + 2, previewX + previewSize - 2, previewY + previewSize - 2, 0xFF1A1A1A);
        graphics.fill(previewX + 4, previewY + 4, previewX + previewSize - 4, previewY + previewSize - 4, 0x33000000 | (rarityColor & 0x00FFFFFF));
        graphics.renderItem(selectedAuction.item(), previewX + (previewSize - 16) / 2, previewY + (previewSize - 16) / 2);
        graphics.renderItemDecorations(font, selectedAuction.item(), previewX + (previewSize - 16) / 2, previewY + (previewSize - 16) / 2);

        int detailX = previewX + previewSize + 16;
        int detailW = Math.max(80, x + modalW - 22 - detailX);
        graphics.drawString(font, Component.literal(trimToWidth(selectedAuction.itemName(), detailW)).withStyle(ChatFormatting.BOLD), detailX, previewY + 8, rarityColor, false);
        graphics.drawString(font, Component.literal(Component.translatable("Seller").getString() + ": " + trimToWidth(selectedAuction.sellerName(), detailW - 40)), detailX, previewY + 26, 0xFFBDBDBD, false);
        graphics.fill(detailX, previewY + 44, detailX + Math.min(detailW, 128), previewY + 66, 0xFF000000);
        graphics.fill(detailX + 2, previewY + 46, detailX + Math.min(detailW, 128) - 2, previewY + 64, 0xFF191919);
        graphics.drawString(font, Component.literal(timeLeft(selectedAuction.endsAt(), selectedAuction.state()) + " " + Component.translatable("remaining").getString()), detailX + 8, previewY + 52, 0xFFFF6666, false);

        graphics.fill(x + 20, currentY, x + modalW - 20, currentY + 48, 0xFF000000);
        graphics.fill(x + 22, currentY + 2, x + modalW - 22, currentY + 46, 0xFF191919);
        graphics.drawString(font, Component.translatable("Current Bid"), x + 34, currentY + 10, 0xFFBDBDBD, false);
        graphics.drawString(font, Component.literal("$" + selectedAuction.currentBid()).withStyle(ChatFormatting.BOLD), x + 34, currentY + 26, 0xFFFFD700, false);
        if (payload.account().present()) {
            graphics.drawString(font, Component.literal("$" + payload.account().balance()), x + modalW - 90, currentY + 26, 0xFF55FF55, false);
        }

        graphics.drawString(font, Component.translatable("Your Bid").append(Component.literal(" (" + Component.translatable("Minimum").getString() + ": $" + nextBidValue(selectedAuction) + ")")), x + 20, inputY + 2, 0xFFFFFFFF, false);
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
        graphics.renderItem(selectedAuction.item(), x + 34 + (itemBox - 16) / 2, summaryTop + 8 + (itemBox - 16) / 2);
        graphics.renderItemDecorations(font, selectedAuction.item(), x + 34 + (itemBox - 16) / 2, summaryTop + 8 + (itemBox - 16) / 2);

        int detailX = x + 48 + itemBox;
        int detailW = Math.max(80, modalW - 96 - itemBox);
        graphics.drawString(font, Component.literal(trimToWidth(selectedAuction.itemName(), detailW)).withStyle(ChatFormatting.BOLD), detailX, summaryTop + 16, rarityColor, false);
        graphics.fill(detailX, summaryTop + 40, detailX + 84, summaryTop + 62, 0xFF000000);
        graphics.fill(detailX + 2, summaryTop + 42, detailX + 82, summaryTop + 60, 0xFF191919);
        graphics.drawString(font, Component.literal("$" + selectedAuction.currentBid()).withStyle(ChatFormatting.BOLD), detailX + 8, summaryTop + 48, 0xFFFFD700, false);
        graphics.fill(detailX + 96, summaryTop + 40, detailX + 190, summaryTop + 62, 0xFF000000);
        graphics.fill(detailX + 98, summaryTop + 42, detailX + 188, summaryTop + 60, 0xFF191919);
        graphics.drawString(font, Component.literal(timeLeft(selectedAuction.endsAt(), selectedAuction.state())), detailX + 104, summaryTop + 48, 0xFFFF6666, false);

        int titleY = summaryTop + summaryH + 16;
        graphics.drawString(font, Component.translatable("Bid History").append(Component.literal(" (" + bids.size() + ")")).withStyle(ChatFormatting.BOLD), x + 20, titleY, 0xFFFFFFFF, false);

        int listTop = titleY + 18;
        int listBottom = Math.max(listTop + 24, y + modalH - 58);
        graphics.fill(x + 20, listTop, x + modalW - 20, listBottom, 0xFF000000);
        graphics.fill(x + 22, listTop + 2, x + modalW - 22, listBottom - 2, 0xFF191919);

        if (bids.isEmpty()) {
            graphics.drawString(font, Component.translatable("No bids yet"), x + 34, listTop + 18, 0xFFBDBDBD, false);
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
            graphics.drawString(font, Component.literal("$" + bid.amount()).withStyle(ChatFormatting.BOLD), x + modalW - 112, rowY + 12, 0xFFFFD700, false);
            if (winning) {
                graphics.drawString(font, Component.translatable("Winning Bid").withStyle(ChatFormatting.BOLD), x + modalW - 112, rowY + 28, 0xFF55FF55, false);
            }
        }
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 14, listTop + 2, listBottom - 2, bidsScroll, bidsContentHeight(), Math.max(1, listBottom - listTop - 4));
    }

    private void renderCreateModal(GuiGraphics graphics, int x, int y, int modalW, int mouseX, int mouseY) {
        int modalH = modalHeight();
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        boolean compact = compactCreateModal(modalW);
        int scroll = createScroll;

        graphics.enableScissor(x + 6, bodyTop, x + modalW - 6, bodyBottom);
        graphics.drawString(font, Component.translatable("Select Item from Inventory").withStyle(ChatFormatting.BOLD), x + 20, y + 54 - scroll, 0xFFFFFFFF, false);
        graphics.fill(inventoryGridLeft - 4, inventoryGridTop - 4, inventoryGridLeft + 202, inventoryGridTop + 92, 0xFF111111);
        graphics.fill(inventoryGridLeft - 2, inventoryGridTop - 2, inventoryGridLeft + 200, inventoryGridTop + 90, 0xFF2C2C2C);
        renderInventoryGrid(graphics, mouseX, mouseY);
        if (compact) {
            renderSelectedItemPreview(graphics, x + 20, y + 174 - scroll, modalW - 40, 74);
            graphics.drawString(font, Component.translatable("Starting Bid (dollars)").withStyle(ChatFormatting.BOLD), x + 20, y + 262 - scroll, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("Buyout (dollars)").withStyle(ChatFormatting.BOLD), x + 20, y + 310 - scroll, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("Auction End Date & Time").withStyle(ChatFormatting.BOLD), x + 20, y + 358 - scroll, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("Description").withStyle(ChatFormatting.BOLD), x + 20, y + 406 - scroll, 0xFFFFFFFF, false);
        } else {
            renderSelectedItemPreview(graphics, x + 236, y + 72 - scroll, modalW - 256, 88);
            graphics.drawString(font, Component.translatable("Starting Bid (dollars)").withStyle(ChatFormatting.BOLD), x + 20, y + 178 - scroll, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("Buyout (dollars)").withStyle(ChatFormatting.BOLD), x + 170, y + 178 - scroll, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("Auction End Date & Time").withStyle(ChatFormatting.BOLD), x + 310, y + 178 - scroll, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("Description").withStyle(ChatFormatting.BOLD), x + 20, y + 226 - scroll, 0xFFFFFFFF, false);
        }

        int feeY = y + (compact ? 466 : 274) - scroll;
        graphics.fill(x + 20, feeY, x + modalW - 20, feeY + 52, 0xFF000000);
        graphics.fill(x + 22, feeY + 2, x + modalW - 22, feeY + 50, 0xFF191919);
        graphics.drawString(font, Component.translatable("Listing Fee").append(Component.literal(" (" + listingFeeRateLabel() + "%)")), x + 34, feeY + 12, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.literal("$" + moneyTwoDecimals(listingFeePreview())).withStyle(ChatFormatting.BOLD), x + 34, feeY + 30, 0xFFFFD700, false);
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 12, bodyTop, bodyBottom, createScroll, createContentHeight(modalW), bodyBottom - bodyTop);
    }

    private void renderFilterModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        int bodyTop = modalBodyTop(y);
        int bodyBottom = modalBodyBottom(y, modalH);
        int scroll = filterScroll;
        int labelX = x + 18;

        graphics.enableScissor(x + 6, bodyTop, x + modalW - 6, bodyBottom);
        graphics.drawString(font, Component.translatable("Categories"), labelX, y + 58 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Price Range"), labelX, y + 114 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Closing Time"), labelX, y + 204 - scroll, 0xFFE0E0E0, false);
        graphics.drawString(font, Component.translatable("Sort By"), labelX, y + 266 - scroll, 0xFFE0E0E0, false);
        graphics.disableScissor();
        renderScrollBar(graphics, x + modalW - 12, bodyTop, bodyBottom, filterScroll, filterContentHeight(), bodyBottom - bodyTop);
    }

    private void renderSelectedItemPreview(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF000000);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF191919);
        ItemStack stack = selectedInventoryStack();
        graphics.fill(x + 16, y + 18, x + 66, y + 68, 0xFF050505);
        graphics.fill(x + 18, y + 20, x + 64, y + 66, stack.isEmpty() ? 0xFF333333 : 0xFF1B1B62);
        if (stack.isEmpty()) {
            graphics.drawString(font, Component.translatable("No item selected"), x + 80, y + 28, 0xFFBDBDBD, false);
            graphics.drawString(font, Component.translatable("Select a slot below"), x + 80, y + 42, 0xFF8E8E8E, false);
            return;
        }

        graphics.renderItem(stack, x + 33, y + 35);
        graphics.renderItemDecorations(font, stack, x + 33, y + 35);
        graphics.drawString(font, stack.getHoverName(), x + 80, y + 24, 0xFF5F6BFF, false);
        graphics.drawString(font, Component.literal(stack.getCount() + "x " + Component.translatable("Inventory Slot").getString() + " " + (selectedInventorySlot + 1)), x + 80, y + 42, 0xFFE0E0E0, false);
    }

    private void renderDatePickerModal(GuiGraphics graphics, int x, int y, int modalW, int modalH) {
        DatePickerLayout layout = datePickerLayout(x, y, modalW, modalH);

        graphics.drawString(font, Component.translatable("Select Date (up to 30 days)").withStyle(ChatFormatting.BOLD), x + 22, y + 52, 0xFFFFFFFF, false);
        graphics.drawCenteredString(font, Component.literal(calendarMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ROOT) + " " + calendarMonth.getYear()), x + modalW / 2, layout.monthY() + 7, 0xFFFFFFFF);

        String[] weekdays = {"S", "M", "T", "W", "T", "F", "S"};
        for (int col = 0; col < weekdays.length; col++) {
            graphics.drawCenteredString(font, Component.literal(weekdays[col]), layout.calendarX() + col * layout.cell() + (layout.cell() - 2) / 2, layout.weekdayY(), 0xFFE0E0E0);
        }

        graphics.drawString(font, Component.translatable("Select Hour").withStyle(ChatFormatting.BOLD), x + 22, layout.hourY() - 18, 0xFFFFFFFF, false);
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
        return compactCreateModal(modalW) ? 478 : 286;
    }

    private int filterContentHeight() {
        return 318;
    }

    private int bidsContentHeight() {
        return acceptedBids(selectedAuction).size() * 50 + 4;
    }

    private void clampModalScrolls() {
        int modalW = modalWidth();
        int viewport = Math.max(1, modalHeight() - 92);
        createScroll = clamp(createScroll, 0, Math.max(0, createContentHeight(modalW) - viewport));
        filterScroll = clamp(filterScroll, 0, Math.max(0, filterContentHeight() - viewport));
        int bidsViewport = Math.max(1, modalHeight() - (modalHeight() < 360 ? 206 : 224));
        bidsScroll = clamp(bidsScroll, 0, Math.max(0, bidsContentHeight() - bidsViewport));
    }

    private void setVisibleInModalBody(AbstractWidget widget, int bodyTop, int bodyBottom) {
        boolean visible = widget.getY() + widget.getHeight() > bodyTop && widget.getY() < bodyBottom;
        widget.visible = visible;
        if (!visible) {
            widget.active = false;
        }
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
                || !maxPriceDraft.isBlank();
    }

    private void applyFilters() {
        minPriceDraft = value(minPriceBox, minPriceDraft);
        maxPriceDraft = value(maxPriceBox, maxPriceDraft);
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
            int color = slot == selectedInventorySlot ? 0xFF3EFF47 : 0xFF202020;
            graphics.fill(x, y, x + 20, y + 20, color);
            graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFF555555);
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
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (modal == Modal.CREATE || modal == Modal.FILTER || modal == Modal.BIDS) {
            int delta = (int) Math.round(scrollY * 22.0D);
            if (modal == Modal.CREATE) {
                createScroll -= delta;
            } else if (modal == Modal.FILTER) {
                filterScroll -= delta;
            } else {
                bidsScroll -= delta;
            }
            clampModalScrolls();
            if (modal == Modal.CREATE || modal == Modal.FILTER) {
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
                    selectedInventorySlot = slot;
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
        return switch (activeTab) {
            case BROWSE -> payload.browseListings();
            case MY_BIDS -> payload.myBids();
            case MY_AUCTIONS -> payload.myAuctions();
        };
    }

    private String modalTitle() {
        return switch (modal) {
            case BID -> selectedAuction != null && selectedAuction.viewerHasBid() ? "Raise Bid" : "Place Bid";
            case CREATE -> "Create Auction";
            case DATE_PICKER -> "Select End Date & Time";
            case BIDS -> "Auction Bids";
            case DELIVERY -> "Delivery Storage";
            case FILTER -> "Filters";
            case NONE -> "";
        };
    }

    private String nextBidValue(AuctionEntrySummary entry) {
        if (entry == null) {
            return "";
        }
        try {
            return new BigDecimal(entry.currentBid()).add(BigDecimal.ONE).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private String timeFilterLabel() {
        if (maxHoursLeft == 0L) {
            return "Any Time";
        }
        if (maxHoursLeft == 1L) {
            return "Under 1 Hour";
        }
        if (maxHoursLeft == 24L) {
            return "Under 24 Hours";
        }
        return "Under 7 Days";
    }

    private String timeLeft(String rawEnd, String state) {
        if (!"ACTIVE".equals(state)) {
            return state;
        }
        try {
            Duration duration = Duration.between(LocalDateTime.now(), LocalDateTime.parse(rawEnd));
            if (duration.isNegative() || duration.isZero()) {
                return "Ended";
            }
            long days = duration.toDays();
            long hours = duration.toHoursPart();
            long minutes = duration.toMinutesPart();
            if (days > 0) {
                return days + "d " + hours + "h";
            }
            if (hours > 0) {
                return hours + "h " + minutes + "m";
            }
            return Math.max(1, minutes) + "m";
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
                return days + "d ago";
            }
            long hours = duration.toHours();
            if (hours > 0) {
                return hours + "h ago";
            }
            long minutes = duration.toMinutes();
            if (minutes > 0) {
                return minutes + "m ago";
            }
            return "just now";
        } catch (DateTimeParseException exception) {
            return rawTimestamp == null ? "" : rawTimestamp;
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
            return new BigDecimal(raw.trim().replace("$", "").replace(",", "")).max(BigDecimal.ZERO);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String moneyTwoDecimals(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
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
        return value(minPriceBox, minPriceDraft);
    }

    private String maxPriceValue() {
        return value(maxPriceBox, maxPriceDraft);
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

}
