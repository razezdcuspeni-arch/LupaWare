package ru.levin.mixin.display;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.IMinecraft;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventHandledScreen;
import ru.levin.manager.Manager;
import ru.levin.util.player.TimerUtil;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen<T extends ScreenHandler> extends Screen implements ScreenHandlerProvider<T>,IMinecraft{

    @Unique
    private final TimerUtil timerUtil = new TimerUtil();

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    protected int backgroundWidth;

    @Shadow
    protected int backgroundHeight;

    protected MixinHandledScreen(Text title) {
        super(title);
    }

    @Shadow
    protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    @Inject(method = "render", at = @At("TAIL"))
    private void onHandledScreenRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Event.call(new EventHandledScreen(context, (HandledScreen<?>) (Object) this));
        renderInventoryActions(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onHandledScreenMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || mc.player == null) return;
        int buttonY = getActionButtonY();
        boolean container = !(this.getScreenHandler() instanceof PlayerScreenHandler);
        int leftButtonX = container ? getActionButtonX(false) : this.x + (this.backgroundWidth - 104) / 2;
        int rightButtonX = getActionButtonX(true);
        if (!container && isInside(mouseX, mouseY, leftButtonX, buttonY, 104, 18)) {
            dropAllFromPlayerInventory();
            cir.setReturnValue(true);
        } else if (container) {
            if (isInside(mouseX, mouseY, leftButtonX, buttonY, 104, 18)) {
                dropAllFromContainer();
                cir.setReturnValue(true);
            } else if (isInside(mouseX, mouseY, rightButtonX, buttonY, 104, 18)) {
                moveAllIntoContainer();
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private int getActionButtonY() {
        return this.y + this.backgroundHeight + 6;
    }

    @Unique
    private int getActionButtonX(boolean right) {
        int centerX = this.x + this.backgroundWidth / 2;
        return right ? centerX + 8 : centerX - 112;
    }

    @Unique
    private boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Unique
    private void renderInventoryActions(DrawContext context, int mouseX, int mouseY) {
        if (mc.player == null) return;
        boolean container = !(this.getScreenHandler() instanceof PlayerScreenHandler);
        int y = getActionButtonY();
        int leftButtonX = container ? getActionButtonX(false) : this.x + (this.backgroundWidth - 104) / 2;
        int rightButtonX = getActionButtonX(true);
        if (container) {
            drawActionButton(context, leftButtonX, y, "Выбросить всё", isInside(mouseX, mouseY, leftButtonX, y, 104, 18));
            drawActionButton(context, rightButtonX, y, "Сложить всё", isInside(mouseX, mouseY, rightButtonX, y, 104, 18));
        } else {
            drawActionButton(context, leftButtonX, y, "Выбросить всё", isInside(mouseX, mouseY, leftButtonX, y, 104, 18));
        }
    }

    @Unique
    private void drawActionButton(DrawContext context, int x, int y, String label, boolean hovered) {
        int background = hovered ? 0xFFBEBEBE : 0xFF777777;
        context.fill(x, y, x + 104, y + 18, 0xFF202020);
        context.fill(x + 1, y + 1, x + 103, y + 17, background);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label), x + 52, y + 5, 0xFFFFFFFF);
    }

    @Unique
    private void dropAllFromPlayerInventory() {
        for (Slot slot : this.getScreenHandler().slots) {
            if (slot.inventory == mc.player.getInventory() && slot.hasStack()) {
                this.onMouseClick(slot, slot.id, 1, SlotActionType.THROW);
            }
        }
    }

    @Unique
    private void dropAllFromContainer() {
        for (Slot slot : this.getScreenHandler().slots) {
            if (slot.inventory != mc.player.getInventory() && slot.hasStack()) {
                this.onMouseClick(slot, slot.id, 1, SlotActionType.THROW);
            }
        }
    }

    @Unique
    private void moveAllIntoContainer() {
        for (Slot slot : this.getScreenHandler().slots) {
            if (slot.inventory == mc.player.getInventory() && slot.hasStack()) {
                this.onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
            }
        }
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"))
    private void onDrawMouseoverTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        if (this.focusedSlot == null || !this.focusedSlot.hasStack()) return;

        long windowHandle = mc.getWindow().getHandle();

        boolean leftMousePressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean shiftPressed = InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (Manager.FUNCTION_MANAGER.itemScroller != null && Manager.FUNCTION_MANAGER.itemScroller.state && leftMousePressed && shiftPressed && client.currentScreen != null) {
            if (timerUtil.hasTimeElapsed(Manager.FUNCTION_MANAGER.itemScroller.scroll.get().longValue()) && this.focusedSlot.hasStack()) {
                this.onMouseClick(this.focusedSlot, this.focusedSlot.id, 0, SlotActionType.QUICK_MOVE);
                timerUtil.reset();
            }
        }
    }
}