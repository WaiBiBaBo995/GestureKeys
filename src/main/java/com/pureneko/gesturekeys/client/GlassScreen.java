package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.GestureKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class GlassScreen extends Screen {
  protected final Screen parent;
  protected String error = "";
  private final long opened = System.nanoTime();

  protected GlassScreen(Component title, Screen parent) {
    super(title);
    this.parent = parent;
  }

  @Override
  public final void render(GuiGraphics g, int mx, int my, float partial) {
    if (minecraft.level == null) renderPanorama(g, partial);
    Appearance appearance = appearance();
    float t =
        appearance.animations()
            ? Math.clamp((System.nanoTime() - opened) / 200_000_000f, 0, 1)
            : 1;
    renderGlassBackground(g, 1 - (float) Math.pow(1 - t, 3), appearance);
    content(g, mx, my, partial);
    super.render(g, mx, my, partial);
    if (!error.isBlank()) {
      g.drawCenteredString(
          font,
          GlassScreen.clip(font, Component.literal(error), width - 24),
          width / 2,
          height - 46,
          0xFFFFA99A);
    }
  }

  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float partial) {}

  protected void renderGlassBackground(GuiGraphics g, float progress, Appearance appearance) {
    GlassRenderer.begin(g, width, height, progress, appearance);
  }

  public Appearance appearance() {
    return Appearance.from(GestureKeys.STORE.get());
  }

  protected abstract void content(GuiGraphics g, int mx, int my, float partial);

  protected int left() {
    return (width - contentWidth()) / 2;
  }

  protected int contentWidth() {
    return Math.min(720, width - 24);
  }

  protected Component tr(String key, Object... args) {
    return Component.translatable("gesturekeys." + key, args);
  }

  protected GlassButton button(int x, int y, int w, int h, String key, Runnable action) {
    return addRenderableWidget(new GlassButton(x, y, w, h, tr(key), action));
  }

  protected void heading(GuiGraphics g) {
    g.drawString(font, title, left() + 1, 18, 0xFFF0FBFF, false);
    g.fill(left(), 35, left() + contentWidth(), 36, GlassRenderer.accent(0x44, 1));
  }

  public static net.minecraft.util.FormattedCharSequence clip(
      net.minecraft.client.gui.Font font, Component text, int width) {
    return net.minecraft.locale.Language.getInstance()
        .getVisualOrder(font.substrByWidth(text, Math.max(1, width)));
  }

  protected boolean save() {
    try {
      GestureKeys.STORE.save();
      GestureKeys.rebuildTemplates();
      return true;
    } catch (Exception e) {
      GestureKeys.LOGGER.error("Could not save GestureKeys config", e);
      error = tr("error.save", e.getMessage()).getString();
      return false;
    }
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent);
  }
}
