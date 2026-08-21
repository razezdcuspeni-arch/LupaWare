package ru.levin.modules.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4d;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.LupaWareTheme;
import ru.levin.util.render.Render3DUtil;
import ru.levin.util.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("All")
@FunctionAnnotation(name = "ESP", desc = "Красивые квадраты на игроках и SkeletonESP", type = Type.Render)
public class ESP extends Function {

    private final MultiSetting targets = new MultiSetting(
            "Отображать",
            Arrays.asList("Игроков", "Друзей", "Меня"),
            new String[]{"Игроков", "Друзей", "Меня", "Предметы"}
    );
    private final BooleanSetting skeletonESP = new BooleanSetting("SkeletonESP", false,
            "Рисует скелетные линии на игроках");
    private final BooleanSetting skeletonSelf = new BooleanSetting("Render Self", true,
            "Показывает скелет собственного игрока", () -> skeletonESP.get());

    public ESP() {
        addSettings(targets, skeletonESP, skeletonSelf);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventRender2D e) {
            if (mc.options.hudHidden) return;

            Matrix4f matrix = e.getDrawContext().getMatrices().peek().getPositionMatrix();

            RenderUtil.enableRender();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            List<AbstractClientPlayerEntity> players = Manager.SYNC_MANAGER.getPlayers();
            List<Entity> entities = targets.get("Предметы") ? Manager.SYNC_MANAGER.getEntities() : List.of();

            for (PlayerEntity player : players) {
                if (shouldRender(player)) {
                    drawBox(e.getDeltatick(), buffer, player, matrix);
                }
            }

            for (Entity entity : entities) {
                if (entity instanceof ItemEntity) {
                    drawBox(e.getDeltatick(), buffer, entity, matrix);
                }
            }

            RenderUtil.render3D.endBuilding(buffer);
            RenderUtil.disableRender();
            return;
        }

        if (event instanceof EventRender3D render3D) {
            renderSkeleton(render3D);
        }
    }

    private boolean shouldRender(PlayerEntity entity) {
        if (entity == mc.player) {
            if (mc.options.getPerspective() == Perspective.FIRST_PERSON) return false;
            return targets.get("Меня");
        }
        if (targets.get("Друзей") && Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) {
            return true;
        }
        return targets.get("Игроков");
    }

    public void drawBox(RenderTickCounter tick, BufferBuilder buffer, @NotNull Entity ent, Matrix4f matrix) {
        Vec3d[] corners = getVectors(tick, ent);

        Vector4d pos = null;
        for (Vec3d corner : corners) {
            Vec3d screen = RenderUtil.render3D.worldSpaceToScreenSpace(corner);
            if (screen.z <= 0 || screen.z >= 1) continue;

            if (pos == null) pos = new Vector4d(screen.x, screen.y, screen.x, screen.y);
            else {
                if (screen.x < pos.x) pos.x = screen.x;
                if (screen.y < pos.y) pos.y = screen.y;
                if (screen.x > pos.z) pos.z = screen.x;
                if (screen.y > pos.w) pos.w = screen.y;
            }
        }

        if (pos == null) return;

        double screenW = mc.getWindow().getScaledWidth();
        double screenH = mc.getWindow().getScaledHeight();
        if (pos.z < 0 || pos.x > screenW || pos.w < 0 || pos.y > screenH) return;

        float x1 = (float) pos.x;
        float y1 = (float) pos.y;
        float x2 = (float) pos.z;
        float y2 = (float) pos.w;

        int black = Color.BLACK.getRGB();

        drawRect(buffer, matrix, x1 - 1f, y1, x1 + 0.5f, y2 + 0.5f, black);
        drawRect(buffer, matrix, x1 - 1f, y1 - 0.5f, x2 + 0.5f, y1 + 1f, black);
        drawRect(buffer, matrix, x2 - 1f, y1, x2 + 0.5f, y2 + 0.5f, black);
        drawRect(buffer, matrix, x1 - 1f, y2 - 1f, x2 + 0.5f, y2 + 0.5f, black);

        int cTop = ColorUtil.getColorStyle(270);
        int cRight = ColorUtil.getColorStyle(90);
        int cBottom = ColorUtil.getColorStyle(180);
        int cLeft = ColorUtil.getColorStyle(0);

        drawRect(buffer, matrix, x1 - 0.5f, y1, x1 + 0.5f, y2, cTop, cLeft, cLeft, cTop);
        drawRect(buffer, matrix, x1, y2 - 0.5f, x2, y2, cLeft, cBottom, cBottom, cLeft);
        drawRect(buffer, matrix, x1 - 0.5f, y1, x2, y1 + 0.5f, cBottom, cRight, cRight, cBottom);
        drawRect(buffer, matrix, x2 - 0.5f, y1, x2, y2, cRight, cTop, cTop, cRight);
    }

    private void renderSkeleton(EventRender3D event) {
        if (!skeletonESP.get() || mc.player == null || mc.world == null) return;
        float tickDelta = event.getDeltatick().getTickDelta(false);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrix = event.getMatrixStack();
        int color = LupaWareTheme.GOLD;

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.lineWidth(2.0f);

        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player && !skeletonSelf.get()) continue;
            if (player.isInvisible()) continue;

            double x = MathHelper.lerp(tickDelta, player.prevX, player.getX()) - cameraPos.x;
            double y = MathHelper.lerp(tickDelta, player.prevY, player.getY()) - cameraPos.y;
            double z = MathHelper.lerp(tickDelta, player.prevZ, player.getZ()) - cameraPos.z;
            renderSkeletonPlayer(buffer, matrix, player, x, y, z, tickDelta, color);
        }
        RenderUtil.render3D.endBuilding(buffer);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private void renderSkeletonPlayer(BufferBuilder buffer, MatrixStack matrix, PlayerEntity player,
                                      double x, double y, double z, float tickDelta, int color) {
        if (player == mc.player && mc.options.getPerspective().isFirstPerson()) return;

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float pitch = MathHelper.lerp(tickDelta, player.prevPitch, player.getPitch());
        float swing = player.limbAnimator.getPos(tickDelta);
        float swingAmount = player.limbAnimator.getSpeed(tickDelta);
        float handSwing = player.getHandSwingProgress(tickDelta);

        List<Vec3d[]> bones = getBones(x, y, z, bodyYaw, headYaw, pitch, swing, swingAmount,
                handSwing, player.getHeight(), player.isGliding(), player.isSneaking());
        for (Vec3d[] bone : bones) {
            line(buffer, matrix, bone[0], bone[1], color);
        }
    }

    private void line(BufferBuilder buffer, MatrixStack matrix, Vec3d start, Vec3d end, int color) {
        Render3DUtil.vertexLine(matrix, buffer, start, end, color, color);
    }

    private List<Vec3d[]> getBones(double x, double y, double z, float bodyYaw, float headYaw, float pitch,
                                   float swing, float swingAmount, float handSwing, float height,
                                   boolean elytra, boolean sneak) {
        List<Vec3d[]> bones = new ArrayList<>();
        MatrixStack matrices = new MatrixStack();
        matrices.translate(x, y, z);

        if (sneak && !elytra) matrices.translate(0, 0.125, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

        float bodyPitch = 0;
        if (elytra) bodyPitch = 1.57f + pitch / 57.2958f;
        else if (sneak) bodyPitch = 0.5f;
        if (elytra || sneak) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(bodyPitch * 57.2958f));
        if (sneak && !elytra) matrices.translate(0, -0.13, 0);

        matrices.push();
        matrices.translate(0, height * 0.75, 0);
        Vec3d neck = getPos(matrices);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - headYaw));
        if (!elytra) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.translate(0, height * 0.15, 0);
        Vec3d head = getPos(matrices);
        matrices.pop();

        swingAmount = Math.min(swingAmount, 1.0f) * 0.5f;

        matrices.push();
        matrices.translate(0.25, 0, 0);
        Vec3d leftShoulder = getPos(matrices);
        float leftArmRot = elytra ? -0.2f : MathHelper.cos(swing * 0.6662f + (float) Math.PI) * 0.8f * swingAmount;
        if (elytra) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-5));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(leftArmRot * 57.2958f));
        matrices.translate(0, -0.25, 0);
        Vec3d leftElbow = getPos(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.max(0, leftArmRot * 15)));
        matrices.translate(0, -0.25, 0);
        Vec3d leftHand = getPos(matrices);
        matrices.pop();

        matrices.push();
        matrices.translate(-0.25, 0, 0);
        Vec3d rightShoulder = getPos(matrices);
        float rightArmRot = elytra ? -0.2f : MathHelper.cos(swing * 0.6662f) * 0.8f * swingAmount;
        if (elytra) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(5));
        if (handSwing > 0 && !elytra) {
            float swingProgress = 1.0f - handSwing;
            swingProgress *= swingProgress;
            float swingRot = MathHelper.sin(swingProgress * (float) Math.PI);
            float yawFactor = MathHelper.clamp((headYaw - bodyYaw) / 75.0f, -1.0f, 1.0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swingRot * 15 * yawFactor));
            rightArmRot -= swingRot * 0.8f;
        }
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rightArmRot * 57.2958f));
        matrices.translate(0, -0.25, 0);
        Vec3d rightElbow = getPos(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.max(0, rightArmRot * 15)));
        matrices.translate(0, -0.25, 0);
        Vec3d rightHand = getPos(matrices);
        matrices.pop();

        matrices.pop();

        matrices.push();
        matrices.translate(0, height * 0.5, 0);
        Vec3d waist = getPos(matrices);
        matrices.pop();

        matrices.push();
        matrices.translate(0, height * 0.3, 0);
        Vec3d pelvis = getPos(matrices);

        matrices.push();
        matrices.translate(0.125, 0, 0);
        Vec3d leftHip = getPos(matrices);
        float leftLegRot = elytra ? 0.1f : MathHelper.cos(swing * 0.6662f) * 0.5f * swingAmount;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(leftLegRot * 57.2958f));
        matrices.translate(0, -0.25, 0);
        Vec3d leftKnee = getPos(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(leftLegRot) * 15));
        matrices.translate(0, -0.25, 0);
        Vec3d leftFoot = getPos(matrices);
        matrices.pop();

        matrices.push();
        matrices.translate(-0.125, 0, 0);
        Vec3d rightHip = getPos(matrices);
        float rightLegRot = elytra ? 0.1f : MathHelper.cos(swing * 0.6662f + (float) Math.PI) * 0.5f * swingAmount;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rightLegRot * 57.2958f));
        matrices.translate(0, -0.25, 0);
        Vec3d rightKnee = getPos(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(rightLegRot) * 15));
        matrices.translate(0, -0.25, 0);
        Vec3d rightFoot = getPos(matrices);
        matrices.pop();

        matrices.pop();
        matrices.pop();

        bones.add(new Vec3d[]{neck, head});
        bones.add(new Vec3d[]{neck, waist});
        bones.add(new Vec3d[]{waist, pelvis});
        bones.add(new Vec3d[]{neck, leftShoulder});
        bones.add(new Vec3d[]{neck, rightShoulder});
        bones.add(new Vec3d[]{leftShoulder, leftElbow});
        bones.add(new Vec3d[]{leftElbow, leftHand});
        bones.add(new Vec3d[]{rightShoulder, rightElbow});
        bones.add(new Vec3d[]{rightElbow, rightHand});
        bones.add(new Vec3d[]{pelvis, leftHip});
        bones.add(new Vec3d[]{pelvis, rightHip});
        bones.add(new Vec3d[]{leftHip, leftKnee});
        bones.add(new Vec3d[]{leftKnee, leftFoot});
        bones.add(new Vec3d[]{rightHip, rightKnee});
        bones.add(new Vec3d[]{rightKnee, rightFoot});
        return bones;
    }

    private Vec3d getPos(MatrixStack matrices) {
        Vector3f pos = matrices.peek().getPositionMatrix().transformPosition(0, 0, 0, new Vector3f());
        return new Vec3d(pos.x, pos.y, pos.z);
    }

    private void drawRect(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, int c1) {
        buffer.vertex(matrix, x1, y2, 0f).color(c1);
        buffer.vertex(matrix, x2, y2, 0f).color(c1);
        buffer.vertex(matrix, x2, y1, 0f).color(c1);
        buffer.vertex(matrix, x1, y1, 0f).color(c1);
    }

    private void drawRect(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2,
                          int c1, int c2, int c3, int c4) {
        buffer.vertex(matrix, x1, y2, 0f).color(c1);
        buffer.vertex(matrix, x2, y2, 0f).color(c2);
        buffer.vertex(matrix, x2, y1, 0f).color(c3);
        buffer.vertex(matrix, x1, y1, 0f).color(c4);
    }

    @NotNull
    private Vec3d[] getVectors(RenderTickCounter tick, @NotNull Entity ent) {
        double x = ent.prevX + (ent.getX() - ent.prevX) * tick.getTickDelta(true);
        double y = ent.prevY + (ent.getY() - ent.prevY) * tick.getTickDelta(true);
        double z = ent.prevZ + (ent.getZ() - ent.prevZ) * tick.getTickDelta(true);

        Box bb = ent.getBoundingBox();
        double dx = bb.minX - ent.getX() + x;
        double dy = bb.minY - ent.getY() + y;
        double dz = bb.minZ - ent.getZ() + z;
        double dx2 = bb.maxX - ent.getX() + x;
        double dy2 = bb.maxY - ent.getY() + y;
        double dz2 = bb.maxZ - ent.getZ() + z;

        return new Vec3d[]{
                new Vec3d(dx - 0.05, dy, dz - 0.05),
                new Vec3d(dx - 0.05, dy2 + 0.15, dz - 0.05),
                new Vec3d(dx2 + 0.05, dy, dz - 0.05),
                new Vec3d(dx2 + 0.05, dy2 + 0.15, dz - 0.05),
                new Vec3d(dx - 0.05, dy, dz2 + 0.05),
                new Vec3d(dx - 0.05, dy2 + 0.15, dz2 + 0.05),
                new Vec3d(dx2 + 0.05, dy, dz2 + 0.05),
                new Vec3d(dx2 + 0.05, dy2 + 0.15, dz2 + 0.05)
        };
    }
}
