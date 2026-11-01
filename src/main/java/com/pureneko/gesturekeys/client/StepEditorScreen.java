package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.config.ConfigStore;
import com.pureneko.gesturekeys.config.GestureConfig.Action;
import com.pureneko.gesturekeys.config.GestureConfig.Kind;
import com.pureneko.gesturekeys.config.GestureConfig.PressMode;
import com.pureneko.gesturekeys.input.ActionExecutor;
import java.math.BigDecimal;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StepEditorScreen extends GlassScreen {
  private final Action draft;
  private final Consumer<Action> choose;
  private final int index;
  private String gap, duration;

  private int durationY() {
    return height < 300 ? 129 : 154;
  }

  private int gapY() {
    return height < 300 ? 165 : 202;
  }

  public StepEditorScreen(Screen parent, Action action, int index, Consumer<Action> choose) {
    super(Component.translatable("gesturekeys.sequence.step", index + 1), parent);
    draft = ConfigStore.GSON.fromJson(ConfigStore.GSON.toJson(action), Action.class);
    this.index = index;
    this.choose = choose;
    gap = ActionExecutor.seconds(draft.delayTicks);
    duration = ActionExecutor.seconds(draft.ticks);
  }

  private EditBox field(int y, String value, int max, Component label, Consumer<String> change) {
    EditBox field = new EditBox(font, left() + 8, y + 6, contentWidth() - 16, 14, label);
    field.setBordered(false);
    field.setMaxLength(max);
    field.setValue(value);
    field.setResponder(change);
    field.setTooltip(Tooltip.create(tr(max == 2048 ? "command.local" : "seconds.hint")));
    return addRenderableWidget(field);
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth();
    button(x, height < 300 ? 42 : 47, w, height < 300 ? 20 : 24, "action." + switch (draft.kind) {
      case COMMAND -> "command";
      case WAIT -> "wait";
      default -> "key";
    }, () -> {
      draft.kind = switch (draft.kind) {
        case KEY -> Kind.COMMAND;
        case COMMAND -> Kind.WAIT;
        default -> Kind.KEY;
      };
      rebuildWidgets();
    });
    if (draft.kind == Kind.KEY) {
      addRenderableWidget(new GlassButton(x, height < 300 ? 68 : 79, w, height < 300 ? 20 : 24, Component.translatable(draft.keyId),
          () -> minecraft.setScreen(new KeyPickerScreen(this, key -> draft.keyId = key.getName()))));
      button(x, height < 300 ? 94 : 111, w, height < 300 ? 20 : 24, draft.mode == PressMode.HOLD ? "action.hold" : "action.click", () -> {
        draft.mode = draft.mode == PressMode.HOLD ? PressMode.CLICK : PressMode.HOLD;
        rebuildWidgets();
      });
    } else if (draft.kind == Kind.COMMAND)
      field(79, draft.command, 2048, tr("action.command"), text -> draft.command = text);
    if (draft.kind == Kind.WAIT || (draft.kind == Kind.KEY && draft.mode == PressMode.HOLD))
      field(durationY(), duration, 8, tr("sequence.duration"), text -> duration = text);
    if (index > 0) field(gapY(), gap, 8, tr("sequence.gap"), text -> gap = text);
    button(x, height - 32, (w - 8) / 2, 22, "cancel", this::onClose);
    button(x + (w + 8) / 2, height - 32, (w - 8) / 2, 22, "done", this::commit);
  }

  private static int ticks(String text, int minimum) {
    int ticks = new BigDecimal(text.strip()).multiply(BigDecimal.valueOf(20)).intValueExact();
    if (ticks < minimum || ticks > 200) throw new NumberFormatException();
    return ticks;
  }

  private void commit() {
    try {
      if (index > 0) draft.delayTicks = ticks(gap, 0);
      if (draft.kind == Kind.WAIT || (draft.kind == Kind.KEY && draft.mode == PressMode.HOLD))
        draft.ticks = ticks(duration, draft.kind == Kind.WAIT ? 0 : 1);
    } catch (ArithmeticException | NumberFormatException e) {
      error = tr("seconds.hint").getString();
      return;
    }
    if (draft.kind == Kind.COMMAND && draft.command.strip().replaceFirst("^/", "").isBlank()) {
      error = tr("error.empty_command").getString();
      return;
    }
    choose.accept(draft);
    onClose();
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    heading(g);
    if (draft.kind == Kind.COMMAND) {
      GlassRenderer.panel(g, left(), 79, contentWidth(), 24, 8, 0);
      g.drawString(font, GlassScreen.clip(font, tr("command.local"), contentWidth()), left(), 115, 0xFF9DBECB, false);
    }
    if (draft.kind == Kind.WAIT || (draft.kind == Kind.KEY && draft.mode == PressMode.HOLD)) {
      g.drawString(font, tr("sequence.duration"), left(), durationY() - 11, 0xFF9DBECB, false);
      GlassRenderer.panel(g, left(), durationY(), contentWidth(), 24, 8, 0);
    }
    if (index > 0) {
      g.drawString(font, GlassScreen.clip(font, tr("sequence.gap"), contentWidth()), left(), gapY() - 11, 0xFF9DBECB, false);
      GlassRenderer.panel(g, left(), gapY(), contentWidth(), 24, 8, 0);
    }
  }
}
