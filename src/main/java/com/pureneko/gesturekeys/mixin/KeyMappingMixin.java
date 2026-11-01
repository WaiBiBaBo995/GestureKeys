package com.pureneko.gesturekeys.mixin;

import com.pureneko.gesturekeys.input.ActionExecutor;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
  @Inject(method = "release", at = @At("HEAD"))
  private void gesturekeys$clearSyntheticClicks(CallbackInfo callback) {
    ActionExecutor.clearingClicks((KeyMapping) (Object) this);
  }

  @Inject(method = "consumeClick", at = @At("HEAD"))
  private void gesturekeys$consumeSyntheticClick(CallbackInfoReturnable<Boolean> callback) {
    ActionExecutor.consumingClick((KeyMapping) (Object) this);
  }

  @ModifyVariable(method = "setDown", at = @At("HEAD"), argsOnly = true)
  private boolean gesturekeys$retainHold(boolean down) {
    boolean retain = ActionExecutor.retainSyntheticDown((KeyMapping) (Object) this, down);
    if ((Object) this instanceof ToggleKeyMappingAccessor toggle
        && toggle.gesturekeys$needsToggle().getAsBoolean()) return down;
    return down || retain;
  }
}
