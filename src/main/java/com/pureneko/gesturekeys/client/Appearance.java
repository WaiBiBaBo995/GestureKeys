package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.config.GestureConfig.GlassStyle;

/** Frame-local appearance. Previewing never mutates the saved configuration. */
public record Appearance(GlassStyle style, double strength, int interfaceColor, int trailColor, boolean animations) {
  public static Appearance from(GestureConfig c) {
    GlassStyle style = !c.glassEnabled ? GlassStyle.OFF
        : c.glassStyle == null ? GlassStyle.LIQUID : c.glassStyle;
    return new Appearance(style, c.glassStrength, c.interfaceColor, c.trailColor, c.animations);
  }

  public Appearance withColor(boolean trail, int rgb) {
    return new Appearance(style, strength, trail ? interfaceColor : rgb, trail ? rgb : trailColor, animations);
  }

  public void apply(GestureConfig c) {
    c.glassStyle = style;
    c.glassEnabled = style != GlassStyle.OFF;
    c.glassStrength = strength;
    c.interfaceColor = interfaceColor;
    c.trailColor = trailColor;
    c.animations = animations;
  }
}
