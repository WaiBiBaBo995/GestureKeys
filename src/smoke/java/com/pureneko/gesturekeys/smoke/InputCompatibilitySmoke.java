package com.pureneko.gesturekeys.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.input.ActionExecutor;
import com.pureneko.gesturekeys.mixin.KeyMappingAccessor;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Exercises actual transformed KeyMappings, including subclasses and optional Mekanism packets. */
final class InputCompatibilitySmoke {
  private static int tick;
  private static KeyMapping[] original;
  private static CallbackKey callback, sibling;
  private static KeyMapping plain;
  private static ToggleKeyMapping toggle;
  private static KeyMapping chest, feet;
  private static InputConstants.Key chestKey, feetKey;
  private static KeyModifier chestModifier, feetModifier;
  private static volatile String serverChest, serverFeet;
  private static int equipmentWait;
  private static int modeWait;

  static boolean tick(List<String> checks) throws Exception {
    Minecraft mc = Minecraft.getInstance();
    tick++;
    if (tick == 1) {
      // Earlier UI checks close creative inventory through setScreen; restore its menu too.
      mc.player.closeContainer();
      mc.setScreen(null);
      original = mc.options.keyMappings;
      callback = new CallbackKey("key.gesturekeys.smoke.callback", KeyModifier.CONTROL);
      sibling = new CallbackKey("key.gesturekeys.smoke.sibling", KeyModifier.NONE);
      plain = new KeyMapping("key.gesturekeys.smoke.plain", KeyConflictContext.IN_GAME,
          KeyModifier.SHIFT, InputConstants.UNKNOWN, KeyMapping.CATEGORY_MISC);
      toggle = new ToggleKeyMapping("key.gesturekeys.smoke.toggle", GLFW.GLFW_KEY_UNKNOWN,
          KeyMapping.CATEGORY_MISC, () -> true);
      mc.options.keyMappings = Arrays.copyOf(original, original.length + 4);
      System.arraycopy(new KeyMapping[] {callback, sibling, plain, toggle}, 0,
          mc.options.keyMappings, original.length, 4);
      check(checks, "physical Ctrl is not required for this test",
          !KeyModifier.CONTROL.isActive(KeyConflictContext.IN_GAME));
      queue(callback, 3);
    } else if (tick == 2) {
      check(checks, "unbound modified subclass receives press callback",
          callback.presses == 1 && callback.isDown() && callback.isUnbound());
      check(checks, "stored modifier is preserved", callback.getKeyModifier() == KeyModifier.CONTROL);
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
      check(checks, "plain modified mapping is down and consumes exactly one click",
          plain.isDown() && plain.consumeClick() && !plain.consumeClick());
    } else if (tick == 9) {
      check(checks, "plain modified mapping releases", !plain.isDown());
      callback.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
      sibling.setKey(callback.getKey());
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
      callback.setKeyModifierAndCode(KeyModifier.NONE,
          InputConstants.Type.SCANCODE.getOrCreate(1234));
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
      if (!ModList.get().isLoaded("mekanism")) return finish(mc);
      chest = find(mc, "key.mekanism.chest_mode");
      feet = find(mc, "key.mekanism.feet_mode");
      chestKey = chest.getKey();
      feetKey = feet.getKey();
      chestModifier = chest.getKeyModifier();
      feetModifier = feet.getKeyModifier();
      chest.setKeyModifierAndCode(KeyModifier.CONTROL, InputConstants.UNKNOWN);
      feet.setKeyModifierAndCode(KeyModifier.SHIFT, InputConstants.UNKNOWN);
      mc.getSingleplayerServer().execute(() -> {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("mekanism:jetpack"))));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(BuiltInRegistries.ITEM.get(
            ResourceLocation.parse("mekanism:free_runners"))));
        player.inventoryMenu.sendAllDataToRemote();
        try {
          serverChest = mode(player.getItemBySlot(EquipmentSlot.CHEST), true);
          serverFeet = mode(player.getItemBySlot(EquipmentSlot.FEET), false);
        } catch (Exception e) {
          serverChest = e.toString();
        }
      });
    } else if (tick == 34) {
      if (mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
          || mc.player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
        if (++equipmentWait > 100) {
          throw new IllegalStateException("Equipment sync timed out; server modes: "
              + serverChest + " / " + serverFeet);
        }
        tick--;
        return false;
      }
      check(checks, "Mekanism equipment synced from server",
          mode(mc.player.getItemBySlot(EquipmentSlot.CHEST), true).equals("NORMAL")
              && mode(mc.player.getItemBySlot(EquipmentSlot.FEET), false).equals("NORMAL"));
      queue(chest, 1);
    } else if (tick == 44) {
      queue(feet, 1);
    } else if (tick == 54 || tick == 84) {
      mc.getSingleplayerServer().execute(() -> {
        try {
          var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
          serverChest = mode(player.getItemBySlot(EquipmentSlot.CHEST), true);
          serverFeet = mode(player.getItemBySlot(EquipmentSlot.FEET), false);
        } catch (Exception e) {
          serverChest = e.toString();
        }
      });
    } else if (tick == 64) {
      check(checks, "Mekanism modified unbound chest mode changes on server",
          "HOVER".equals(serverChest));
      check(checks, "Mekanism modified unbound feet mode changes on server",
          "SAFETY".equals(serverFeet));
      String clientChest = mode(mc.player.getItemBySlot(EquipmentSlot.CHEST), true);
      String clientFeet = mode(mc.player.getItemBySlot(EquipmentSlot.FEET), false);
      if (!clientChest.equals("HOVER") || !clientFeet.equals("SAFETY")) {
        if (++modeWait > 100) {
          throw new IllegalStateException("Mode sync timed out: " + clientChest + " / "
              + clientFeet + "; client menu=" + mc.player.containerMenu.containerId
              + "; paused=" + mc.isPaused());
        }
        tick--;
        return false;
      }
      check(checks, "Mekanism mode changes sync back to client", true);
      queue(chest, 1);
    } else if (tick == 74) {
      queue(feet, 1);
    } else if (tick == 94) {
      check(checks, "Mekanism chest mode retriggers after release", "VECTOR".equals(serverChest));
      check(checks, "Mekanism feet mode retriggers after release", "DISABLED".equals(serverFeet));
      chest.setKeyModifierAndCode(chestModifier, chestKey);
      feet.setKeyModifierAndCode(feetModifier, feetKey);
      return finish(mc);
    }
    return false;
  }

  private static boolean finish(Minecraft mc) {
    ActionExecutor.cancel();
    mc.options.keyMappings = original;
    return true;
  }

  private static KeyMapping find(Minecraft mc, String name) {
    return Arrays.stream(mc.options.keyMappings).filter(key -> key.getName().equals(name))
        .findFirst().orElseThrow();
  }

  private static String mode(ItemStack stack, boolean chest) throws Exception {
    return ((Enum<?>) stack.getItem().getClass()
        .getMethod(chest ? "getJetpackMode" : "getFreeRunnerMode", ItemStack.class)
        .invoke(stack.getItem(), stack)).name();
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

    CallbackKey(String name, KeyModifier modifier) {
      super(name, KeyConflictContext.IN_GAME, modifier, InputConstants.UNKNOWN, CATEGORY_MISC);
    }

    @Override
    public boolean isDown() {
      // Deliberately bypass the parent isDown(), as Mekanism does.
      return ((KeyMappingAccessor) (Object) this).gesturekeys$getDown()
          && isConflictContextAndModifierActive();
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
