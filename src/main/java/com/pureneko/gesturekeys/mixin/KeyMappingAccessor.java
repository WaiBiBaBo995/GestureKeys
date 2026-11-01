package com.pureneko.gesturekeys.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
  @Accessor("isDown")
  boolean gesturekeys$getDown();

  @Accessor("clickCount")
  int gesturekeys$getClicks();

  @Accessor("clickCount")
  void gesturekeys$setClicks(int value);
}
