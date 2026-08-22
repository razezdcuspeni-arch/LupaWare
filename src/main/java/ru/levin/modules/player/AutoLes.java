package ru.levin.modules.player;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FunctionAnnotation(
        name = "AutoLes",
        desc = "Автоматически собирает деревья на ReallyWorld и реагирует на администрацию",
        type = Type.Player
)
public class AutoLes extends Function {
    private static final Pattern REMAINING_PATTERN = Pattern.compile(
            "(?iu)(?:остал(?:ось|о|ась)|осталось\\s+сломать|нужно\\s+сломать|remaining|left)[^0-9]{0,40}(\\d{1,2})"
    );
    private static final Pattern TREE_DONE_PATTERN = Pattern.compile(
            "(?iu)(?:дерев|tree)[^.!?]{0,50}(?:сломано|сломлен|готов|разрушен|destroyed|broken)"
    );

    private static final String[] STAFF_MARKERS = {
            "модератор", "модер", "хелпер", "helper", "moderator", "admin", "админ",
            "администратор", "administrator", "куратор", "владелец", "owner", "создатель",
            "стажер", "стажёр", "support", "поддержк", "персонал"
    };

    private final SliderSetting searchRadius = new SliderSetting(
            "Радиус поиска деревьев", 8, 3, 16, 1
    );
    private final SliderSetting breakDelay = new SliderSetting(
            "Задержка разрушения (мс)", 300, 100, 1000, 25
    );
    private final BooleanSetting sprintToTree = new BooleanSetting(
            "Бежать к дереву", true, "Автоматически идти к найденному дереву"
    );
    private final BooleanSetting staffEscape = new BooleanSetting(
            "Выходить при администрации", true,
            "При обнаружении модератора, хелпера или другого staff отправляет /hub и выключает AutoLes"
    );

    private BlockPos targetLog;
    private final Set<BlockPos> completedLogs = new HashSet<>();
    private final Set<String> knownStaff = new HashSet<>();
    private int remainingBreaks = 1;
    private long nextBreakAt;
    private long nextStaffScanAt;
    private boolean hubSent;
    private boolean controllingMovement;

    public AutoLes() {
        addSettings(searchRadius, breakDelay, sprintToTree, staffEscape);
    }

    @Override
    protected void onEnable() {
        targetLog = null;
        completedLogs.clear();
        knownStaff.clear();
        remainingBreaks = 1;
        nextBreakAt = 0L;
        nextStaffScanAt = 0L;
        hubSent = false;
        controllingMovement = false;

        if (mc.player != null) {
            for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
                String name = entry.getProfile() == null ? "" : entry.getProfile().getName();
                String display = entry.getDisplayName() == null ? "" : entry.getDisplayName().getString();
                if (isStaffText(name + " " + display)) {
                    knownStaff.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    @Override
    protected void onDisable() {
        releaseMovementKeys();
        targetLog = null;
        completedLogs.clear();
        knownStaff.clear();
        remainingBreaks = 1;
        nextBreakAt = 0L;
        nextStaffScanAt = 0L;
        hubSent = false;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventPacket packet && packet.isReceivePacket()) {
            handleServerMessage(packet);
            return;
        }

        if (!(event instanceof EventUpdate) || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (!isReallyWorld() || mc.currentScreen != null) {
            releaseMovementKeys();
            return;
        }

        long now = System.currentTimeMillis();
        if (staffEscape.get() && now >= nextStaffScanAt) {
            nextStaffScanAt = now + 750L;
            if (detectNewStaff()) {
                requestEmergencyHub();
                return;
            }
        }

        if (targetLog == null || !isTreeLog(targetLog)) {
            selectNextTree();
        }

        if (targetLog == null) {
            releaseMovementKeys();
            return;
        }

        Vec3d center = Vec3d.ofCenter(targetLog);
        double distance = mc.player.getEyePos().distanceTo(center);
        if (distance > 4.5D) {
            if (sprintToTree.get()) {
                rotateTo(center);
                mc.options.forwardKey.setPressed(true);
                mc.options.sprintKey.setPressed(true);
                controllingMovement = true;
            } else {
                releaseMovementKeys();
            }
            return;
        }

        releaseMovementKeys();
        rotateTo(center);
        if (now >= nextBreakAt) {
            breakTarget();
            nextBreakAt = now + breakDelay.get().longValue();
        }
    }

    private void handleServerMessage(EventPacket event) {
        if (!staffEscape.get() || !isReallyWorld()) return;
        String text = extractMessageText(event);
        if (text.isEmpty()) return;
        if (isStaffText(text) && isStaffJoinMessage(text)) {
            requestEmergencyHub();
            return;
        }

        if (targetLog == null) return;
        Matcher remainingMatcher = REMAINING_PATTERN.matcher(text);
        if (remainingMatcher.find()) {
            int parsed = Integer.parseInt(remainingMatcher.group(1));
            remainingBreaks = MathHelper.clamp(parsed, 0, 10);
            if (remainingBreaks <= 0 || TREE_DONE_PATTERN.matcher(text).find()) {
                completedLogs.add(targetLog);
                targetLog = null;
                remainingBreaks = 1;
                nextBreakAt = System.currentTimeMillis() + breakDelay.get().longValue();
            } else {
                nextBreakAt = System.currentTimeMillis() + breakDelay.get().longValue();
            }
            return;
        }

        if (TREE_DONE_PATTERN.matcher(text).find()) {
            completedLogs.add(targetLog);
            targetLog = null;
            remainingBreaks = 1;
            nextBreakAt = System.currentTimeMillis() + breakDelay.get().longValue();
        }
    }

    private void selectNextTree() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int radius = searchRadius.get().intValue();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 6; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos candidate = origin.add(x, y, z);
                    if (completedLogs.contains(candidate) || !isTreeLog(candidate)) continue;

                    double distance = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(candidate));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.toImmutable();
                    }
                }
            }
        }

        if (best == null && !completedLogs.isEmpty()) {
            completedLogs.clear();
            selectNextTree();
            return;
        }

        if (best != null && !best.equals(targetLog)) {
            targetLog = best;
            remainingBreaks = 1;
            nextBreakAt = 0L;
        }
    }

    private boolean isTreeLog(BlockPos pos) {
        if (mc.world == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isIn(BlockTags.LOGS)) return true;
        return state.isOf(Blocks.BAMBOO_BLOCK);
    }

    private void breakTarget() {
        if (targetLog == null || mc.interactionManager == null) return;
        mc.interactionManager.attackBlock(targetLog, Direction.UP);
        mc.interactionManager.updateBlockBreakingProgress(targetLog, Direction.UP);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void rotateTo(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.hypot(dx, dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private boolean detectNewStaff() {
        if (mc.player == null || mc.player.networkHandler == null) return false;

        for (PlayerListEntry entry : mc.player.networkHandler.getPlayerList()) {
            String name = entry.getProfile() == null ? "" : entry.getProfile().getName();
            String display = entry.getDisplayName() == null ? "" : entry.getDisplayName().getString();
            String key = name.toLowerCase(Locale.ROOT);
            if (isStaffText(name + " " + display) && knownStaff.add(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStaffText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : STAFF_MARKERS) {
            if (lower.contains(marker)) return true;
        }
        return lower.contains("[мод]") || lower.contains("[хелп]") || lower.contains("[staff]");
    }

    private boolean isStaffJoinMessage(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("зашел") || lower.contains("зашёл") || lower.contains("вошел")
                || lower.contains("вошёл") || lower.contains("присоедини") || lower.contains("подключ")
                || lower.contains("join") || lower.contains("connected") || lower.contains("online");
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

    private void requestEmergencyHub() {
        if (hubSent || mc.player == null || !isReallyWorld()) return;
        hubSent = true;
        mc.execute(() -> {
            if (mc.player == null || !isReallyWorld()) return;
            releaseMovementKeys();
            mc.player.networkHandler.sendChatCommand("hub");
            setState(false);
        });
    }

    private boolean isReallyWorld() {
        if (mc.getCurrentServerEntry() == null || mc.isConnectedToLocalServer()) return false;
        String address = mc.getCurrentServerEntry().address;
        if (address == null) return false;
        String lower = address.toLowerCase(Locale.ROOT);
        return lower.contains("reallyworld") || lower.contains("playrw");
    }

    private void releaseMovementKeys() {
        if (!controllingMovement || mc == null) return;
        restoreKey(mc.options.forwardKey);
        restoreKey(mc.options.backKey);
        restoreKey(mc.options.leftKey);
        restoreKey(mc.options.rightKey);
        restoreKey(mc.options.sprintKey);
        controllingMovement = false;
    }

    private void restoreKey(KeyBinding key) {
        key.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getDefaultKey().getCode()));
    }
}
