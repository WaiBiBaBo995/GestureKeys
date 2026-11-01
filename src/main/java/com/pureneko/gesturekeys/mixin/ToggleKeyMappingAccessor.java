package com.pureneko.gesturekeys.mixin;

import java.util.function.BooleanSupplier;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ToggleKeyMapping.class)
public interface ToggleKeyMappingAccessor {
  @Accessor("needsToggle")
  BooleanSupplier gesturekeys$needsToggle();
}
