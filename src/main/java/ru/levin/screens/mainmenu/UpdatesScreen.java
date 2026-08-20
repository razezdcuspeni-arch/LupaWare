package ru.levin.screens.mainmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.RenderUtil;

import java.awt.Color;

public class UpdatesScreen extends Screen {
    private final Screen parent;
    private final Button backButton = new Button("Back", 0, 0, 112, 32);

    public UpdatesScreen(Screen parent) {
        super(Text.literal("Updates | LupaWare"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        backButton.x = 34;
        backButton.y = height - 64;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int accent = ColorUtil.rgba(235, 235, 235, 255);
        int purple = ColorUtil.rgba(175, 175, 175, 255);
        int text = Color.WHITE.getRGB();
        int muted = ColorUtil.rgba(155, 155, 155, 255);
        int panel = ColorUtil.rgba(24, 24, 24, 248);
        int card = ColorUtil.rgba(32, 32, 32, 248);

        RenderUtil.drawRoundedRect(context.getMatrices(), 0, 0, width, height, 0, ColorUtil.rgba(10, 10, 10, 255));
        RenderUtil.drawRoundedRect(context.getMatrices(), 26, 24, width - 52, height - 48, 16, panel);
        RenderUtil.drawRoundedBorder(context.getMatrices(), 26, 24, width - 52, height - 48, 16, 1f, ColorUtil.rgba(90, 90, 90, 190));

        FontUtils.sf_medium[15].drawLeftAligned(context.getMatrices(), "LUPAWARE / CHANGELOG", 58, 56, accent);
        FontUtils.sf_bold[36].drawLeftAligned(context.getMatrices(), "Updates", 58, 82, text);
        FontUtils.sf_medium[17].drawLeftAligned(context.getMatrices(), "Последние изменения клиента", 60, 128, muted);

        int cardX = 58;
        int cardY = 175;
        int cardWidth = width - 116;
        int cardHeight = 238;
        RenderUtil.drawRoundedRect(context.getMatrices(), cardX, cardY, cardWidth, cardHeight, 14, card);
        RenderUtil.drawRoundedBorder(context.getMatrices(), cardX, cardY, cardWidth, cardHeight, 14, 1f, ColorUtil.rgba(105, 105, 105, 190));
        RenderUtil.drawRoundedRect(context.getMatrices(), cardX, cardY, 5, cardHeight, 3, accent);

        FontUtils.sf_medium[14].drawLeftAligned(context.getMatrices(), "LATEST UPDATE  /  1.21.4", cardX + 28, cardY + 26, accent);
        FontUtils.sf_bold[24].drawLeftAligned(context.getMatrices(), "A refreshed LupaWare experience", cardX + 28, cardY + 55, text);
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "Что изменилось в последнем обновлении:", cardX + 28, cardY + 96, muted);

        drawBullet(context, cardX + 30, cardY + 130, "Полностью обновлён главный интерфейс клиента", accent);
        drawBullet(context, cardX + 30, cardY + 157, "Переработан ClickGUI и улучшена работа поиска", purple);
        drawBullet(context, cardX + 30, cardY + 184, "Добавлены новые визуальные состояния и анимации", accent);

        backButton.render(context, mouseX, mouseY, delta);
        FontUtils.sf_medium[15].drawRightAligned(context.getMatrices(), "LupaWare | by: wasdd", width - 58, height - 54, muted);
    }

    private void drawBullet(DrawContext context, int x, int y, String value, int color) {
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y + 3, 8, 8, 4, color);
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), value, x + 19, y, ColorUtil.rgba(218, 218, 218, 255));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (backButton.isHovered(mouseX, mouseY)) {
            client.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static class Button {
        final String label;
        int x, y, width, height;
        private float hover;

        Button(String label, int x, int y, int width, int height) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        void render(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered(mouseX, mouseY);
            hover += ((hovered ? 1f : 0f) - hover) * 0.16f;
            int base = ColorUtil.rgba(30, 30, 30, 255);
            int active = ColorUtil.rgba(72, 72, 72, 255);
            RenderUtil.drawRoundedRect(context.getMatrices(), x, y, width, height, 9, ColorUtil.blendColorsInt(base, active, hover));
            RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, width, height, 9, 1f, ColorUtil.rgba(105, 105, 105, 220));
            FontUtils.sf_medium[17].centeredDraw(context.getMatrices(), label, x + width / 2f, y + height / 2f - 6, Color.WHITE.getRGB());
        }

        boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
