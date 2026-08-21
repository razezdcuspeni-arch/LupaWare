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
    private Button singleplayerButton;
    private Button multiplayerButton;
    private Button altmanagerButton;
    private Button updatesButton;
    private Button optionsButton;
    private Button quitButton;

    private static final String TITLE = "LupaWare 1.21.4";
    private static final String WINDOW_LABEL = "LupaWare | by: wasdd";

    public MainMenu() {
        super(Text.literal(WINDOW_LABEL));
    }

    @Override
    protected void init() {
        singleplayerButton = new Button("Singleplayer", 0, 0, 210, 38, true);
        multiplayerButton = new Button("Multiplayer", 0, 0, 210, 38, true);
        altmanagerButton = new Button("AltManager", 0, 0, 210, 38, true);
        updatesButton = new Button("Updates", 0, 0, 210, 38, true);
        optionsButton = new Button("Options", 0, 0, 102, 32, false);
        quitButton = new Button("Quit", 0, 0, 102, 32, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int cyan = ColorUtil.rgba(93, 226, 209, 255);
        int violet = ColorUtil.rgba(150, 126, 255, 255);
        int white = Color.WHITE.getRGB();
        int muted = ColorUtil.rgba(164, 177, 194, 255);
        int ink = ColorUtil.rgba(9, 14, 24, 255);
        int surface = ColorUtil.rgba(16, 25, 40, 245);
        int surface2 = ColorUtil.rgba(22, 34, 54, 238);

        RenderUtil.drawRoundedRect(context.getMatrices(), 0, 0, width, height, 0, ink);
        // Subtle depth grid: deliberately unlike the previous flat menu.
        for (int i = -height; i < width + height; i += 64) {
            RenderUtil.drawRoundedRect(context.getMatrices(), i, 0, 1, height, 0, ColorUtil.rgba(37, 57, 78, 70));
        }
        RenderUtil.drawRoundedRect(context.getMatrices(), width - 330, -90, 430, 430, 215, ColorUtil.rgba(54, 93, 118, 70));
        RenderUtil.drawRoundedRect(context.getMatrices(), width - 245, height - 175, 330, 330, 165, ColorUtil.rgba(85, 61, 138, 45));

        int railX = 28;
        int railW = 248;
        RenderUtil.drawRoundedRect(context.getMatrices(), railX, 26, railW, height - 52, 18, surface);
        RenderUtil.drawRoundedBorder(context.getMatrices(), railX, 26, railW, height - 52, 18, 1f, ColorUtil.rgba(77, 103, 128, 210));
        RenderUtil.drawRoundedRect(context.getMatrices(), railX + 20, 48, 48, 48, 14, cyan);
        FontUtils.sf_bold[23].centeredDraw(context.getMatrices(), "LW", railX + 44, 59, ink);
        FontUtils.sf_bold[24].drawLeftAligned(context.getMatrices(), "LupaWare", railX + 82, 51, white);
        FontUtils.sf_medium[12].drawLeftAligned(context.getMatrices(), "CONTROL DECK", railX + 83, 78, muted);
        RenderUtil.drawRoundedRect(context.getMatrices(), railX + 22, 120, railW - 44, 1, 0, ColorUtil.rgba(88, 113, 137, 180));
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), "NAVIGATION", railX + 24, 139, muted);

        int navY = 164;
        singleplayerButton.x = railX + 19; singleplayerButton.y = navY;
        multiplayerButton.x = railX + 19; multiplayerButton.y = navY + 48;
        altmanagerButton.x = railX + 19; altmanagerButton.y = navY + 96;
        updatesButton.x = railX + 19; updatesButton.y = navY + 144;
        singleplayerButton.render(context, mouseX, mouseY, delta);
        multiplayerButton.render(context, mouseX, mouseY, delta);
        altmanagerButton.render(context, mouseX, mouseY, delta);
        updatesButton.render(context, mouseX, mouseY, delta);

        optionsButton.x = railX + 19; optionsButton.y = height - 72;
        quitButton.x = railX + 127; quitButton.y = height - 72;
        optionsButton.render(context, mouseX, mouseY, delta);
        quitButton.render(context, mouseX, mouseY, delta);

        int contentX = 314;
        int contentW = width - contentX - 34;
        FontUtils.sf_medium[12].drawLeftAligned(context.getMatrices(), "OVERVIEW  /  SESSION READY", contentX, 48, cyan);
        FontUtils.sf_bold[40].drawLeftAligned(context.getMatrices(), "Welcome back.", contentX, 76, white);
        FontUtils.sf_medium[17].drawLeftAligned(context.getMatrices(), "A quiet command surface for your next Minecraft session.", contentX + 2, 126, muted);

        int heroY = 174;
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX, heroY, contentW, 190, 18, surface2);
        RenderUtil.drawRoundedBorder(context.getMatrices(), contentX, heroY, contentW, 190, 18, 1f, ColorUtil.rgba(82, 121, 145, 220));
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX + 24, heroY + 24, 7, 142, 3, cyan);
        FontUtils.sf_medium[12].drawLeftAligned(context.getMatrices(), "CURRENT PROFILE", contentX + 53, heroY + 28, muted);
        FontUtils.sf_bold[29].drawLeftAligned(context.getMatrices(), TITLE, contentX + 51, heroY + 53, white);
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "Fabric runtime / 1.21.4", contentX + 53, heroY + 94, muted);
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX + 53, heroY + 132, 11, 11, 6, cyan);
        FontUtils.sf_medium[15].drawLeftAligned(context.getMatrices(), "CLIENT ONLINE", contentX + 75, heroY + 129, white);
        FontUtils.sf_medium[15].drawRightAligned(context.getMatrices(), "READY", contentX + contentW - 30, heroY + 129, cyan);

        int cardsY = heroY + 214;
        int cardW = (contentW - 18) / 2;
        drawInfoCard(context, contentX, cardsY, cardW, "ACCOUNTS", "AltManager", "Manage your login profiles", violet);
        drawInfoCard(context, contentX + cardW + 18, cardsY, cardW, "CHANGELOG", "Updates", "See what changed recently", cyan);
        FontUtils.sf_medium[14].drawLeftAligned(context.getMatrices(), "LupaWare / by: wasdd", contentX, height - 35, muted);
        FontUtils.sf_medium[14].drawRightAligned(context.getMatrices(), "v1.21.4", width - 34, height - 35, muted);
    }

    private void drawInfoCard(DrawContext context, int x, int y, int cardWidth, String eyebrow, String title, String subtitle, int accent) {
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y, cardWidth, 92, 15, ColorUtil.rgba(19, 30, 48, 245));
        RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, cardWidth, 92, 15, 1f, ColorUtil.rgba(77, 107, 133, 200));
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 20, y + 20, 42, 42, 12, ColorUtil.applyAlpha(accent, 42));
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 20, y + 20, 4, 42, 2, accent);
        FontUtils.sf_medium[11].drawLeftAligned(context.getMatrices(), eyebrow, x + 79, y + 20, accent);
        FontUtils.sf_bold[20].drawLeftAligned(context.getMatrices(), title, x + 79, y + 39, Color.WHITE.getRGB());
        FontUtils.sf_medium[13].drawLeftAligned(context.getMatrices(), subtitle, x + 79, y + 65, ColorUtil.rgba(164, 177, 194, 255));
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
        Button(String name, int x, int y, int width, int height, boolean prominent) { this.name = name; this.x = x; this.y = y; this.width = width; this.height = height; this.prominent = prominent; }
        void render(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered(mouseX, mouseY);
            hoverAnim += ((hovered ? 1f : 0f) - hoverAnim) * 0.18f;
            int base = prominent ? ColorUtil.rgba(24, 38, 57, 255) : ColorUtil.rgba(20, 31, 48, 255);
            int active = prominent ? ColorUtil.rgba(53, 85, 102, 255) : ColorUtil.rgba(42, 62, 83, 255);
            int background = ColorUtil.blendColorsInt(base, active, hoverAnim);
            RenderUtil.drawRoundedRect(context.getMatrices(), x, y, width, height, 11, background);
            if (hovered) RenderUtil.drawRoundedRect(context.getMatrices(), x, y, 4, height, 2, ColorUtil.rgba(93, 226, 209, 255));
            FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), name, x + 18, y + 10, hovered ? Color.WHITE.getRGB() : ColorUtil.rgba(202, 214, 226, 255));
            FontUtils.sf_medium[16].drawRightAligned(context.getMatrices(), ">", x + width - 16, y + 10, ColorUtil.rgba(135, 156, 176, 255));
        }
        boolean isHovered(double mouseX, double mouseY) { return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height; }
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
