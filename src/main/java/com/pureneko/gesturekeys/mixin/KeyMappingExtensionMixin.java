package com.pureneko.gesturekeys.mixin;

import com.pureneko.gesturekeys.input.ActionExecutor;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.extensions.IKeyMappingExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IKeyMappingExtension.class, remap = false)
public interface KeyMappingExtensionMixin {
  @Inject(method = "isConflictContextAndModifierActive", at = @At("HEAD"), cancellable = true)
  default void gesturekeys$directAction(CallbackInfoReturnable<Boolean> cir) {
    KeyMapping key = (KeyMapping) this;
    if (ActionExecutor.syntheticDown(key)) {
      // Only this action bypasses its chord; physical modifiers and other mappings are untouched.
      cir.setReturnValue(key.getKeyConflictContext().isActive());
    }
  }
}
