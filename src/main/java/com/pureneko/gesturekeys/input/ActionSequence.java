package com.pureneko.gesturekeys.input;

import com.pureneko.gesturekeys.config.GestureConfig.Action;
import com.pureneko.gesturekeys.config.GestureConfig.Kind;
import com.pureneko.gesturekeys.config.GestureConfig.PressMode;
import java.util.ArrayList;
import java.util.List;

/** Immutable execution snapshot, independent of the editor and Minecraft's input loop. */
public final class ActionSequence {
  public record Step(Kind kind, String keyId, String command, PressMode mode, int ticks,
                     int delayTicks) {
    public int duration() {
      return switch (kind) {
        case KEY -> mode == PressMode.HOLD ? ticks : 1;
        case WAIT -> ticks;
        default -> 1;
      };
    }
  }

  public static final class InvalidStep extends IllegalArgumentException {
    public final int step;
    public final String translation;

    public InvalidStep(int step, String translation) {
      super(translation);
      this.step = step;
      this.translation = translation;
    }
  }

  private ActionSequence() {}

  public static List<Step> snapshot(Action action) {
    if (action == null || action.kind == null) throw new InvalidStep(1, "sequence.invalid");
    List<Action> actions = action.kind == Kind.SEQUENCE ? action.steps : List.of(action);
    if (actions == null || actions.isEmpty()) throw new InvalidStep(1, "sequence.empty");
    var result = new ArrayList<Step>();
    for (int i = 0; i < actions.size(); i++) {
      Action a = actions.get(i);
      if (a == null || a.kind == null || a.kind == Kind.SEQUENCE)
        throw new InvalidStep(i + 1, "sequence.invalid");
      if (a.delayTicks < 0 || a.delayTicks > 200)
        throw new InvalidStep(i + 1, "seconds.hint");
      if (a.kind == Kind.KEY && (a.keyId == null || a.keyId.isBlank() || a.mode == null))
        throw new InvalidStep(i + 1, "sequence.invalid");
      if ((a.kind == Kind.WAIT && (a.ticks < 0 || a.ticks > 200))
          || (a.kind == Kind.KEY && a.mode == PressMode.HOLD && (a.ticks < 1 || a.ticks > 200)))
        throw new InvalidStep(i + 1, "seconds.hint");
      String command = a.command == null ? "" : a.command.strip().replaceFirst("^/", "");
      if (a.kind == Kind.COMMAND && command.isBlank())
        throw new InvalidStep(i + 1, "error.empty_command");
      result.add(new Step(a.kind, a.keyId, command, a.mode, a.ticks, a.delayTicks));
    }
    return List.copyOf(result);
  }

  /** At most one step per client tick, including when all configured gaps are zero. */
  public static final class Runner {
    private List<Step> steps = List.of();
    private int index;
    private long due, lastPoll = Long.MIN_VALUE;

    public void start(List<Step> steps, long tick) {
      this.steps = List.copyOf(steps);
      index = 0;
      due = tick;
      lastPoll = Long.MIN_VALUE;
    }

    public Step poll(long tick) {
      if (tick < due || tick == lastPoll) return null;
      lastPoll = tick;
      if (index >= steps.size()) {
        cancel();
        return null;
      }
      Step step = steps.get(index++);
      due = tick + Math.max(1, step.duration());
      if (index < steps.size()) due += steps.get(index).delayTicks();
      return step;
    }

    public int stepNumber() {
      return index;
    }

    public boolean active() {
      return !steps.isEmpty();
    }

    public void cancel() {
      steps = List.of();
      index = 0;
    }
  }
}
