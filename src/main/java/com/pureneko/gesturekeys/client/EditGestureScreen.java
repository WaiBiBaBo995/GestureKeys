package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.config.ConfigStore;
import com.pureneko.gesturekeys.config.GestureConfig;
import com.pureneko.gesturekeys.gesture.GestureRecognizer;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class EditGestureScreen extends GlassScreen {
  private final GestureConfig.Binding draft;
  private String tickValue;
  private boolean deleteArmed;
  private boolean conflict;

  public EditGestureScreen(SettingsScreen parent, GestureConfig.Binding binding) {
    super(Component.translatable(binding == null ? "gesturekeys.add" : "gesturekeys.edit"), parent);
    draft = binding == null ? new GestureConfig.Binding() : ConfigStore.copy(binding);
    tickValue = Integer.toString(draft.action.ticks);
  }

  private EditBox field(
      int x, int y, int w, String value, int max, java.util.function.Consumer<String> change) {
    EditBox box = new EditBox(font, x + 7, y + 6, w - 14, 14, Component.empty());
    box.setBordered(false);
    box.setMaxLength(max);
    box.setValue(value);
    box.setResponder(change);
    return addRenderableWidget(box);
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth(), half = (w - 8) / 2;
    button(
        x + w - 92,
        12,
        92,
        20,
        draft.enabled ? "enabled" : "disabled",
        () -> {
          draft.enabled = !draft.enabled;
          rebuildWidgets();
        });
    field(x, 53, w - 48, draft.name, 64, value -> draft.name = value);
    button(
        x,
        85,
        Math.min(100, w / 3),
        24,
        draft.action.kind == GestureConfig.Kind.KEY ? "action.key" : "action.command",
        () -> {
          draft.action.kind =
              draft.action.kind == GestureConfig.Kind.KEY
                  ? GestureConfig.Kind.COMMAND
                  : GestureConfig.Kind.KEY;
          rebuildWidgets();
        });
    int start = x + Math.min(100, w / 3) + 8, remaining = x + w - start;
    if (draft.action.kind == GestureConfig.Kind.KEY) {
      addRenderableWidget(
          new GlassButton(
              start,
              85,
              remaining,
              24,
              Component.translatable(draft.action.keyId),
              () ->
                  minecraft.setScreen(
                      new KeyPickerScreen(this, key -> draft.action.keyId = key.getName()))));
      button(
          x,
          117,
          half,
          24,
          draft.action.mode == GestureConfig.PressMode.CLICK ? "action.click" : "action.hold",
          () -> {
            draft.action.mode =
                draft.action.mode == GestureConfig.PressMode.CLICK
                    ? GestureConfig.PressMode.HOLD
                    : GestureConfig.PressMode.CLICK;
            rebuildWidgets();
          });
      if (draft.action.mode == GestureConfig.PressMode.HOLD) {
        var ticks =
            field(
                x + half + 8,
                117,
                half,
                tickValue,
                3,
                value -> {
                  tickValue = value;
                  try {
                    draft.action.ticks = Integer.parseInt(value);
                  } catch (NumberFormatException ignored) {
                  }
                });
        ticks.setTooltip(Tooltip.create(tr("ticks.hint")));
      }
    } else
      field(
          start, 85, remaining, draft.action.command, 2048, value -> draft.action.command = value);
    int b = (w - 12) / 3;
    button(
                x,
                153,
                b,
                24,
                "record",
                () ->
                    minecraft.setScreen(
                        new RecordScreen(
                            this,
                            draft,
                            false,
                            points -> {
                              if (draft.samples.size() < 8)
                                draft.samples.add(new ArrayList<>(points));
                            })))
            .active =
        draft.samples.size() < 8;
    button(
                x + b + 6,
                153,
                b,
                24,
                "test",
                () -> minecraft.setScreen(new RecordScreen(this, draft, true, null)))
            .active =
        !draft.samples.isEmpty();
    button(
                x + 2 * (b + 6),
                153,
                b,
                24,
                "sample.remove",
                () -> {
                  if (!draft.samples.isEmpty()) draft.samples.removeLast();
                  rebuildWidgets();
                })
            .active =
        !draft.samples.isEmpty();
    int fw = (w - 12) / 3;
    button(
        x,
        height - 32,
        fw,
        22,
        deleteArmed ? "delete.confirm" : "delete",
        () -> {
          if (!deleteArmed) {
            deleteArmed = true;
            rebuildWidgets();
            return;
          }
          var gestures = GestureKeys.STORE.get().gestures;
          var old = new ArrayList<>(gestures);
          gestures.removeIf(bd -> bd.id.equals(draft.id));
          if (save()) onClose();
          else {
            gestures.clear();
            gestures.addAll(old);
          }
        });
    button(x + fw + 6, height - 32, fw, 22, "cancel", this::onClose);
    button(x + 2 * (fw + 6), height - 32, fw, 22, "save", this::commit);
    conflict = false;
    for (var sample : draft.samples) {
      var result =
          GestureRecognizer.recognize(
              sample,
              GestureKeys.templates().stream().filter(t -> !t.id().equals(draft.id)).toList(),
              .90,
              .01);
      if (result.score() >= .90) conflict = true;
    }
  }

  private void commit() {
    if (draft.name.isBlank()) {
      error = tr("error.name").getString();
      return;
    }
    if (draft.samples.isEmpty()) {
      error = tr("error.sample").getString();
      return;
    }
    if (draft.action.kind == GestureConfig.Kind.COMMAND
        && draft.action.command.strip().replaceFirst("^/", "").isBlank()) {
      error = tr("error.empty_command").getString();
      return;
    }
    if (draft.action.kind == GestureConfig.Kind.KEY
        && draft.action.mode == GestureConfig.PressMode.HOLD) {
      try {
        int ticks = Integer.parseInt(tickValue);
        if (ticks < 1 || ticks > 200) throw new NumberFormatException();
        draft.action.ticks = ticks;
      } catch (NumberFormatException e) {
        error = tr("ticks.hint").getString();
        return;
      }
    }
    var gestures = GestureKeys.STORE.get().gestures;
    int existing = -1;
    for (int i = 0; i < gestures.size(); i++) if (gestures.get(i).id.equals(draft.id)) existing = i;
    if (existing < 0 && gestures.size() >= 128) {
      error = tr("error.limit").getString();
      return;
    }
    var previous = new ArrayList<>(gestures);
    draft.name = draft.name.strip();
    if (existing < 0) gestures.add(ConfigStore.copy(draft));
    else gestures.set(existing, ConfigStore.copy(draft));
    if (save()) onClose();
    else {
      gestures.clear();
      gestures.addAll(previous);
    }
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    int x = left(), w = contentWidth(), half = (w - 8) / 2;
    heading(g);
    g.drawString(font, tr("name"), x, 42, 0xFF9DBECB, false);
    GlassRenderer.panel(g, x, 53, w, 24, 8, 0);
    if (!draft.samples.isEmpty()) {
      GlassButton.miniature(g, draft.samples.getLast(), x + w - 38, 57, 28, 16);
    }
    if (draft.action.kind == GestureConfig.Kind.COMMAND) {
      int start = x + Math.min(100, w / 3) + 8;
      GlassRenderer.panel(g, start, 85, x + w - start, 24, 8, 0);
      g.drawString(font, GlassScreen.clip(font, tr("command.local"), w), x, 124, 0xFF9DBECB, false);
    } else if (draft.action.mode == GestureConfig.PressMode.HOLD) {
      GlassRenderer.panel(g, x + half + 8, 117, half, 24, 8, 0);
      g.drawString(font, "tick", x + w - 31, 125, 0xFF9DBECB, false);
    }
    g.drawString(font, tr("sample.count", draft.samples.size()), x, 143, 0xFF9DBECB, false);
    int ph = height - 226;
    if (ph >= 36) {
      GlassRenderer.panel(g, x, 182, w, ph, 10, 0);
      if (!draft.samples.isEmpty())
        GlassButton.miniature(g, draft.samples.getLast(), x + 12, 188, w - 24, ph - 12);
    }
    if (conflict && error.isBlank())
      g.drawCenteredString(
          font,
          GlassScreen.clip(font, tr("sample.conflict"), w),
          width / 2,
          height - 45,
          0xFFFFC48C);
  }
}
