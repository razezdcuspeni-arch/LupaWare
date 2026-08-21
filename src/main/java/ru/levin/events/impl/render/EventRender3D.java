package ru.levin.events.impl.render;

import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import ru.levin.events.Event;

@SuppressWarnings("All")
public class EventRender3D extends Event {
    private final MatrixStack matrixStack;
    private final RenderTickCounter deltatick;
    private final boolean worldSpace;

    public EventRender3D(MatrixStack matrixStack, RenderTickCounter deltatick) {
        this(matrixStack, deltatick, false);
    }

    public EventRender3D(MatrixStack matrixStack, RenderTickCounter deltatick, boolean worldSpace) {
        this.matrixStack = matrixStack;
        this.deltatick = deltatick;
        this.worldSpace = worldSpace;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public RenderTickCounter getDeltatick() {
        return deltatick;
    }

    public boolean isWorldSpace() {
        return worldSpace;
    }
}
