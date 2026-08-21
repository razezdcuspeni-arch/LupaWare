package ru.levin.screens.mainmenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import ru.levin.screens.altmanager.AltManager;

@SuppressWarnings("All")
public class MainMenu extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    public MainMenu() {
        super(Text.literal("LupaWare"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int left = centerX - BUTTON_WIDTH / 2;
        int top = height / 4 + 48;

        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.singleplayer"), button ->
                client.setScreen(new SelectWorldScreen(this)))
                .dimensions(left, top, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.multiplayer"), button ->
                client.setScreen(new MultiplayerScreen(this)))
                .dimensions(left, top + (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("AltManager"), button ->
                client.setScreen(new AltManager(this)))
                .dimensions(left, top + 2 * (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH, BUTTON_HEIGHT).build());
        int footerY = top + 3 * (BUTTON_HEIGHT + BUTTON_GAP);
        int footerWidth = (BUTTON_WIDTH - BUTTON_GAP) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.options"), button ->
                client.setScreen(new OptionsScreen(this, client.options)))
                .dimensions(left, footerY, footerWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("menu.quit"), button ->
                client.scheduleStop())
                .dimensions(left + footerWidth + BUTTON_GAP, footerY, footerWidth, BUTTON_HEIGHT).build());


    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("LupaWare"), width / 2, 32, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
