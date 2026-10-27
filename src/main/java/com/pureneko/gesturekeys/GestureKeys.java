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
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod(value = GestureKeys.ID, dist = Dist.CLIENT)
public final class GestureKeys {
  public static final String ID = "gesturekeys";
  public static final Logger LOGGER = LogUtils.getLogger();
  public static final KeyMapping OPEN =
      new KeyMapping(
          "key.gesturekeys.open",
          KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_LEFT_ALT,
          "key.categories.gesturekeys");
  public static final ConfigStore STORE =
      new ConfigStore(FMLPaths.CONFIGDIR.get().resolve("gesturekeys.json"));
  private static List<GestureRecognizer.Template> templates = List.of();
  private boolean wasDown;

  public GestureKeys(IEventBus bus, ModContainer container) {
    try {
      STORE.load();
    } catch (IOException e) {
      LOGGER.error("Could not read GestureKeys config", e);
    }
    rebuildTemplates();
    bus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN));
    bus.addListener(GlassRenderer::registerShader);
    bus.addListener(com.pureneko.gesturekeys.client.TrailRenderer::registerShader);
    NeoForge.EVENT_BUS.addListener(this::tick);
    NeoForge.EVENT_BUS.addListener(this::commands);
    container.registerExtensionPoint(
        IConfigScreenFactory.class, (c, parent) -> new SettingsScreen(parent));
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
    InputConstants.Key key = OPEN.getKey();
    long window = mc.getWindow().getWindow();
    boolean pressed =
        key.getType() == InputConstants.Type.MOUSE
            ? GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS
            : key.getType() == InputConstants.Type.KEYSYM
                && key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                && InputConstants.isKeyDown(window, key.getValue());
    return pressed && OPEN.getKeyModifier().isActive(KeyConflictContext.IN_GAME);
  }

  private void tick(ClientTickEvent.Pre event) {
    Minecraft mc = Minecraft.getInstance();
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

  private void commands(RegisterClientCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal(ID)
                .then(
                    Commands.literal("config")
                        .executes(
                            ctx -> {
                              Minecraft mc = Minecraft.getInstance();
                              mc.execute(() -> mc.setScreen(new SettingsScreen(null)));
                              return 1;
                            }))
                .executes(
                    ctx -> {
                      ctx.getSource()
                          .sendSuccess(
                              () -> Component.translatable("gesturekeys.command.help"), false);
                      return 1;
                    }));
  }
}
