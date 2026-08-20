package ru.levin.modules.player;

import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import ru.levin.events.Event;
import ru.levin.events.impl.input.EventKey;
import ru.levin.events.impl.input.EventMouse;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BindSetting;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.util.player.InventoryUtil;

@FunctionAnnotation(name = "MiddleClickPearl",keywords = {"MCP"}, desc = "Откидывание пёрла по колёсику мыши", type = Type.Player)
public class MiddleClickPearl extends Function {
    private final ModeSetting mode = new ModeSetting("Тип","Обычный","Обычный","По бинду");
    private final BindSetting bind = new BindSetting("Кнопка кидания",0,() -> mode.is("По бинду"));
    private final BooleanSetting inventoryUse = new BooleanSetting("Использовать из инвентаря",true,"Не используйте на HollyWorld (баниться)");

    public MiddleClickPearl() {
        addSettings(mode,bind,inventoryUse);
    }

    @Override
    public void onEvent(Event event) {
        if (mode.is("Обычный")) {
            if (event instanceof EventMouse mouseTick) {
                if (mouseTick.getButton() == 2) {
                    handleMouseTickEvent();
                }
            }
        }
        if (mode.is("По бинду")) {
            if (event instanceof EventKey e) {
                if (e.key == bind.getKey()) {
                    handleMouseTickEvent();
                }
            }
        }
    }

    private void handleMouseTickEvent() {
        if (mc.player == null || mc.interactionManager == null
                || mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
            return;
        }

        if (Manager.FUNCTION_MANAGER.attackAura.target != null) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));
        }

        int currentSlot = mc.player.getInventory().selectedSlot;
        int pearlSlot = InventoryUtil.getHotBarSlot(Items.ENDER_PEARL);
        if (pearlSlot != -1) {
            if (pearlSlot != currentSlot) {
                mc.player.getInventory().selectedSlot = pearlSlot;
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(pearlSlot));
            }
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (pearlSlot != currentSlot) {
                mc.player.getInventory().selectedSlot = currentSlot;
                mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(currentSlot));
            }
            return;
        }

        if (inventoryUse.get()) {
            InventoryUtil.inventorySwapClick2(Items.ENDER_PEARL, true, true);
        }
    }
}
