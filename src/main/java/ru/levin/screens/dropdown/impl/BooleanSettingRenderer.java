package ru.levin.screens.dropdown.impl;

import net.minecraft.client.gui.DrawContext;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.screens.dropdown.DescriptionRenderQueue;
import ru.levin.screens.dropdown.SettingRenderer;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.LupaWareTheme;
import ru.levin.util.render.Scissor;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class BooleanSettingRenderer implements SettingRenderer<BooleanSetting>, IMinecraft {

    private static final int HEIGHT = 13, BOX_SIZE = 9;
    private final Map<BooleanSetting, Float> toggleMap = new HashMap<>(), scrollMap = new HashMap<>();

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void render(DrawContext ctx, BooleanSetting setting, int x, int y, int width, int height) {
        float progress = toggleMap.getOrDefault(setting, setting.get() ? 1f : 0f);
        progress += ((setting.get() ? 1f : 0f) - progress) * 0.15f;
        toggleMap.put(setting, progress);

        int boxX = x + 4;
        int boxY = y + 2;
        int offColor = LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_RAISED, 235);
        int onColor = LupaWareTheme.withAlpha(LupaWareTheme.GOLD, 235);
        int bgColor = ColorUtil.interpolateColor(offColor, onColor, progress);
        RenderUtil.drawRoundedRect(ctx.getMatrices(), boxX, boxY, BOX_SIZE, BOX_SIZE, 1.5f, bgColor);
        RenderUtil.drawRoundedBorder(ctx.getMatrices(), boxX, boxY, BOX_SIZE, BOX_SIZE, 1.5f, 0.35f,
                progress > 0.15f ? LupaWareTheme.GOLD : LupaWareTheme.BORDER_SOFT);
        if (progress > 0.15f) {
            FontUtils.icomoon[6].drawLeftAligned(ctx.getMatrices(), "S", boxX + 1.2f, boxY + 1.1f, LupaWareTheme.WHITE);
        }

        var font = FontUtils.durman[13];
        String text = setting.getName();
        int textY = (int) (y + Math.max(1, (HEIGHT - font.getHeight()) / 2));
        float textX = x + BOX_SIZE + 6;
        float maxTextWidth = width - (textX - x) - 2;
        float textWidth = font.getWidth(text);

        double scale = mc.getWindow().getScaleFactor();
        double mX = mc.mouse.getX() / scale;
        double mY = mc.mouse.getY() / scale;

        float overflow = textWidth - maxTextWidth;
        float offset = scrollMap.getOrDefault(setting, 0f);

        boolean textHovered = RenderUtil.isHovered((int) mX, (int) mY, textX, textY - 1, maxTextWidth, font.getHeight() + 2);

        if (textHovered && overflow > 0) {
            offset = Math.min(offset + 0.5f, overflow);
        } else {
            offset = Math.max(offset - 0.5f, 0);
        }
        scrollMap.put(setting, offset);

        Scissor.push();
        Scissor.setFromComponentCoordinates(textX, y, maxTextWidth, HEIGHT);
        font.drawLeftAligned(ctx.getMatrices(), text, textX - offset, textY, LupaWareTheme.WHITE);
        Scissor.pop();


        boolean isHovered = mX >= x && mX <= x + width && mY >= y && mY <= y + HEIGHT;
        if (isHovered && setting.getDesc() != null && !setting.getDesc().isEmpty()) {
            DescriptionRenderQueue.add(setting.getDesc(), (float) mX + 6, (float) mY + 6);
        }
    }

    @Override
    public boolean mouseClicked(BooleanSetting setting, double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        if (button != 0) return false;
        int boxX = x + 4;
        int boxY = y + 2;
        if (mouseX >= boxX && mouseX <= boxX + BOX_SIZE && mouseY >= boxY && mouseY <= boxY + BOX_SIZE) {
            setting.set(!setting.get());
            return true;
        }
        return false;
    }
}
