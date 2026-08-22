package ru.levin.modules.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.input.EventKeyBoard;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.player.EventSprint;
import ru.levin.manager.Manager;
import ru.levin.mixin.iface.ClientPlayerEntityAccessor;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.movement.ElytraTarget;
import ru.levin.modules.render.littlePet.GhostWolfEntity;
import ru.levin.modules.setting.BindBooleanSetting;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.math.MathUtil;
import ru.levin.util.math.RayTraceUtil;
import ru.levin.util.move.MoveUtil;
import ru.levin.util.player.AuraUtil;
import ru.levin.util.player.InventoryUtil;
import ru.levin.util.vector.VectorUtil;

import java.util.*;

import static net.minecraft.util.Hand.MAIN_HAND;

@SuppressWarnings("All")
@FunctionAnnotation(name = "AttackAura", keywords = {"Пиздить","Хуячить","KillAura"}, desc = "Ебашит бошки всем вокруг", type = Type.Combat)
public class AttackAura extends Function {

    protected final ModeSetting mode = new ModeSetting("Мод",
            "ReallyWorld",
            "ReallyWorld",
            "HollyWorld",
            "FunTime",
            "SpookyTime",
            "AresMIne",
            "testspooky",
            "SlothAC",
            "LonyGrief",
            "ФанТайм",
            "ФанТайм ФОВ",
            "Легит"
    );

    private final MultiSetting targets = new MultiSetting(
            "Цели",
            Arrays.asList("Игроки", "Голые", "Мобы", "Монстры"),
            new String[]{"Игроки", "Голые", "Друзья", "Мобы", "Монстры", "Жители"}
    );

    private final ModeSetting sort = new ModeSetting("Сортировать",
            "По здоровью",
            "По здоровью",
            "По дистанции",
            "По броне"
    );

    private final MultiSetting setting = new MultiSetting(
            "Настройки",
            Arrays.asList("Только критами", "Ломать щит", "Отжим щита"),
            new String[]{"Только критами", "Ломать щит", "Отжим щита"}
    );

    private final SliderSetting distance = new SliderSetting("Радиус атаки", 3.0f, 1.8f, 6f, 0.1f);
    private final SliderSetting rotateDistance = new SliderSetting("Радиус обнаружения", 5f, 0.0f, 10f, 0.1f);

    private final SliderSetting elytraDistance = new SliderSetting("Радиус на элитрах", 40f, 0f, 80f, 1f);
    private final BindBooleanSetting onlySpaceCritical = new BindBooleanSetting("Только с пробелом", false, () -> setting.get("Только критами"));
    private final BooleanSetting noAttackIfEat = new BooleanSetting("Не бить если ешь", false);
    private final BooleanSetting raycast = new BooleanSetting("Проверять наведение", false);
    private final BooleanSetting noAttackThroughWalls = new BooleanSetting("Не бить через стены", true);

    public final BooleanSetting correction = new BooleanSetting("Коррекция", true);
    public final ModeSetting correctionType = new ModeSetting(() -> correction.get(), "Тип коррекции", "Free", "Free", "Focus");
    private final ModeSetting sprintreset = new ModeSetting("Тип спринта", "Rage", "Rage", "Legit", "None");

    public LivingEntity target = null;
    private long cpsLimit = 0L;
    private long lastHitMs = 0L;
    private int preSprintTicks = 0;

    // State copied from HolyClient FocusedAngle: pitch acceleration persists
    // while the same target is being tracked and resets at the neutral state.
    private float focusedPitchAcceleration = 1.0f;

    /**
     * Active rotation implementation. Imported Shade modules override this
     * instead of relying on the legacy AttackAura mode selector.
     */
    protected String getRotationMode() {
        return mode.get();
    }

    protected boolean isRotationMode(String value) {
        String active = getRotationMode();
        if (value.equals("SpookyTime")) return active.equals("SpookyTime") || active.equals("testspooky");
        if (value.equals("FunTime")) return active.equals("FunTime");
        if (value.equals("HollyWorld")) return active.equals("HollyWorld");
        return value.equals(active);
    }

    public AttackAura() {
        addSettings(
                mode,
                targets,
                sort,
                setting,
                distance,
                rotateDistance,
                elytraDistance,
                correction,
                correctionType,
                sprintreset,
                onlySpaceCritical,
                noAttackIfEat,
                raycast,
                noAttackThroughWalls
        );
    }

    @Override
    public void onEvent(Event event) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.isDead()) {
            target = null;
            preSprintTicks = 0;
            return;
        }

        if (event instanceof EventKeyBoard e) {
            if (correction.get() && correctionType.is("Free")) {
                MoveUtil.fixMovement(e, Manager.FUNCTION_MANAGER.autoPotion.isActivePotion ? Manager.ROTATION.getPitch() : Manager.ROTATION.getYaw());
            }
        }

        if (event instanceof EventSprint sprint) {
            if (sprintreset.is("Legit")) {
                if (canAttack() && target != null  && player.isSprinting()) {
                    sprint.setSprinting(false);
                }
            }
        }

        if (event instanceof EventUpdate) {
            if (target == null || !isValidTarget(target)) {
                target = findTarget();
            }

            if (target == null) {
                Manager.ROTATION.set(player.getYaw(), player.getPitch());
                lastHitMs = 0L;
                shakeStartTime = 0L;
                focusedPitchAcceleration = 1.0f;
                cpsLimit = System.currentTimeMillis();
                return;
            }

            handleAttackAndRotation(target);
        }

        if (event instanceof EventMotion motion) {
            motion.setYaw(Manager.ROTATION.getYaw());
            motion.setPitch(Manager.ROTATION.getPitch());
        }
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        if (mc.player != null) {
            // Never start a combat rotation from the controller's default 0/0 state.
            Manager.ROTATION.set(mc.player.getYaw(), mc.player.getPitch());
        }
        lastHitMs = 0L;
        shakeStartTime = 0L;
        testSpookyTarget = null;
        testSpookyTargetSwitchTime = 0L;
        testSpookyLastAttackTime = 0L;
        testSpookyNextAttackTime = 0L;
        testSpookyAim.reset();
        focusedPitchAcceleration = 1.0f;
        Arrays.fill(deltaPitchHistory, mc.player != null ? mc.player.getPitch() : 0f);
    }

    @Override
    protected void onDisable() {
        if (target != null && isValidTarget(target)) {
            String modeName = getRotationMode();
            if (modeName.equals("FunTime")
                    || modeName.equals("HollyWorld")
                    || modeName.equals("ReallyWorld")) {
                Manager.ROTATION.smoothReturn(350);
            } else {
                Manager.ROTATION.set(mc.player.getYaw(), mc.player.getPitch());
            }
        }

        target = null;
        testSpookyTarget = null;
        testSpookyAim.reset();
        focusedPitchAcceleration = 1.0f;
        testSpookyNextAttackTime = 0L;
        cpsLimit = System.currentTimeMillis();
        super.onDisable();
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isAlive() || entity == mc.player) return false;

        double dist = AuraUtil.getDistance(entity);
        double attackRange = distance.get().doubleValue();
        double detectRange = mc.player.isGliding() ? elytraDistance.get().doubleValue() : rotateDistance.get().doubleValue();

        if (dist > attackRange && (detectRange <= 0 || dist > detectRange)) return false;
        if (Manager.FUNCTION_MANAGER.antiBot.check(entity)) return false;

        if (entity instanceof PlayerEntity) {
            if (!targets.get("Игроки")) return false;
            if (!targets.get("Друзья") && Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) return false;
        } else if (entity instanceof VillagerEntity && !targets.get("Жители")) return false;
        else if (entity instanceof MobEntity || entity instanceof AnimalEntity) {
            if (!targets.get("Мобы")) return false;
        } else if (entity instanceof Monster && !targets.get("Монстры")) return false;

        if (entity instanceof ArmorStandEntity) return false;
        if (Manager.FUNCTION_MANAGER.littleSnickers.state && (entity instanceof GhostWolfEntity)) return false;

        return true;
    }

    private LivingEntity findTarget() {
        List<LivingEntity> list = new ArrayList<>();
        for (Entity e : Manager.SYNC_MANAGER.getEntities()) {
            if (e instanceof LivingEntity le && isValidTarget(le)) list.add(le);
        }
        if (list.isEmpty()) return null;

        switch (sort.get()) {
            case "По здоровью":
                list.sort(Comparator.comparing(LivingEntity::getHealth));
                break;
            case "По дистанции":
                list.sort(Comparator.comparingDouble(mc.player::distanceTo));
                break;
            case "По броне":
                list.sort(Comparator.comparingDouble(AuraUtil::getArmor));
                break;
            default:
                break;
        }
        return list.get(0);
    }

    private final Random random = new Random();


    private long shakeStartTime = 0L;
    private final float[] deltaPitchHistory = new float[30];
    private final TestSpookyRotation.SmoothAim testSpookyAim =
            new TestSpookyRotation.SmoothAim(TestSpookyRotation.AimProfile.human());

    private void handleAttackAndRotation(LivingEntity t) {
        float currYaw = Manager.ROTATION.getYaw();
        float currPitch = Manager.ROTATION.getPitch();

        boolean canAttackNow = shouldAttack(t);
        boolean passRay = !raycast.get() || RayTraceUtil.getMouseOver(t, currYaw, currPitch, distance.get().floatValue()) == t;
        boolean noPotion = !Manager.FUNCTION_MANAGER.autoPotion.isActivePotion;

        if (handleElytraRotation(t)) {
            if (canAttackNow && passRay && noPotion) attackTarget(mc.player);
            return;
        }

        if (isRotationMode("SlothAC")) {
            slothAcRotation(t, canAttackNow, noPotion);
            return;
        }

        if (isRotationMode("ФанТайм") || isRotationMode("ФанТайм ФОВ") || isRotationMode("Легит")) {
            deltaRotation(t, canAttackNow, passRay, noPotion);
            return;
        }

        if (isRotationMode("SpookyTime")) {
            testSpookyRotation(t, canAttackNow, passRay, noPotion);
            return;
        }

        if (isRotationMode("LonyGrief")) {
            Vec3d tp = predictPos(t);
            double yawToTarget = Math.toDegrees(Math.atan2(tp.z - mc.player.getZ(), tp.x - mc.player.getX())) - 90.0;
            double yawDiff = Math.abs(MathHelper.wrapDegrees((float) yawToTarget - currYaw));
            if (yawDiff <= 180 && canAttackNow && passRay && noPotion) attackTarget(mc.player);
            Manager.ROTATION.set(mc.player.getYaw(), mc.player.getPitch());
            return;
        }

        if (isRotationMode("FunTime")) {
           if (System.currentTimeMillis() - lastHitMs < 450) {
               funtime(t);

           } else {
               long currentTime = System.currentTimeMillis();
               if (shakeStartTime == 0L)
                   shakeStartTime = currentTime;
               float elapsedSec = (currentTime - shakeStartTime) / 1000f;

               double angle = 2 * Math.PI * 2.4 * elapsedSec;
               float yawOffset = (float) Math.sin(angle) * 24f;

               double angle2 = 2 * Math.PI * 0.08f * elapsedSec;
               double[] options = {5.0, 5.5, 5.8, 6.0};
               double randAmplitude = options[(int)(Math.random() * options.length)];
               float yawOffset2 = (float) ((float) Math.sin(angle2) * randAmplitude);


               float finalYaw = mc.player.getYaw() + yawOffset + yawOffset2;
               float finalPitch = 0.0f + yawOffset2;
               Manager.ROTATION.setSmooth(finalYaw, finalPitch, 1.0f, 20f, 10f, true);
           }

            boolean aligned = !raycast.get()
                    || RayTraceUtil.getMouseOver(t, Manager.ROTATION.getYaw(), Manager.ROTATION.getPitch(), distance.get().floatValue()) == t;
            if (canAttackNow && canAttack() && aligned && noPotion) {
                attackTarget(mc.player);
                lastHitMs = System.currentTimeMillis();
            }
            return;
        }

        if (isRotationMode("AresMIne")) {
            focusedRotation(t, canAttackNow, noPotion);
            return;
        }

        if (isRotationMode("HollyWorld")) {
            boolean attackReady = canAttackNow && canAttack() && noPotion;
            hollyworld(t, attackReady);
            boolean aligned = !raycast.get()
                    || RayTraceUtil.getMouseOver(t, Manager.ROTATION.getYaw(), Manager.ROTATION.getPitch(), distance.get().floatValue()) == t;
            if (attackReady && aligned) {
                attackTarget(mc.player);
            }
            return;
        }

        if (canAttackNow && passRay && noPotion) {
            attackTarget(mc.player);
        }
        setRotation(t, true);
    }
    private void deltaRotation(LivingEntity entity, boolean canAttackNow, boolean passRay, boolean noPotion) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = entity.getBoundingBox().getCenter();
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        float yawToTarget = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0d);
        float pitchToTarget = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), -90f, 90f);

        System.arraycopy(deltaPitchHistory, 0, deltaPitchHistory, 1, deltaPitchHistory.length - 1);
        deltaPitchHistory[0] = pitchToTarget;

        float time = mc.player.age + mc.getRenderTickCounter().getTickDelta(false);
        boolean legit = isRotationMode("Легит");
        float smoothYaw;
        float smoothPitch;
        float finalYaw;
        float finalPitch;

        if (legit) {
            float wave = (float) (((Math.sin(time * 0.31f) * 0.5d)
                    + (Math.sin(time * 1.7f + 2.6f) * 0.2d)) * 2.0d);
            smoothYaw = wave;
            smoothPitch = wave;
            finalYaw = MathUtil.interpolateFloat(mc.player.getYaw(), yawToTarget, 0.3f);
            finalPitch = MathUtil.interpolateFloat(mc.player.getPitch(), pitchToTarget, 0.2f);
            if (isRotationMode("ФанТайм ФОВ")) finalPitch = mc.player.getPitch();
        } else {
            smoothYaw = (float) (Math.sin(time * 0.4f) * 3.0d + Math.sin(time * 0.95f + 1.4d) * 2.0d);
            smoothPitch = (float) (Math.cos(time * 0.5f + 0.7d) * 0.5d
                    + Math.cos(time * 0.78f + 3.1d) * 1.5d);
            finalPitch = MathUtil.interpolateFloat(mc.player.getPitch(),
                    deltaPitchHistory[MathHelper.clamp(canAttackNow ? 0 : 10, 0, 29)] + smoothPitch * 1.5f,
                    0.35f);
            finalYaw = MathUtil.interpolateFloat(mc.player.getYaw(), yawToTarget + smoothYaw, 0.25f);
            if (isRotationMode("ФанТайм ФОВ")) finalPitch = mc.player.getPitch();
        }

        if (!canAttackNow && ((int) time % 2 == 0)) finalYaw = mc.player.getYaw();
        float outputPitch = MathHelper.clamp(finalPitch + smoothPitch, -90f, 90f);
        Manager.ROTATION.setSmooth(finalYaw + smoothYaw, outputPitch,
                legit ? 0.35f : 0.55f, legit ? 180f : 220f, legit ? 1 : 1, true);

        boolean aligned = !raycast.get()
                || RayTraceUtil.getMouseOver(entity, Manager.ROTATION.getYaw(), Manager.ROTATION.getPitch(), distance.get().floatValue()) == entity;
        if (canAttackNow && passRay && aligned && canAttack() && noPotion) attackTarget(mc.player);
    }

    private static final long TEST_SPOOKY_TARGET_SWITCH_DELAY_MS = 150L;
    private LivingEntity testSpookyTarget;
    private long testSpookyTargetSwitchTime = 0L;
    private long testSpookyLastAttackTime = 0L;
    private long testSpookyNextAttackTime = 0L;

    private void testSpookyRotation(LivingEntity entity, boolean canAttackNow, boolean passRay, boolean noPotion) {
        long now = System.currentTimeMillis();

        if (testSpookyTarget != entity) {
            if (testSpookyTarget != null && now - testSpookyTargetSwitchTime < TEST_SPOOKY_TARGET_SWITCH_DELAY_MS) {
                return;
            }
            testSpookyTarget = entity;
            testSpookyTargetSwitchTime = now;
            testSpookyAim.reset();
            testSpookyNextAttackTime = now + 80L;
        }

        if (testSpookyTarget == null || testSpookyTarget != entity) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d point = testSpookyTarget.getBoundingBox().getCenter();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;

        float targetYaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
        float targetPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), -89.9f, 89.9f);

        // Feed the algorithm with the same angles that EventMotion will send.
        // Reading mc.player here can lag one motion event behind and causes the
        // controller to make a second turn away from the target.
        TestSpookyRotation.Vector2D currentAngles = new TestSpookyRotation.Vector2D(
                Manager.ROTATION.getYaw(), Manager.ROTATION.getPitch());
        TestSpookyRotation.Vector2D targetAngles = new TestSpookyRotation.Vector2D(targetYaw, targetPitch);
        TestSpookyRotation.Vector2D newAngles = testSpookyAim.update(currentAngles, targetAngles);

        Manager.ROTATION.set((float) newAngles.x, (float) newAngles.y);

        boolean aligned = !raycast.get()
                || RayTraceUtil.getMouseOver(testSpookyTarget, Manager.ROTATION.getYaw(),
                Manager.ROTATION.getPitch(), distance.get().floatValue()) == testSpookyTarget;
        TestSpookyRotation.Vector2D remaining = targetAngles.sub(newAngles);
        remaining.x = normalizeTestSpookyAngle(remaining.x);
        remaining.y = normalizeTestSpookyAngle(remaining.y);
        if (canAttackNow && aligned && Math.abs(remaining.x) < 3.0 && Math.abs(remaining.y) < 3.0
                && noPotion && canAttack() && now >= testSpookyNextAttackTime) {
            attackTarget(mc.player);
            testSpookyLastAttackTime = now;
            testSpookyNextAttackTime = now + TestSpookyRotation.NoiseGenerator.randomDelay(100, 180);
        }
    }

    private double normalizeTestSpookyAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    private void slothAcRotation(LivingEntity entity, boolean canAttackNow, boolean noPotion) {
        Vec3d targetPos = predictPos(entity);
        Vec3d eyePos = mc.player.getEyePos();
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y + entity.getEyeHeight(entity.getPose()) / 2.0 - eyePos.y;
        double dz = targetPos.z - eyePos.z;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        float yawDelta = MathHelper.wrapDegrees(targetYaw - Manager.ROTATION.getYaw());
        float pitchDelta = targetPitch - Manager.ROTATION.getPitch();
        float rotationDifference = (float) Math.hypot(yawDelta, pitchDelta);

        boolean attackWindow = canAttackNow && canAttack();
        boolean rayPassed = !raycast.get()
                || RayTraceUtil.getMouseOver(entity, Manager.ROTATION.getYaw(), Manager.ROTATION.getPitch(), distance.get().floatValue()) == entity;

        float yawOffset = attackWindow ? 0.0f
                : (float) (randomLerp(1.0f, 40.0f) * Math.sin(System.currentTimeMillis() / 60.0D));
        float pitchOffset = attackWindow ? 0.0f
                : (float) (randomLerp(30.0f, 180.0f) * Math.cos(System.currentTimeMillis() / 40.0D));
        float speed = attackWindow ? 1.0f : (canAttack() ? 0.5f : 0.3f);
        if (attackWindow && !rayPassed) speed = 1.0f;

        float lineYaw = rotationDifference < 0.001f ? 0.0f
                : Math.abs(yawDelta / rotationDifference) * 180.0f;
        float linePitch = Math.abs(pitchDelta) * 180.0f;
        float moveYaw = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);
        float factor = MathHelper.clamp(randomLerp(speed, speed + 0.2f), 0.0f, 1.0f);

        float nextYaw = Manager.ROTATION.getYaw() + moveYaw * factor + yawOffset;
        float nextPitch = MathHelper.clamp(Manager.ROTATION.getPitch() + movePitch * factor + pitchOffset, -89.9f, 89.9f);
        Manager.ROTATION.set(nextYaw, nextPitch);

        boolean aligned = !raycast.get()
                || RayTraceUtil.getMouseOver(entity, nextYaw, nextPitch, distance.get().floatValue()) == entity;
        if (canAttackNow && canAttack() && aligned && noPotion) {
            attackTarget(mc.player);
        }
    }

    private float randomLerp(float min, float max) {
        return MathHelper.lerp(random.nextFloat(), min, max);
    }

    private void setRotation(LivingEntity entity, boolean applyGcd) {
        Vec3d tp = predictPos(entity);
        double dx = tp.x - mc.player.getX();
        double dy = (tp.y + entity.getEyeHeight(entity.getPose()) / 2.0) - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = tp.z - mc.player.getZ();

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        Manager.ROTATION.setSmooth(yaw, pitch, 1.2f, 180f, 15f, applyGcd);
    }

    private boolean swingSideRight = false;
    private float jitterYaw = 0f, jitterYawTarget = 0f, jitterYawSpeed = 0f;
    private float microJitter = 0f;
    private float swayPhase = 0f;
    private float swaySpeed = 0.04f;
    private float swayAmplitude = 2.5f;
    private long lastSwitch = 0L;
    private long lastBreathChange = 0L;


    private void funtime(LivingEntity entity) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d base = entity.getPos();

        float[] points = new float[]{0.82f, 0.67f, 0.43f, 0.27f};
        float mul = points[(int) (System.currentTimeMillis() / 180 % points.length)];
        Vec3d targetPos = new Vec3d(base.x, base.y + entity.getHeight() * mul, base.z);

        double halfWidth = entity.getWidth() / 2.0;
        double sideOffset = swingSideRight ? halfWidth * 1.2f : -halfWidth * 1;

        double yawToEntity = Math.atan2(entity.getZ() - mc.player.getZ(), entity.getX() - mc.player.getX());
        double offsetX = Math.cos(yawToEntity + Math.PI / 2) * sideOffset;
        double offsetZ = Math.sin(yawToEntity + Math.PI / 2) * sideOffset;
        targetPos = targetPos.add(offsetX, 0, offsetZ);

        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        float baseYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float basePitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        long now = System.currentTimeMillis();

        if (now - lastSwitch > 200 + random.nextInt(250)) {
            lastSwitch = now;
            swingSideRight = !swingSideRight;

            float distanceFactor = (float) MathHelper.clamp(dist / 6.0f, 0.4f, 1.0f);
            float maxDeviation = 4.0f * distanceFactor;

            jitterYawTarget = (swingSideRight ? maxDeviation : -maxDeviation) + (float) (random.nextGaussian() * 0.6f);
        }

        float diff = jitterYawTarget - jitterYaw;
        jitterYawSpeed += diff * 0.05f;
        jitterYawSpeed *= 0.88f;
        jitterYaw += jitterYawSpeed;
        jitterYaw *= 0.985f;

        if (now - lastBreathChange > 2000 + random.nextInt(1500)) {
            lastBreathChange = now;
            swaySpeed = 0.035f + random.nextFloat() * 0.02f;
            swayAmplitude = 2.0f + random.nextFloat() * 1.2f;
        }

        swayPhase += swaySpeed;
        float sway = (float) Math.sin(swayPhase) * swayAmplitude;
        float totalYawOffset = (float) MathHelper.clamp(jitterYaw + sway, -halfWidth * 8.5f, halfWidth * 8.5f);
        microJitter += (random.nextFloat() - 0.5f) * 0.25f;
        microJitter *= 0.85f;

        float finalYaw = baseYaw + totalYawOffset + microJitter;
        float finalPitch = basePitch + (float) Math.sin(swayPhase * 0.8f) * 0.5f;


        Manager.ROTATION.setSmooth(finalYaw, finalPitch, 1.1f, 180f, 15f, true);
    }



    /**
     * HolyClient FocusedAngle port. The only adaptation is the LupaWare
     * Manager.ROTATION container and MathUtil/GCDUtil equivalents.
     */
    private void focusedRotation(LivingEntity entity, boolean canAttackNow, boolean noPotion) {
        float currentYaw = Manager.ROTATION.getYaw();
        float currentPitch = Manager.ROTATION.getPitch();
        Vec3d eye = mc.player.getEyePos();
        Vec3d aimPoint = MathUtil.getClosestVec(eye, entity);
        double dx = aimPoint.x - eye.x;
        double dy = aimPoint.y - eye.y;
        double dz = aimPoint.z - eye.z;
        double horizontal = Math.hypot(dx, dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;

        float maxYawStep = MathUtil.random(65.0f, 75.0f);
        float maxPitchStep;
        if (mc.player.isGliding()) {
            maxPitchStep = 180.0f;
        } else {
            focusedPitchAcceleration = Math.abs(pitchDelta) < 1.0f
                    ? 1.0f
                    : Math.min(focusedPitchAcceleration * 1.45f, 10.0f);
            maxPitchStep = focusedPitchAcceleration + MathUtil.random(-1.0f, 1.0f);
        }

        float yawStep = MathHelper.clamp(yawDelta, -maxYawStep, maxYawStep);
        float pitchStep = MathHelper.clamp(pitchDelta, -maxPitchStep, maxPitchStep);
        float outputYaw = currentYaw + yawStep;
        float outputPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f);

        // Equivalent to HolyClient Angle.adjustSensitivity(): round relative
        // to the current server angle using the vanilla mouse GCD.
        float gcd = ru.levin.util.player.GCDUtil.getGCDValue();
        if (gcd > 0.0f) {
            outputYaw = currentYaw + Math.round(MathHelper.wrapDegrees(outputYaw - currentYaw) / gcd) * gcd;
            outputPitch = currentPitch + Math.round((outputPitch - currentPitch) / gcd) * gcd;
            outputPitch = MathHelper.clamp(outputPitch, -90.0f, 90.0f);
        }
        Manager.ROTATION.set(outputYaw, outputPitch);

        boolean aligned = !raycast.get()
                || RayTraceUtil.getMouseOver(entity, outputYaw, outputPitch, distance.get().floatValue()) == entity;
        if (canAttackNow && canAttack() && aligned && noPotion) {
            attackTarget(mc.player);
        }
    }

    private void hollyworld(LivingEntity entity, boolean force) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d base = entity.getPos();
        float[] points = new float[]{0.85f, 0.65f, 0.35f, 0.25f};
        float mul = points[(int) (System.nanoTime() % points.length)];
        Vec3d aim = new Vec3d(base.x, base.y + entity.getHeight() * mul, base.z);

        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;

        double hd = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, hd));

        if (force) {
            Manager.ROTATION.set(yaw, MathHelper.clamp(pitch, -89.9f, 89.9f));
        } else {
            Manager.ROTATION.setSmooth(yaw, pitch, 0.25f, 45f, 12f, true);
        }
    }

    private boolean handleElytraRotation(LivingEntity t) {
        ElytraTarget ely = Manager.FUNCTION_MANAGER.elytraTarget;
        if (ely.state && mc.player.isGliding()) {
            if (ely.mode.is("Продвинутый")) ely.overtakingElytra(t, false);
            else ely.targetDefault(t, false);
            return true;
        }
        return false;
    }

    public void attackTarget(PlayerEntity player) {
        boolean sprintStop = false;
        boolean canStartSprint = mc.player.input.movementForward > 0
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.isGliding()
                && !mc.player.isUsingItem()
                && !mc.player.horizontalCollision
                && mc.player.getHungerManager().getFoodLevel() > 6
                && !mc.player.isSneaking();

        if (setting.get("Отжим щита") && mc.player.isBlocking()) {
            mc.interactionManager.stopUsingItem(mc.player);
        }

        if (sprintreset.is("Legit")) {
            if (mc.player.isSprinting() || canAttack()) {
                if (mc.player.isSprinting()) return;
            }
        }

        if (sprintreset.is("Rage")) {
            if (((ClientPlayerEntityAccessor) mc.player).getLastSprinting()) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                mc.player.setSprinting(false);
                sprintStop = true;
            }
        }

        // The supplied KillAura owns the testspooky inter-hit delay.
        cpsLimit = System.currentTimeMillis() + (isRotationMode("SpookyTime") ? 0L : 500L);

        mc.interactionManager.attackEntity(player, target);
        mc.player.swingHand(MAIN_HAND);

        ElytraTarget elytraTarget = Manager.FUNCTION_MANAGER.elytraTarget;
        if (elytraTarget.mode.is("Продвинутый")) {
            elytraTarget.trueFireWork = true;
            if (elytraTarget.prefer.get()) elytraTarget.nextPhase(target);
        }

        if (setting.get("Ломать щит")) shieldBreaker(false);

        if (sprintreset.is("Rage") && sprintStop && canStartSprint) {
            if (((ClientPlayerEntityAccessor) mc.player).getLastSprinting()) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                mc.player.setSprinting(true);
            }
        }
    }

    private boolean shouldAttack(LivingEntity e) {
        if (e == null || cpsLimit > System.currentTimeMillis()) return false;
        if (AuraUtil.getDistance(e) > distance.get().doubleValue()) return false;
        return canAttack();
    }

    private boolean canAttack() {
        if (noAttackIfEat.get() && mc.player.isUsingItem() && !mc.player.getActiveItem().isOf(Items.SHIELD)) return false;
        if (target != null && noAttackThroughWalls.get() && !mc.player.canSee(target)) return false;

        if (System.currentTimeMillis() < cpsLimit
                || (!(mc.player.getMainHandStack().isOf(Items.MACE))
                && mc.player.getAttackCooldownProgress(mc.getRenderTickCounter().getTickDelta(true)) < 0.9F)) return false;

        boolean restrict = mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || mc.player.isInLava()
                || mc.player.inPowderSnow
                || mc.player.isClimbing()
                || mc.player.hasVehicle()
                || mc.player.getAbilities().flying
                || (mc.player.isInFluid() && !mc.options.jumpKey.isPressed())
                || MoveUtil.isInWeb();

        boolean needSpace = onlySpaceCritical.get()
                && mc.player.isOnGround()
                && !mc.options.jumpKey.isPressed();

        if (setting.get("Только критами") && !restrict) {
            return needSpace || (!mc.player.isOnGround() && mc.player.fallDistance > 0.0f);
        }
        return true;
    }

    private boolean shieldBreaker(boolean instant) {
        int axeSlot = InventoryUtil.getAxe().slot();
        if (axeSlot == -1) return false;
        if (!(target instanceof PlayerEntity)) return false;
        if (!((PlayerEntity) target).isUsingItem() && !instant) return false;
        if (((PlayerEntity) target).getOffHandStack().getItem() != Items.SHIELD
                && ((PlayerEntity) target).getMainHandStack().getItem() != Items.SHIELD) return false;

        if (axeSlot >= 9) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        } else {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
        }
        return true;
    }

    private Vec3d predictPos(LivingEntity e) {
        Vec3d p = e.getPos();
        var ts = Manager.FUNCTION_MANAGER.targetStrafe;
        if (ts.state && ts.predictCheck.get()) {
            float pr = ts.predict.get().floatValue();
            if (pr > 0) {
                Vec3d v = e.getVelocity();
                p = p.add(v.x * pr, v.y * pr, v.z * pr);
            }
        }
        return p;
    }
}