package ru.levin.modules.misc;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

import java.util.Arrays;

@FunctionAnnotation(name = "ClientSounds", desc = "Звуки", type = Type.Misc)
public class ClientSounds extends Function {
    public final MultiSetting check = new MultiSetting(
            "Выбрать",
            Arrays.asList("Вход в клиент", "Включение модулей", "Выключение модулей"),
            new String[]{"Вход в клиент", "Включение модулей", "Выключение модулей"}
    );
    public final ModeSetting mode = new ModeSetting("Мод", "Type-1", "Type-1", "Type-2", "Type-3", "Type-4", "Type-5", "Type-6");
    public final SliderSetting volume = new SliderSetting("Громкость", 100f, 1f, 100f,1f);


    public ClientSounds() {
        addSettings(check,mode,volume);
    }

    public boolean shouldPlayLogin() {
        return state && check.get("Вход в клиент");
    }

    public boolean shouldPlayModuleToggle(boolean enabled) {
        return state && check.get(enabled ? "Включение модулей" : "Выключение модулей");
    }

    @Override
    public void onEvent(Event event) {
    }
}
