package com.pureneko.gesturekeys.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

public final class ConfigStore {
  public static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  private final Path file;
  private GestureConfig config = new GestureConfig();
  private String loadWarning;

  public ConfigStore(Path file) {
    this.file = file;
  }

  public GestureConfig get() {
    return config;
  }

  public String loadWarning() {
    return loadWarning;
  }

  public Path file() {
    return file;
  }

  public void load() throws IOException {
    if (!Files.exists(file)) return;
    try {
      GestureConfig read =
          GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GestureConfig.class);
      if (read == null || read.schemaVersion != 1)
        throw new IllegalArgumentException("Unsupported config schema");
      sanitize(read);
      config = read;
    } catch (RuntimeException exception) {
      Path backup =
          file.resolveSibling(file.getFileName() + ".broken-" + Instant.now().toEpochMilli());
      Files.copy(file, backup);
      loadWarning = backup.toString();
      config = new GestureConfig();
    }
  }

  public void save() throws IOException {
    sanitize(config);
    Files.createDirectories(file.toAbsolutePath().getParent());
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
    try {
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public static GestureConfig.Binding copy(GestureConfig.Binding binding) {
    return GSON.fromJson(GSON.toJson(binding), GestureConfig.Binding.class);
  }

  private static void sanitize(GestureConfig config) {
    config.threshold = finiteClamp(config.threshold, .5, .98, .78);
    config.ambiguityMargin = finiteClamp(config.ambiguityMargin, .01, .25, .06);
    config.glassStrength = finiteClamp(config.glassStrength, .25, 1.5, 1);
    if (config.glassStyle == null)
      config.glassStyle = config.glassEnabled ? GestureConfig.GlassStyle.LIQUID : GestureConfig.GlassStyle.OFF;
    config.glassEnabled = config.glassStyle != GestureConfig.GlassStyle.OFF;
    if (config.interfaceColor < 0 || config.interfaceColor > 0xFFFFFF) config.interfaceColor = 0x80D9EA;
    if (config.trailColor < 0 || config.trailColor > 0xFFFFFF) config.trailColor = 0xAAF3FF;
    if (config.gestures == null) config.gestures = new ArrayList<>();
    config.gestures.removeIf(b -> b == null);
    if (config.gestures.size() > 128)
      config.gestures = new ArrayList<>(config.gestures.subList(0, 128));
    HashSet<String> ids = new HashSet<>();
    for (GestureConfig.Binding b : config.gestures) {
      if (b.id == null || !ids.add(b.id)) {
        b.id = UUID.randomUUID().toString();
        ids.add(b.id);
      }
      b.name = b.name == null ? "" : b.name.substring(0, Math.min(64, b.name.length()));
      if (b.action == null) b.action = new GestureConfig.Action();
      if (b.action.kind == null) b.action.kind = GestureConfig.Kind.KEY;
      if (b.action.mode == null) b.action.mode = GestureConfig.PressMode.CLICK;
      if (b.action.keyId == null) b.action.keyId = "key.inventory";
      if (b.action.command == null) b.action.command = "";
      b.action.command = b.action.command.substring(0, Math.min(2048, b.action.command.length()));
      b.action.ticks = Math.clamp(b.action.ticks, 1, 200);
      if (b.samples == null) b.samples = new ArrayList<>();
      b.samples.removeIf(s -> s == null);
      if (b.samples.size() > 8) b.samples = new ArrayList<>(b.samples.subList(0, 8));
      for (int i = 0; i < b.samples.size(); i++) {
        var clean =
            b.samples.get(i).stream()
                .filter(p -> p != null && p.finite())
                .limit(4096)
                .map(
                    p ->
                        new GesturePoint(
                            Math.clamp(p.x(), -1e6, 1e6), Math.clamp(p.y(), -1e6, 1e6), p.stroke()))
                .toList();
        b.samples.set(i, new ArrayList<>(clean));
      }
      b.samples.removeIf(s -> s.size() < 2);
    }
  }

  private static double finiteClamp(double value, double min, double max, double fallback) {
    return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
  }
}
