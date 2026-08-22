package ru.levin.modules.player;

import net.minecraft.util.math.BlockPos;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

import java.util.Locale;

@FunctionAnnotation(
        name = "AutoFarm",
        desc = "Ломает растительность через Baritone в ограниченной зоне ReallyWorld",
        type = Type.Player
)
public class AutoFarm extends Function {
    private final SliderSetting farmRadius = new SliderSetting(
            "Радиус AFK-зоны", 24, 8, 64, 1
    );
    private final BooleanSetting preventPlacing = new BooleanSetting(
            "Не ставить блоки", true,
            "Запрещает Baritone пересаживать культуры и ставить блоки"
    );
    private final BooleanSetting stopAtBoundary = new BooleanSetting(
            "Стоп на границе", true,
            "Останавливает Baritone при выходе за радиус от точки старта"
    );

    private BlockPos startPos;
    private boolean baritoneStarted;
    private boolean boundaryStopped;

    public AutoFarm() {
        addSettings(farmRadius, preventPlacing, stopAtBoundary);
    }

    @Override
    protected void onEnable() {
        startPos = null;
        baritoneStarted = false;
        boundaryStopped = false;
    }

    @Override
    protected void onDisable() {
        stopBaritone();
        startPos = null;
        boundaryStopped = false;
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate) || mc.player == null || mc.world == null) {
            return;
        }

        if (!isReallyWorld()) {
            if (baritoneStarted) stopBaritone();
            return;
        }

        if (startPos == null) {
            startPos = mc.player.getBlockPos().toImmutable();
            startBaritone();
            return;
        }

        if (stopAtBoundary.get() && isOutsideFarmZone()) {
            if (!boundaryStopped) {
                boundaryStopped = true;
                stopBaritone();
            }
        }
    }

    private void startBaritone() {
        if (mc.player == null || baritoneStarted) return;

        if (preventPlacing.get()) {
            sendBaritone("#set allowPlace false");
            sendBaritone("#set replantCrops false");
        }
        sendBaritone("#farm " + farmRadius.get().intValue());
        baritoneStarted = true;
    }

    private void stopBaritone() {
        if (mc.player == null || !baritoneStarted) return;
        sendBaritone("#stop");
        if (preventPlacing.get()) {
            sendBaritone("#set allowPlace true");
            sendBaritone("#set replantCrops true");
        }
        baritoneStarted = false;
    }

    private boolean isOutsideFarmZone() {
        if (startPos == null || mc.player == null) return false;
        double radius = farmRadius.get().doubleValue() + 0.75D;
        return startPos.toCenterPos().squaredDistanceTo(mc.player.getPos()) > radius * radius;
    }

    private void sendBaritone(String command) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.sendChatMessage(command);
    }

    private boolean isReallyWorld() {
        if (mc.getCurrentServerEntry() == null || mc.isConnectedToLocalServer()) return false;
        String address = mc.getCurrentServerEntry().address;
        if (address == null) return false;
        String lower = address.toLowerCase(Locale.ROOT);
        return lower.contains("reallyworld") || lower.contains("playrw");
    }
}
