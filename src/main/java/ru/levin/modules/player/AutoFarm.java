package ru.levin.modules.player;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.util.math.BlockPos;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
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
    private final BooleanSetting staffEscape = new BooleanSetting(
            "Выход при администрации", true,
            "Останавливает ферму и отправляет /hub, если администрация есть в сети"
    );
    private final BooleanSetting stealthBaritone = new BooleanSetting(
            "Менее заметный Baritone", true,
            "Включает легитное ломание, небольшую случайность взгляда и спокойный темп"
    );

    private static final String[] STAFF_MARKERS = {
            "модератор", "модер", "хелпер", "helper", "moderator", "admin", "админ",
            "администратор", "administrator", "куратор", "владелец", "owner", "создатель",
            "стажер", "стажёр", "support", "поддержк", "персонал", "[мод]", "[хелп]", "[staff]"
    };

    private BlockPos startPos;
    private boolean baritoneStarted;
    private boolean boundaryStopped;
    private long nextStaffScanAt;
    private boolean hubSent;

    public AutoFarm() {
        addSettings(farmRadius, preventPlacing, stopAtBoundary, staffEscape, stealthBaritone);
    }

    @Override
    protected void onEnable() {
        startPos = null;
        baritoneStarted = false;
        boundaryStopped = false;
        nextStaffScanAt = 0L;
        hubSent = false;
    }

    @Override
    protected void onDisable() {
        stopBaritone();
        startPos = null;
        boundaryStopped = false;
        nextStaffScanAt = 0L;
        hubSent = false;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventPacket packet && packet.isReceivePacket()) {
            handleIncomingMessage(packet);
            return;
        }

        if (!(event instanceof EventUpdate) || mc.player == null || mc.world == null) {
            return;
        }

        if (!isReallyWorld()) {
            if (baritoneStarted) stopBaritone();
            return;
        }

        long now = System.currentTimeMillis();
        if (staffEscape.get() && now >= nextStaffScanAt) {
            nextStaffScanAt = now + 500L;
            if (isStaffOnline()) {
                requestEmergencyHub();
                return;
            }
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
        if (stealthBaritone.get()) {
            sendBaritone("#set legitMine true");
            sendBaritone("#set randomLooking 0.12");
            sendBaritone("#set randomLooking113 2.5");
            sendBaritone("#set blockBreakSpeed 10");
            sendBaritone("#set allowSprint false");
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
        if (stealthBaritone.get()) {
            sendBaritone("#set legitMine false");
            sendBaritone("#set randomLooking 0.01");
            sendBaritone("#set randomLooking113 2");
            sendBaritone("#set blockBreakSpeed 6");
            sendBaritone("#set allowSprint true");
        }
        baritoneStarted = false;
    }

    private void handleIncomingMessage(EventPacket event) {
        if (!staffEscape.get() || !isReallyWorld()) return;
        String text = extractMessageText(event);
        if (!text.isEmpty() && isStaffText(text)) {
            requestEmergencyHub();
        }
    }

    private String extractMessageText(EventPacket event) {
        if (event.getPacket() instanceof GameMessageS2CPacket packet) {
            return packet.content().getString();
        }
        if (event.getPacket() instanceof ProfilelessChatMessageS2CPacket packet) {
            return packet.message().getString();
        }
        if (event.getPacket() instanceof ChatMessageS2CPacket packet) {
            return packet.unsignedContent().getString();
        }
        return "";
    }

    private boolean isStaffOnline() {
        if (mc.player == null || mc.player.networkHandler == null) return false;
        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            String name = entry.getProfile() == null ? "" : entry.getProfile().getName();
            String display = entry.getDisplayName() == null ? "" : entry.getDisplayName().getString();
            if (isStaffText(name + " " + display)) return true;
        }
        return false;
    }

    private boolean isStaffText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : STAFF_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    private void requestEmergencyHub() {
        if (hubSent || mc.player == null || !isReallyWorld()) return;
        hubSent = true;
        mc.execute(() -> {
            if (mc.player == null || !isReallyWorld()) return;
            stopBaritone();
            mc.player.networkHandler.sendChatCommand("hub");
            setState(false);
        });
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
