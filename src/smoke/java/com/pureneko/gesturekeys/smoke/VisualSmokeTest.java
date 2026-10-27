package com.pureneko.gesturekeys.smoke;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.client.*;
import com.pureneko.gesturekeys.config.GestureConfig.GlassStyle;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.opengl.*;

/** Opt-in live UI checks, material gallery and GPU elapsed queries. Never part of a release JAR. */
@EventBusSubscriber(modid = GestureKeys.ID, value = Dist.CLIENT)
public final class VisualSmokeTest {
  private static final Path OUT = Path.of("../build/visual-smoke");
  private static final List<String> checks = new ArrayList<>(), timing = new ArrayList<>();
  private static boolean running;
  private static int mode, frames;
  private static boolean highResolution;
  private static int previewStage = -1;
  private static PreferencesScreen previewOwner;
  private static Appearance previewSaved;
  private static MaterialScreen screen;
  private static final List<Pending> pending = new ArrayList<>();
  private static final List<Long> gpu = new ArrayList<>(), cpu = new ArrayList<>();
  private record Pending(int id, int mode) {}
  private static List<GesturePoint> normal, dense;

  @EventBusSubscriber(modid = GestureKeys.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
  public static final class Shaders {
    @SubscribeEvent public static void register(RegisterShadersEvent event) {
      if (Boolean.getBoolean("gesturekeys.visualSmoke")) BaselineGlassRenderer.registerShader(event);
    }
  }

  public static void start() throws Exception {
    Files.createDirectories(OUT);
    Files.deleteIfExists(OUT.resolve("FAILED.txt"));
    verifyDrafts();
    Files.writeString(OUT.resolve("environment.txt"), "Minecraft 1.21.1 / NeoForge 21.1.172\nGPU: "
        + GL11.glGetString(GL11.GL_RENDERER) + "\nJava: " + System.getProperty("java.version"));
    Minecraft mc = Minecraft.getInstance();
    mc.options.enableVsync().set(false);
    mc.options.framerateLimit().set(260);
    mc.options.guiScale().set(2);
    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
    mc.resizeDisplay();
    // Same visible world, camera and framebuffer for every sequential sample.
    mc.player.setXRot(-12);
    mc.player.setYRot(0);
    GestureKeys.STORE.get().animations = false;
    GestureKeys.STORE.get().glassEnabled = true;
    normal = makeTrail(80);
    dense = makeTrail(4096);
    screen = new MaterialScreen();
    mc.setScreen(screen);
    timing.add("mode,cpu_median_ms,gpu_median_ms,gpu_p95_ms,samples");
    running = true;
  }

  private static List<GesturePoint> makeTrail(int count) {
    List<GesturePoint> points = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      double t = i / (double) (count - 1);
      // Dense case remains a visible curve rather than identical degenerate vertices.
      points.add(new GesturePoint(50 + t * 315, 120 + Math.sin(t * Math.PI * 4) * 28, i < count / 2 ? 1 : 2));
    }
    return List.copyOf(points);
  }

  private static GlassButton button(GlassScreen s, String key) {
    return s.children().stream().filter(GlassButton.class::isInstance).map(GlassButton.class::cast)
        .filter(b -> b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents c
            && c.getKey().equals("gesturekeys." + key)).findFirst().orElseThrow();
  }

  private static void check(String label, boolean result) {
    checks.add((result ? "PASS " : "FAIL ") + label);
    if (!result) throw new AssertionError(label);
  }

  private static void verifyDrafts() throws Exception {
    Minecraft mc = Minecraft.getInstance();
    SettingsScreen settings = new SettingsScreen(null);
    Appearance original = Appearance.from(GestureKeys.STORE.get());
    PreferencesScreen preferences = new PreferencesScreen(settings);
    mc.setScreen(preferences);
    var slider = preferences.children().stream().filter(GlassSlider.class::isInstance)
        .map(GlassSlider.class::cast).toList().get(2);
    preferences.mouseClicked(slider.getX() + slider.getWidth() - 4, slider.getY() + 10, 0);
    preferences.mouseReleased(slider.getX() + slider.getWidth() - 4, slider.getY() + 10, 0);
    check("strength previews in draft", preferences.appearance().strength() == 1.5);
    check("strength preview does not touch STORE", Appearance.from(GestureKeys.STORE.get()).equals(original));
    Set<GlassStyle> visited = new HashSet<>();
    for (int i = 0; i < 10; i++) {
      visited.add(preferences.appearance().style());
      button(preferences, "preferences.style").onPress();
      check("style cycle previews without saving " + i, Appearance.from(GestureKeys.STORE.get()).equals(original));
    }
    check("style button reaches nine materials and off", visited.size() == 10);
    button(preferences, "color.interface").onPress();
    ColorPaletteScreen palette = (ColorPaletteScreen) mc.screen;
    EditBox hex = palette.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    hex.setValue("#EE66BB");
    check("HEX previews interface tint immediately", palette.appearance().interfaceColor() == 0xEE66BB);
    check("palette keeps parent draft isolated", preferences.appearance().interfaceColor() == original.interfaceColor());
    palette.onClose();
    check("canceling palette restores parent tint", ((PreferencesScreen) mc.screen).appearance().interfaceColor() == original.interfaceColor());
    button(preferences, "color.trail").onPress();
    palette = (ColorPaletteScreen) mc.screen;
    hex = palette.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    hex.setValue("#66EE99");
    check("trail HEX previews independently", palette.appearance().trailColor() == 0x66EE99
        && palette.appearance().interfaceColor() == original.interfaceColor());
    button(palette, "done").onPress();
    check("palette done commits only parent draft", preferences.appearance().trailColor() == 0x66EE99
        && Appearance.from(GestureKeys.STORE.get()).equals(original));
    preferences.onClose();
    check("cancel preferences restores all saved appearance", Appearance.from(GestureKeys.STORE.get()).equals(original));
    preferences = new PreferencesScreen(settings);
    mc.setScreen(preferences);
    preferences.setColor(false, 0xEE66BB);
    preferences.setColor(true, 0x66EE99);
    button(preferences, "preferences.style").onPress();
    Appearance draft = preferences.appearance();
    button(preferences, "save").onPress();
    check("save persists style and both independent colors", Appearance.from(GestureKeys.STORE.get()).equals(draft));
    com.pureneko.gesturekeys.config.ConfigStore reload = new com.pureneko.gesturekeys.config.ConfigStore(GestureKeys.STORE.file());
    reload.load();
    check("appearance survives disk reload", Appearance.from(reload.get()).equals(draft));
    original.apply(GestureKeys.STORE.get());
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void focus(ClientTickEvent.Pre event) {
    if (running) Minecraft.getInstance().setWindowActive(true);
  }

  private static void fail(Throwable e) {
    try { Files.writeString(OUT.resolve("FAILED.txt"), e + "\n" + String.join("\n", checks)); }
    catch (Exception ignored) {}
    GestureKeys.LOGGER.error("Visual checks failed", e);
    running = false;
    Minecraft.getInstance().stop();
  }

  private static double ms(long nanos) { return nanos / 1_000_000.0; }
  private static String label(int m) {
    if (m == 0) return "original-liquid-80points";
    if (m <= 10) return "new-" + GlassStyle.values()[m - 1].key() + "-80points";
    return m == 11 ? "original-liquid-4096points" : "new-liquid-4096points";
  }

  private static void summarize() {
    Collections.sort(gpu); Collections.sort(cpu);
    int n = gpu.size();
    if (n == 0) throw new IllegalStateException("No GPU timing results for " + label(mode));
    timing.add(String.format(Locale.ROOT, "%s,%.4f,%.4f,%.4f,%d", label(mode),
        ms(cpu.get(cpu.size()/2)), ms(gpu.get(n/2)), ms(gpu.get(Math.min(n-1,(int)(n*.95)))), n));
    gpu.clear(); cpu.clear();
  }

  @SubscribeEvent public static void afterFrame(RenderFrameEvent.Post event) {
    if (!running) return;
    try {
      if (previewStage >= 0) {
        previewFrames();
        return;
      }
      Iterator<Pending> it = pending.iterator();
      while (it.hasNext()) {
        Pending p = it.next();
        if (GL15.glGetQueryObjecti(p.id, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) continue;
        long value = GL33.glGetQueryObjectui64(p.id, GL15.GL_QUERY_RESULT);
        GL15.glDeleteQueries(p.id);
        if (p.mode == mode) gpu.add(value);
        it.remove();
      }
      frames++;
      if (!highResolution && frames == 25 && mode >= 1 && mode <= 10) {
        check("renders " + GlassStyle.values()[mode-1].key(), GL11.glGetError() == 0);
        check("trail shaders loaded " + mode, TrailRenderer.available());
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
          image.writeToFile(OUT.resolve(GlassStyle.values()[mode-1].key()+".png"));
        }
      }
      if (frames >= 125 && pending.isEmpty()) {
        summarize();
        frames = 0;
        mode++;
        if (mode == 13) {
          String suffix = highResolution ? "-1080p" : "";
          Files.write(OUT.resolve("timing" + suffix + ".csv"), timing);
          Files.writeString(OUT.resolve("resolution" + suffix + ".txt"), Minecraft.getInstance().getMainRenderTarget().width
              + "x" + Minecraft.getInstance().getMainRenderTarget().height);
          if (!highResolution) {
            highResolution = true;
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), 1920, 1080);
            Minecraft.getInstance().resizeDisplay();
            mode = 0;
            timing.clear();
            timing.add("mode,cpu_median_ms,gpu_median_ms,gpu_p95_ms,samples");
            return;
          }
          org.lwjgl.glfw.GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), 854, 480);
          Minecraft.getInstance().resizeDisplay();
          previewSaved = Appearance.from(GestureKeys.STORE.get());
          previewOwner = new PreferencesScreen(new SettingsScreen(null));
          Minecraft.getInstance().setScreen(previewOwner);
          previewStage = 0;
        }
      }
    } catch (Throwable e) { fail(e); }
  }


  private static void previewFrames() throws Exception {
    Minecraft mc = Minecraft.getInstance();
    if (++frames < 12) return;
    frames = 0;
    GlassScreen current = (GlassScreen) mc.screen;
    check("frame uses current preview appearance " + previewStage,
        GlassRenderer.appearance().equals(current.appearance()));
    if (previewStage == 0) {
      var slider = previewOwner.children().stream().filter(GlassSlider.class::isInstance).map(GlassSlider.class::cast).toList().get(2);
      previewOwner.mouseClicked(slider.getX()+4, slider.getY()+10, 0);
      previewOwner.mouseReleased(slider.getX()+4, slider.getY()+10, 0);
      button(previewOwner, "preferences.style").onPress();
    } else if (previewStage == 1) {
      check("strength reaches renderer without save", GlassRenderer.appearance().strength()==.5);
      check("style uniform changed on next frame", GlassRenderer.appearance().style() == previewOwner.appearance().style());
      button(previewOwner, "color.interface").onPress();
    } else if (previewStage == 2) {
      var palette = (ColorPaletteScreen) mc.screen;
      var field = palette.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
      field.setValue("#CB7CFA");
    } else if (previewStage == 3) {
      capture("palette-interface.png");
      check("interface tint updates renderer before save", GlassRenderer.appearance().interfaceColor() == 0xCB7CFA);
      // Move hue and SV using actual screen dispatch, not setters.
      var palette = (ColorPaletteScreen) mc.screen;
      var fields = palette.children().stream().filter(w -> !(w instanceof GlassButton) && !(w instanceof EditBox))
          .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).toList();
      var hue = fields.get(1);
      palette.mouseClicked(hue.getX()+hue.getWidth()*.3, hue.getY()+6, 0);
      palette.mouseDragged(hue.getX()+hue.getWidth()*.6, hue.getY()+6, 0, 20, 0);
      palette.mouseReleased(hue.getX()+hue.getWidth()*.6, hue.getY()+6, 0);
      var sv = fields.getFirst();
      palette.mouseClicked(sv.getX()+sv.getWidth()*.7, sv.getY()+sv.getHeight()*.25, 0);
      palette.mouseReleased(sv.getX()+sv.getWidth()*.7, sv.getY()+sv.getHeight()*.25, 0);
    } else if (previewStage == 4) {
      check("HSV mouse drag changes preview", GlassRenderer.appearance().interfaceColor() != 0xCB7CFA);
      check("live palette keeps saved config untouched", Appearance.from(GestureKeys.STORE.get()).equals(previewSaved));
      mc.screen.onClose();
      button(previewOwner, "color.trail").onPress();
      var palette = (ColorPaletteScreen) mc.screen;
      var field = palette.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
      field.setValue("#FA7890");
    } else if (previewStage == 5) {
      check("trail tint updates renderer before save", GlassRenderer.appearance().trailColor() == 0xFA7890);
      capture("palette-trail.png");
      var palette = (ColorPaletteScreen) mc.screen;
      var field = palette.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
      field.setValue("#FA");
      button(palette, "done").onPress();
      check("incomplete HEX cannot apply", mc.screen == palette);
      field.setValue("#FA7890");
      button(palette, "done").onPress();
      button(previewOwner, "cancel").onPress();
      check("canceling live preview restores saved config", Appearance.from(GestureKeys.STORE.get()).equals(previewSaved));
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 540);
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
      mc.setScreen(new PreferencesScreen(new SettingsScreen(null)));
    } else if (previewStage == 6) {
      capture("preferences-small.png");
      check("preferences controls fit compact GUI", current.children().stream()
          .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
          .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
          .allMatch(w -> w.getY()>=0 && w.getY()+w.getHeight()<=current.height));
      org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 854, 480);
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
      mc.setScreen(new PreferencesScreen(new SettingsScreen(null)));
    } else if (previewStage == 7) {
      capture("preferences.png");
      // Simulate unsupported shader fallback while retaining the color and trail settings.
      var failed = GlassRenderer.class.getDeclaredField("failed");
      failed.setAccessible(true);
      failed.setBoolean(null, true);
    } else {
      capture("shader-fallback.png");
      var failed = GlassRenderer.class.getDeclaredField("failed");
      failed.setAccessible(true);
      check("glass fallback remains usable with trail shader", failed.getBoolean(null) && TrailRenderer.available());
      failed.setBoolean(null, false);
      check("final render has no OpenGL errors", GL11.glGetError()==0);
      Files.write(OUT.resolve("checks.txt"), checks);
      running = false;
      mc.stop();
    }
    previewStage++;
  }

  private static void capture(String name) throws Exception {
    try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
      image.writeToFile(OUT.resolve(name));
    }
  }

  private static final class MaterialScreen extends GlassScreen {
    MaterialScreen() { super(Component.literal("GestureKeys"), null); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public Appearance appearance() {
      GlassStyle style = mode >= 1 && mode <= 10 ? GlassStyle.values()[mode-1] : GlassStyle.LIQUID;
      return new Appearance(style, 1, 0x80D9EA, 0xAAF3FF, false);
    }
    @Override protected void renderGlassBackground(GuiGraphics g, float progress, Appearance appearance) {
      boolean baseline = mode == 0 || mode == 11;
      boolean measure = frames >= 35 && frames < 115;
      int query = measure ? GL15.glGenQueries() : 0;
      if (measure) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query);
      long started = System.nanoTime();
      if (baseline) BaselineGlassRenderer.begin(g, width, height, 1);
      else GlassRenderer.begin(g, width, height, 1, appearance());
      for (int i = 0; i < 8; i++) {
        int x = 24 + (i % 2) * (width / 2), y = 42 + (i / 2) * 32;
        if (baseline) BaselineGlassRenderer.panel(g, x, y, width / 2 - 40, 24, 10, i==0?1:0);
        else GlassRenderer.panel(g, x, y, width / 2 - 40, 24, 10, i==0?1:0);
      }
      List<GesturePoint> points = mode >= 11 ? dense : normal;
      if (baseline) BaselineGlassRenderer.trail(g, points);
      else GlassRenderer.trail(g, points);
      g.flush();
      if (measure) {
        cpu.add(System.nanoTime() - started);
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        pending.add(new Pending(query, mode));
      }
    }
    @Override protected void content(GuiGraphics g, int mx, int my, float partial) {
      g.drawCenteredString(font, Component.literal(label(mode)), width / 2, 18, 0xFFFFFFFF);
    }
  }
}
