package ru.levin.mixin.display;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FogShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.events.Event;
import ru.levin.events.impl.world.EventFog;
import ru.levin.manager.Manager;


@SuppressWarnings("All")
@Mixin(BackgroundRenderer.class)
public class MixinBackgroundRenderer {

    @Inject(method = "getFogModifier(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/BackgroundRenderer$StatusEffectFogModifier;", at = @At("HEAD"), cancellable = true)
    private static void onGetFogModifier(Entity entity, float tickDelta, CallbackInfoReturnable<Object> info) {
        if (Manager.FUNCTION_MANAGER.noRender.state) {
            boolean removeBadEffects = Manager.FUNCTION_MANAGER.noRender.mods.get("Плохие эффекты");
            boolean removeDarkness = Manager.FUNCTION_MANAGER.noRender.mods.get("Тьма")
                    && entity instanceof LivingEntity living && living.hasStatusEffect(StatusEffects.DARKNESS);
            if (removeBadEffects || removeDarkness) {
                info.setReturnValue(null);
            }
        }
    }

    @ModifyReturnValue(method = "applyFog", at = @At("RETURN"))
    private static Fog modifyFog(Fog original, Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickenFog, float tickDelta) {
        EventFog fogEvent = new EventFog();
        Event.call(fogEvent);
        if (fogEvent.modified) {
            return new Fog(fogEvent.start, fogEvent.end, fogEvent.shape, fogEvent.r, fogEvent.g, fogEvent.b, fogEvent.alpha);
        }
        return original;
    }
}