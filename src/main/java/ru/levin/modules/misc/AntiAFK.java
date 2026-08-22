package ru.levin.modules.misc;

import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.input.EventKeyBoard;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;

import java.util.concurrent.ThreadLocalRandom;

@FunctionAnnotation(
        name = "AntiAFK",
        desc = "Имитирует безопасную активность, чтобы сервер не считал игрока AFK",
        type = Type.Misc
)
public class AntiAFK extends Function {
    private final ModeSetting action = new ModeSetting(
            "Действие",
            "Случайно",
            "Случайно",
            "Поворот",
            "Движение",
            "Прыжок",
            "Приседание"
    );
    private final SliderSetting interval = new SliderSetting("Интервал (сек)", 45, 10, 300, 5);
    private final SliderSetting duration = new SliderSetting("Длительность (тики)", 3, 1, 10, 1);
    private final BooleanSetting onlyInWorld = new BooleanSetting("Только в мире", true,
            "Не выполняет действия в меню и интерфейсах");

    private long nextActionAt;
    private int actionTicks;
    private boolean forwardInjected;
    private boolean sneakInjected;

    public AntiAFK() {
        addSettings(action, interval, duration, onlyInWorld);
    }

    @Override
    protected void onEnable() {
        nextActionAt = System.currentTimeMillis() + 1500L;
        actionTicks = 0;
        forwardInjected = false;
        sneakInjected = false;
    }

    @Override
    protected void onDisable() {
        actionTicks = 0;
        forwardInjected = false;
        sneakInjected = false;
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.world == null) return;

        if (event instanceof EventKeyBoard keyboard) {
            if (onlyInWorld.get() && mc.currentScreen != null) return;
            if (actionTicks > 0 && forwardInjected
                    && keyboard.getMovementForward() == 0.0f
                    && keyboard.getMovementStrafe() == 0.0f) {
                keyboard.setMovementForward(1.0f);
            }
            if (actionTicks > 0 && sneakInjected) keyboard.setSneak(true);
            if (actionTicks > 0) {
                actionTicks--;
                if (actionTicks == 0) {
                    forwardInjected = false;
                    sneakInjected = false;
                }
            }
            return;
        }

        if (!(event instanceof EventUpdate)) return;

        if (actionTicks > 0) return;

        if (onlyInWorld.get() && mc.currentScreen != null) {
            nextActionAt = System.currentTimeMillis() + 1000L;
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionAt) return;

        performAction();
        nextActionAt = now + interval.get().longValue() * 1000L;
    }

    private void performAction() {
        String selected = action.get();
        if (selected.equals("Случайно")) {
            String[] actions = {"Поворот", "Движение", "Прыжок", "Приседание"};
            selected = actions[ThreadLocalRandom.current().nextInt(actions.length)];
        }

        switch (selected) {
            case "Поворот" -> rotateCamera();
            case "Движение" -> holdForward();
            case "Прыжок" -> {
                if (mc.player.isOnGround()) mc.player.jump();
            }
            case "Приседание" -> holdSneak();
            default -> {
            }
        }
    }

    private void rotateCamera() {
        float yawOffset = ThreadLocalRandom.current().nextBoolean() ? 18.0f : -18.0f;
        mc.player.setYaw(mc.player.getYaw() + yawOffset);
        mc.player.setHeadYaw(mc.player.getYaw());
    }

    private void holdForward() {
        forwardInjected = true;
        sneakInjected = false;
        actionTicks = Math.max(1, duration.get().intValue());
    }

    private void holdSneak() {
        sneakInjected = true;
        forwardInjected = false;
        actionTicks = Math.max(1, duration.get().intValue());
    }
}
