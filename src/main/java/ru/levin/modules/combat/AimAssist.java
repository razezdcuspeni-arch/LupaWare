package ru.levin.modules.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.player.EventAttack;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@SuppressWarnings("All")
@FunctionAnnotation(name = "AimAssist", desc = "Плавно наводится на хитбокс цели", type = Type.Combat)
public class AimAssist extends Function {
    private final BooleanSetting onlyWeapon = new BooleanSetting("Только оружие", false);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", false);
    private final BooleanSetting attackInvisible = new BooleanSetting("Невидимые", false);
    private final BooleanSetting attackNaked = new BooleanSetting("Без брони", true);
    private final BooleanSetting attackMobs = new BooleanSetting("Мобы", true);
    private final BooleanSetting lastHit = new BooleanSetting("Последняя цель", false);
    private final BooleanSetting sorting = new BooleanSetting("Сортировка по дистанции", true);
    private final SliderSetting distance = new SliderSetting("Дистанция", 3.7, 2.0, 7.0, 0.1);
    private final SliderSetting fov = new SliderSetting("FOV", 90.0, 1.0, 360.0, 1.0);
    private final SliderSetting yawSpeed = new SliderSetting("Yaw скорость", 180.0, 0.0, 400.0, 1.0);
    private final SliderSetting pitchSpeed = new SliderSetting("Pitch скорость", 150.0, 0.0, 400.0, 1.0);
    private final ModeSetting aimMode = new ModeSetting("Режим", "Normal", "Normal", "Smooth", "FT", "CakeWorld", "HVH");

    private final SecureRandom secureRandom = new SecureRandom();
    private final float[] speedHistory = new float[5];
    private final double[] phaseParams = new double[15];

    private long patternDelay;
    private int hitCount;
    private long sessionSeed = System.nanoTime();
    private long startTime;
    private int currentPhase;
    private double phaseProgress;
    private long phaseStartTime;
    private int attackCount;
    private long lastAttackTime = System.currentTimeMillis();
    private boolean aggressive;
    private int historyIndex;
    private float lastSpeed;
    private float lastPitchVelocity;
    private float smoothPitchOffset;
    private long lastPitchTime;
    private float pitchVelocity;
    private long lastMouseMoveTime = System.currentTimeMillis();
    private float lastAppliedYaw = Float.MAX_VALUE;
    private float lastAppliedPitch = Float.MAX_VALUE;
    private LivingEntity target;

    public AimAssist() {
        addSettings(onlyWeapon, throughWalls, attackInvisible, attackNaked, attackMobs, lastHit, sorting,
                distance, fov, yawSpeed, pitchSpeed, aimMode);
    }

    @Override
    public void onEvent(Event event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || player.isDead()) {
            target = null;
            return;
        }

        if (event instanceof EventAttack attack && lastHit.get() && attack.getTarget() instanceof LivingEntity living) {
            target = living;
            attackCount++;
            lastAttackTime = System.currentTimeMillis();
        }

        if (event instanceof EventUpdate) {
            if (Manager.FUNCTION_MANAGER.attackAura.state) {
                target = null;
                return;
            }

            // Wayne's algorithm always uses the player's real current angles as
            // its base. Manager.ROTATION is only mirrored for packet/raycast users.
            Manager.ROTATION.set(player.getYaw(), player.getPitch());
            detectMouseMovement(player);
            runAimingLogic(player);
            Manager.ROTATION.set(player.getYaw(), player.getPitch());
        } else if (event instanceof EventMotion motion && target != null && isValidTarget(target)) {
            // Keep packets and the visible player aligned with the angles produced
            // by the Wayne algorithm. No camera override is needed for AimAssist.
            motion.setYaw(player.getYaw());
            motion.setPitch(player.getPitch());
        }
    }

    @Override
    protected void onEnable() {
        target = null;
        startTime = System.currentTimeMillis();
        hitCount = 0;
        patternDelay = 0L;
        currentPhase = 0;
        phaseProgress = 0.0;
        phaseStartTime = System.currentTimeMillis();
        initPhaseParams();
        lastPitchTime = System.currentTimeMillis();
        smoothPitchOffset = 0.0f;
        pitchVelocity = 0.0f;
        lastPitchVelocity = 0.0f;
        lastSpeed = 0.0f;
        historyIndex = 0;
        for (int i = 0; i < speedHistory.length; i++) speedHistory[i] = 0.0f;
        lastAppliedYaw = Float.MAX_VALUE;
        lastAppliedPitch = Float.MAX_VALUE;
        lastMouseMoveTime = System.currentTimeMillis();
        if (mc.player != null) Manager.ROTATION.set(mc.player.getYaw(), mc.player.getPitch());
    }

    @Override
    protected void onDisable() {
        target = null;
        currentPhase = 0;
        phaseProgress = 0.0;
        smoothPitchOffset = 0.0f;
        pitchVelocity = 0.0f;
        lastPitchVelocity = 0.0f;
        lastSpeed = 0.0f;
        lastAppliedYaw = Float.MAX_VALUE;
        lastAppliedPitch = Float.MAX_VALUE;
        Manager.ROTATION.set(mc.player != null ? mc.player.getYaw() : 0.0f, mc.player != null ? mc.player.getPitch() : 0.0f);
    }

    public boolean hasTarget() {
        return state && target != null && isValidTarget(target);
    }

    public LivingEntity getTarget() {
        return target;
    }

    private void detectMouseMovement(ClientPlayerEntity player) {
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        if (lastAppliedYaw != Float.MAX_VALUE && lastAppliedPitch != Float.MAX_VALUE
                && (Math.abs(yaw - lastAppliedYaw) > 0.001f || Math.abs(pitch - lastAppliedPitch) > 0.001f)) {
            lastMouseMoveTime = System.currentTimeMillis();
        }
        lastAppliedYaw = yaw;
        lastAppliedPitch = pitch;
    }

    public static boolean isInFOV(Entity entity, double fov) {
        if (entity == null || mc.player == null) return false;
        double difference = getAngleDifference(mc.player.getYaw(), getRotations(entity)[0]);
        double halfFov = fov * 0.5;
        return difference > 0.0 && difference < halfFov || -halfFov < difference && difference < 0.0;
    }

    public static float[] getRotationFromPosition(double x, double y, double z) {
        if (mc.player == null) return new float[]{0.0f, 0.0f};
        double dx = x - mc.player.getX();
        double dy = y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = z - mc.player.getZ();
        double horizontal = Math.hypot(dx, dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] getRotations(Entity entity) {
        return getRotationFromPosition(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
    }

    public static float[] getEntityAngles(Entity source, Entity destination) {
        float tickDelta = mc.getRenderTickCounter().getLastFrameDuration();
        Vec3d sourcePos = source.getPos();
        Vec3d destinationPos = destination.getPos();
        double sourceX = interpolate(source.lastRenderX, sourcePos.x, tickDelta);
        double sourceY = interpolate(source.lastRenderY, sourcePos.y, tickDelta);
        double sourceZ = interpolate(source.lastRenderZ, sourcePos.z, tickDelta);
        double destinationX = interpolate(destination.lastRenderX, destinationPos.x, tickDelta);
        double destinationY = interpolate(destination.lastRenderY, destinationPos.y, tickDelta);
        double destinationZ = interpolate(destination.lastRenderZ, destinationPos.z, tickDelta);
        return calculateAngles(sourceX, sourceY + source.getStandingEyeHeight(), sourceZ,
                destinationX, destinationY + destination.getStandingEyeHeight() / 1.5f, destinationZ);
    }

    private static double interpolate(double previous, double current, float tickDelta) {
        return previous + (current - previous) * MathHelper.clamp(tickDelta, 0.0f, 1.0f);
    }

    public boolean isValidTarget(Entity entity) {
        if (mc.player == null || entity == null || !entity.isAlive() || !(entity instanceof LivingEntity living)) return false;
        if (!isInFOV(entity, fov.get().doubleValue())) return false;
        if (entity == mc.player) return false;
        if (distance.get().doubleValue() < entity.distanceTo(mc.player)) return false;
        if (!attackMobs.get() && !(entity instanceof PlayerEntity)) return false;
        if (!attackInvisible.get() && living.isInvisible()) return false;
        if (!throughWalls.get() && !mc.player.canSee(entity)) return false;

        if (entity instanceof PlayerEntity player) {
            if (Manager.FRIEND_MANAGER.isFriend(player.getName().getString())) return false;
            if (Manager.FUNCTION_MANAGER.antiBot.check(player)) return false;
            boolean wearingArmor = false;
            for (ItemStack armor : player.getInventory().armor) {
                if (!armor.isEmpty()) {
                    wearingArmor = true;
                    break;
                }
            }
            return wearingArmor || attackNaked.get();
        }
        return entity instanceof MobEntity && attackMobs.get();
    }

    public float[] getNormalAngles(Entity entity, float yawFactor, float pitchFactor) {
        float[] angles = getEntityAngles(mc.player, entity);
        float targetYaw = angles[0];
        float targetPitch = angles[1];
        boolean validYaw = Math.abs(wrapDegrees(targetYaw - mc.player.getYaw())) <= 180.0f;
        boolean validPitch = Math.abs(wrapDegrees(targetPitch - mc.player.getPitch())) <= 90.0f;
        if (validYaw && validPitch) {
            float yawStep = wrapDegrees(targetYaw - mc.player.getYaw()) * yawFactor / 100.0f;
            float pitchStep = wrapDegrees(targetPitch - mc.player.getPitch()) * pitchFactor / 100.0f;
            float pitch = mc.player.getPitch() + pitchStep;
            float humanPitch = applyHumanPitch(pitch, pitchFactor, true);
            return new float[]{mc.player.getYaw() + yawStep, MathHelper.clamp(mc.player.getPitch() + humanPitch, -90.0f, 90.0f)};
        }
        return new float[]{mc.player.getYaw(), mc.player.getPitch()};
    }

    public float[] getSmoothAngles(Entity entity, float yawFactor, float pitchFactor) {
        float[] angles = getEntityAngles(mc.player, entity);
        float yawDelta = wrapDegrees(angles[0] - mc.player.getYaw());
        float pitchDelta = wrapDegrees(angles[1] - mc.player.getPitch());
        float entityDistance = mc.player.distanceTo(entity);
        float distanceFactor = 1.0f;
        if (entityDistance <= 1.0f) distanceFactor = 0.2f;
        else if (entityDistance <= 3.0f) distanceFactor = 0.2f + 0.8f * ((entityDistance - 1.0f) / 2.0f);
        float yawInfluence = Math.min(1.0f, Math.abs(yawDelta) / 10.0f);
        float pitchInfluence = Math.min(1.0f, Math.abs(pitchDelta) / 10.0f);
        float yawStep = yawDelta * (yawFactor / 100.0f) * distanceFactor * (0.4f + 0.6f * yawInfluence);
        float pitchStep = pitchDelta * (pitchFactor / 100.0f) * distanceFactor * (0.4f + 0.6f * pitchInfluence);
        yawStep = Math.min(Math.abs(yawStep), Math.abs(yawDelta)) * Math.signum(yawStep);
        pitchStep = Math.min(Math.abs(pitchStep), Math.abs(pitchDelta)) * Math.signum(pitchStep);
        float pitch = mc.player.getPitch() + pitchStep;
        float humanPitch = applyHumanPitch(pitch, pitchFactor * distanceFactor, true);
        return new float[]{mc.player.getYaw() + yawStep, MathHelper.clamp(mc.player.getPitch() + humanPitch, -90.0f, 90.0f)};
    }

    public float[] getFTAngles(Entity entity, float yawFactor, float pitchFactor) {
        float[] angles = getEntityAngles(mc.player, entity);
        float yawDelta = wrapDegrees(angles[0] - mc.player.getYaw());
        float pitchDelta = wrapDegrees(angles[1] - mc.player.getPitch());
        float total = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (total < 1.0E-4f) return new float[]{mc.player.getYaw(), mc.player.getPitch()};
        float aggressiveness = aggressive ? 1.0f : (secureRandom.nextBoolean() ? 0.4f : 0.2f);
        float yawLimit = Math.abs(yawDelta / total) * 180.0f;
        float pitchLimit = Math.abs(pitchDelta / total) * 180.0f;
        float yawStep = MathHelper.clamp(yawDelta, -yawLimit, yawLimit);
        float pitchStep = MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit);
        float factor = randomRange(aggressiveness, aggressiveness + 0.2f);
        float yaw = MathHelper.lerp(factor, mc.player.getYaw(), mc.player.getYaw() + yawStep);
        float pitch = MathHelper.clamp(MathHelper.lerp(factor, mc.player.getPitch(), mc.player.getPitch() + pitchStep), -90.0f, 90.0f);
        float humanPitch = applyHumanPitch(pitch, pitchFactor, true);
        return new float[]{yaw, MathHelper.clamp(mc.player.getPitch() + humanPitch, -90.0f, 90.0f)};
    }

    public float[] getCakeWorldAngles(Entity entity, float yawFactor, float pitchFactor) {
        if (secureRandom.nextFloat() < 0.02f) return new float[]{mc.player.getYaw(), mc.player.getPitch()};
        float[] angles = getEntityAngles(mc.player, entity);
        float yawDelta = wrapDegrees(angles[0] - mc.player.getYaw());
        float pitchDelta = wrapDegrees(angles[1] - mc.player.getPitch());
        float total = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (total < 1.0E-4f) return new float[]{mc.player.getYaw(), mc.player.getPitch()};
        boolean cooled = mc.player.getAttackCooldownProgress(0.0f) >= 0.87f;
        float entityDistance = mc.player.distanceTo(entity);
        float speed = calculateCakeSpeed(total, entity, cooled, entityDistance);
        speedHistory[historyIndex] = speed;
        historyIndex = (historyIndex + 1) % speedHistory.length;
        float averageSpeed = getAverageSpeed();
        float clampFactor = getClampFactor(total);
        float yawLimit = Math.abs(yawDelta / total) * 180.0f * clampFactor;
        float pitchLimit = Math.abs(pitchDelta / total) * 180.0f * clampFactor;
        float yawOffset = 0.0f;
        float pitchOffset = 0.0f;
        if (!cooled) {
            yawOffset = randomRange(21.0f, 28.2f) * (float) Math.sin(System.currentTimeMillis() / 20.0);
            pitchOffset = randomRange(6.0f, 23.8f) * (float) Math.sin(System.currentTimeMillis() / 22.0);
        }
        float yawStep = MathHelper.clamp(yawDelta, -yawLimit, yawLimit);
        float pitchStep = MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit);
        Angles result = applyCakeRotation(new Angles(mc.player.getYaw(), mc.player.getPitch()), yawStep, pitchStep, averageSpeed, yawOffset, pitchOffset);
        return new float[]{result.yaw, result.pitch};
    }

    public float[] getHVHAngles(Entity entity, float yawFactor, float pitchFactor) {
        if (secureRandom.nextFloat() < 0.02f) return new float[]{mc.player.getYaw(), mc.player.getPitch()};
        float[] angles = getEntityAngles(mc.player, entity);
        float yawDelta = wrapDegrees(angles[0] - mc.player.getYaw());
        float pitchDelta = wrapDegrees(angles[1] - mc.player.getPitch());
        float total = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
        if (total < 1.0E-4f) return new float[]{mc.player.getYaw(), mc.player.getPitch()};
        float speed = calculateHVHSpeed(total, entity);
        speedHistory[historyIndex] = speed;
        historyIndex = (historyIndex + 1) % speedHistory.length;
        float averageSpeed = getAverageSpeed();
        float clampFactor = getClampFactor(total);
        float yawLimit = Math.abs(yawDelta / total) * 180.0f * clampFactor;
        float pitchLimit = Math.abs(pitchDelta / total) * 180.0f * clampFactor;
        float yawStep = MathHelper.clamp(yawDelta, -yawLimit, yawLimit);
        float pitchStep = MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit);
        float humanPitch = applyHVHPitch(pitchStep, pitchDelta, entity != null);
        Angles result = applyHVHRotation(new Angles(mc.player.getYaw(), mc.player.getPitch()), yawStep, humanPitch, averageSpeed);
        return new float[]{result.yaw, result.pitch};
    }

    private void runAimingLogic(ClientPlayerEntity player) {
        if (onlyWeapon.get() && !isWeapon(player.getMainHandStack())) return;
        if (!isValidTarget(target)) target = null;
        if (!lastHit.get() && target == null) {
            for (Entity entity : getSortedEntities(mc.world.getEntities())) {
                if (isValidTarget(entity)) {
                    target = (LivingEntity) entity;
                    break;
                }
            }
        }
        if (target == null) return;
        updatePattern();
        float yawFactor = yawSpeed.get().floatValue() / getFPS() * 5.0f;
        float pitchFactor = pitchSpeed.get().floatValue() / getFPS() * 5.0f;
        float[] result = new float[]{player.getYaw(), player.getPitch()};
        switch (aimMode.get()) {
            case "Normal" -> result = getNormalAngles(target, yawFactor, pitchFactor);
            case "Smooth" -> result = getSmoothAngles(target, yawFactor, pitchFactor);
            case "FT" -> result = getFTAngles(target, yawFactor, pitchFactor);
            case "CakeWorld" -> result = getCakeWorldAngles(target, yawFactor, pitchFactor);
            case "HVH" -> result = getHVHAngles(target, yawFactor, pitchFactor);
            default -> {}
        }
        if (yawSpeed.get().doubleValue() > 0.0 && !Float.isInfinite(result[0])) player.setYaw(result[0]);
        if (pitchSpeed.get().doubleValue() > 0.0 && !Float.isInfinite(result[1])) player.setPitch(result[1]);
    }

    private boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof AxeItem || stack.getItem() instanceof SwordItem || stack.getItem() instanceof MaceItem;
    }

    private int getFPS() {
        return Math.max(1, mc.getCurrentFps());
    }

    public void updatePattern() {
        long now = System.currentTimeMillis();
        hitCount++;
        updatePhase(now);
        sessionSeed = sessionSeed * 6364136223846793005L + 1442695040888963407L;
        long randomPart = sessionSeed >> 33 & 0x3FFL;
        double randomOffset = (double) randomPart / 1024.0 * 4.0;
        double hitOffset = Math.sin(hitCount * 0.17) * 4.0;
        double sessionOffset = Math.sin((now - startTime) / 10000.0) * 2.5;
        double phaseBonus = getPhaseBonus(15.0);
        double delay = 4.0 + randomOffset + hitOffset + sessionOffset + phaseBonus;
        if (delay < 0.0) delay = Math.random() * 4.0;
        if (delay > 15.0) delay = 12.0 + Math.random() * 3.0;
        patternDelay = (long) delay;
    }

    private void updatePhase(long now) {
        long elapsed = now - phaseStartTime;
        double phaseDuration = 3000.0 + phaseParams[0] * 2000.0;
        phaseProgress = elapsed / phaseDuration;
        if (phaseProgress >= 1.0) {
            currentPhase = (currentPhase + 1) % 5;
            phaseProgress = 0.0;
            phaseStartTime = now;
        }
    }

    public void generatePhaseParams(int phase) {
        int index = phase * 3;
        long seed = sessionSeed + phase * 7919L;
        phaseParams[index] = sinBasedRandom(seed, 0.3, 0.7);
        phaseParams[index + 1] = sinBasedRandom(seed + 1L, 0.2, 0.9);
        phaseParams[index + 2] = sinBasedRandom(seed + 2L, 0.1, 0.8);
    }

    private void initPhaseParams() {
        for (int phase = 0; phase < 5; phase++) generatePhaseParams(phase);
    }

    public float randomRange(float min, float max) {
        return MathHelper.lerp(secureRandom.nextFloat(), min, max);
    }

    public float calculateCakeSpeed(float angle, Entity entity, boolean cooled, float entityDistance) {
        long sincePitch = System.currentTimeMillis() - lastPitchTime;
        float speed;
        switch (currentPhase) {
            case 0 -> speed = 0.6f + secureRandom.nextFloat() * 0.4f;
            case 1 -> speed = 0.8f + secureRandom.nextFloat() * 0.3f;
            case 2 -> speed = 0.5f + secureRandom.nextFloat() * 0.3f;
            case 3 -> speed = 0.7f + secureRandom.nextFloat() * 0.5f;
            case 4 -> speed = 0.4f + secureRandom.nextFloat() * 0.4f;
            case 5 -> speed = 0.9f + secureRandom.nextFloat() * 0.2f;
            default -> speed = 0.7f + secureRandom.nextFloat() * 0.4f;
        }
        speed *= cooled ? 1.0f + secureRandom.nextFloat() * 0.15f : 0.8f + secureRandom.nextFloat() * 0.2f;
        if (entity != null) speed *= 0.9f + secureRandom.nextFloat() * 0.3f;
        if (entityDistance > 0.0f && entityDistance < 1.5f) speed *= MathHelper.clamp(entityDistance / 2.0f, 0.4f, 0.9f);
        if (sincePitch < 45L) speed *= 0.7f + secureRandom.nextFloat() * 0.4f;
        else if (sincePitch > 300L) speed *= 1.1f + secureRandom.nextFloat() * 0.4f;
        if (angle > 120.0f) speed *= 1.3f;
        else if (angle < 15.0f) speed *= 0.5f + secureRandom.nextFloat() * 0.4f;
        if (lastSpeed > 0.0f && Math.abs(speed - lastSpeed) > 0.35f) speed = lastSpeed + (speed > lastSpeed ? 0.35f : -0.35f);
        lastSpeed = MathHelper.clamp(speed, 0.15f, 1.8f);
        return lastSpeed;
    }

    public float calculateHVHSpeed(float angle, Entity entity) {
        long sincePitch = System.currentTimeMillis() - lastPitchTime;
        float speed;
        switch (currentPhase) {
            case 0 -> speed = 0.6f + secureRandom.nextFloat() * 0.4f;
            case 1 -> speed = 0.8f + secureRandom.nextFloat() * 0.3f;
            case 2 -> speed = 0.5f + secureRandom.nextFloat() * 0.3f;
            case 3 -> speed = 0.7f + secureRandom.nextFloat() * 0.5f;
            case 4 -> speed = 0.4f + secureRandom.nextFloat() * 0.4f;
            case 5 -> speed = 0.9f + secureRandom.nextFloat() * 0.2f;
            default -> speed = 0.7f + secureRandom.nextFloat() * 0.4f;
        }
        if (entity != null) speed *= 0.9f + secureRandom.nextFloat() * 0.3f;
        if (sincePitch < 45L) speed *= 0.7f + secureRandom.nextFloat() * 0.4f;
        else if (sincePitch > 300L) speed *= 1.1f + secureRandom.nextFloat() * 0.4f;
        if (angle > 120.0f) speed *= 1.3f;
        else if (angle < 15.0f) speed *= 0.5f + secureRandom.nextFloat() * 0.4f;
        if (lastSpeed > 0.0f && Math.abs(speed - lastSpeed) > 0.35f) speed = lastSpeed + (speed > lastSpeed ? 0.35f : -0.35f);
        lastSpeed = MathHelper.clamp(speed, 0.15f, 1.8f);
        return lastSpeed;
    }

    public float getAverageSpeed() {
        float total = 0.0f;
        int count = 0;
        for (float speed : speedHistory) {
            if (speed > 0.0f) {
                total += speed;
                count++;
            }
        }
        return count > 0 ? total / count : lastSpeed;
    }

    public List<Entity> getSortedEntities(Iterable<Entity> entities) {
        ArrayList<Entity> sorted = new ArrayList<>(StreamSupport.stream(entities.spliterator(), false).toList());
        if (sorting.get()) {
            sorted.sort((first, second) -> Double.compare(
                    getAngleDifference(mc.player.getYaw(), getRotations(second)[0]),
                    getAngleDifference(mc.player.getYaw(), getRotations(first)[0])));
        }
        return sorted;
    }

    public float applyHumanPitch(float desiredPitch, float speed, boolean active) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastPitchTime;
        lastPitchTime = now;
        if (elapsed > 100L) elapsed = 16L;
        if (elapsed < 1L) elapsed = 1L;
        float desiredVelocity = (desiredPitch - mc.player.getPitch()) * (speed / 100.0f) * 0.12f;
        float acceleration = (desiredVelocity - lastPitchVelocity) * 0.08f;
        lastPitchVelocity += acceleration * (elapsed / 16.0f);
        float wave = (float) (Math.sin(now * 0.01) * 0.25 + Math.cos(now * 0.017) * 0.15);
        float interpolation = 0.1f + Math.abs(lastPitchVelocity) * 0.05f;
        smoothPitchOffset += (lastPitchVelocity + (wave += (Math.random() - 0.5f) * 0.3f) - smoothPitchOffset) * interpolation;
        smoothPitchOffset = MathHelper.clamp(smoothPitchOffset, -6.0f, 6.0f);
        return smoothPitchOffset;
    }

    public float applyHVHPitch(float pitchDelta, float rawPitchDelta, boolean active) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastPitchTime;
        lastPitchTime = now;
        if (elapsed > 100L) elapsed = 16L;
        if (elapsed < 1L) elapsed = 1L;
        float desiredVelocity = pitchDelta * (active ? 0.15f : 0.08f);
        float acceleration = (desiredVelocity - pitchVelocity) * 0.08f;
        pitchVelocity += acceleration * (elapsed / 16.0f);
        float wave = (float) (Math.sin(now * 0.01) * 0.3 + Math.cos(now * 0.023) * 0.2);
        float interpolation = active ? 0.12f : 0.06f;
        smoothPitchOffset += (pitchVelocity + (wave += (secureRandom.nextFloat() - 0.5f) * 0.4f) - smoothPitchOffset)
                * (interpolation + Math.abs(pitchVelocity) * 0.05f);
        smoothPitchOffset = MathHelper.clamp(smoothPitchOffset, active ? -8.0f : -3.0f, active ? 8.0f : 3.0f);
        return smoothPitchOffset;
    }

    private Angles applyCakeRotation(Angles base, float yawDelta, float pitchDelta, float factor, float yawOffset, float pitchOffset) {
        float yawLerp;
        float pitchLerp;
        switch (currentPhase) {
            case 0 -> {
                yawLerp = factor + secureRandom.nextFloat() * 0.2f - 0.1f;
                pitchLerp = yawLerp;
            }
            case 1 -> {
                yawLerp = factor * (1.2f + secureRandom.nextFloat() * 0.3f);
                pitchLerp = yawLerp;
            }
            case 2 -> {
                yawLerp = factor * (0.7f + secureRandom.nextFloat() * 0.3f);
                pitchLerp = yawLerp;
            }
            case 3 -> {
                yawLerp = factor * (0.9f + secureRandom.nextFloat() * 0.3f);
                pitchLerp = factor * (1.1f + secureRandom.nextFloat() * 0.2f);
            }
            case 4 -> {
                yawLerp = (float) (factor * (0.8 + Math.sin(System.currentTimeMillis() * 0.01) * 0.2));
                pitchLerp = yawLerp;
            }
            default -> {
                yawLerp = factor * (0.8f + secureRandom.nextFloat() * 0.4f);
                pitchLerp = yawLerp;
            }
        }
        return new Angles(
                MathHelper.lerp(yawLerp, base.yaw, base.yaw + yawDelta) + yawOffset,
                MathHelper.lerp(pitchLerp, base.pitch, base.pitch + pitchDelta) + pitchOffset);
    }

    private Angles applyHVHRotation(Angles base, float yawDelta, float pitchDelta, float factor) {
        float yawLerp;
        switch (currentPhase) {
            case 0 -> yawLerp = factor + secureRandom.nextFloat() * 0.2f - 0.1f;
            case 1 -> yawLerp = factor * (1.2f + secureRandom.nextFloat() * 0.3f);
            case 2 -> yawLerp = factor * (0.7f + secureRandom.nextFloat() * 0.3f);
            case 3 -> yawLerp = factor * (0.9f + secureRandom.nextFloat() * 0.3f);
            case 4 -> yawLerp = (float) (factor * (0.8 + Math.sin(System.currentTimeMillis() * 0.01) * 0.2));
            default -> yawLerp = factor * (0.8f + secureRandom.nextFloat() * 0.4f);
        }
        return new Angles(MathHelper.lerp(yawLerp, base.yaw, base.yaw + yawDelta), MathHelper.clamp(base.pitch + pitchDelta, -90.0f, 90.0f));
    }

    public float getClampFactor(float angle) {
        if (angle < 20.0f) return 0.95f + secureRandom.nextFloat() * 0.1f;
        return angle > 90.0f ? 0.8f + secureRandom.nextFloat() * 0.3f : 0.85f + secureRandom.nextFloat() * 0.25f;
    }

    public double getPhaseBonus(double base) {
        double progress = phaseProgress;
        double factor = phaseParams[currentPhase * 3 + 1];
        double secondary = phaseParams[currentPhase * 3 + 2];
        return switch (currentPhase) {
            case 0 -> (progress - 0.5) * base * factor * 0.5;
            case 1 -> Math.sin(progress * Math.PI * 2.0) * base * secondary * 0.4;
            case 2 -> (Math.exp(progress * 2.0) / Math.exp(2.0) - 0.5) * base * factor * 0.45;
            case 3 -> (2.0 * Math.abs(2.0 * (progress - Math.floor(progress + 0.5))) - 1.0) * base * secondary * 0.35;
            case 4 -> (Math.sin(progress * Math.PI * 3.0) * 0.5 + Math.cos(progress * Math.PI * 5.0) * 0.3) * base * factor * 0.5;
            default -> 0.0;
        };
    }

    public double sinBasedRandom(long seed, double min, double max) {
        double value = Math.abs(Math.sin(seed * 0.001)) % 1.0;
        return min + value * (max - min);
    }

    public static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    public static float getAngleDifference(float first, float second) {
        float difference = Math.abs(second - first) % 360.0f;
        return difference > 180.0f ? 360.0f - difference : difference;
    }

    public static float[] calculateAngles(double sourceX, double sourceY, double sourceZ,
                                          double destinationX, double destinationY, double destinationZ) {
        double dx = destinationX - sourceX;
        double dy = destinationY - sourceY;
        double dz = destinationZ - sourceZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0);
        float pitch = (float) (-(Math.atan2(dy, horizontal) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    private record Angles(float yaw, float pitch) {}
}
