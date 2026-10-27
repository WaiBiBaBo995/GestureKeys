package com.pureneko.gesturekeys;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.pureneko.gesturekeys.client.GestureScreen;
import com.pureneko.gesturekeys.client.GlassRenderer;
import com.pureneko.gesturekeys.client.SettingsScreen;
import com.pureneko.gesturekeys.config.ConfigStore;
import com.pureneko.gesturekeys.gesture.GestureRecognizer;
import com.pureneko.gesturekeys.input.ActionExecutor;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import com.mojang.brigadier.CommandDispatcher;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public final class GestureKeys implements ClientModInitializer {
  public static final String ID = "gesturekeys";
  public static final Logger LOGGER = LogUtils.getLogger();
  public static final KeyMapping OPEN =
      new KeyMapping(
          "key.gesturekeys.open",
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_LEFT_ALT,
          "key.categories.gesturekeys");
  public static final ConfigStore STORE =
      new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("gesturekeys.json"));
  private static List<GestureRecognizer.Template> templates = List.of();
  private boolean wasDown;

  @Override
  public void onInitializeClient() {
    try {
      STORE.load();
    } catch (IOException e) {
      LOGGER.error("Could not read GestureKeys config", e);
    }
    rebuildTemplates();
    KeyBindingHelper.registerKeyBinding(OPEN);
    CoreShaderRegistrationCallback.EVENT.register(GlassRenderer::registerShader);
    CoreShaderRegistrationCallback.EVENT.register(com.pureneko.gesturekeys.client.TrailRenderer::registerShader);
    ClientTickEvents.START_CLIENT_TICK.register(this::tick);
    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> commands(dispatcher));
    if (Boolean.getBoolean("gesturekeys.launchCheck")) {
      try {
        Class.forName("com.pureneko.gesturekeys.check.FabricLaunchCheck")
            .getMethod("register").invoke(null);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Development launch checker unavailable", e);
      }
    }
  }

  public static void rebuildTemplates() {
    templates =
        STORE.get().gestures.stream()
            .filter(b -> b.enabled)
            .map(
                b ->
                    new GestureRecognizer.Template(
                        b.id,
                        b.samples.stream()
                            .map(GestureRecognizer::normalize)
                            .filter(c -> !c.points().isEmpty())
                            .toList()))
            .filter(t -> !t.samples().isEmpty())
            .toList();
  }

  public static List<GestureRecognizer.Template> templates() {
    return templates;
  }

  public static boolean activationDown() {
    Minecraft mc = Minecraft.getInstance();
    if (!mc.isWindowActive()) return false;
    InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(OPEN);
    long window = mc.getWindow().getWindow();
    boolean pressed =
        key.getType() == InputConstants.Type.MOUSE
            ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
            : key.getType() == InputConstants.Type.KEYSYM
                && key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                && InputConstants.isKeyDown(window, key.getValue());
    return pressed;
  }

  private void tick(Minecraft mc) {
    boolean down = activationDown();
    ActionExecutor.tick();
    if (mc.screen instanceof GestureScreen gesture) {
      if (!mc.isWindowActive() || mc.level == null || mc.player == null) gesture.onClose();
      else if (!down) gesture.finish();
    } else if (down
        && !wasDown
        && mc.screen == null
        && mc.level != null
        && mc.player != null
        && mc.isWindowActive()
        && mc.getOverlay() == null) {
      ActionExecutor.cancel();
      mc.setScreen(new GestureScreen());
    }
    wasDown = down;
    while (OPEN.consumeClick()) {
      /* Poll physical state across screen transitions. */
    }
  }

  private void commands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
    dispatcher.register(
            ClientCommandManager.literal(ID)
                .then(
                    ClientCommandManager.literal("config")
                        .executes(
                            ctx -> {
                              Minecraft mc = Minecraft.getInstance();
                              mc.execute(() -> mc.setScreen(new SettingsScreen(null)));
                              return 1;
                            }))
                .executes(
                    ctx -> {
                      ctx.getSource()
                          .sendFeedback(Component.translatable("gesturekeys.command.help"));
                      return 1;
                    }));
  }
}
