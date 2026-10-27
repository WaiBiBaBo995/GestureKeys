package com.pureneko.gesturekeys.smoke;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.client.*;
import com.pureneko.gesturekeys.config.ConfigStore;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import com.pureneko.gesturekeys.input.ActionExecutor;
import com.pureneko.gesturekeys.mixin.KeyMappingAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** A development-only client harness: invokes our own screen/input APIs, no OS input injection. */
@EventBusSubscriber(modid = GestureKeys.ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
  private static int stage, ticks, stageTick;
  private static String screenshot;
  private static final Path OUT = Path.of("../build/smoke");
  private static GestureConfig.Binding binding;
  private static SettingsScreen settings;
  private static final List<String> checks = new ArrayList<>();
  private static boolean done;
  private static long captureAfter;
  private static GestureScreen preparedGesture;

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void focus(ClientTickEvent.Pre event) {
    if ((Boolean.getBoolean("gesturekeys.smoke") || Boolean.getBoolean("gesturekeys.visualSmoke")) && !done)
      Minecraft.getInstance().setWindowActive(true);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if ((!Boolean.getBoolean("gesturekeys.smoke") && !Boolean.getBoolean("gesturekeys.visualSmoke")) || done) return;
    Minecraft mc = Minecraft.getInstance();
    ticks++;
    if (mc.getOverlay() != null) return;
    try {
      if (stage == 0 && mc.screen != null && ticks > 40) {
        Files.createDirectories(OUT);
        Files.deleteIfExists(OUT.resolve("FAILED.txt"));
        mc.setScreen(new TitleScreen());
        mc.options.guiScale().set(2);
        mc.resizeDisplay();
        mc.options.pauseOnLostFocus = false;
        mc.options.languageCode = "zh_cn";
        mc.getLanguageManager().setSelected("zh_cn");
        mc.reloadResourcePacks();
        stage = 1;
        stageTick = ticks;
      } else if (stage == 1 && ticks - stageTick > 30) {
        binding = new GestureConfig.Binding();
        binding.name = "背包 · L 手势";
        binding.samples.add(
            new ArrayList<>(
                List.of(
                    new GesturePoint(0, 0, 1),
                    new GesturePoint(0, 100, 1),
                    new GesturePoint(100, 100, 2),
                    new GesturePoint(0, 100, 2))));
        GestureKeys.STORE.get().gestures.clear();
        GestureKeys.STORE.get().gestures.add(binding);
        GestureKeys.rebuildTemplates();
        settings = new SettingsScreen(null);
        mc.setScreen(settings);
        capture("01-settings.png");
        next();
      } else if (stage == 2 && ticks - stageTick > 15) {
        mc.setScreen(new EditGestureScreen(settings, binding));
        capture("02-editor.png");
        next();
      } else if (stage == 3 && ticks - stageTick > 15) {
        verifyPreferences();
        capture("03-preferences.png");
        next();
      } else if (stage == 4 && ticks - stageTick > 15) {
        mc.setScreen(new KeyPickerScreen(new EditGestureScreen(settings, binding), k -> {}));
        capture("04-key-picker.png");
        next();
      } else if (stage == 5 && ticks - stageTick > 15) {
        RecordScreen record =
            new RecordScreen(new EditGestureScreen(settings, binding), binding, true, null);
        mc.setScreen(record);
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 45, y = 85;
        record.mouseClicked(x, y, 0);
        record.mouseDragged(x, y + 65, 0, 0, 65);
        record.mouseReleased(x, y + 65, 0);
        record.mouseClicked(x + 80, y + 65, 0);
        record.mouseDragged(x, y + 65, 0, -80, 0);
        record.mouseReleased(x, y + 65, 0);
        capture("05-recording.png");
        next();
      } else if (stage == 6 && ticks - stageTick > 15) {
        check("glass shader compiled", shaderLoaded());
        mc.setScreen(null);
        mc.createWorldOpenFlows()
            .createFreshLevel(
                "gesturekeys-smoke-" + System.currentTimeMillis(),
                new LevelSettings(
                    "GestureKeys smoke",
                    GameType.CREATIVE,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    new GameRules(),
                    WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false),
                registries ->
                    registries
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                null);
        next();
      } else if (stage == 7 && mc.level != null && mc.player != null && mc.screen == null) {
        mc.setWindowActive(true);
        check("key accessor mixin", mc.options.keyInventory instanceof KeyMappingAccessor);
        ActionExecutor.queue(binding.action);
        next();
      } else if (stage == 8 && ticks - stageTick > 4) {
        check(
            "inventory action dispatched",
            mc.screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>);
        mc.setScreen(null);
        GestureConfig.Action hold = new GestureConfig.Action();
        hold.keyId = "key.jump";
        hold.mode = GestureConfig.PressMode.HOLD;
        hold.ticks = 1;
        ActionExecutor.queue(hold);
        next();
      } else if (stage == 9 && ticks - stageTick == 1) {
        check("one tick hold active", ActionExecutor.syntheticDown(mc.options.keyJump));
      } else if (stage == 9 && ticks - stageTick >= 2) {
        check("one tick hold released", !ActionExecutor.syntheticDown(mc.options.keyJump));
        GestureConfig.Action hold = new GestureConfig.Action();
        hold.keyId = "key.jump";
        hold.mode = GestureConfig.PressMode.HOLD;
        hold.ticks = 200;
        ActionExecutor.queue(hold);
        next();
      } else if (stage == 10 && ticks - stageTick == 1) {
        check("long hold active", ActionExecutor.syntheticDown(mc.options.keyJump));
        mc.setScreen(settings);
      } else if (stage == 10 && ticks - stageTick >= 2) {
        check("screen cancels hold", !ActionExecutor.syntheticDown(mc.options.keyJump));
        mc.setScreen(null);
        GestureConfig.Action cmd = new GestureConfig.Action();
        cmd.kind = GestureConfig.Kind.COMMAND;
        cmd.command = "gesturekeys config";
        ActionExecutor.queue(cmd);
        next();
      } else if (stage == 11 && ticks - stageTick > 3) {
        check("client command opens settings", mc.screen instanceof SettingsScreen);
        capture("06-world-glass.png");
        next();
      } else if (stage == 12 && ticks - stageTick > 15) {
        GestureKeys.STORE.get().glassEnabled = false;
        capture("07-fallback.png");
        next();
      } else if (stage == 13 && ticks - stageTick > 15) {
        check("fallback completed", true);
        GestureKeys.STORE.get().glassEnabled = true;
        GestureKeys.STORE.get().threshold = .78;
        GestureKeys.STORE.get().ambiguityMargin = .06;
        mc.setScreen(null);
        preparedGesture = new GestureScreen();
        mc.gui.setOverlayMessage(Component.empty(), false);
        next();
      } else if (stage == 15 && ticks - stageTick > 4) {
        check(
            "multistroke gesture executes inventory",
            mc.screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>);
        check("successful gesture is silent", overlayMessage().isEmpty());
        verifyFailureFeedback();
        mc.setScreen(new GestureScreen());
        mc.screen.mouseClicked(22, mc.getWindow().getGuiScaledHeight() - 27, 0);
        next();
      } else if (stage == 16 && ticks - stageTick > 4) {
        check("gear settings persists", mc.screen instanceof SettingsScreen);
        check("settings pauses single player", mc.screen.isPauseScreen());
        mc.setScreen(new GestureScreen());
        mc.screen.onClose();
        next();
      } else if (stage == 17 && ticks - stageTick > 3) {
        check("escape cancels gesture", mc.screen == null);
        mc.setScreen(settings);
        mc.reloadResourcePacks();
        next();
      } else if (stage == 18 && ticks - stageTick > 30) {
        check("shader survives resource reload", shaderLoaded());
        check("OpenGL render state", org.lwjgl.opengl.GL11.glGetError() == 0);
        mc.options.guiScale().set(1);
        mc.resizeDisplay();
        capture("09-resized.png");
        next();
      } else if (stage == 19 && ticks - stageTick > 15) {
        Files.createDirectories(OUT);
        Files.write(OUT.resolve("checks.txt"), checks);
        done = true;
        if (Boolean.getBoolean("gesturekeys.visualSmoke")) VisualSmokeTest.start();
        else mc.stop();
      }
      if (ticks > 2400) throw new IllegalStateException("Smoke test timeout at stage " + stage);
    } catch (Throwable e) {
      GestureKeys.LOGGER.error("Smoke test failed", e);
      try {
        Files.createDirectories(OUT);
        Files.writeString(
            OUT.resolve("FAILED.txt"), e.toString() + "\n" + String.join("\n", checks));
      } catch (Exception ignored) {
      }
      done = true;
      mc.stop();
    }
  }

  private static void next() {
    stage++;
    stageTick = ticks;
  }

  private static void verifyPreferences() throws Exception {
    Minecraft mc = Minecraft.getInstance();
    PreferencesScreen screen = new PreferencesScreen(settings);
    mc.setScreen(screen);
    var sliders = screen.children().stream().filter(GlassSlider.class::isInstance)
        .map(GlassSlider.class::cast).toList();
    check("three numeric preferences use sliders", sliders.size() == 3);
    GlassButton shaderToggle = screen.children().stream().filter(GlassButton.class::isInstance)
        .map(GlassButton.class::cast)
        .filter(b -> b.getMessage().getContents()
            instanceof net.minecraft.network.chat.contents.TranslatableContents text
            && text.getKey().equals("gesturekeys.preferences.style"))
        .findFirst().orElseThrow();
    check("shader toggle is directly below glass strength slider",
        shaderToggle.getY() > sliders.get(2).getY() + sliders.get(2).getHeight() - 1
            && shaderToggle.getY() <= sliders.get(2).getY() + 30);
    double original = GestureKeys.STORE.get().threshold;
    drag(screen, sliders.get(0), -1);
    check("threshold slider clamps minimum", sliders.get(0).getMessage().getString().endsWith("60%"));
    drag(screen, sliders.get(0), 2);
    check("threshold slider clamps maximum", sliders.get(0).getMessage().getString().endsWith("95%"));
    drag(screen, sliders.get(0), 20.0 / 35);
    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0, 0);
    check("slider keyboard moves one percent", sliders.get(0).getMessage().getString().endsWith("79%"));
    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
    drag(screen, sliders.get(1), -1);
    check("margin slider clamps minimum", sliders.get(1).getMessage().getString().endsWith("2%"));
    drag(screen, sliders.get(1), 2);
    check("margin slider clamps maximum", sliders.get(1).getMessage().getString().endsWith("15%"));
    drag(screen, sliders.get(1), 6.0 / 13);
    drag(screen, sliders.get(2), -1);
    check("glass slider clamps minimum", sliders.get(2).getMessage().getString().endsWith("50%"));
    drag(screen, sliders.get(2), 2);
    check("glass slider clamps maximum", sliders.get(2).getMessage().getString().endsWith("150%"));
    drag(screen, sliders.get(2), .75);
    check("slider changes remain a draft", GestureKeys.STORE.get().threshold == original);
    // Rebuilding widgets for a toggle must preserve all three draft slider values.
    screen.children().stream().filter(GlassButton.class::isInstance).map(GlassButton.class::cast)
        .filter(b -> b.getMessage().getContents()
            instanceof net.minecraft.network.chat.contents.TranslatableContents text
            && text.getKey().equals("gesturekeys.preferences.animation"))
        .findFirst().orElseThrow().onPress();
    screen.children().stream().filter(GlassButton.class::isInstance).map(GlassButton.class::cast)
        .filter(b -> b.getMessage().getString().equals(Component.translatable("gesturekeys.save").getString()))
        .findFirst().orElseThrow().onPress();
    ConfigStore loaded = new ConfigStore(GestureKeys.STORE.file());
    loaded.load();
    check("sliders persist after save and toggle rebuild", loaded.get().threshold == .8
        && loaded.get().ambiguityMargin == .08 && loaded.get().glassStrength == 1.25);
    screen = new PreferencesScreen(settings);
    mc.setScreen(screen);
    GlassSlider threshold = (GlassSlider) screen.children().getFirst();
    drag(screen, threshold, 0);
    screen.onClose();
    check("cancel discards slider draft", GestureKeys.STORE.get().threshold == .8);
    GestureKeys.STORE.get().animations = true;
    mc.setScreen(new PreferencesScreen(settings));
  }

  private static void drag(PreferencesScreen screen, GlassSlider slider, double fraction) {
    double start = slider.getX() + slider.getWidth() * .5;
    double end = slider.getX() + 4 + (slider.getWidth() - 8) * fraction;
    double y = slider.getY() + slider.getHeight() * .5;
    screen.mouseClicked(start, y, 0);
    screen.mouseDragged(end, y, 0, end - start, 0);
    screen.mouseReleased(end, y, 0);
  }

  private static String overlayMessage() throws Exception {
    var field = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
    field.setAccessible(true);
    Component message = (Component) field.get(Minecraft.getInstance().gui);
    return message == null ? "" : message.getString();
  }

  private static void verifyFailureFeedback() throws Exception {
    Minecraft mc = Minecraft.getInstance();
    mc.gui.setOverlayMessage(Component.empty(), false);
    GestureScreen empty = new GestureScreen();
    mc.setScreen(empty);
    empty.finish();
    check("empty gesture release stays silent", overlayMessage().isEmpty());
    GestureScreen shortStroke = new GestureScreen();
    mc.setScreen(shortStroke);
    shortStroke.mouseClicked(100, 90, 0);
    shortStroke.mouseReleased(105, 90, 0);
    shortStroke.finish();
    check("short gesture gives brief unrecognized feedback", overlayMessage().equals("未识别"));
    GestureConfig.Binding duplicate = ConfigStore.copy(binding);
    duplicate.id = java.util.UUID.randomUUID().toString();
    GestureKeys.STORE.get().gestures.add(duplicate);
    GestureKeys.rebuildTemplates();
    try {
      GestureScreen conflict = new GestureScreen();
      mc.setScreen(conflict);
      drawGesture(conflict);
      conflict.finish();
      check("conflicting gestures give brief conflict feedback", overlayMessage().equals("手势冲突"));
    } finally {
      GestureKeys.STORE.get().gestures.remove(duplicate);
      GestureKeys.rebuildTemplates();
    }
    GestureScreen cleared = new GestureScreen();
    mc.setScreen(cleared);
    drawGesture(cleared);
    cleared.mouseClicked(100, 90, 1);
    mc.gui.setOverlayMessage(Component.empty(), false);
    cleared.finish();
    check("cleared gesture release stays silent", overlayMessage().isEmpty());
  }

  private static void drawGesture(GestureScreen screen) {
    int x = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2 - 45, y = 85;
    screen.mouseClicked(x, y, 0);
    screen.mouseDragged(x, y + 65, 0, 0, 65);
    screen.mouseReleased(x, y + 65, 0);
    screen.mouseClicked(x + 80, y + 65, 0);
    screen.mouseDragged(x, y + 65, 0, -80, 0);
    screen.mouseReleased(x, y + 65, 0);
  }

  private static void capture(String name) {
    screenshot = name;
    captureAfter = System.nanoTime() + 300_000_000L;
  }

  private static void check(String label, boolean result) {
    checks.add((result ? "PASS " : "FAIL ") + label);
    if (!result) throw new AssertionError(label);
  }

  private static boolean shaderLoaded() throws Exception {
    var field = GlassRenderer.class.getDeclaredField("shader");
    field.setAccessible(true);
    var failed = GlassRenderer.class.getDeclaredField("failed");
    failed.setAccessible(true);
    return field.get(null) != null && !failed.getBoolean(null);
  }

  @SubscribeEvent
  public static void beforeFrame(RenderFrameEvent.Pre event) {
    if (stage != 14 || ticks - stageTick < 7) return;
    Minecraft mc = Minecraft.getInstance();
    mc.setScreen(preparedGesture);
    drawGesture(preparedGesture);
    screenshot = "08-gesture-overlay.png";
    captureAfter = 0;
    stage = 15;
    stageTick = ticks;
  }

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (screenshot == null || System.nanoTime() < captureAfter) return;
    try {
      Files.createDirectories(OUT);
      try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
        image.writeToFile(OUT.resolve(screenshot));
      }
      screenshot = null;
      if (stage == 15) preparedGesture.finish();
    } catch (Exception e) {
      GestureKeys.LOGGER.error("Smoke screenshot failed", e);
      screenshot = null;
    }
  }
}
