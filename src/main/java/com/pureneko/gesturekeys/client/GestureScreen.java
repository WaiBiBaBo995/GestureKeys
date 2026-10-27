package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.gesture.Drawing;
import com.pureneko.gesturekeys.gesture.GestureRecognizer;
import com.pureneko.gesturekeys.input.ActionExecutor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class GestureScreen extends GlassScreen {
  private final Drawing drawing = new Drawing();
  private GestureRecognizer.Result match;
  private long matchedRevision = -1, lastMatch;
  private boolean finished;

  public GestureScreen() {
    super(Component.literal("GestureKeys"), null);
  }

  @Override
  protected void init() {
    var gear =
        addRenderableWidget(
            new GlassButton(
                    12,
                    height - 42,
                    30,
                    30,
                    tr("settings"),
                    () -> {
                      finished = true;
                      drawing.clear();
                      minecraft.setScreen(new SettingsScreen(null));
                    })
                .gear());
    gear.setTooltip(Tooltip.create(tr("settings")));
  }

  private void updateMatch(boolean force) {
    long now = System.nanoTime();
    if (!force && (matchedRevision == drawing.revision() || now - lastMatch < 70_000_000L)) return;
    match =
        drawing.usable()
            ? GestureRecognizer.recognize(
                drawing.points(),
                GestureKeys.templates(),
                GestureKeys.STORE.get().threshold,
                GestureKeys.STORE.get().ambiguityMargin)
            : null;
    matchedRevision = drawing.revision();
    lastMatch = now;
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    updateMatch(false);
    GlassRenderer.trail(g, drawing.points());
    if (match == null) return;
    int w = Math.min(350, width - 80), x = (width - w) / 2, y = height - 65;
    GlassRenderer.panel(g, x, y, w, 49, 12, 0);
    Component line = tr("gesture.unrecognized");
    Component detail = Component.empty();
    int color = 0xFFCCE5EE;
    if (match != null && match.id() != null) {
      var binding =
          GestureKeys.STORE.get().gestures.stream()
              .filter(b -> b.id.equals(match.id()))
              .findFirst()
              .orElse(null);
      if (binding != null) {
        line = Component.literal(binding.name + "  " + Math.round(match.score() * 100) + "%");
        detail =
            match.accepted()
                ? ActionExecutor.describe(binding.action)
                : tr("match." + match.status().name().toLowerCase(java.util.Locale.ROOT));
        color = match.accepted() ? 0xFF9FF1D8 : 0xFFFFC18C;
      }
    }
    g.drawCenteredString(font, GlassScreen.clip(font, line, w - 18), width / 2, y + 10, color);
    g.drawCenteredString(
        font, GlassScreen.clip(font, detail, w - 18), width / 2, y + 29, 0xFFAAC8D5);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (super.mouseClicked(x, y, button)) return true;
    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
      drawing.clear();
      match = null;
      return true;
    }
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      drawing.begin(x, y);
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      drawing.move(x, y);
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      drawing.move(x, y);
      drawing.end();
      return true;
    }
    return super.mouseReleased(x, y, button);
  }

  public void finish() {
    if (finished) return;
    finished = true;
    drawing.end();
    updateMatch(true);
    minecraft.setScreen(null);
    if (drawing.points().isEmpty()) return;
    if (match != null && match.accepted()) {
      GestureKeys.STORE.get().gestures.stream()
          .filter(b -> b.enabled && b.id.equals(match.id()))
          .findFirst()
          .ifPresent(
              b -> ActionExecutor.queue(b.action));
    } else
      ActionExecutor.message(
          match != null && match.status() == GestureRecognizer.Status.AMBIGUOUS
              ? tr("gesture.conflict")
              : tr("gesture.unrecognized"));
  }

  @Override
  public void onClose() {
    finished = true;
    drawing.clear();
    minecraft.setScreen(null);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
