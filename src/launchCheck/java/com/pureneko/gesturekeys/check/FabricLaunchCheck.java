package com.pureneko.gesturekeys.check;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.client.*;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import com.pureneko.gesturekeys.input.ActionExecutor;
import com.pureneko.gesturekeys.mixin.KeyMappingAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.opengl.GL11;

/** Opt-in development code, excluded from ordinary release builds. */
public final class FabricLaunchCheck {
  private static final Path OUT = Path.of("../build/fabric-launch-check");
  private static final List<String> checks = new ArrayList<>();
  private static int ticks, stage, since;
  private static boolean done;
  private static PreferencesScreen preferences;

  public static void register() {
    ClientTickEvents.END_CLIENT_TICK.register(FabricLaunchCheck::tick);
  }

  private static void tick(Minecraft mc) {
    if (done) return;
    ticks++;
    mc.setWindowActive(true);
    if (mc.getOverlay() != null || ticks - since < 25) return;
    try {
      Files.createDirectories(OUT);
      if (ticks > 3600) throw new IllegalStateException("Client check timeout at stage " + stage);
      if (stage == 0) {
        if (!(mc.screen instanceof TitleScreen) || ticks < 60) return;
        Files.deleteIfExists(OUT.resolve("FAILED.txt"));
        check("client reaches title screen", true);
        check("activation key registered", Arrays.asList(mc.options.keyMappings).contains(GestureKeys.OPEN));
        check("physical binding readable", KeyBindingHelper.getBoundKeyOf(GestureKeys.OPEN) != null);
        check("glass shader loaded", shaderLoaded());
        check("trail shaders loaded", TrailRenderer.available());
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(new SettingsScreen(null));
      } else if (stage == 1) {
        capture(mc, "settings.png");
        check("settings renders without GL errors", GL11.glGetError() == GL11.GL_NO_ERROR);
        preferences = new PreferencesScreen(new SettingsScreen(null));
        mc.setScreen(preferences);
      } else if (stage == 2) {
        capture(mc, "preferences.png");
        mc.setScreen(new ColorPaletteScreen(preferences, true));
      } else if (stage == 3) {
        capture(mc, "palette.png");
        check("palette opens", mc.screen instanceof ColorPaletteScreen);
        mc.setScreen(new KeyPickerScreen(new EditGestureScreen(new SettingsScreen(null), null), key -> {}));
      } else if (stage == 4) {
        capture(mc, "actions.png");
        check("action picker renders", mc.screen instanceof KeyPickerScreen);
        mc.screen.mouseScrolled(120, 120, 0, -1);
        mc.screen.mouseScrolled(120, 120, 0, 1);
        check("action picker wheel handler runs", true);
        mc.setScreen(new ModMenuIntegration().getModConfigScreenFactory().create(null));
      } else if (stage == 5) {
        check("optional Mod Menu factory opens settings", mc.screen instanceof SettingsScreen);
        mc.setScreen(new MaterialScreen(GestureConfig.GlassStyle.LIQUID));
      } else if (stage >= 6 && stage <= 15) {
        GestureConfig.GlassStyle style = GestureConfig.GlassStyle.values()[stage - 6];
        capture(mc, style.key() + ".png");
        check("style renders: " + style.key(), shaderLoaded() && TrailRenderer.available()
            && GL11.glGetError() == GL11.GL_NO_ERROR);
        if (stage < 15) mc.setScreen(new MaterialScreen(GestureConfig.GlassStyle.values()[stage - 5]));
        else {
          mc.setScreen(new TitleScreen());
          mc.reloadResourcePacks();
        }
      } else if (stage == 16) {
        check("shaders survive resource reload", shaderLoaded() && TrailRenderer.available());
        Files.writeString(OUT.resolve("checks.txt"), String.join(System.lineSeparator(), checks) + System.lineSeparator());
        Files.writeString(OUT.resolve("environment.txt"), "Minecraft 1.21.1 / Fabric"
            + System.lineSeparator() + "GPU: " + GL11.glGetString(GL11.GL_RENDERER)
            + System.lineSeparator() + "Java: " + System.getProperty("java.version"));
        mc.setScreen(null);
        mc.createWorldOpenFlows().createFreshLevel(
            "gesturekeys-fabric-check-" + System.currentTimeMillis(),
            new LevelSettings("GestureKeys Fabric check", GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
            new WorldOptions(42L, false, false),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), null);
      } else if (stage == 17) {
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        check("single-player world loads", true);
        check("key accessor mixin applied", mc.options.keyInventory instanceof KeyMappingAccessor);
        ActionExecutor.queue(new GestureConfig.Action());
      } else if (stage == 18) {
        check("gesture action opens inventory", mc.screen instanceof
            net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>);
        mc.setScreen(null);
        GestureConfig.Action hold = new GestureConfig.Action();
        hold.keyId = "key.jump";
        hold.mode = GestureConfig.PressMode.HOLD;
        hold.ticks = 200;
        ActionExecutor.queue(hold);
      } else if (stage == 19) {
        check("timed action holds jump", ActionExecutor.syntheticDown(mc.options.keyJump));
        check("key state mixin exposes held action", mc.options.keyJump.isDown());
        mc.setScreen(new SettingsScreen(null));
      } else if (stage == 20) {
        check("opening screen releases action", !ActionExecutor.syntheticDown(mc.options.keyJump));
        mc.setScreen(null);
        GestureConfig.Action command = new GestureConfig.Action();
        command.kind = GestureConfig.Kind.COMMAND;
        command.command = "gesturekeys config";
        ActionExecutor.queue(command);
      } else if (stage == 21) {
        check("Fabric local client command opens settings", mc.screen instanceof SettingsScreen);
        capture(mc, "world-settings.png");
        Files.writeString(OUT.resolve("checks.txt"), String.join(System.lineSeparator(), checks) + System.lineSeparator());
        done = true;
        mc.stop();
      }
      stage++;
      since = ticks;
    } catch (Throwable failure) {
      done = true;
      GestureKeys.LOGGER.error("Fabric launch check failed", failure);
      try {
        Files.writeString(OUT.resolve("checks.txt"), String.join(System.lineSeparator(), checks));
        Files.writeString(OUT.resolve("FAILED.txt"), failure.toString());
      } catch (Exception ignored) {}
      mc.stop();
    }
  }

  private static void capture(Minecraft mc, String name) throws Exception {
    try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
      image.writeToFile(OUT.resolve(name));
    }
  }

  private static boolean shaderLoaded() throws Exception {
    var field = GlassRenderer.class.getDeclaredField("shader"); field.setAccessible(true);
    var failed = GlassRenderer.class.getDeclaredField("failed"); failed.setAccessible(true);
    return field.get(null) != null && !failed.getBoolean(null);
  }

  private static void check(String label, boolean result) {
    checks.add((result ? "PASS " : "FAIL ") + label);
    if (!result) throw new AssertionError(label);
  }

  private static final class MaterialScreen extends GlassScreen {
    private final Appearance draft;
    private final List<GesturePoint> points = new ArrayList<>();
    MaterialScreen(GestureConfig.GlassStyle style) {
      super(net.minecraft.network.chat.Component.literal("Fabric: " + style.key()), null);
      draft = new Appearance(style, 1, 0x80D9EA, 0xAAF3FF, false);
    }
    @Override public Appearance appearance() { return draft; }
    @Override protected void init() {
      points.clear();
      for (int i = 0; i < 80; i++) points.add(new GesturePoint(
          30 + i * (width - 60) / 79.0, height / 2.0 + Math.sin(i * .15) * 35, i < 40 ? 0 : 1));
    }
    @Override protected void content(GuiGraphics g, int x, int y, float partial) {
      heading(g);
      GlassRenderer.panel(g, 18, 55, width - 36, height - 85, 14, 0);
      GlassRenderer.trail(g, points);
    }
  }
}
