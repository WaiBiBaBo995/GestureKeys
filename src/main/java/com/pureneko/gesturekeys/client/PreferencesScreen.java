package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.config.GestureConfig.GlassStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PreferencesScreen extends GlassScreen {
  private double threshold, margin, strength;
  private GlassStyle style;
  private int interfaceColor, trailColor;
  private boolean animations;

  public PreferencesScreen(SettingsScreen parent) {
    super(Component.translatable("gesturekeys.preferences"), parent);
    var c = GestureKeys.STORE.get();
    threshold = c.threshold;
    margin = c.ambiguityMargin;
    Appearance a = Appearance.from(c);
    strength = a.strength();
    style = a.style();
    interfaceColor = a.interfaceColor();
    trailColor = a.trailColor();
    animations = a.animations();
  }

  @Override
  public Appearance appearance() {
    return new Appearance(style, strength, interfaceColor, trailColor, animations);
  }

  public void setColor(boolean trail, int rgb) {
    if (trail) trailColor = rgb;
    else interfaceColor = rgb;
  }

  @Override
  protected void init() {
    int gap = Math.min(30, Math.max(24, (height - 84) / 6));
    int top = 42;
    addRenderableWidget(new GlassSlider(left(), top, contentWidth(), 24,
        "preferences.threshold", 60, 95, threshold, p -> threshold = p / 100.0));
    addRenderableWidget(new GlassSlider(left(), top + gap, contentWidth(), 24,
        "preferences.margin", 2, 15, margin, p -> margin = p / 100.0));
    addRenderableWidget(new GlassSlider(left(), top + 2 * gap, contentWidth(), 24,
        "preferences.strength", 50, 150, strength, p -> strength = p / 100.0));
    addRenderableWidget(new GlassButton(left(), top + 3 * gap, contentWidth(), 24,
        tr("preferences.style", tr("style." + style.key())), () -> {
          style = style.next();
          rebuildWidgets();
        }));
    addRenderableWidget(new GlassButton(left(), top + 4 * gap, contentWidth(), 24,
        tr("preferences.animation", tr(animations ? "on" : "off")), () -> {
          animations = !animations;
          rebuildWidgets();
        }));
    int half = (contentWidth() - 8) / 2;
    button(left(), top + 5 * gap, half, 24, "color.interface",
        () -> minecraft.setScreen(new ColorPaletteScreen(this, false)));
    button(left() + half + 8, top + 5 * gap, half, 24, "color.trail",
        () -> minecraft.setScreen(new ColorPaletteScreen(this, true)));
    button(left(), height - 32, half, 22, "cancel", this::onClose);
    button(left() + half + 8, height - 32, half, 22, "save", () -> {
      var c = GestureKeys.STORE.get();
      double ot = c.threshold, om = c.ambiguityMargin;
      Appearance original = Appearance.from(c);
      c.threshold = threshold;
      c.ambiguityMargin = margin;
      appearance().apply(c);
      if (save()) onClose();
      else {
        c.threshold = ot;
        c.ambiguityMargin = om;
        original.apply(c);
      }
    });
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    heading(g);
  }
}
