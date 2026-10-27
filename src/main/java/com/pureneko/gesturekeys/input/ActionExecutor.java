package com.pureneko.gesturekeys.input;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.mixin.KeyMappingAccessor;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ActionExecutor {
  private static final TickHolds<KeyMapping> HOLDS = new TickHolds<>();
  private static GestureConfig.Action pending;
  private static long tick;

  private ActionExecutor() {}

  public static void queue(GestureConfig.Action action) {
    pending = action;
  }

  public static boolean syntheticDown(KeyMapping key) {
    return HOLDS.isDown(key);
  }

  public static void cancel() {
    pending = null;
    HOLDS.clear();
  }

  public static void tick() {
    tick++;
    Minecraft mc = Minecraft.getInstance();
    if (mc.screen != null || !mc.isWindowActive() || mc.level == null || mc.player == null) {
      cancel();
      return;
    }
    HOLDS.advance(tick);
    if (pending == null) return;
    GestureConfig.Action action = pending;
    pending = null;
    if (action.kind == GestureConfig.Kind.COMMAND) {
      String command = action.command.strip();
      if (command.startsWith("/")) command = command.substring(1);
      try {
        // Strictly local dispatch. Never route an unknown command to the server.
        if (ClientCommandManager.getActiveDispatcher() == null || mc.getConnection() == null)
          throw new IllegalStateException("Client dispatcher unavailable");
        ClientCommandManager.getActiveDispatcher().execute(
            command, (FabricClientCommandSource) mc.getConnection().getSuggestionsProvider());
      } catch (CommandSyntaxException e) {
        message(Component.translatable("gesturekeys.error.command", e.getRawMessage().getString()));
      } catch (RuntimeException e) {
        GestureKeys.LOGGER.error("Gesture client command failed", e);
        message(Component.translatable("gesturekeys.error.command", e.getMessage()));
      }
      return;
    }
    KeyMapping key =
        Arrays.stream(mc.options.keyMappings)
            .filter(k -> k.getName().equals(action.keyId))
            .findFirst()
            .orElse(null);
    if (key == null || key == GestureKeys.OPEN) {
      message(Component.translatable("gesturekeys.error.key", action.keyId));
      return;
    }
    KeyMappingAccessor clicks = (KeyMappingAccessor) key;
    clicks.gesturekeys$setClicks(clicks.gesturekeys$getClicks() + 1);
    HOLDS.press(key, action.mode == GestureConfig.PressMode.HOLD ? action.ticks : 1, tick);
  }

  public static void message(Component text) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) mc.player.displayClientMessage(text, true);
  }

  public static Component describe(GestureConfig.Action action) {
    if (action.kind == GestureConfig.Kind.COMMAND)
      return Component.literal("/" + action.command.replaceFirst("^/", ""));
    Component name = Component.translatable(action.keyId);
    return action.mode == GestureConfig.PressMode.HOLD
        ? Component.translatable("gesturekeys.action.timed", name, action.ticks)
        : name;
  }
}
