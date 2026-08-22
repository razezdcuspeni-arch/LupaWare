package ru.levin.modules.player;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ProfilelessChatMessageS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.Manager;
import ru.levin.manager.notificationManager.NotificationType;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

import java.util.Comparator;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FunctionAnnotation(
        name = "AutoFarm",
        desc = "Ломает растительность через Baritone и периодически забирает зарплату у Фермера",
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
    private final BooleanSetting collectSalary = new BooleanSetting(
            "Забирать зарплату", true,
            "Периодически подходит к NPC Фермер и забирает выплату"
    );
    private final SliderSetting salaryMinMinutes = new SliderSetting(
            "Мин. интервал зарплаты", 10, 10, 15, 1
    );
    private final SliderSetting salaryMaxMinutes = new SliderSetting(
            "Макс. интервал зарплаты", 15, 10, 15, 1
    );
    private final SliderSetting farmerSearchRadius = new SliderSetting(
            "Радиус поиска Фермера", 96, 16, 128, 1
    );

    private static final String[] STAFF_MARKERS = {
            "модератор", "модер", "хелпер", "helper", "moderator", "admin", "админ",
            "администратор", "administrator", "куратор", "владелец", "owner", "создатель",
            "стажер", "стажёр", "support", "поддержк", "персонал", "[мод]", "[хелп]", "[staff]"
    };
    private static final Pattern SALARY_PATTERN = Pattern.compile(
            "(?i)(?:работы\\s*[»>:-]?\\s*)?(?:ваша\\s+зарплата|salary)\\s*:\\s*([0-9][0-9\\s.,]*[a-zа-я]*)"
    );
    private static final Random RANDOM = new Random();

    private enum SalaryState {
        FARMING,
        GOING_TO_FARMER,
        WAITING_FOR_SALARY,
        RETURNING_TO_FARM
    }

    private BlockPos startPos;
    private VillagerEntity farmer;
    private SalaryState salaryState = SalaryState.FARMING;
    private boolean baritoneStarted;
    private boolean boundaryStopped;
    private boolean hubSent;
    private boolean salaryConfirmed;
    private int salaryAttempts;
    private long nextStaffScanAt;
    private long nextSalaryAt;
    private long salaryDeadline;
    private long nextInteractionAt;

    public AutoFarm() {
        addSettings(
                farmRadius,
                preventPlacing,
                stopAtBoundary,
                staffEscape,
                stealthBaritone,
                collectSalary,
                salaryMinMinutes,
                salaryMaxMinutes,
                farmerSearchRadius
        );
    }

    @Override
    protected void onEnable() {
        startPos = null;
        farmer = null;
        salaryState = SalaryState.FARMING;
        baritoneStarted = false;
        boundaryStopped = false;
        hubSent = false;
        salaryConfirmed = false;
        salaryAttempts = 0;
        nextStaffScanAt = 0L;
        nextSalaryAt = 0L;
        salaryDeadline = 0L;
        nextInteractionAt = 0L;
    }

    @Override
    protected void onDisable() {
        stopBaritone();
        releaseMovementKeys();
        startPos = null;
        farmer = null;
        salaryState = SalaryState.FARMING;
        boundaryStopped = false;
        hubSent = false;
        salaryConfirmed = false;
        salaryAttempts = 0;
        nextSalaryAt = 0L;
        salaryDeadline = 0L;
        nextInteractionAt = 0L;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventPacket packet && packet.isReceivePacket()) {
            handleIncomingMessage(packet);
            return;
        }

        if (!(event instanceof EventUpdate) || mc.player == null || mc.world == null) return;
        if (!isReallyWorld()) {
            stopBaritone();
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
            scheduleNextSalary(now);
            startFarmBaritone();
            return;
        }

        if (collectSalary.get()) {
            tickSalaryCycle(now);
        }

        if (salaryState == SalaryState.FARMING
                && stopAtBoundary.get()
                && isOutsideFarmZone()) {
            if (!boundaryStopped) {
                boundaryStopped = true;
                stopBaritone();
            }
        }
    }

    private void tickSalaryCycle(long now) {
        switch (salaryState) {
            case FARMING -> {
                if (now >= nextSalaryAt) beginSalaryTrip();
            }
            case GOING_TO_FARMER -> {
                if (farmer == null || !farmer.isAlive()) {
                    finishSalaryTrip(false, "NPC Фермер не найден");
                    return;
                }

                double distance = mc.player.squaredDistanceTo(farmer);
                if (distance <= 3.6D * 3.6D && now >= nextInteractionAt) {
                    stopBaritone();
                    interactWithFarmer();
                }
            }
            case WAITING_FOR_SALARY -> {
                if (salaryConfirmed) {
                    beginReturnToFarm();
                } else if (now >= salaryDeadline) {
                    finishSalaryTrip(false, "Подтверждение зарплаты не пришло");
                }
            }
            case RETURNING_TO_FARM -> {
                if (startPos != null && mc.player.squaredDistanceTo(startPos.toCenterPos()) <= 3.5D * 3.5D) {
                    finishSalaryTrip(salaryConfirmed, salaryConfirmed ? "Зарплата получена" : "Возврат к ферме");
                }
            }
        }
    }

    private void beginSalaryTrip() {
        if (salaryState != SalaryState.FARMING || startPos == null) return;

        farmer = findFarmer();
        if (farmer == null) {
            scheduleNextSalary(System.currentTimeMillis());
            Manager.NOTIFICATION_MANAGER.add(NotificationType.INFO, name, "NPC Фермер не найден", 3);
            return;
        }

        boundaryStopped = false;
        salaryConfirmed = false;
        salaryAttempts = 0;
        salaryState = SalaryState.GOING_TO_FARMER;
        stopBaritone();
        startGotoBaritone(farmer.getBlockPos());
    }

    private void interactWithFarmer() {
        if (mc.player == null || mc.interactionManager == null || farmer == null) return;

        nextInteractionAt = System.currentTimeMillis() + 2500L;
        salaryDeadline = System.currentTimeMillis() + 15000L;
        salaryAttempts++;
        salaryState = SalaryState.WAITING_FOR_SALARY;
        mc.interactionManager.interactEntity(mc.player, farmer, Hand.MAIN_HAND);
    }

    private void beginReturnToFarm() {
        if (salaryState != SalaryState.WAITING_FOR_SALARY || startPos == null) return;
        salaryState = SalaryState.RETURNING_TO_FARM;
        stopBaritone();
        startGotoBaritone(startPos);
    }

    private void finishSalaryTrip(boolean confirmed, String message) {
        stopBaritone();
        salaryConfirmed = confirmed;
        salaryState = SalaryState.FARMING;
        farmer = null;
        boundaryStopped = false;
        scheduleNextSalary(System.currentTimeMillis());
        startFarmBaritone();
        if (confirmed) {
            Manager.NOTIFICATION_MANAGER.add(NotificationType.SUCCESS, name, message, 3);
        }
    }

    private VillagerEntity findFarmer() {
        if (mc.player == null || mc.world == null) return null;
        double radius = farmerSearchRadius.get().doubleValue();
        Box searchBox = mc.player.getBoundingBox().expand(radius);
        return mc.world.getEntitiesByClass(
                        VillagerEntity.class,
                        searchBox,
                        entity -> entity.isAlive() && isFarmerName(entity)
                ).stream()
                .min(Comparator.comparingDouble(entity -> mc.player.squaredDistanceTo(entity)))
                .orElse(null);
    }

    private boolean isFarmerName(VillagerEntity entity) {
        String customName = entity.hasCustomName() && entity.getCustomName() != null
                ? entity.getCustomName().getString()
                : "";
        String displayName = entity.getDisplayName() == null ? "" : entity.getDisplayName().getString();
        String text = (customName + " " + displayName).toLowerCase(Locale.ROOT);
        return text.contains("фермер") || text.contains("farmer");
    }

    private void scheduleNextSalary(long now) {
        int min = Math.round(salaryMinMinutes.get().floatValue());
        int max = Math.round(salaryMaxMinutes.get().floatValue());
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        int delayMinutes = min + (max == min ? 0 : RANDOM.nextInt(max - min + 1));
        nextSalaryAt = now + delayMinutes * 60_000L;
    }

    private void startFarmBaritone() {
        if (mc.player == null || baritoneStarted || boundaryStopped) return;
        configureBaritone();
        sendBaritone("#farm " + farmRadius.get().intValue());
        baritoneStarted = true;
    }

    private void startGotoBaritone(BlockPos target) {
        if (mc.player == null || target == null) return;
        configureBaritone();
        sendBaritone("#goto " + target.getX() + " " + target.getY() + " " + target.getZ());
        baritoneStarted = true;
    }

    private void configureBaritone() {
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
        String text = extractMessageText(event);
        if (text.isEmpty()) return;

        if (collectSalary.get() && salaryState == SalaryState.WAITING_FOR_SALARY) {
            Matcher matcher = SALARY_PATTERN.matcher(text);
            if (matcher.find()) {
                String amount = matcher.group(1).trim();
                mc.execute(() -> {
                    if (salaryState != SalaryState.WAITING_FOR_SALARY) return;
                    salaryConfirmed = true;
                    Manager.NOTIFICATION_MANAGER.add(
                            NotificationType.SUCCESS,
                            name,
                            "Зарплата: " + amount,
                            3
                    );
                    beginReturnToFarm();
                });
                return;
            }
        }

        if (staffEscape.get() && isReallyWorld() && isStaffText(text)) {
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
            releaseMovementKeys();
            mc.player.networkHandler.sendChatCommand("hub");
            setState(false);
        });
    }

    private void releaseMovementKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
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
