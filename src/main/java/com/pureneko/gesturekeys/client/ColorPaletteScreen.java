package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import org.lwjgl.glfw.GLFW;

public final class ColorPaletteScreen extends GlassScreen {
  private static final int[] PRESETS = {0x80D9EA, 0xAAF3FF, 0xA8E6B0, 0xFFE082,
      0xFFAB91, 0xEF9AAA, 0xCE9CFF, 0x82ABFF, 0xFFFFFF, 0xB0BEC5, 0x545C70, 0x171923};
  private final PreferencesScreen owner;
  private final boolean trail;
  private float hue, saturation, brightness;
  private int rgb;
  private EditBox hex;
  private boolean syncing;
  private List<GesturePoint> sample = List.of();

  public ColorPaletteScreen(PreferencesScreen owner, boolean trail) {
    super(net.minecraft.network.chat.Component.translatable(
        "gesturekeys.color." + (trail ? "trail" : "interface")), owner);
    this.owner = owner;
    this.trail = trail;
    setRGB(trail ? owner.appearance().trailColor() : owner.appearance().interfaceColor());
  }

  @Override
  public Appearance appearance() {
    return owner.appearance().withColor(trail, rgb);
  }

  private void setRGB(int color) {
    rgb = color;
    float[] hsv = ColorMath.hsv(color);
    hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
    error = "";
    syncHex();
  }

  private void syncHex() {
    if (hex == null || syncing) return;
    syncing = true;
    hex.setValue(ColorMath.hex(rgb));
    syncing = false;
    error = "";
  }

  @Override
  protected int contentWidth() {
    return Math.min(540, width - 24);
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth(), area = Math.max(36, Math.min(130, height - 152));
    int paletteWidth = w * 2 / 3 - 8, side = x + paletteWidth + 12;
    addRenderableWidget(new ColorField(x, 43, paletteWidth, area, false));
    addRenderableWidget(new ColorField(x, 49 + area, paletteWidth, 14, true));
    int cell = Math.max(8, Math.min(24, (w - paletteWidth - 20) / 4));
    for (int i = 0; i < PRESETS.length; i++) {
      final int color = PRESETS[i];
      int bx = side + i % 4 * (cell + 2), by = 43 + i / 4 * (cell + 3);
      var swatch = new GlassButton(bx, by, cell, cell, net.minecraft.network.chat.Component.literal(
          ColorMath.hex(color)), () -> setRGB(color)) {
        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
          g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000 | color);
          if (rgb == color || isHoveredOrFocused()) g.renderOutline(getX(), getY(), width, height, 0xFFFFFFFF);
        }
      };
      swatch.setTooltip(Tooltip.create(net.minecraft.network.chat.Component.literal(ColorMath.hex(color))));
      addRenderableWidget(swatch);
    }
    int hexY = Math.max(43 + area + 30, height - 78);
    hex = new EditBox(font, x + 38, hexY, Math.min(120, w - 48), 20, tr("color.hex"));
    hex.setTooltip(Tooltip.create(tr("color.hex.help")));
    hex.setMaxLength(7);
    hex.setValue(ColorMath.hex(rgb));
    hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
    hex.setResponder(value -> {
      if (syncing) return;
      String digits = value.startsWith("#") ? value.substring(1) : value;
      if (digits.length() == 6) {
        rgb = Integer.parseInt(digits, 16);
        float[] hsv = ColorMath.hsv(rgb);
        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
        error = "";
      } else error = tr("color.invalid").getString();
    });
    addRenderableWidget(hex);
    sample = new ArrayList<>();
    for (int i = 0; i <= 48; i++) sample.add(new GesturePoint(
        side + i * Math.max(1, w - paletteWidth - 24) / 48.0,
        Math.max(43 + 3 * (cell + 3) + 12, Math.min(hexY - 13, 43 + area)) + Math.sin(i * Math.PI * 2 / 48) * 7, 0));
    int half = (w - 8) / 2;
    button(x, height - 32, half, 22, "cancel", this::onClose);
    button(x + half + 8, height - 32, half, 22, "done", () -> {
      if (!error.isBlank()) return;
      owner.setColor(trail, rgb);
      onClose();
    });
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    heading(g);
    if (hex != null) g.drawString(font, tr("color.hex"), left(), hex.getY() + 6, 0xFFE5F8FF, false);
    GlassRenderer.trail(g, sample);
  }

  private final class ColorField extends AbstractWidget {
    private final boolean hueOnly;
    ColorField(int x, int y, int w, int h, boolean hueOnly) {
      super(x, y, w, h, tr(hueOnly ? "color.hue" : "color.saturation"));
      this.hueOnly = hueOnly;
    }

    private void pick(double mx, double my) {
      if (hueOnly) hue = (float) Math.clamp((mx - getX()) / Math.max(1, width - 1), 0, .99999);
      else {
        saturation = (float) Math.clamp((mx - getX()) / Math.max(1, width - 1), 0, 1);
        brightness = 1 - (float) Math.clamp((my - getY()) / Math.max(1, height - 1), 0, 1);
      }
      rgb = ColorMath.hsv(hue, saturation, brightness);
      syncHex();
    }

    @Override public void onClick(double x, double y) { pick(x, y); }
    @Override protected void onDrag(double x, double y, double dx, double dy) { pick(x, y); }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
      if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT
          && key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN) return false;
      float delta = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN ? -.01f : .01f;
      if (hueOnly) hue = Math.clamp(hue + delta, 0, .99999f);
      else if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)
        saturation = Math.clamp(saturation + delta, 0, 1);
      else brightness = Math.clamp(brightness + delta, 0, 1);
      rgb = ColorMath.hsv(hue, saturation, brightness);
      syncHex();
      return true;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      // At most 64 batched quads; no textures are allocated while dragging.
      int strips = Math.min(64, width);
      for (int i = 0; i < strips; i++) {
        int a = getX() + i * width / strips, b = getX() + (i + 1) * width / strips;
        float t = i / (float) (strips - 1);
        if (hueOnly) g.fill(a, getY(), b, getY() + height, 0xFF000000 | ColorMath.hsv(t, 1, 1));
        else g.fillGradient(a, getY(), b, getY() + height,
            0xFF000000 | ColorMath.hsv(hue, t, 1), 0xFF000000);
      }
      int px = getX() + Math.round((hueOnly ? hue : saturation) * (width - 1));
      int py = hueOnly ? getY() + height / 2 : getY() + Math.round((1 - brightness) * (height - 1));
      g.renderOutline(px - 3, py - 3, 7, 7, 0xFF000000);
      g.renderOutline(px - 2, py - 2, 5, 5, 0xFFFFFFFF);
    }
  }
}
