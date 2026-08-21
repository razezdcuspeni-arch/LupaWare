package ru.levin.modules.player;

import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.mixin.iface.FishingBobberEntityAccessor;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

@FunctionAnnotation(
        name = "AutoFish",
        desc = "Автоматически реагирует на поклёвку и забрасывает удочку снова",
        type = Type.Player
)
public class AutoFish extends Function {
    private final SliderSetting reactionDelay = new SliderSetting("Задержка реакции (мс)", 180, 50, 800, 10);
    private final SliderSetting recastDelay = new SliderSetting("Задержка заброса (мс)", 350, 100, 1500, 25);
    private final BooleanSetting stopInScreen = new BooleanSetting("Остановиться в интерфейсе", true,
            "Не использует удочку при открытом интерфейсе");
    private final BooleanSetting requireOpenWater = new BooleanSetting("Только открытая вода", false,
            "Останавливается, если поплавок не находится в открытой воде");

    private long biteDetectedAt = -1L;
    private long nextRecastAt = 0L;
    private boolean waitingForRecast;

    public AutoFish() {
        addSettings(reactionDelay, recastDelay, stopInScreen, requireOpenWater);
    }

    @Override
    protected void onEnable() {
        biteDetectedAt = -1L;
        nextRecastAt = System.currentTimeMillis() + 250L;
        waitingForRecast = false;
    }

    @Override
    protected void onDisable() {
        biteDetectedAt = -1L;
        waitingForRecast = false;
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate) || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (stopInScreen.get() && mc.currentScreen != null) return;
        if (!mc.player.getMainHandStack().isOf(Items.FISHING_ROD)
                && !mc.player.getOffHandStack().isOf(Items.FISHING_ROD)) return;

        Hand hand = mc.player.getMainHandStack().isOf(Items.FISHING_ROD) ? Hand.MAIN_HAND : Hand.OFF_HAND;
        FishingBobberEntity hook = mc.player.fishHook;

        if (hook == null) {
            if (waitingForRecast && System.currentTimeMillis() >= nextRecastAt) {
                useRod(hand);
                waitingForRecast = false;
                biteDetectedAt = -1L;
            } else if (!waitingForRecast && System.currentTimeMillis() >= nextRecastAt) {
                useRod(hand);
                biteDetectedAt = -1L;
            }
            return;
        }

        if (requireOpenWater.get() && !hook.isInOpenWater()) return;

        boolean caughtFish = ((FishingBobberEntityAccessor) hook).lupaware$isCaughtFish();
        if (!caughtFish) {
            biteDetectedAt = -1L;
            return;
        }

        long now = System.currentTimeMillis();
        if (biteDetectedAt < 0L) biteDetectedAt = now;
        if (now - biteDetectedAt >= reactionDelay.get().longValue()) {
            useRod(hand);
            waitingForRecast = true;
            nextRecastAt = now + recastDelay.get().longValue();
            biteDetectedAt = -1L;
        }
    }

    private void useRod(Hand hand) {
        mc.interactionManager.interactItem(mc.player, hand);
        mc.player.swingHand(hand);
    }
}
