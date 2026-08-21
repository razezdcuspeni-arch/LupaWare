package ru.levin.mixin.client;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.IMinecraft;
import ru.levin.mixin.iface.ScreenAccessor;
import ru.levin.screens.altmanager.AltManager;

@Mixin(TitleScreen.class)
public class MixinTitleScreen implements IMinecraft {
    @Inject(method = "init", at = @At("TAIL"))
    private void addLupaWareTabs(CallbackInfo ci) {
        int centerX = mc.getWindow().getScaledWidth() / 2;
        // Keep AltManager on its own row below the vanilla Options/Quit row.
        // The extra 8 scaled pixels prevent the button hitbox from touching the row above.
        int y = mc.getWindow().getScaledHeight() / 4 + 152;
        int buttonWidth = 200;
        int left = centerX - buttonWidth / 2;
        TitleScreen parent = (TitleScreen) (Object) this;
        ScreenAccessor screen = (ScreenAccessor) (Object) this;

        ButtonWidget altManager = ButtonWidget.builder(Text.literal("AltManager"), button ->
                        mc.setScreen(new AltManager(parent)))
                .dimensions(left, y, buttonWidth, 20).build();
        addWidget(screen, altManager);
    }

    private static void addWidget(ScreenAccessor screen, ButtonWidget button) {
        screen.getDrawables().add(button);
        screen.getChildren().add(button);
        screen.getSelectables().add(button);
    }
}
