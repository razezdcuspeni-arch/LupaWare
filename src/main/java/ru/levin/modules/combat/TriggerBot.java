package ru.levin.modules.combat;

import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;

@SuppressWarnings("All")
@FunctionAnnotation(name = "TriggerBot", desc = "Атакует сущность под прицелом", type = Type.Combat)
public class TriggerBot extends Function {
    private final BooleanSetting pauseIfEating = new BooleanSetting("Не бить когда ешь", true);
    private final BooleanSetting onlyCriticals = new BooleanSetting("Только критами", true);

    private int delay;

    public TriggerBot() {
        addSettings(pauseIfEating, onlyCriticals);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;

        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null) return;
        if (pauseIfEating.get() && player.isUsingItem()) return;

        if (delay > 0) {
            delay--;
            return;
        }

        Item heldItem = player.getMainHandStack().getItem();
        if (!(heldItem instanceof SwordItem || heldItem instanceof AxeItem || heldItem instanceof MaceItem)) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult result)) return;

        Entity entity = result.getEntity();
        if (!(entity instanceof LivingEntity target) || target == player || !target.isAlive() || target.isDead()) return;
        if (!canAttack(player)) return;

        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        delay = 10;
    }

    private boolean canAttack(ClientPlayerEntity player) {
        if (!onlyCriticals.get()) return true;

        boolean specialSurfaceOrEffect = player.isCreative()
                || player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                || player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION)
                || mc.world.getBlockState(player.getBlockPos()).isOf(Blocks.SOUL_SAND);

        float cooldown = player.getAttackCooldownProgress(0.5f);
        if (cooldown < (player.isOnGround() ? 1.0f : 0.9f)) return false;

        if (player.isOnGround()) return true;
        if (player.isClimbing()) return true;
        if (specialSurfaceOrEffect) return true;
        return player.fallDistance > 0.0f;
    }

    @Override
    protected void onEnable() {
        delay = 0;
    }

    @Override
    protected void onDisable() {
        delay = 0;
    }
}
