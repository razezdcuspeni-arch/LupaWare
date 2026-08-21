package ru.levin.screens.mainmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.screens.altmanager.AltManager;
import ru.levin.util.render.LupaWareTheme;
import ru.levin.util.render.RenderUtil;

@SuppressWarnings("All")
public class MainMenu extends Screen {
    private Button singleplayerButton, multiplayerButton, altmanagerButton, updatesButton, optionsButton, quitButton;
    private static final String WINDOW_LABEL = "LupaWare | by: wasdd";

    public MainMenu() {
        super(Text.literal(WINDOW_LABEL));
    }

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
        drawBackdrop(context);

        int railX = Math.max(26, width / 38);
        int railY = 24;
        int railW = Math.min(258, Math.max(226, width / 5));
        int railH = Math.max(330, height - 48);
        drawRail(context, railX, railY, railW, railH, mouseX, mouseY, delta);

        int contentX = railX + railW + 42;
        int contentW = Math.max(260, width - contentX - 28);
        drawContent(context, contentX, contentW, mouseX, mouseY);
    }

    private void drawBackdrop(DrawContext context) {
        RenderUtil.drawRoundedRect(context.getMatrices(), 0, 0, width, height, 0, LupaWareTheme.INK);
        for (int line = -height; line < width + height; line += 76) {
            RenderUtil.drawRoundedRect(context.getMatrices(), line, 0, 1, height, 0, LupaWareTheme.withAlpha(LupaWareTheme.BORDER, 30));
        }
        RenderUtil.drawRoundedRect(context.getMatrices(), width - 210, -110, 320, 320, 160, LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_RAISED, 150));
        RenderUtil.drawRoundedRect(context.getMatrices(), -160, height - 190, 380, 380, 190, LupaWareTheme.withAlpha(LupaWareTheme.SURFACE_SOFT, 150));
        RenderUtil.drawRoundedRect(context.getMatrices(), width / 2 - 170, height - 50, 340, 1, 0, LupaWareTheme.withAlpha(LupaWareTheme.GOLD, 55));
    }

    private void drawRail(DrawContext context, int x, int y, int railW, int railH, int mouseX, int mouseY, float delta) {
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y, railW, railH, 20, LupaWareTheme.SURFACE);
        RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, railW, railH, 20, 1f, LupaWareTheme.BORDER);

        RenderUtil.drawRoundedRect(context.getMatrices(), x + 22, y + 22, 50, 50, 15, LupaWareTheme.GOLD);
        FontUtils.sf_bold[24].centeredDraw(context.getMatrices(), "LW", x + 47, y + 32, LupaWareTheme.INK);
        FontUtils.sf_bold[24].drawLeftAligned(context.getMatrices(), "LupaWare", x + 88, y + 27, LupaWareTheme.WHITE);
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), "CONTROL DECK", x + 89, y + 57, LupaWareTheme.DIM);
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 22, y + 99, railW - 44, 1, 0, LupaWareTheme.BORDER_SOFT);
        FontUtils.sf_medium[10].drawLeftAligned(context.getMatrices(), "PLAY", x + 24, y + 118, LupaWareTheme.GOLD);

        int buttonW = railW - 20;
        int navY = y + 148;
        singleplayerButton.setBounds(x + 10, navY, buttonW, 42);
        multiplayerButton.setBounds(x + 10, navY + 50, buttonW, 42);
        altmanagerButton.setBounds(x + 10, navY + 100, buttonW, 42);
        updatesButton.setBounds(x + 10, navY + 150, buttonW, 42);
        singleplayerButton.render(context, mouseX, mouseY, delta);
        multiplayerButton.render(context, mouseX, mouseY, delta);
        altmanagerButton.render(context, mouseX, mouseY, delta);
        updatesButton.render(context, mouseX, mouseY, delta);

        int footerY = y + railH - 48;
        int footerW = Math.max(96, (buttonW - 10) / 2);
        optionsButton.setBounds(x + 10, footerY, footerW, 34);
        quitButton.setBounds(x + 20 + footerW, footerY, footerW, 34);
        optionsButton.render(context, mouseX, mouseY, delta);
        quitButton.render(context, mouseX, mouseY, delta);
    }

    private void drawContent(DrawContext context, int contentX, int contentW, int mouseX, int mouseY) {
        FontUtils.sf_medium[12].drawLeftAligned(context.getMatrices(), "OVERVIEW / SESSION READY", contentX, 49, LupaWareTheme.GOLD);
        FontUtils.sf_bold[42].drawLeftAligned(context.getMatrices(), "Welcome back", contentX, 77, LupaWareTheme.WHITE);
        FontUtils.sf_medium[17].drawLeftAligned(context.getMatrices(), "Everything you need before entering the next world.", contentX + 2, 128, LupaWareTheme.MUTED);

        int heroY = 174;
        int heroH = 190;
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX, heroY, contentW, heroH, 20, LupaWareTheme.SURFACE_RAISED);
        RenderUtil.drawRoundedBorder(context.getMatrices(), contentX, heroY, contentW, heroH, 20, 1f, LupaWareTheme.BORDER);
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX + 24, heroY + 25, 7, 140, 3, LupaWareTheme.GOLD);
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), "CURRENT PROFILE", contentX + 56, heroY + 27, LupaWareTheme.DIM);
        FontUtils.sf_bold[30].drawLeftAligned(context.getMatrices(), "LupaWare 1.21.4", contentX + 54, heroY + 52, LupaWareTheme.WHITE);
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "Fabric runtime / 1.21.4", contentX + 56, heroY + 94, LupaWareTheme.MUTED);
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX + 56, heroY + 132, 11, 11, 6, LupaWareTheme.MINT);
        FontUtils.sf_medium[15].drawLeftAligned(context.getMatrices(), "CLIENT ONLINE", contentX + 77, heroY + 129, LupaWareTheme.WHITE);
        FontUtils.sf_medium[15].drawRightAligned(context.getMatrices(), "READY", contentX + contentW - 30, heroY + 129, LupaWareTheme.MINT);

        int cardsY = heroY + heroH + 22;
        int cardW = Math.max(116, (contentW - 18) / 2);
        drawInfoCard(context, contentX, cardsY, cardW, "ACCOUNTS", "AltManager", "Manage login profiles", LupaWareTheme.VIOLET);
        drawInfoCard(context, contentX + cardW + 18, cardsY, cardW, "CHANGELOG", "Updates", "See recent changes", LupaWareTheme.MINT);
        FontUtils.sf_medium[14].drawLeftAligned(context.getMatrices(), "LupaWare / by: wasdd", contentX, height - 34, LupaWareTheme.DIM);
        FontUtils.sf_medium[14].drawRightAligned(context.getMatrices(), "v1.21.4", width - 28, height - 34, LupaWareTheme.DIM);
    }

    private void drawInfoCard(DrawContext context, int x, int y, int cardW, String eyebrow, String title, String subtitle, int accent) {
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y, cardW, 96, 15, LupaWareTheme.SURFACE);
        RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, cardW, 96, 15, 1f, LupaWareTheme.BORDER_SOFT);
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 19, y + 20, 5, 56, 2, accent);
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), eyebrow, x + 39, y + 18, accent);
        FontUtils.sf_bold[21].drawLeftAligned(context.getMatrices(), title, x + 39, y + 38, LupaWareTheme.WHITE);
        FontUtils.sf_medium[14].drawLeftAligned(context.getMatrices(), subtitle, x + 39, y + 67, LupaWareTheme.MUTED);
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
        final String name;
        int x, y, width, height;
        final boolean prominent;
        private float hoverAnim;

        Button(String name, int x, int y, int width, int height, boolean prominent) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.prominent = prominent;
        }

        void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        void render(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered(mouseX, mouseY);
            hoverAnim += ((hovered ? 1f : 0f) - hoverAnim) * 0.18f;
            int base = prominent ? LupaWareTheme.SURFACE_RAISED : LupaWareTheme.SURFACE;
            int active = prominent ? LupaWareTheme.SURFACE_SOFT : LupaWareTheme.withAlpha(LupaWareTheme.BORDER, 80);
            RenderUtil.drawRoundedRect(context.getMatrices(), x, y, width, height, 12, ru.levin.util.color.ColorUtil.blendColorsInt(base, active, hoverAnim));
            if (hovered) RenderUtil.drawRoundedRect(context.getMatrices(), x, y, 4, height, 2, LupaWareTheme.GOLD);
            FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), name, x + 18, y + 11, hovered ? LupaWareTheme.WHITE : LupaWareTheme.MUTED);
            FontUtils.sf_medium[17].drawRightAligned(context.getMatrices(), ">", x + width - 17, y + 11, hovered ? LupaWareTheme.GOLD : LupaWareTheme.DIM);
        }

        boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
