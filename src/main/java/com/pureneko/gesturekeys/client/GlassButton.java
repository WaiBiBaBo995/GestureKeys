package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class GlassButton extends AbstractButton {
  private final Runnable action;
  private Component secondary;
  private List<GesturePoint> preview;
  private boolean gear;

  public GlassButton(int x, int y, int w, int h, Component text, Runnable action) {
    super(x, y, w, h, text);
    this.action = action;
  }

  public GlassButton secondary(Component text, List<GesturePoint> preview) {
    this.secondary = text;
    this.preview = preview;
    return this;
  }

  public GlassButton gear() {
    gear = true;
    return this;
  }

  @Override
  public void onPress() {
    action.run();
  }

  @Override
  protected void updateWidgetNarration(NarrationElementOutput output) {
    defaultButtonNarrationText(output);
  }

  @Override
  protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
    GlassRenderer.panel(
        g,
        getX(),
        getY(),
        width,
        height,
        Math.min(10, height / 2),
        isHoveredOrFocused() && active ? 1 : 0);
    var font = Minecraft.getInstance().font;
    int color = active ? 0xFFE5F8FF : 0xFF788D9B;
    if (gear) {
      double cx = getX() + width * .5, cy = getY() + height * .5;
      for (int i = 0; i < 16; i++) {
        double a = Math.PI * 2 * i / 16, b = Math.PI * 2 * (i + 1) / 16;
        GlassRenderer.line(
            g,
            cx + Math.cos(a) * 5,
            cy + Math.sin(a) * 5,
            cx + Math.cos(b) * 5,
            cy + Math.sin(b) * 5,
            2,
            color);
        if (i % 2 == 0)
          GlassRenderer.line(
              g,
              cx + Math.cos(a) * 7,
              cy + Math.sin(a) * 7,
              cx + Math.cos(a) * 10,
              cy + Math.sin(a) * 10,
              2,
              color);
      }
    } else if (secondary != null) {
      if (preview != null) miniature(g, preview, getX() + 9, getY() + 7, 35, height - 14);
      int start = getX() + (preview == null ? 10 : 54);
      g.drawString(
          font,
          GlassScreen.clip(font, getMessage(), width - (start - getX()) - 10),
          start,
          getY() + 8,
          color,
          false);
      g.drawString(
          font,
          GlassScreen.clip(font, secondary, width - (start - getX()) - 10),
          start,
          getY() + height - 16,
          0xFF9BBACB,
          false);
    } else {
      var text = GlassScreen.clip(font, getMessage(), Math.max(1, width - 10));
      g.drawString(
          font,
          text,
          getX() + (width - font.width(text)) / 2,
          getY() + (height - 8) / 2,
          color,
          false);
    }
  }

  public static void miniature(
      GuiGraphics g, List<GesturePoint> points, int x, int y, int w, int h) {
    if (points.isEmpty() || w <= 0 || h <= 0) return;
    double minX = points.stream().mapToDouble(GesturePoint::x).min().orElse(0);
    double minY = points.stream().mapToDouble(GesturePoint::y).min().orElse(0);
    double dx = points.stream().mapToDouble(GesturePoint::x).max().orElse(0) - minX;
    double dy = points.stream().mapToDouble(GesturePoint::y).max().orElse(0) - minY;
    double scale = Math.min(w / Math.max(dx, 1), h / Math.max(dy, 1));
    double ox = x + (w - dx * scale) * .5, oy = y + (h - dy * scale) * .5;
    java.util.ArrayList<GesturePoint> scaled = new java.util.ArrayList<>(points.size());
    for (var p : points) scaled.add(new GesturePoint(ox + (p.x() - minX) * scale,
        oy + (p.y() - minY) * scale, p.stroke()));
    TrailRenderer.draw(g, scaled, .8f, false);
  }
}
