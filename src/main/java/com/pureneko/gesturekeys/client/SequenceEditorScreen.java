package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.config.ConfigStore;
import com.pureneko.gesturekeys.config.GestureConfig.Action;
import com.pureneko.gesturekeys.config.GestureConfig.Kind;
import com.pureneko.gesturekeys.input.ActionExecutor;
import com.pureneko.gesturekeys.input.ActionSequence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edits an isolated draft; only Done publishes it to the gesture editor. */
public final class SequenceEditorScreen extends GlassScreen {
  private final List<Action> steps = new ArrayList<>();
  private final Consumer<Action> saveDraft;
  private int page, perPage;

  public SequenceEditorScreen(Screen parent, Action action, Consumer<Action> saveDraft) {
    super(Component.translatable("gesturekeys.sequence.edit"), parent);
    this.saveDraft = saveDraft;
    Action copy = ConfigStore.GSON.fromJson(ConfigStore.GSON.toJson(action), Action.class);
    if (copy.kind == Kind.SEQUENCE) {
      if (copy.steps != null) steps.addAll(copy.steps);
    } else steps.add(copy);
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth(), third = (w - 12) / 3;
    perPage = Math.max(1, (height - 140) / 44);
    page = Math.clamp(page, 0, pages() - 1);
    Kind[] kinds = {Kind.KEY, Kind.COMMAND, Kind.WAIT};
    String[] labels = {"sequence.add_key", "sequence.add_command", "sequence.add_wait"};
    for (int i = 0; i < kinds.length; i++) {
      Kind kind = kinds[i];
      button(x + i * (third + 6), 47, third, 24, labels[i], () -> {
        Action action = new Action();
        action.kind = kind;
        if (kind == Kind.WAIT) action.ticks = 4;
        minecraft.setScreen(new StepEditorScreen(this, action, steps.size(), edited -> {
          steps.add(edited);
          page = (steps.size() - 1) / perPage;
        }));
      });
    }
    for (int row = 0; row < perPage && page * perPage + row < steps.size(); row++) {
      int index = page * perPage + row;
      Action step = steps.get(index);
      int y = 80 + row * 44, controls = x + w - 80;
      Component name = step == null || step.kind == Kind.SEQUENCE ? tr("sequence.invalid") : ActionExecutor.describe(step);
      Component timing = index == 0 ? tr("sequence.first")
          : tr("sequence.gap_time", ActionExecutor.seconds(step == null ? 4 : step.delayTicks));
      addRenderableWidget(new GlassButton(x, y, w - 86, 38,
          Component.literal((index + 1) + ". ").append(name), () -> {
            minecraft.setScreen(new StepEditorScreen(this, step == null || step.kind == null || step.kind == Kind.SEQUENCE ? new Action() : step,
                index, edited -> steps.set(index, edited)));
          }).secondary(timing, null));
      button(controls, y, 24, 38, "sequence.up", () -> move(index, -1)).active = index > 0;
      button(controls + 28, y, 24, 38, "sequence.down", () -> move(index, 1)).active = index + 1 < steps.size();
      button(controls + 56, y, 24, 38, "sequence.remove", () -> {
        steps.remove(index);
        rebuildWidgets();
      });
    }
    button(x, height - 32, 40, 22, "previous", () -> changePage(-1)).active = page > 0;
    button(x + 46, height - 32, 40, 22, "next", () -> changePage(1)).active = page + 1 < pages();
    button(x + w - 136, height - 32, 64, 22, "cancel", this::onClose);
    button(x + w - 66, height - 32, 66, 22, "done", this::commit);
  }

  private int pages() {
    return Math.max(1, (steps.size() + Math.max(1, perPage) - 1) / Math.max(1, perPage));
  }

  private void move(int index, int direction) {
    Collections.swap(steps, index, index + direction);
    page = (index + direction) / perPage;
    rebuildWidgets();
  }

  private void changePage(int direction) {
    int target = Math.clamp(page + direction, 0, pages() - 1);
    if (target != page) {
      page = target;
      rebuildWidgets();
    }
  }

  private void commit() {
    Action action = new Action();
    action.kind = Kind.SEQUENCE;
    action.steps = new ArrayList<>(steps);
    try {
      ActionSequence.snapshot(action);
    } catch (ActionSequence.InvalidStep e) {
      error = tr("error.step", e.step, tr(e.translation)).getString();
      return;
    }
    saveDraft.accept(action);
    onClose();
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
    changePage(vertical < 0 ? 1 : -1);
    return true;
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    heading(g);
    g.drawString(font, GlassScreen.clip(font, tr("sequence.count", steps.size(), page + 1, pages()), contentWidth()),
        left(), height - 60, 0xFF9DBECB, false);
    if (steps.isEmpty()) g.drawCenteredString(font, tr("sequence.empty"), width / 2, 90, 0xFF9DBECB);
  }
}
