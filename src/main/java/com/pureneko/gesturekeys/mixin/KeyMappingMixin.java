package com.pureneko.gesturekeys.mixin;

import com.pureneko.gesturekeys.input.ActionExecutor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
  @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
  private void gesturekeys$syntheticState(CallbackInfoReturnable<Boolean> cir) {
    if (ActionExecutor.syntheticDown((KeyMapping) (Object) this)) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen == null && mc.level != null && mc.isWindowActive()) cir.setReturnValue(true);
    }
  }
}
