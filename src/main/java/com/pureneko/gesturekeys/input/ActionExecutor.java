package com.pureneko.gesturekeys.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.mixin.KeyMappingAccessor;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

public final class ActionExecutor {
  private static final TickHolds<KeyMapping> HOLDS = new TickHolds<>();
  private static List<ActionSequence.Step> pending;
  private static final ActionSequence.Runner SEQUENCE = new ActionSequence.Runner();
  // Zero-based positions of our clicks within each mapping's pending click queue.
  private static final Map<KeyMapping, List<Integer>> CLICKS = new HashMap<>();
  private static long tick;
  private static KeyMapping dispatching;
  private static final Map<KeyMapping, Boolean> SCANCODE_STATES = new HashMap<>();

  private ActionExecutor() {}

  public static void queue(GestureConfig.Action action) {
    cancel();
    Component invalid = validate(action);
    if (invalid != null) {
      message(invalid);
      return;
    }
    pending = ActionSequence.snapshot(action);
  }

  /** Validate the entire plan without executing any command or input. */
  public static Component validate(GestureConfig.Action action) {
    List<ActionSequence.Step> steps;
    try {
      steps = ActionSequence.snapshot(action);
    } catch (ActionSequence.InvalidStep e) {
      return stepError(e.step, Component.translatable("gesturekeys." + e.translation));
    }
    for (int i = 0; i < steps.size(); i++) {
      var step = steps.get(i);
      if (step.kind() == GestureConfig.Kind.KEY && findKey(step.keyId()) == null)
        return stepError(i + 1, Component.translatable("gesturekeys.error.key", step.keyId()));
      if (step.kind() == GestureConfig.Kind.COMMAND) {
        if (!isLocalCommand(step.command())) {
          if (!serverConnectionAvailable())
            return stepError(i + 1, Component.translatable("gesturekeys.error.connection"));
          // Server command trees may be incomplete. Let the server check syntax and permissions.
          continue;
        }
        try {
          var dispatcher = ClientCommandManager.getActiveDispatcher();
          if (dispatcher == null) throw new IllegalStateException("Client dispatcher unavailable");
          var parsed = dispatcher.parse(step.command(), (FabricClientCommandSource) Minecraft.getInstance().getConnection().getSuggestionsProvider());
          Commands.validateParseResults(parsed);
          if (ContextChain.tryFlatten(parsed.getContext().build(step.command())).isEmpty())
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
        } catch (CommandSyntaxException e) {
          return stepError(i + 1, Component.translatable("gesturekeys.error.command", e.getRawMessage().getString()));
        } catch (RuntimeException e) {
          return stepError(i + 1, Component.translatable("gesturekeys.error.command", e.getMessage()));
        }
      }
    }
    return null;
  }

  private static boolean isLocalCommand(String command) {
    var dispatcher = ClientCommandManager.getActiveDispatcher();
    int separator = command.indexOf(' ');
    String root = separator < 0 ? command : command.substring(0, separator);
    return dispatcher != null && dispatcher.getRoot().getChild(root) != null;
  }

  private static boolean serverConnectionAvailable() {
    var connection = Minecraft.getInstance().getConnection();
    return connection != null && connection.getConnection().isConnected();
  }

  private static KeyMapping findKey(String id) {
    return Arrays.stream(Minecraft.getInstance().options.keyMappings)
        .filter(k -> k != GestureKeys.OPEN && k.getName().equals(id)).findFirst().orElse(null);
  }

  private static Component stepError(int step, Component reason) {
    return Component.translatable("gesturekeys.error.step", step, reason);
  }

  /** Track consumption so cancellation removes pending synthetic clicks, not earlier clicks. */
  public static void consumingClick(KeyMapping key) {
    List<Integer> positions = CLICKS.get(key);
    if (positions == null || ((KeyMappingAccessor) key).gesturekeys$getClicks() <= 0) return;
    positions.replaceAll(position -> position - 1);
    positions.removeIf(position -> position < 0);
    if (positions.isEmpty()) CLICKS.remove(key);
  }

  public static void clearingClicks(KeyMapping key) {
    CLICKS.remove(key);
  }

  public static boolean syntheticDown(KeyMapping key) {
    Minecraft mc = Minecraft.getInstance();
    return HOLDS.isDown(key) && mc.screen == null && mc.getOverlay() == null && mc.isWindowActive()
        && mc.level != null && mc.player != null;
  }

  public static boolean retainSyntheticDown(KeyMapping key, boolean physicalDown) {
    if (dispatching != key && HOLDS.isDown(key)
        && KeyBindingHelper.getBoundKeyOf(key).getType() == InputConstants.Type.SCANCODE) {
      SCANCODE_STATES.put(key, physicalDown);
    }
    return dispatching != key && syntheticDown(key);
  }

  public static void cancel() {
    pending = null;
    SEQUENCE.cancel();
    CLICKS.forEach((key, positions) -> {
      KeyMappingAccessor access = (KeyMappingAccessor) key;
      int count = access.gesturekeys$getClicks();
      int owned = (int) positions.stream().filter(position -> position < count).count();
      access.gesturekeys$setClicks(count - Math.min(count, owned));
    });
    CLICKS.clear();
    var held = HOLDS.keys();
    HOLDS.clear();
    for (var key : held) {
      try {
        release(key);
      } catch (RuntimeException e) {
        GestureKeys.LOGGER.error("Could not release gesture action {}", key.getName(), e);
      }
    }
  }

  public static void tick() {
    tick++;
    Minecraft mc = Minecraft.getInstance();
    if (mc.screen != null || mc.getOverlay() != null || !mc.isWindowActive() || mc.level == null || mc.player == null) {
      cancel();
      return;
    }
    var held = HOLDS.keys();
    HOLDS.advance(tick);
    try {
      held.stream().filter(key -> !HOLDS.isDown(key)).forEach(ActionExecutor::release);
    } catch (RuntimeException e) {
      int step = SEQUENCE.stepNumber();
      cancel();
      GestureKeys.LOGGER.error("Could not finish gesture sequence step {}", step, e);
      message(stepError(step, Component.translatable("gesturekeys.sequence.failed")));
      return;
    }
    if (pending != null) {
      SEQUENCE.start(pending, tick);
      pending = null;
    }
    var action = SEQUENCE.poll(tick);
    if (action == null) return;
    int step = SEQUENCE.stepNumber();
    try {
      if (action.kind() == GestureConfig.Kind.WAIT) return;
      if (action.kind() == GestureConfig.Kind.COMMAND) {
        if (isLocalCommand(action.command())) {
          int result = ClientCommandManager.getActiveDispatcher().execute(action.command(), (FabricClientCommandSource) Minecraft.getInstance().getConnection().getSuggestionsProvider());
          if (result == 0) {
            cancel();
            message(stepError(step, Component.translatable("gesturekeys.sequence.failed")));
          }
        } else {
          if (!serverConnectionAvailable()) {
            cancel();
            message(stepError(step, Component.translatable("gesturekeys.error.connection")));
            return;
          }
          // Match normal chat: includes signed arguments (e.g. /msg), unlike sendUnsignedCommand.
          // Server feedback is asynchronous and has no generic per-command success acknowledgment.
          mc.getConnection().sendCommand(action.command());
        }
        if (mc.screen != null) cancel();
        return;
      }
      KeyMapping key = findKey(action.keyId());
      if (key == null) {
        cancel();
        message(stepError(step, Component.translatable("gesturekeys.error.key", action.keyId())));
        return;
      }
      KeyMappingAccessor clicks = (KeyMappingAccessor) key;
      CLICKS.computeIfAbsent(key, k -> new ArrayList<>()).add(clicks.gesturekeys$getClicks());
      clicks.gesturekeys$setClicks(clicks.gesturekeys$getClicks() + 1);
      if (!HOLDS.isDown(key) && KeyBindingHelper.getBoundKeyOf(key).getType() == InputConstants.Type.SCANCODE)
        SCANCODE_STATES.put(key, clicks.gesturekeys$getDown());
      HOLDS.press(key, action.duration(), tick);
      setDown(key, true);
    } catch (CommandSyntaxException e) {
      cancel();
      message(stepError(step, Component.translatable("gesturekeys.error.command", e.getRawMessage().getString())));
    } catch (RuntimeException e) {
      cancel();
      GestureKeys.LOGGER.error("Gesture sequence step {} failed", step, e);
      message(stepError(step, Component.translatable("gesturekeys.sequence.failed")));
    }
  }

  private static void setDown(KeyMapping key, boolean down) {
    KeyMapping previous = dispatching;
    dispatching = key;
    try {
      // Dispatch virtually so subclasses receive their normal state-change callbacks.
      key.setDown(down);
    } finally {
      dispatching = previous;
    }
  }

  private static void release(KeyMapping key) {
    Minecraft mc = Minecraft.getInstance();
    // A synthetic release must not release a key the player is still holding.
    try {
      if (mc.screen == null && mc.isWindowActive() && mc.level != null && mc.player != null
          && physicalDown(key)) return;
      setDown(key, false);
    } finally {
      SCANCODE_STATES.remove(key);
    }
  }

  private static boolean physicalDown(KeyMapping mapping) {
    InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
    long window = Minecraft.getInstance().getWindow().getWindow();
    if (key.equals(InputConstants.UNKNOWN)) return false;
    return switch (key.getType()) {
      case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
      case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
      // GLFW cannot poll a scan code; remember its native setDown events instead.
      case SCANCODE -> SCANCODE_STATES.getOrDefault(mapping, false);
    };
  }

  public static void message(Component text) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null) mc.player.displayClientMessage(text, true);
  }

  public static Component describe(GestureConfig.Action action) {
    if (action == null || action.kind == null)
      return Component.translatable("gesturekeys.sequence.invalid");
    if (action.kind == GestureConfig.Kind.SEQUENCE) {
      int count = action.steps == null ? 0 : action.steps.size();
      return Component.translatable("gesturekeys.sequence.summary", count)
          .append(count == 0 ? Component.empty() : Component.literal(" · ").append(
              action.steps.getFirst() != null && action.steps.getFirst().kind == GestureConfig.Kind.SEQUENCE
                  ? Component.translatable("gesturekeys.sequence.invalid") : describe(action.steps.getFirst())));
    }
    if (action.kind == GestureConfig.Kind.WAIT)
      return Component.translatable("gesturekeys.sequence.wait_time", seconds(action.ticks));
    if (action.kind == GestureConfig.Kind.COMMAND)
      return Component.literal("/" + action.command.replaceFirst("^/", ""));
    Component name = Component.translatable(action.keyId);
    return action.mode == GestureConfig.PressMode.HOLD
        ? Component.translatable("gesturekeys.sequence.hold_time", name, seconds(action.ticks))
        : name;
  }

  public static String seconds(int ticks) {
    return java.math.BigDecimal.valueOf(ticks).divide(java.math.BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
  }
}
