package com.pureneko.gesturekeys.config;

import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GestureConfig {
  public int schemaVersion = 1;
  public double threshold = .78;
  public double ambiguityMargin = .06;
  public boolean glassEnabled = true;
  public double glassStrength = 1;
  // null denotes the original boolean-only configuration and is migrated on load.
  public GlassStyle glassStyle;
  public int interfaceColor = 0x80D9EA;
  public int trailColor = 0xAAF3FF;
  public boolean animations = true;
  public List<Binding> gestures = new ArrayList<>();

  public enum GlassStyle {
    LIQUID, FROSTED, GLASSMORPHISM, ACRYLIC, MICA, SMOKED, GRADIENT, AURORA, CRYSTAL, OFF;

    public GlassStyle next() {
      return values()[(ordinal() + 1) % values().length];
    }

    public String key() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  public enum Kind {
    KEY,
    COMMAND
  }

  public enum PressMode {
    CLICK,
    HOLD
  }

  public static final class Action {
    public Kind kind = Kind.KEY;
    public String keyId = "key.inventory";
    public String command = "";
    public PressMode mode = PressMode.CLICK;
    public int ticks = 1;
  }

  public static final class Binding {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public boolean enabled = true;
    public Action action = new Action();
    public List<List<GesturePoint>> samples = new ArrayList<>();
  }
}
