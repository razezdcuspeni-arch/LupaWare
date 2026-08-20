package ru.levin.modules.combat;

import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "testFT", keywords = {"FunTime", "Aura"}, desc = "Перенесённый режим FunTime из Shade", type = Type.Combat)
public final class TestFT extends AttackAura {
    public TestFT() {
        super();
        mode.set("FunTime");
        mode.setVisible(() -> false);
    }
}

