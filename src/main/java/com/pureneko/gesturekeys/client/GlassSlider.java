package com.pureneko.gesturekeys.client;

import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Integer-percent slider with the same glass finish as the other controls. */
public final class GlassSlider extends AbstractSliderButton {
  private final String key;
  private final int min, max;
  private final IntConsumer changed;

  public GlassSlider(
      int x, int y, int width, int height, String key, int min, int max, double initial,
      IntConsumer changed) {
    super(x, y, width, height, Component.empty(),
        Math.clamp((Math.round(initial * 100) - min) / (double) (max - min), 0, 1));
    this.key = key;
    this.min = min;
    this.max = max;
    this.changed = changed;
    updateMessage();
  }

  private int percent() {
    return min + (int) Math.round(value * (max - min));
  }

  @Override
  protected void updateMessage() {
    setMessage(Component.translatable("gesturekeys." + key, percent()));
  }

  @Override
  protected void applyValue() {
    int percent = percent();
    value = (percent - min) / (double) (max - min);
    changed.accept(percent);
  }

  @Override
  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (active && isFocused()
        && (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT)) {
      int next = Math.clamp(percent() + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1), min, max);
      value = (next - min) / (double) (max - min);
      applyValue();
      updateMessage();
      return true;
    }
    return super.keyPressed(keyCode, scanCode, modifiers);
  }

  @Override
  public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
    GlassRenderer.panel(g, getX(), getY(), width, height, 10,
        isHoveredOrFocused() && active ? 1 : 0);
    var font = Minecraft.getInstance().font;
    var text = GlassScreen.clip(font, getMessage(), width - 16);
    g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + 4,
        active ? 0xFFE5F8FF : 0xFF788D9B, false);
    int start = getX() + 4, end = getX() + width - 4, y = getY() + height - 6;
    int handle = start + (int) Math.round(value * (end - start));
    g.fill(start, y, end, y + 2, GlassRenderer.accent(0x70, .55));
    g.fill(start, y, handle, y + 2, active ? GlassRenderer.accent(0xFF, 1) : 0xFF788D9B);
    GlassRenderer.rounded(g, handle - 3, y - 2, 6, 6, 3,
        active ? 0xFFE5F8FF : 0xFF788D9B);
  }
}
