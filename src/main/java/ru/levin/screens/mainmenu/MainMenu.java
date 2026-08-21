package ru.levin.screens.mainmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import ru.levin.screens.altmanager.AltManager;
import ru.levin.util.color.ColorUtil;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.render.RenderUtil;

import java.awt.Color;

@SuppressWarnings("All")
public class MainMenu extends Screen {
    private Button singleplayerButton, multiplayerButton, altmanagerButton, updatesButton, optionsButton, quitButton;
    private static final String WINDOW_LABEL = "LupaWare | by: wasdd";

    public MainMenu() { super(Text.literal(WINDOW_LABEL)); }

    @Override
    protected void init() {
        singleplayerButton = new Button("Singleplayer", 0, 0, 238, 42, true);
        multiplayerButton = new Button("Multiplayer", 0, 0, 238, 42, true);
        altmanagerButton = new Button("AltManager", 0, 0, 238, 42, true);
        updatesButton = new Button("Updates", 0, 0, 238, 42, true);
        optionsButton = new Button("Options", 0, 0, 112, 34, false);
        quitButton = new Button("Quit", 0, 0, 112, 34, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int ink = ColorUtil.rgba(7, 13, 23, 255);
        int surface = ColorUtil.rgba(15, 27, 44, 246);
        int surface2 = ColorUtil.rgba(21, 38, 59, 242);
        int border = ColorUtil.rgba(66, 103, 128, 210);
        int mint = ColorUtil.rgba(94, 229, 211, 255);
        int violet = ColorUtil.rgba(150, 126, 255, 255);
        int white = Color.WHITE.getRGB();
        int muted = ColorUtil.rgba(164, 191, 205, 255);

        RenderUtil.drawRoundedRect(context.getMatrices(), 0, 0, width, height, 0, ink);
        for (int line = -height; line < width + height; line += 76) RenderUtil.drawRoundedRect(context.getMatrices(), line, 0, 1, height, 0, ColorUtil.rgba(42, 69, 95, 44));
        RenderUtil.drawRoundedRect(context.getMatrices(), width - 250, -150, 430, 430, 215, ColorUtil.rgba(42, 107, 122, 38));
        RenderUtil.drawRoundedRect(context.getMatrices(), -160, height - 190, 380, 380, 190, ColorUtil.rgba(83, 57, 142, 34));

        int railX = 28, railY = 26, railW = 258, railH = height - 52;
        RenderUtil.drawRoundedRect(context.getMatrices(), railX, railY, railW, railH, 20, surface);
        RenderUtil.drawRoundedBorder(context.getMatrices(), railX, railY, railW, railH, 20, 1f, border);
        RenderUtil.drawRoundedRect(context.getMatrices(), railX + 22, railY + 22, 50, 50, 15, mint);
        FontUtils.sf_bold[24].centeredDraw(context.getMatrices(), "LW", railX + 47, railY + 32, ColorUtil.rgba(7, 24, 30, 255));
        FontUtils.sf_bold[24].drawLeftAligned(context.getMatrices(), "LupaWare", railX + 88, railY + 27, white);
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), "CONTROL DECK", railX + 89, railY + 57, muted);
        RenderUtil.drawRoundedRect(context.getMatrices(), railX + 22, railY + 99, railW - 44, 1, 0, ColorUtil.rgba(70, 105, 130, 180));
        FontUtils.sf_medium[10].drawLeftAligned(context.getMatrices(), "PLAY", railX + 24, railY + 118, muted);

        int navY = railY + 148;
        singleplayerButton.x = railX + 10; singleplayerButton.y = navY;
        multiplayerButton.x = railX + 10; multiplayerButton.y = navY + 50;
        altmanagerButton.x = railX + 10; altmanagerButton.y = navY + 100;
        updatesButton.x = railX + 10; updatesButton.y = navY + 150;
        singleplayerButton.render(context, mouseX, mouseY, delta);
        multiplayerButton.render(context, mouseX, mouseY, delta);
        altmanagerButton.render(context, mouseX, mouseY, delta);
        updatesButton.render(context, mouseX, mouseY, delta);
        optionsButton.x = railX + 10; optionsButton.y = railY + railH - 48;
        quitButton.x = railX + 136; quitButton.y = railY + railH - 48;
        optionsButton.render(context, mouseX, mouseY, delta);
        quitButton.render(context, mouseX, mouseY, delta);

        int contentX = 326, contentW = width - contentX - 36;
        FontUtils.sf_medium[12].drawLeftAligned(context.getMatrices(), "OVERVIEW / SESSION READY", contentX, 49, mint);
        FontUtils.sf_bold[42].drawLeftAligned(context.getMatrices(), "Welcome back", contentX, 77, white);
        FontUtils.sf_medium[17].drawLeftAligned(context.getMatrices(), "Everything you need before entering the next world.", contentX + 2, 128, muted);

        int heroY = 174, heroH = 190;
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX, heroY, contentW, heroH, 20, surface2);
        RenderUtil.drawRoundedBorder(context.getMatrices(), contentX, heroY, contentW, heroH, 20, 1f, border);
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX + 24, heroY + 25, 7, 140, 3, mint);
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), "CURRENT PROFILE", contentX + 56, heroY + 27, muted);
        FontUtils.sf_bold[30].drawLeftAligned(context.getMatrices(), "LupaWare 1.21.4", contentX + 54, heroY + 52, white);
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "Fabric runtime / 1.21.4", contentX + 56, heroY + 94, muted);
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX + 56, heroY + 132, 11, 11, 6, mint);
        FontUtils.sf_medium[15].drawLeftAligned(context.getMatrices(), "CLIENT ONLINE", contentX + 77, heroY + 129, white);
        FontUtils.sf_medium[15].drawRightAligned(context.getMatrices(), "READY", contentX + contentW - 30, heroY + 129, mint);

        int cardsY = heroY + heroH + 22;
        int cardW = (contentW - 18) / 2;
        drawInfoCard(context, contentX, cardsY, cardW, "ACCOUNTS", "AltManager", "Manage login profiles", violet);
        drawInfoCard(context, contentX + cardW + 18, cardsY, cardW, "CHANGELOG", "Updates", "See recent changes", mint);
        FontUtils.sf_medium[14].drawLeftAligned(context.getMatrices(), "LupaWare / by: wasdd", contentX, height - 34, muted);
        FontUtils.sf_medium[14].drawRightAligned(context.getMatrices(), "v1.21.4", width - 36, height - 34, muted);
    }

    private void drawInfoCard(DrawContext context, int x, int y, int width, String eyebrow, String title, String subtitle, int accent) {
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y, width, 96, 15, ColorUtil.rgba(18, 32, 51, 246));
        RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, width, 96, 15, 1f, ColorUtil.rgba(66, 103, 128, 190));
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 19, y + 20, 5, 56, 2, accent);
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), eyebrow, x + 39, y + 18, accent);
        FontUtils.sf_bold[21].drawLeftAligned(context.getMatrices(), title, x + 39, y + 38, Color.WHITE.getRGB());
        FontUtils.sf_medium[14].drawLeftAligned(context.getMatrices(), subtitle, x + 39, y + 67, ColorUtil.rgba(164, 191, 205, 255));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (singleplayerButton.isHovered(mouseX, mouseY)) { client.setScreen(new SelectWorldScreen(this)); return true; }
        if (multiplayerButton.isHovered(mouseX, mouseY)) { client.setScreen(new MultiplayerScreen(this)); return true; }
        if (altmanagerButton.isHovered(mouseX, mouseY)) { client.setScreen(new AltManager(this)); return true; }
        if (updatesButton.isHovered(mouseX, mouseY)) { client.setScreen(new UpdatesScreen(this)); return true; }
        if (optionsButton.isHovered(mouseX, mouseY)) { client.setScreen(new OptionsScreen(this, client.options)); return true; }
        if (quitButton.isHovered(mouseX, mouseY)) { client.scheduleStop(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static class Button {
        final String name; int x, y, width, height; final boolean prominent; private float hoverAnim;
        Button(String name, int x, int y, int width, int height, boolean prominent) { this.name = name; this.x = x; this.y = y; this.width = width; this.height = height; this.prominent = prominent; }
        void render(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered(mouseX, mouseY);
            hoverAnim += ((hovered ? 1f : 0f) - hoverAnim) * 0.18f;
            int base = prominent ? ColorUtil.rgba(21, 38, 59, 255) : ColorUtil.rgba(18, 32, 51, 255);
            int active = prominent ? ColorUtil.rgba(39, 78, 85, 255) : ColorUtil.rgba(39, 59, 80, 255);
            RenderUtil.drawRoundedRect(context.getMatrices(), x, y, width, height, 12, ColorUtil.blendColorsInt(base, active, hoverAnim));
            if (hovered) RenderUtil.drawRoundedRect(context.getMatrices(), x, y, 4, height, 2, ColorUtil.rgba(94, 229, 211, 255));
            FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), name, x + 18, y + 11, hovered ? Color.WHITE.getRGB() : ColorUtil.rgba(202, 216, 226, 255));
            FontUtils.sf_medium[17].drawRightAligned(context.getMatrices(), ">", x + width - 17, y + 11, ColorUtil.rgba(135, 161, 179, 255));
        }
        boolean isHovered(double mouseX, double mouseY) { return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height; }
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
