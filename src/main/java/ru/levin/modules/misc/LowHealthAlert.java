package ru.levin.modules.misc;

import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.Manager;
import ru.levin.manager.notificationManager.NotificationType;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.SliderSetting;

@FunctionAnnotation(
        name = "LowHealthAlert",
        desc = "Предупреждает, когда здоровье игрока становится низким",
        type = Type.Misc
)
public class LowHealthAlert extends Function {
    private final SliderSetting threshold = new SliderSetting(
            "Порог здоровья", 30f, 5f, 80f, 5f
    );

    private long nextAlertAt;

    public LowHealthAlert() {
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

        float maxHealth = mc.player.getMaxHealth();
        if (maxHealth <= 0f) return;

        float percent = mc.player.getHealth() / maxHealth * 100f;
        long now = System.currentTimeMillis();
        if (percent <= threshold.get().floatValue() && now >= nextAlertAt) {
            nextAlertAt = now + 3000L;
            Manager.NOTIFICATION_MANAGER.add(
                    NotificationType.INFO,
                    name,
                    String.format("Здоровье: %.0f%%", Math.max(0f, percent)),
                    3
            );
        } else if (percent > threshold.get().floatValue()) {
            nextAlertAt = 0L;
        }
    }
}
