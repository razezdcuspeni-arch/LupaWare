package ru.levin.modules.player;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.modules.setting.TextSetting;
import ru.levin.util.player.TimerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FunctionAnnotation(
        name = "AutoSell",
        desc = "Автоматически продаёт выбранные предметы через AH FunTime",
        type = Type.Player
)
public class AutoSell extends Function {
    private static final Pattern NAMED_PRICE = Pattern.compile(
            "(?iu)(?:цена|цены|price|стоимость|cost|средняя цена|ср\\.\\s*цена)\\s*[:=\\-]?\\s*([$€₽]?\\s*[0-9][0-9\\s.,]*\\s*(?:тыс|кк|млрд|млн|[кkмmтtбb])?)");
    private static final Pattern CURRENCY_PRICE = Pattern.compile(
            "(?iu)[$€₽]?\\s*([0-9][0-9\\s.,]*\\s*(?:тыс|кк|млрд|млн|[кkмmтtбb])?)\\s*(?:монет|руб(?:лей)?|coins?|[$€₽])");

    private final TextSetting itemFilter = new TextSetting("Предметы", "Все");
    private final SliderSetting actionDelay = new SliderSetting("Задержка действий", 900, 250, 3000, 50);
    private final SliderSetting searchWait = new SliderSetting("Ожидание AH", 1800, 700, 5000, 50);
    private final BooleanSetting pricePerUnit = new BooleanSetting("Цена за штуку", true,
            "Делит стоимость найденного стака на количество предметов");
    private final BooleanSetting reopenSource = new BooleanSetting("Переоткрывать сундук", true,
            "Пытается снова открыть исходный сундук после продажи");

    private final TimerUtil timer = new TimerUtil();
    private AutoSellState state = AutoSellState.WAITING_SOURCE;
    private long nextActionAt;
    private long searchDeadline;
    private int searchAttempts;
    private int pendingSellPrice;
    private String pendingSearchName;
    private BlockPos sourcePos;
    private int sourceSyncId = -1;
    private boolean sourceWasOpened;

    public AutoSell() {
        addSettings(itemFilter, actionDelay, searchWait, pricePerUnit, reopenSource);
    }

    @Override
    protected void onEnable() {
        state = AutoSellState.WAITING_SOURCE;
        nextActionAt = 0L;
        searchDeadline = 0L;
        searchAttempts = 0;
        pendingSellPrice = 0;
        pendingSearchName = null;
        sourcePos = null;
        sourceSyncId = -1;
        sourceWasOpened = false;
        timer.reset();
    }

    @Override
    protected void onDisable() {
        state = AutoSellState.STOPPED;
        pendingSearchName = null;
        pendingSellPrice = 0;
        sourcePos = null;
        sourceSyncId = -1;
        if (mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate) || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!isFunTimeServer()) {
            stopWithNotice("AutoSell работает только на FunTime");
            return;
        }
        if (System.currentTimeMillis() < nextActionAt) return;

        switch (state) {
            case WAITING_SOURCE -> {
                tryOpenSourceContainer();
                handleSourceScreen();
            }
            case WAITING_SEARCH_RESULTS -> handleSearchResults();
            case SELLING -> finishSellingStep();
            case REOPENING_SOURCE -> reopenSourceChest();
            case STOPPED -> { }
        }
    }

    private void tryOpenSourceContainer() {
        if (sourcePos != null || mc.currentScreen != null || mc.crosshairTarget == null
                || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
        BlockPos candidate = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        String blockId = Registries.BLOCK.getId(mc.world.getBlockState(candidate).getBlock()).getPath();
        if (!blockId.contains("chest") && !blockId.contains("barrel") && !blockId.contains("shulker_box")) return;
        sourcePos = candidate;
        reopenSourceChest();
    }

    private void handleSourceScreen() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || isAhScreen(screen)) return;
        if (!sourceWasOpened) captureSource(screen);

        int firstNine = Math.min(9, screen.getScreenHandler().getRows() * 9);
        Slot selected = null;
        for (int i = 0; i < firstNine; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            if (slot.hasStack() && matchesFilter(slot.getStack())) {
                selected = slot;
                break;
            }
        }
        if (selected == null) {
            stopWithNotice("В первых 9 слотах нет выбранных предметов");
            return;
        }

        ItemStack stack = selected.getStack().copy();
        Slot hotbarSlot = findFreeHotbarSlot(screen);
        if (hotbarSlot == null) {
            stopWithNotice("Нет свободного слота в хотбаре для продажи");
            return;
        }
        if (!moveToHotbar(screen, selected, hotbarSlot)) {
            stopWithNotice("Не удалось взять предмет из сундука");
            return;
        }

        mc.player.getInventory().selectedSlot = hotbarSlot.getIndex();
        pendingSearchName = getSearchName(stack);
        sourceSyncId = screen.getScreenHandler().syncId;
        mc.player.closeHandledScreen();
        state = AutoSellState.WAITING_SEARCH_RESULTS;
        searchAttempts = 0;
        searchDeadline = 0L;
        nextActionAt = System.currentTimeMillis() + actionDelay.get().longValue();
    }

    private void handleSearchResults() {
        long now = System.currentTimeMillis();
        if (searchDeadline == 0L) {
            if (pendingSearchName == null || pendingSearchName.isBlank()) {
                stopWithNotice("Не удалось определить предмет для поиска");
                return;
            }
            sendSearchCommand();
            searchDeadline = now + searchWait.get().longValue();
            nextActionAt = now + actionDelay.get().longValue();
            return;
        }

        if (mc.currentScreen instanceof GenericContainerScreen screen && isAhScreen(screen)) {
            double average = getAverageUnitPrice(screen);
            if (average > 0.0) {
                pendingSellPrice = Math.max(1, (int) Math.round(average));
                mc.player.networkHandler.sendCommand("ah sell " + pendingSellPrice);
                state = AutoSellState.SELLING;
                nextActionAt = now + actionDelay.get().longValue();
                return;
            }
        }

        if (now >= searchDeadline) {
            if (searchAttempts < 1) {
                searchAttempts++;
                searchDeadline = 0L;
                nextActionAt = now + actionDelay.get().longValue();
            } else {
                stopWithNotice("Не удалось найти цены в меню AH");
            }
        }
    }

    private void finishSellingStep() {
        long now = System.currentTimeMillis();
        if (mc.currentScreen instanceof GenericContainerScreen) mc.player.closeHandledScreen();
        pendingSellPrice = 0;
        searchDeadline = 0L;
        if (reopenSource.get() && sourcePos != null) {
            state = AutoSellState.REOPENING_SOURCE;
            nextActionAt = now + actionDelay.get().longValue();
        } else {
            state = AutoSellState.WAITING_SOURCE;
            nextActionAt = now + actionDelay.get().longValue();
        }
    }

    private void reopenSourceChest() {
        if (mc.currentScreen instanceof GenericContainerScreen screen && !isAhScreen(screen)) {
            state = AutoSellState.WAITING_SOURCE;
            nextActionAt = System.currentTimeMillis() + actionDelay.get().longValue();
            return;
        }
        if (sourcePos == null) {
            state = AutoSellState.WAITING_SOURCE;
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(sourcePos), Direction.UP, sourcePos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        state = AutoSellState.WAITING_SOURCE;
        nextActionAt = System.currentTimeMillis() + actionDelay.get().longValue();
    }

    private void captureSource(GenericContainerScreen screen) {
        sourceWasOpened = true;
        sourceSyncId = screen.getScreenHandler().syncId;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            sourcePos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
        }
    }

    private Slot findFreeHotbarSlot(GenericContainerScreen screen) {
        PlayerInventory inventory = mc.player.getInventory();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory == inventory && slot.getIndex() >= 0 && slot.getIndex() < 9 && !slot.hasStack()) return slot;
        }
        return null;
    }

    private boolean moveToHotbar(GenericContainerScreen screen, Slot source, Slot hotbar) {
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, source.id, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, hotbar.id, 0, SlotActionType.PICKUP, mc.player);
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, source.id, 0, SlotActionType.PICKUP, mc.player);
        }
        return mc.player.getInventory().getStack(hotbar.getIndex()).isEmpty() == false;
    }

    private void sendSearchCommand() {
        mc.player.networkHandler.sendCommand("ah search " + pendingSearchName);
    }

    private double getAverageUnitPrice(GenericContainerScreen screen) {
        int rows = Math.min(screen.getScreenHandler().getRows() * 9, screen.getScreenHandler().slots.size());
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            int totalPrice = parsePriceFromLore(stack);
            if (totalPrice <= 0) continue;
            prices.add(pricePerUnit.get() ? totalPrice / (double) Math.max(1, stack.getCount()) : (double) totalPrice);
        }
        if (prices.isEmpty()) return 0.0;
        return prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private int parsePriceFromLore(ItemStack stack) {
        if (!stack.contains(net.minecraft.component.DataComponentTypes.LORE)) return 0;
        for (Text line : stack.get(net.minecraft.component.DataComponentTypes.LORE).lines()) {
            String plain = Formatting.strip(line.getString());
            if (plain == null || plain.isBlank()) continue;
            Matcher named = NAMED_PRICE.matcher(plain.replace('\u00A0', ' '));
            if (named.find()) {
                int value = parsePriceToken(named.group(1));
                if (value > 0) return value;
            }
            Matcher currency = CURRENCY_PRICE.matcher(plain.replace('\u00A0', ' '));
            if (currency.find()) {
                int value = parsePriceToken(currency.group(1));
                if (value > 0) return value;
            }
        }
        return 0;
    }

    private int parsePriceToken(String raw) {
        String token = raw.toLowerCase(Locale.ROOT).replace('\u00A0', ' ').trim().replaceAll("^[$€₽]\\s*", "");
        double multiplier = 1.0;
        String[] suffixes = {"млрд", "млн", "тыс", "кк", "b", "т", "б", "к", "k", "м", "m"};
        for (String suffix : suffixes) {
            if (!token.endsWith(suffix)) continue;
            multiplier = switch (suffix) {
                case "тыс", "к", "k" -> 1_000.0;
                case "млн", "кк", "м", "m" -> 1_000_000.0;
                default -> 1_000_000_000.0;
            };
            token = token.substring(0, token.length() - suffix.length()).trim();
            break;
        }
        try {
            String compact = token.replace(" ", "");
            if (multiplier != 1.0 && compact.matches("[0-9]+[.,][0-9]+")) {
                return (int) Math.min(Integer.MAX_VALUE, Math.round(Double.parseDouble(compact.replace(',', '.')) * multiplier));
            }
            String digits = compact.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : (int) Math.min(Integer.MAX_VALUE, Long.parseLong(digits));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean matchesFilter(ItemStack stack) {
        String filter = itemFilter.getValue() == null ? "" : itemFilter.getValue().trim();
        if (filter.isEmpty() || filter.equalsIgnoreCase("Все") || filter.equalsIgnoreCase("all")) return true;
        String id = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        String display = stack.getName().getString().toLowerCase(Locale.ROOT);
        for (String rawToken : filter.split("[,;]")) {
            String token = rawToken.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty() && (id.equals(token) || id.contains(token) || display.contains(token))) return true;
        }
        return false;
    }

    private String getSearchName(ItemStack stack) {
        String displayName = Formatting.strip(stack.getName().getString());
        if (displayName != null && !displayName.isBlank()) return displayName.trim();
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }

    private boolean isAhScreen(GenericContainerScreen screen) {
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("аукцион") || title.contains("auction") || title.contains("поиск")
                || title.contains("search") || title.contains("торгов") || title.contains("market") || title.contains("ah");
    }

    private boolean isFunTimeServer() {
        if (mc.getCurrentServerEntry() == null || mc.isConnectedToLocalServer()) return false;
        String address = mc.getCurrentServerEntry().address;
        return address != null && address.toLowerCase(Locale.ROOT).contains("funtime");
    }

    private void stopWithNotice(String message) {
        if (state != AutoSellState.STOPPED) {
            state = AutoSellState.STOPPED;
            pendingSearchName = null;
            pendingSellPrice = 0;
            Manager.NOTIFICATION_MANAGER.add(ru.levin.manager.notificationManager.NotificationType.REMOVED, name, message, 3);
        }
    }

    private enum AutoSellState {
        WAITING_SOURCE,
        WAITING_SEARCH_RESULTS,
        SELLING,
        REOPENING_SOURCE,
        STOPPED
    }
}
