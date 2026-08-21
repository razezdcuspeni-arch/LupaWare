package ru.levin.events.impl.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import ru.levin.events.Event;

@SuppressWarnings("All")
public class EventHandledScreen extends Event {
    private final DrawContext drawContext;
    private final HandledScreen<?> screen;

    public EventHandledScreen(DrawContext drawContext, HandledScreen<?> screen) {
        this.drawContext = drawContext;
        this.screen = screen;
    }

    public DrawContext getDrawContext() {
        return drawContext;
    }

    public HandledScreen<?> getScreen() {
        return screen;
    }
}
