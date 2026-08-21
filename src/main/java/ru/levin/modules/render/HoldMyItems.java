package ru.levin.modules.render;

import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

/** Enables the vanilla three-dimensional first-person arm renderer. */
@FunctionAnnotation(name = "Hold My Items", desc = "Объёмные 3D-руки в первом лице", type = Type.Render)
public class HoldMyItems extends Function {
    public HoldMyItems() {
    }

    @Override
    public void onEvent(Event event) {
        // Rendering is handled by MixinHeldItemRenderer while the module is enabled.
    }
}
