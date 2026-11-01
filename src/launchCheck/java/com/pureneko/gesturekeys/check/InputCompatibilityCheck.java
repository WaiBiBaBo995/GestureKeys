package com.pureneko.gesturekeys.check;

import com.mojang.blaze3d.platform.InputConstants;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.input.ActionExecutor;
import com.pureneko.gesturekeys.mixin.KeyMappingAccessor;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import org.lwjgl.glfw.GLFW;

/** Exercises actual transformed KeyMappings, including subclasses, releases and native toggle behavior. */
final class InputCompatibilityCheck {
  private static int tick;
  private static KeyMapping[] original;
  private static CallbackKey callback, sibling;
  private static KeyMapping plain;
  private static ToggleKeyMapping toggle;

  static boolean tick(List<String> checks) throws Exception {
    Minecraft mc = Minecraft.getInstance();
    tick++;
    if (tick == 1) {
      // Earlier UI checks close creative inventory through setScreen; restore its menu too.
      mc.player.closeContainer();
      mc.setScreen(null);
      original = mc.options.keyMappings.clone();
      callback = new CallbackKey("key.gesturekeys.check.callback");
      sibling = new CallbackKey("key.gesturekeys.check.sibling");
      plain = new KeyMapping("key.gesturekeys.check.plain", GLFW.GLFW_KEY_UNKNOWN, KeyMapping.CATEGORY_MISC);
      toggle = new ToggleKeyMapping("key.gesturekeys.smoke.toggle", GLFW.GLFW_KEY_UNKNOWN,
          KeyMapping.CATEGORY_MISC, () -> true);
      System.arraycopy(new KeyMapping[] {callback, sibling, plain, toggle}, 0,
          mc.options.keyMappings, original.length - 4, 4);
      queue(callback, 3);
    } else if (tick == 2) {
      check(checks, "unbound custom subclass receives press callback",
          callback.presses == 1 && callback.isDown() && callback.isUnbound());
      callback.setDown(false); // A hardware release during the virtual hold.
      check(checks, "hardware release does not interrupt synthetic hold", callback.isDown());
      check(checks, "another unbound mapping is not triggered", sibling.presses == 0);
    } else if (tick == 4) {
      check(checks, "hold does not repeatedly dispatch press callbacks", callback.presses == 1);
    } else if (tick == 5) {
      check(checks, "subclass receives release at deadline",
          !callback.isDown() && callback.releases == 1);
      queue(callback, 1);
    } else if (tick == 7) {
      check(checks, "second single click repeats subclass action",
          callback.presses == 2 && callback.releases == 2);
      queue(plain, 1);
    } else if (tick == 8) {
      check(checks, "plain mapping is down and consumes exactly one click",
          plain.isDown() && plain.consumeClick() && !plain.consumeClick());
    } else if (tick == 9) {
      check(checks, "plain mapping releases", !plain.isDown());
      callback.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
      sibling.setKey(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(callback));
      KeyMapping.resetMapping();
      queue(callback, 200);
    } else if (tick == 10) {
      check(checks, "shared physical binding only dispatches selected mapping",
          callback.presses == 3 && sibling.presses == 0 && !sibling.consumeClick());
      mc.setWindowActive(false);
      ActionExecutor.tick();
      mc.setWindowActive(true);
      check(checks, "focus loss releases subclass and pending hold",
          !callback.isDown() && callback.releases == 3
              && !ActionExecutor.syntheticDown(callback));
      queue(toggle, 1);
    } else if (tick == 12) {
      check(checks, "toggle mapping retains its native toggled state", toggle.isDown());
      queue(toggle, 1);
    } else if (tick == 14) {
      check(checks, "second gesture toggles native mapping off", !toggle.isDown());
      queue(toggle, 5);
    } else if (tick == 15) {
      check(checks, "synthetic hold toggles native mapping on", toggle.isDown());
      toggle.setDown(true);
      check(checks, "physical press can toggle off during synthetic hold", !toggle.isDown());
    } else if (tick == 16) {
      callback.setKey(InputConstants.Type.SCANCODE.getOrCreate(1234));
      callback.setDown(true);
      queue(callback, 2);
    } else if (tick == 19) {
      check(checks, "synthetic expiry preserves a physical scan-code hold",
          callback.isDown() && !ActionExecutor.syntheticDown(callback));
      callback.setDown(false);
      queue(callback, 2);
    } else if (tick == 20) {
      callback.setDown(false);
      check(checks, "scan-code hardware release retains virtual hold", callback.isDown());
    } else if (tick == 22) {
      check(checks, "scan-code virtual release uses latest hardware state", !callback.isDown());
    } else if (tick == 24) {
      return finish(mc);
    }
    return false;
  }

  private static boolean finish(Minecraft mc) {
    ActionExecutor.cancel();
    System.arraycopy(original, 0, mc.options.keyMappings, 0, original.length);
    return true;
  }

  private static void queue(KeyMapping key, int ticks) {
    GestureConfig.Action action = new GestureConfig.Action();
    action.keyId = key.getName();
    action.mode = ticks == 1 ? GestureConfig.PressMode.CLICK : GestureConfig.PressMode.HOLD;
    action.ticks = ticks;
    ActionExecutor.queue(action);
  }

  private static void check(List<String> checks, String label, boolean result) {
    checks.add((result ? "PASS " : "FAIL ") + label);
    if (!result) throw new AssertionError(label);
  }

  private static final class CallbackKey extends KeyMapping {
    private boolean previous;
    int presses, releases;

    CallbackKey(String name) {
      super(name, GLFW.GLFW_KEY_UNKNOWN, CATEGORY_MISC);
    }

    @Override
    public boolean isDown() {
      // Deliberately bypass the parent isDown(), as Mekanism does.
      return ((KeyMappingAccessor) (Object) this).gesturekeys$getDown();
    }

    @Override
    public void setDown(boolean down) {
      super.setDown(down);
      boolean state = isDown();
      if (state != previous) {
        if (state) presses++;
        else releases++;
      }
      previous = state;
    }
  }
}
