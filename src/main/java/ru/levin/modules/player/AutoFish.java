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
    private long nextRecastAt = -1L;
    private FishingBobberEntity processedHook;
    private boolean biteHandled;
    private boolean waitingForRecast;

    public AutoFish() {
        addSettings(reactionDelay, recastDelay, stopInScreen, requireOpenWater);
    }

    @Override
    protected void onEnable() {
        biteDetectedAt = -1L;
        nextRecastAt = System.currentTimeMillis() + 300L;
        processedHook = null;
        biteHandled = false;
        waitingForRecast = false;
    }

    @Override
    protected void onDisable() {
        biteDetectedAt = -1L;
        nextRecastAt = -1L;
        processedHook = null;
        biteHandled = false;
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
        long now = System.currentTimeMillis();

        if (hook == null) {
            // Initial cast and every recast are both delayed, but only one action
            // may be sent for each empty-hook state.
            if (!waitingForRecast && processedHook == null && now >= nextRecastAt) {
                useRod(hand);
                nextRecastAt = now + recastDelay.get().longValue();
                return;
            }

            // Recast only after the previous bobber has actually disappeared.
            if (waitingForRecast && now >= nextRecastAt) {
                useRod(hand);
                waitingForRecast = false;
                biteDetectedAt = -1L;
                processedHook = null;
                biteHandled = false;
            } else if (!waitingForRecast && processedHook != null) {
                // The player may have reeled manually; allow a normal delayed cast again.
                nextRecastAt = Math.max(nextRecastAt, now + recastDelay.get().longValue());
                waitingForRecast = true;
            }
            return;
        }

        // A new hook starts a new bite cycle. The caughtFish flag can remain true for
        // several ticks, so the hook identity and biteHandled guard are both required.
        if (hook != processedHook) {
            processedHook = hook;
            biteDetectedAt = -1L;
            biteHandled = false;
            waitingForRecast = false;
        }

        if (requireOpenWater.get() && !hook.isInOpenWater()) {
            biteDetectedAt = -1L;
            return;
        }

        boolean caughtFish = ((FishingBobberEntityAccessor) hook).lupaware$isCaughtFish();
        if (!caughtFish || biteHandled) {
            if (!caughtFish) biteDetectedAt = -1L;
            return;
        }

        if (biteDetectedAt < 0L) biteDetectedAt = now;
        if (now - biteDetectedAt >= reactionDelay.get().longValue()) {
            useRod(hand);
            biteHandled = true;
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
