package com.pureneko.gesturekeys.input;

import static org.junit.jupiter.api.Assertions.*;

import com.pureneko.gesturekeys.config.GestureConfig.Action;
import com.pureneko.gesturekeys.config.GestureConfig.Kind;
import com.pureneko.gesturekeys.config.GestureConfig.PressMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionSequenceTest {
  private static Action key(String id, int gap) {
    Action a = new Action();
    a.keyId = id;
    a.delayTicks = gap;
    return a;
  }

  private static Action sequence(Action... steps) {
    Action a = new Action();
    a.kind = Kind.SEQUENCE;
    a.steps = new ArrayList<>(List.of(steps));
    return a;
  }

  @Test
  void zeroGapStillSeparatesHotbarSwapAndUseAcrossInputCycles() {
    var runner = new ActionSequence.Runner();
    runner.start(ActionSequence.snapshot(sequence(key("key.hotbar.2", 0),
        key("key.swapOffhand", 0), key("key.use", 0))), 10);
    assertEquals("key.hotbar.2", runner.poll(10).keyId());
    assertNull(runner.poll(10));
    assertEquals("key.swapOffhand", runner.poll(11).keyId());
    assertEquals("key.use", runner.poll(12).keyId());
    assertNull(runner.poll(13));
    assertFalse(runner.active());
  }

  @Test
  void holdCompletesBeforeGapAndExplicitWait() {
    Action hold = key("key.jump", 4);
    hold.mode = PressMode.HOLD;
    hold.ticks = 20;
    Action wait = new Action();
    wait.kind = Kind.WAIT;
    wait.ticks = 10;
    wait.delayTicks = 4;
    var runner = new ActionSequence.Runner();
    runner.start(ActionSequence.snapshot(sequence(hold, wait, key("key.use", 2))), 0);
    assertNotNull(runner.poll(0));
    assertNull(runner.poll(20));
    assertNull(runner.poll(23));
    assertEquals(Kind.WAIT, runner.poll(24).kind());
    assertNull(runner.poll(35));
    assertEquals("key.use", runner.poll(36).keyId());
  }

  @Test
  void restartDuringWaitDiscardsOldTailAndRunsFirstStepImmediately() {
    var runner = new ActionSequence.Runner();
    runner.start(ActionSequence.snapshot(sequence(key("old", 0), key("never", 200))), 0);
    assertNotNull(runner.poll(0));
    runner.cancel();
    assertNull(runner.poll(400));
    runner.start(ActionSequence.snapshot(key("new", 200)), 401);
    assertEquals("new", runner.poll(401).keyId());
    assertNull(runner.poll(402));
    assertFalse(runner.active());
  }

  @Test
  void executionSnapshotCannotBeChangedByEditorOrSourceList() {
    Action use = key("key.use", 4);
    Action sequence = sequence(use);
    var snapshot = ActionSequence.snapshot(sequence);
    use.keyId = "key.inventory";
    sequence.steps.clear();
    assertEquals("key.use", snapshot.getFirst().keyId());
    assertThrows(UnsupportedOperationException.class, snapshot::clear);
  }

  @Test
  void malformedLaterStepIsRejectedBeforeAnyExecution() {
    Action command = new Action();
    command.kind = Kind.COMMAND;
    command.command = "/   ";
    var invalid = assertThrows(ActionSequence.InvalidStep.class,
        () -> ActionSequence.snapshot(sequence(key("key.use", 0), command)));
    assertEquals(2, invalid.step);
    assertThrows(ActionSequence.InvalidStep.class, () -> ActionSequence.snapshot(sequence()));
    assertThrows(ActionSequence.InvalidStep.class,
        () -> ActionSequence.snapshot(sequence(sequence(key("nested", 0)))));
  }

  @Test
  void validatesSecondsLimitsAndLegacySingleActions() {
    Action wait = new Action();
    wait.kind = Kind.WAIT;
    wait.ticks = 0;
    assertEquals(0, ActionSequence.snapshot(wait).getFirst().ticks());
    wait.ticks = 201;
    assertThrows(ActionSequence.InvalidStep.class, () -> ActionSequence.snapshot(wait));
    Action hold = key("key.use", 4);
    hold.mode = PressMode.HOLD;
    hold.ticks = 0;
    assertThrows(ActionSequence.InvalidStep.class, () -> ActionSequence.snapshot(hold));
    assertEquals(1, ActionSequence.snapshot(key("key.inventory", 4)).size());
  }
}
