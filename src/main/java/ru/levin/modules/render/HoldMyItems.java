package ru.levin.modules.render;

import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.manager.Manager;

/** Enables the vanilla three-dimensional first-person arm renderer. */
@FunctionAnnotation(name = "Hold My Items", desc = "Объёмные 3D-руки в первом лице", type = Type.Render)
public class HoldMyItems extends Function {
    private boolean swingAnimationsWasEnabled;

    public HoldMyItems() {
    }

    @Override
    protected void onEnable() {
        swingAnimationsWasEnabled = Manager.FUNCTION_MANAGER.swingAnimations.isState();
        if (swingAnimationsWasEnabled) {
            Manager.FUNCTION_MANAGER.swingAnimations.setState(false);
        }
    }

    @Override
    protected void onDisable() {
        if (swingAnimationsWasEnabled && !Manager.FUNCTION_MANAGER.swingAnimations.isState()) {
            Manager.FUNCTION_MANAGER.swingAnimations.setState(true);
        }
        swingAnimationsWasEnabled = false;
    }

    @Override
    public void onEvent(Event event) {
        // Rendering is handled by MixinHeldItemRenderer while the module is enabled.
    }
}
