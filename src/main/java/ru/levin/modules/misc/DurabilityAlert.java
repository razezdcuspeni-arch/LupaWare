package ru.levin.modules.misc;

import net.minecraft.item.ItemStack;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.Manager;
import ru.levin.manager.notificationManager.NotificationType;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.SliderSetting;

@FunctionAnnotation(
        name = "DurabilityAlert",
        desc = "Предупреждает о низкой прочности предметов и брони",
        type = Type.Misc
)
public class DurabilityAlert extends Function {
    private final SliderSetting threshold = new SliderSetting(
            "Порог прочности", 20f, 5f, 50f, 5f
    );

    private long nextAlertAt;

    public DurabilityAlert() {
        addSettings(threshold);
    }

    @Override
    protected void onEnable() {
        nextAlertAt = 0L;
    }

    @Override
    protected void onDisable() {
        nextAlertAt = 0L;
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate) || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (now < nextAlertAt) return;

        ItemStack warning = findLowestDurabilityItem();
        if (warning.isEmpty()) return;

        int maxDamage = warning.getMaxDamage();
        int remaining = Math.max(0, maxDamage - warning.getDamage());
        int percent = Math.round(remaining * 100f / maxDamage);
        if (percent <= threshold.get().floatValue()) {
            nextAlertAt = now + 5000L;
            Manager.NOTIFICATION_MANAGER.add(
                    NotificationType.INFO,
                    name,
                    "Низкая прочность: " + percent + "%",
                    3
            );
        }
    }

    private ItemStack findLowestDurabilityItem() {
        ItemStack result = ItemStack.EMPTY;

        for (ItemStack stack : mc.player.getInventory().armor) {
            if (!stack.isEmpty() && stack.getMaxDamage() > 0
                    && (result.isEmpty() || durabilityPercent(stack) < durabilityPercent(result))) {
                result = stack;
            }
        }

        ItemStack mainHand = mc.player.getMainHandStack();
        if (!mainHand.isEmpty() && mainHand.getMaxDamage() > 0
                && (result.isEmpty() || durabilityPercent(mainHand) < durabilityPercent(result))) {
            result = mainHand;
        }

        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty() && offHand.getMaxDamage() > 0
                && (result.isEmpty() || durabilityPercent(offHand) < durabilityPercent(result))) {
            result = offHand;
        }

        return result;
    }

    private float durabilityPercent(ItemStack stack) {
        return Math.max(0f, (stack.getMaxDamage() - stack.getDamage()) * 100f / stack.getMaxDamage());
    }
}
