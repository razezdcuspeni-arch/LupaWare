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
import ru.levin.manager.Manager;
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
    private int activePulse;

    public MainMenu() {
        super(Text.literal(WINDOW_LABEL));
    }

    @Override
    protected void init() {
        singleplayerButton = new Button("Singleplayer", 0, 0, 206, 34, true);
        multiplayerButton = new Button("Multiplayer", 0, 0, 206, 34, true);
        altmanagerButton = new Button("AltManager", 0, 0, 206, 34, true);
        updatesButton = new Button("Updates", 0, 0, 206, 34, true);
        optionsButton = new Button("Options", 0, 0, 98, 32, false);
        quitButton = new Button("Quit", 0, 0, 98, 32, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int accent = Color.WHITE.getRGB();
        int secondary = ColorUtil.rgba(210, 210, 210, 255);
        int muted = ColorUtil.rgba(155, 155, 155, 255);
        int panel = ColorUtil.rgba(18, 18, 18, 248);
        int panelLight = ColorUtil.rgba(28, 28, 28, 248);

        RenderUtil.drawRoundedRect(context.getMatrices(), 0, 0, width, height, 0, ColorUtil.rgba(13, 15, 20, 255));
        RenderUtil.drawRoundedRect(context.getMatrices(), 18, 18, 250, height - 36, 12, panel);
        RenderUtil.drawRoundedBorder(context.getMatrices(), 18, 18, 250, height - 36, 12, 1f, ColorUtil.rgba(95, 95, 95, 190));

        // Brand block.
        RenderUtil.drawRoundedRect(context.getMatrices(), 38, 40, 42, 42, 10, accent);
        FontUtils.sf_bold[24].centeredDraw(context.getMatrices(), "LW", 59, 49, Color.WHITE.getRGB());
        FontUtils.sf_bold[24].drawLeftAligned(context.getMatrices(), "LupaWare", 94, 42, Color.WHITE.getRGB());
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "by: wasdd", 95, 68, muted);
        RenderUtil.drawRoundedRect(context.getMatrices(), 38, 101, 210, 1, 0, ColorUtil.rgba(90, 90, 90, 180));

        int navStart = 132;
        singleplayerButton.x = 38;
        singleplayerButton.y = navStart;
        multiplayerButton.x = 38;
        multiplayerButton.y = navStart + 46;
        altmanagerButton.x = 38;
        altmanagerButton.y = navStart + 92;
        updatesButton.x = 38;
        updatesButton.y = navStart + 138;
        optionsButton.x = 38;
        optionsButton.y = height - 72;
        quitButton.x = 148;
        quitButton.y = height - 72;

        singleplayerButton.render(context, mouseX, mouseY, delta);
        multiplayerButton.render(context, mouseX, mouseY, delta);
        altmanagerButton.render(context, mouseX, mouseY, delta);
        updatesButton.render(context, mouseX, mouseY, delta);
        optionsButton.render(context, mouseX, mouseY, delta);
        quitButton.render(context, mouseX, mouseY, delta);

        // Main content area.
        int contentX = 302;
        int contentWidth = width - contentX - 36;
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "DASHBOARD / HOME", contentX, 46, accent);
        FontUtils.sf_bold[42].drawLeftAligned(context.getMatrices(), "Welcome back", contentX, 73, Color.WHITE.getRGB());
        FontUtils.sf_medium[18].drawLeftAligned(context.getMatrices(), "Your client is ready for the next session.", contentX, 121, muted);

        int heroY = 166;
        int heroHeight = 178;
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX, heroY, contentWidth, heroHeight, 14, panelLight);
        RenderUtil.drawRoundedBorder(context.getMatrices(), contentX, heroY, contentWidth, heroHeight, 14, 1f, ColorUtil.rgba(70, 82, 105, 180));
        RenderUtil.drawRoundedRect(context.getMatrices(), contentX, heroY, 5, heroHeight, 3, accent);
        FontUtils.sf_bold[28].drawLeftAligned(context.getMatrices(), TITLE, contentX + 28, heroY + 28, Color.WHITE.getRGB());
        FontUtils.sf_medium[17].drawLeftAligned(context.getMatrices(), "A clean space for your Minecraft experience.", contentX + 29, heroY + 68, muted);
        FontUtils.sf_medium[17].drawLeftAligned(context.getMatrices(), "Choose a section from the navigation to continue.", contentX + 29, heroY + 94, muted);

        int statusX = contentX + 29;
        int statusY = heroY + 130;
        RenderUtil.drawRoundedRect(context.getMatrices(), statusX, statusY, 10, 10, 5, Color.WHITE.getRGB());
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "Client online", statusX + 18, statusY - 3, ColorUtil.rgba(225, 225, 225, 255));
        FontUtils.sf_medium[16].drawLeftAligned(context.getMatrices(), "Fabric 1.21.4", statusX + 142, statusY - 3, muted);

        int cardY = heroY + heroHeight + 20;
        int cardWidth = (contentWidth - 20) / 2;
        drawInfoCard(context, contentX, cardY, cardWidth, "QUICK ACCESS", "AltManager", "Manage your accounts", accent);
        drawInfoCard(context, contentX + cardWidth + 20, cardY, cardWidth, "BUILD", "Stable release", "LupaWare 1.21.4", secondary);

        FontUtils.sf_medium[15].drawLeftAligned(context.getMatrices(), "LupaWare | by: wasdd", contentX, height - 32, muted);
        String versionText = "v1.21.4";
        float versionX = width - 36 - FontUtils.sf_medium[15].getWidth(versionText);
        FontUtils.sf_medium[15].drawLeftAligned(context.getMatrices(), versionText, versionX, height - 32, muted);
    }

    private void drawInfoCard(DrawContext context, int x, int y, int cardWidth, String eyebrow, String title, String subtitle, int accent) {
        RenderUtil.drawRoundedRect(context.getMatrices(), x, y, cardWidth, 94, 12, ColorUtil.rgba(25, 28, 35, 245));
        RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, cardWidth, 94, 12, 1f, ColorUtil.rgba(95, 95, 95, 190));
        RenderUtil.drawRoundedRect(context.getMatrices(), x + 18, y + 19, 4, 52, 2, accent);
        FontUtils.sf_medium[13].drawLeftAligned(context.getMatrices(), eyebrow, x + 34, y + 16, accent);
        FontUtils.sf_bold[20].drawLeftAligned(context.getMatrices(), title, x + 34, y + 36, Color.WHITE.getRGB());
        FontUtils.sf_medium[14].drawLeftAligned(context.getMatrices(), subtitle, x + 34, y + 64, ColorUtil.rgba(155, 155, 155, 255));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (singleplayerButton.isHovered(mouseX, mouseY)) {
            client.setScreen(new SelectWorldScreen(this));
            return true;
        }
        if (multiplayerButton.isHovered(mouseX, mouseY)) {
            client.setScreen(new MultiplayerScreen(this));
            return true;
        }
        if (altmanagerButton.isHovered(mouseX, mouseY)) {
            client.setScreen(new AltManager(this));
            return true;
        }
        if (updatesButton.isHovered(mouseX, mouseY)) {
            client.setScreen(new UpdatesScreen(this));
            return true;
        }
        if (optionsButton.isHovered(mouseX, mouseY)) {
            client.setScreen(new OptionsScreen(this, client.options));
            return true;
        }
        if (quitButton.isHovered(mouseX, mouseY)) {
            client.scheduleStop();
            return true;
        }
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

        void render(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hovered = isHovered(mouseX, mouseY);
            hoverAnim += ((hovered ? 1f : 0f) - hoverAnim) * 0.16f;
            int base = prominent ? ColorUtil.rgba(30, 30, 30, 255) : ColorUtil.rgba(24, 24, 24, 255);
            int hover = prominent ? ColorUtil.rgba(92, 92, 92, 255) : ColorUtil.rgba(70, 70, 70, 255);
            int background = ColorUtil.blendColorsInt(base, hover, hoverAnim);
            int border = prominent ? ColorUtil.rgba(105, 105, 105, 220) : ColorUtil.rgba(85, 85, 85, 180);
            RenderUtil.drawRoundedRect(context.getMatrices(), x, y, width, height, 9, background);
            RenderUtil.drawRoundedBorder(context.getMatrices(), x, y, width, height, 9, 1f, border);
            int textColor = hovered ? Color.WHITE.getRGB() : ColorUtil.rgba(210, 210, 210, 255);
            FontUtils.sf_medium[17].centeredDraw(context.getMatrices(), name, x + width / 2f, y + height / 2f - 6, textColor);
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
