package ru.levin.modules.combat;

import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "testspooky", keywords = {"SpookyTime", "Aura"}, desc = "Перенесённый режим SpookyTime из Shade", type = Type.Combat)
public final class TestSpooky extends AttackAura {
    public TestSpooky() {
        super();
        mode.set("SpookyTime");
    }
}

