package ru.levin.modules.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@SuppressWarnings("All")
@FunctionAnnotation(name = "AimAssist", desc = "Плавно наводится на хитбокс цели", type = Type.Combat)
public class AimAssist extends Function {
    private final SliderSetting aimFov = new SliderSetting("FOV", 180.0, 90.0, 360.0, 1.0);
    private final SliderSetting aimRange = new SliderSetting("Дистанция", 100.0, 10.0, 210.0, 1.0);
    private final SliderSetting aimSpeed = new SliderSetting("Скорость", 5.0, 1.0, 10.0, 0.1);
    private final ModeSetting aimPoint = new ModeSetting("Хитбокс", "Голова", "Голова", "Тело", "Ноги");
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", false);
    private final BooleanSetting friendlyFire = new BooleanSetting("Дружественный огонь", false);
    private final BooleanSetting animals = new BooleanSetting("Животные", false);
    private final BooleanSetting players = new BooleanSetting("Игроки", true);
    private final BooleanSetting mobs = new BooleanSetting("Мобы", true);
    private final BooleanSetting bypassGrim = new BooleanSetting("Bypass Grim", true);
    private final BooleanSetting bypassVulkan = new BooleanSetting("Bypass Vulkan", true);
    private final BooleanSetting bypassMatrix = new BooleanSetting("Bypass Matrix", true);
    private final BooleanSetting bypassPolar = new BooleanSetting("Bypass Polar", true);
    private final BooleanSetting bypassSloth = new BooleanSetting("Bypass Sloth", true);

    private final Random random = new Random();
    private Entity currentTarget;
    private long lastTargetTime;
    private long lastMoveTime;
    private float lastYaw;
    private float lastPitch;

    public AimAssist() {
        addSettings(aimFov, aimRange, aimSpeed, aimPoint, throughWalls, friendlyFire, animals, players, mobs,
                bypassGrim, bypassVulkan, bypassMatrix, bypassPolar, bypassSloth);
    }

    @Override
    public void onEvent(Event event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || player.isDead()) {
            currentTarget = null;
            return;
        }

        if (event instanceof EventUpdate) {
            if (Manager.FUNCTION_MANAGER.attackAura.state) {
                currentTarget = null;
                return;
            }

            Entity target = getBestTarget(player);
            if (target == null) {
                currentTarget = null;
                return;
            }

            applySuperSmoothAim(player, target);
            currentTarget = target;
            lastTargetTime = System.currentTimeMillis();
        }

        if (event instanceof EventMotion motion && currentTarget != null) {
            motion.setYaw(Manager.ROTATION.getYaw());
            motion.setPitch(Manager.ROTATION.getPitch());
        }
    }

    @Override
    protected void onEnable() {
        currentTarget = null;
        lastTargetTime = 0L;
        lastMoveTime = System.currentTimeMillis();
        if (mc.player != null) {
            Manager.ROTATION.set(mc.player.getYaw(), mc.player.getPitch());
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        }
    }

    @Override
    protected void onDisable() {
        currentTarget = null;
        if (mc.player != null) {
            Manager.ROTATION.set(mc.player.getYaw(), mc.player.getPitch());
        }
    }

    private Entity getBestTarget(ClientPlayerEntity player) {
        Vec3d playerPos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);
        Entity bestEntity = null;
        double bestAngle = aimFov.get().doubleValue();

        for (Entity entity : Manager.SYNC_MANAGER.getEntities()) {
            if (!isValidTarget(player, entity)) continue;
            Vec3d targetPos = getAimPoint(entity);
            double distance = targetPos.distanceTo(playerPos);
            if (distance > aimRange.get().doubleValue()) continue;
            if (!throughWalls.get() && !player.canSee(entity)) continue;

            Vec3d delta = targetPos.subtract(playerPos);
            if (delta.lengthSquared() < 1.0E-6) continue;
            double dot = MathHelper.clamp(lookVec.dotProduct(delta.normalize()), -1.0, 1.0);
            double angle = Math.toDegrees(Math.acos(dot));
            if (angle < bestAngle) {
                bestAngle = angle;
                bestEntity = entity;
            }
        }

        if (bestEntity != null && System.currentTimeMillis() - lastTargetTime < 150
                && currentTarget != null && isValidTarget(player, currentTarget)
                && getAimPoint(currentTarget).distanceTo(playerPos) <= aimRange.get().doubleValue()) {
            return currentTarget;
        }
        return bestEntity;
    }

    private boolean isValidTarget(ClientPlayerEntity player, Entity entity) {
        if (entity == null || entity == player) return false;
        if (entity instanceof LivingEntity living && (living.isDead() || !living.isAlive())) return false;

        if (entity instanceof PlayerEntity) {
            if (!players.get()) return false;
            return friendlyFire.get() || entity != player;
        }
        if (entity instanceof AnimalEntity) {
            return animals.get();
        }
        if (entity instanceof MobEntity) {
            return mobs.get();
        }
        return true;
    }

    private Vec3d getAimPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double height = box.maxY - box.minY;
        double y;
        if (aimPoint.is("Голова")) {
            y = box.maxY - height * 0.1;
        } else if (aimPoint.is("Тело")) {
            y = box.minY + height * 0.5;
        } else {
            y = box.minY + height * 0.2;
        }
        return new Vec3d((box.minX + box.maxX) * 0.5, y, (box.minZ + box.maxZ) * 0.5);
    }

    private void applySuperSmoothAim(ClientPlayerEntity player, Entity target) {
        Vec3d delta = getAimPoint(target).subtract(player.getEyePos());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontalDistance));
        targetPitch = MathHelper.clamp(targetPitch, -15.0f, 15.0f);

        float currentYaw = Manager.ROTATION.getYaw();
        float currentPitch = Manager.ROTATION.getPitch();
        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;
        if (Math.abs(yawDelta) > aimFov.get().floatValue()) return;

        long now = System.currentTimeMillis();
        float deltaTime = Math.min(1.0f, Math.max(0.01f, (now - lastMoveTime) / 1000.0f));
        lastMoveTime = now;

        float speedMultiplier = aimSpeed.get().floatValue() / 5.0f;
        float dynamicSpeed = 8.0f * speedMultiplier;
        float distance = Math.abs(yawDelta) + Math.abs(pitchDelta);
        if (distance < 5.0f) dynamicSpeed *= 0.6f;
        else if (distance > 20.0f) dynamicSpeed *= 1.3f;

        float alpha = Math.min(1.0f, deltaTime * dynamicSpeed);
        float newYaw = currentYaw + yawDelta * alpha;
        float newPitch = currentPitch + pitchDelta * alpha;

        if (bypassGrim.get()) {
            float grimStep = Math.min(Math.abs(yawDelta) * 0.15f, 1.2f);
            if (grimStep > 0.0f) {
                newYaw = currentYaw + Math.signum(yawDelta) * grimStep;
                newPitch = currentPitch + Math.signum(pitchDelta) * grimStep * 0.7f;
            }
        }

        if (bypassVulkan.get()) {
            newYaw += (random.nextFloat() - 0.5f) * 0.08f;
            newPitch += (random.nextFloat() - 0.5f) * 0.05f;
        }

        if (bypassMatrix.get()) {
            if (Math.abs(yawDelta) < 3.0f) newYaw = currentYaw + yawDelta * 0.5f;
            if (Math.abs(pitchDelta) < 2.0f) newPitch = currentPitch + pitchDelta * 0.5f;
        }

        if (bypassPolar.get()) {
            if (lastYaw != 0.0f) {
                float yawRate = newYaw - lastYaw;
                if (Math.abs(yawRate) > 8.0f) newYaw = lastYaw + Math.signum(yawRate) * 6.5f;
            }
            lastYaw = newYaw;
            lastPitch = newPitch;
        }

        if (bypassSloth.get() && now % 2000 < 20) {
            newYaw += (random.nextFloat() - 0.5f) * 0.15f;
            newPitch += (random.nextFloat() - 0.5f) * 0.1f;
        }

        float yawChange = MathHelper.wrapDegrees(newYaw - currentYaw);
        if (Math.abs(yawChange) > 12.0f) newYaw = currentYaw + Math.signum(yawChange) * 12.0f;
        float pitchChange = newPitch - currentPitch;
        if (Math.abs(pitchChange) > 8.4f) newPitch = currentPitch + Math.signum(pitchChange) * 8.4f;

        Manager.ROTATION.set(newYaw, MathHelper.clamp(newPitch, -89.9f, 89.9f));
    }
}
