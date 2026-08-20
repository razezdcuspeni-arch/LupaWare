package ru.levin.modules.combat;

import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "testXB", keywords = {"HolyWorld", "Aura"}, desc = "Перенесённый режим HolyWorld из Shade", type = Type.Combat)
public final class TestXB extends AttackAura {
    public TestXB() {
        super();
        mode.set("HollyWorld");
        mode.setVisible(() -> false);
    }

    @Override
    protected String getRotationMode() {
        return "HollyWorld";
    }

    @Override
    protected boolean useBoundedImportedRotation() {
        return true;
    }
}

