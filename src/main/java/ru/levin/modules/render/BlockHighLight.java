package ru.levin.modules.render;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.ModeSetting;

import java.awt.Color;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.Render3DUtil;

@FunctionAnnotation(name = "BlockHighLight", desc = "Подсвечивает текущий блок, на который ты навёлся", type = Type.Render)
public class BlockHighLight extends Function {
    private final ModeSetting color = new ModeSetting("Цвет", "Золотой",
            "Золотой", "Белый", "Красный", "Зелёный", "Голубой", "Фиолетовый", "Розовый");

    public BlockHighLight() {
        addSettings(color);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventRender3D e)) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult result)) return;
        if (result.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = result.getBlockPos();
        if (pos == null) return;
        Render3DUtil.drawShapeAlternative(pos, mc.world.getBlockState(pos).getOutlineShape(mc.world, pos), getHighlightColor(), 2, true, true);

    }

    private int getHighlightColor() {
        return switch (color.get()) {
            case "Белый" -> Color.WHITE.getRGB();
            case "Красный" -> new Color(235, 75, 75).getRGB();
            case "Зелёный" -> new Color(80, 220, 120).getRGB();
            case "Голубой" -> new Color(80, 190, 255).getRGB();
            case "Фиолетовый" -> new Color(170, 100, 255).getRGB();
            case "Розовый" -> new Color(255, 110, 190).getRGB();
            default -> ColorUtil.getColorStyle(45);
        };
    }
}
