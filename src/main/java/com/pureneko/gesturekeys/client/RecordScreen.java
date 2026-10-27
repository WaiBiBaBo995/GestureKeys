package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.gesture.Drawing;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import com.pureneko.gesturekeys.gesture.GestureRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class RecordScreen extends GlassScreen {
  private final Drawing drawing = new Drawing();
  private final GestureConfig.Binding draft;
  private final boolean testing;
  private final Consumer<List<GesturePoint>> accept;
  private Component result = Component.empty();

  public RecordScreen(
      EditGestureScreen parent,
      GestureConfig.Binding draft,
      boolean testing,
      Consumer<List<GesturePoint>> accept) {
    super(Component.translatable(testing ? "gesturekeys.test" : "gesturekeys.record"), parent);
    this.draft = draft;
    this.testing = testing;
    this.accept = accept;
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth(), b = (w - 12) / 3;
    button(
        x,
        height - 32,
        b,
        22,
        "clear",
        () -> {
          drawing.clear();
          result = Component.empty();
        });
    button(x + b + 6, height - 32, b, 22, "cancel", this::onClose);
    button(
        x + 2 * (b + 6),
        height - 32,
        b,
        22,
        testing ? "test" : "record.done",
        () -> {
          if (!drawing.usable()) {
            error = tr("error.short").getString();
            return;
          }
          error = "";
          if (!testing) {
            accept.accept(drawing.points());
            onClose();
            return;
          }
          List<GestureRecognizer.Template> templates =
              new ArrayList<>(
                  GestureKeys.templates().stream().filter(t -> !t.id().equals(draft.id)).toList());
          templates.add(
              new GestureRecognizer.Template(
                  draft.id, draft.samples.stream().map(GestureRecognizer::normalize).toList()));
          var match =
              GestureRecognizer.recognize(
                  drawing.points(),
                  templates,
                  GestureKeys.STORE.get().threshold,
                  GestureKeys.STORE.get().ambiguityMargin);
          String name =
              match.id() != null && match.id().equals(draft.id)
                  ? draft.name
                  : GestureKeys.STORE.get().gestures.stream()
                      .filter(bd -> bd.id.equals(match.id()))
                      .map(bd -> bd.name)
                      .findFirst()
                      .orElse("?");
          result =
              tr(
                  "test.result",
                  name,
                  Math.round(match.score() * 100),
                  tr("match." + match.status().name().toLowerCase(Locale.ROOT)));
        });
  }

  private boolean inside(double x, double y) {
    return x >= left() + 4 && x <= left() + contentWidth() - 4 && y >= 68 && y <= height - 68;
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    heading(g);
    g.drawCenteredString(font, tr("record.hint"), width / 2, 47, 0xFFB3D2DF);
    GlassRenderer.panel(g, left(), 64, contentWidth(), height - 128, 12, 0);
    g.enableScissor(left() + 4, 68, left() + contentWidth() - 4, height - 68);
    GlassRenderer.trail(g, drawing.points());
    g.disableScissor();
    if (!result.getString().isEmpty())
      g.drawCenteredString(
          font, GlassScreen.clip(font, result, contentWidth()), width / 2, height - 58, 0xFFB0F0D7);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (super.mouseClicked(x, y, button)) return true;
    if (inside(x, y)) {
      if (button == 1) {
        drawing.clear();
        result = Component.empty();
        error = "";
        return true;
      }
      if (button == 0) {
        drawing.begin(x, y);
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (button == 0 && drawing.isDrawing()) {
      drawing.move(
          Math.clamp(x, left() + 4, left() + contentWidth() - 4), Math.clamp(y, 68, height - 68));
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (button == 0) {
      drawing.move(
          Math.clamp(x, left() + 4, left() + contentWidth() - 4), Math.clamp(y, 68, height - 68));
      drawing.end();
      return true;
    }
    return super.mouseReleased(x, y, button);
  }
}
