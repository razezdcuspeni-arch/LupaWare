package ru.levin.modules.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventHandledScreen;
import ru.levin.mixin.iface.HandledScreenAccessor;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FunctionAnnotation(name = "AucHelper", desc = "Подсвечивает самый дешёвый и самый дорогой предмет на аукционе FunTime", type = Type.Render)
public class AucHelper extends Function {
    private static final Pattern PRICE_PATTERN = Pattern.compile("Цен[аaАAыЫ]?:?\\s*([\\d,\\s.]+)", Pattern.CASE_INSENSITIVE);
    private static final int CHEAPEST_COLOR = 0xFF4BFF4B;
    private static final int MOST_EXPENSIVE_COLOR = 0xFFFF4B4B;

    private final BooleanSetting onlyMending = new BooleanSetting("Только Починка", false,
            "Учитывать только предметы с чарами Починка");

    private Slot cheapestSlot;
    private Slot mostExpensiveSlot;
    private int lastSlotCount = -1;
    private int lastUpdateTick;

    public AucHelper() {
        addSettings(onlyMending);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            updateSlotsIfNeeded();
        } else if (event instanceof EventHandledScreen handledScreenEvent) {
            renderHighlights(handledScreenEvent);
        }
    }

    private void updateSlotsIfNeeded() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !isFunTimeServer() || !isAuction(screen)) {
            clearSlots();
            return;
        }

        int slotCount = screen.getScreenHandler().slots.size();
        if (slotCount != lastSlotCount || mc.player.age - lastUpdateTick >= 5) {
            lastSlotCount = slotCount;
            lastUpdateTick = mc.player.age;
            updateBestSlots(screen);
        }
    }

    private void renderHighlights(EventHandledScreen event) {
        if (!(event.getScreen() instanceof GenericContainerScreen screen) || !isFunTimeServer() || !isAuction(screen)) return;
        if (!(screen instanceof HandledScreenAccessor accessor)) return;

        DrawContext context = event.getDrawContext();
        if (isValidSlot(cheapestSlot, screen)) {
            highlightSlot(context, accessor.getX(), accessor.getY(), cheapestSlot, blinkingColor(CHEAPEST_COLOR, 520L));
        }
        if (isValidSlot(mostExpensiveSlot, screen) && mostExpensiveSlot != cheapestSlot) {
            highlightSlot(context, accessor.getX(), accessor.getY(), mostExpensiveSlot, blinkingColor(MOST_EXPENSIVE_COLOR, 620L));
        }
    }

    private void updateBestSlots(GenericContainerScreen screen) {
        int containerSlotCount = screen.getScreenHandler().getRows() * 9;
        List<ItemPriceData> validItems = new ArrayList<>();

        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = screen.getScreenHandler().getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || slot.inventory == mc.player.getInventory()) continue;
            if (onlyMending.get() && !hasMending(stack)) continue;

            int price = parsePriceFromLore(stack);
            if (price > 0) validItems.add(new ItemPriceData(slot, price));
        }

        if (validItems.isEmpty()) {
            clearSlots();
            return;
        }

        cheapestSlot = validItems.stream()
                .min(Comparator.comparingInt(ItemPriceData::price))
                .map(ItemPriceData::slot)
                .orElse(null);
        mostExpensiveSlot = validItems.stream()
                .max(Comparator.comparingInt(ItemPriceData::price))
                .map(ItemPriceData::slot)
                .orElse(null);
    }

    private boolean isValidSlot(Slot slot, GenericContainerScreen screen) {
        if (slot == null || slot.id < 0 || slot.id >= screen.getScreenHandler().slots.size()) return false;
        Slot current = screen.getScreenHandler().getSlot(slot.id);
        return current.hasStack() && !current.getStack().isEmpty();
    }

    private boolean isAuction(GenericContainerScreen screen) {
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("аукцион") || title.contains("auction") || title.contains("/ah");
    }

    private boolean isFunTimeServer() {
        if (mc.getCurrentServerEntry() == null || mc.isConnectedToLocalServer()) return false;
        String address = mc.getCurrentServerEntry().address;
        return address != null && address.toLowerCase(Locale.ROOT).contains("funtime");
    }

    private boolean hasMending(ItemStack stack) {
        var enchantments = stack.getEnchantments();
        for (var entry : enchantments.getEnchantments()) {
            if (entry.getKey().isPresent() && entry.getKey().get().getValue().toString().equals("minecraft:mending")) return true;
        }
        return false;
    }

    private int parsePriceFromLore(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return 0;

        for (Text line : lore.lines()) {
            String plain = Formatting.strip(line.getString());
            if (plain == null) continue;
            Matcher matcher = PRICE_PATTERN.matcher(plain);
            if (!matcher.find()) continue;
            try {
                return Integer.parseInt(matcher.group(1).replaceAll("[,\\s.]", ""));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private int blinkingColor(int color, long period) {
        double wave = Math.sin((double) System.currentTimeMillis() / period * Math.PI) * 0.25 + 0.75;
        int alpha = (int) (255 * Math.max(0.45, Math.min(1.0, wave)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private void highlightSlot(DrawContext context, int screenX, int screenY, Slot slot, int color) {
        int x = screenX + slot.x;
        int y = screenY + slot.y;
        int fill = (color & 0x00FFFFFF) | 0x33000000;
        context.fill(x, y, x + 16, y + 16, fill);
        context.fill(x, y, x + 16, y + 1, color);
        context.fill(x, y + 15, x + 16, y + 16, color);
        context.fill(x, y, x + 1, y + 16, color);
        context.fill(x + 15, y, x + 16, y + 16, color);
    }

    private void clearSlots() {
        cheapestSlot = null;
        mostExpensiveSlot = null;
        lastSlotCount = -1;
    }

    private record ItemPriceData(Slot slot, int price) {
    }
}
