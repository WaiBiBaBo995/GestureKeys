package com.pureneko.gesturekeys.input;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TickHoldsTest {
  @Test
  void oneTickHoldSurvivesExactlyOneInputCycle() {
    TickHolds<String> holds = new TickHolds<>();
    holds.press("jump", 1, 40);
    holds.advance(40);
    assertTrue(holds.isDown("jump"));
    holds.advance(41);
    assertFalse(holds.isDown("jump"));
  }

  @Test
  void retriggerRestartsDeadline() {
    TickHolds<String> holds = new TickHolds<>();
    holds.press("jump", 20, 0);
    holds.press("jump", 20, 15);
    holds.advance(20);
    assertTrue(holds.isDown("jump"));
    holds.advance(35);
    assertFalse(holds.isDown("jump"));
  }

  @Test
  void cancelReleasesEveryActionAndDurationIsClamped() {
    TickHolds<String> holds = new TickHolds<>();
    holds.press("a", 9999, 1);
    holds.press("b", -1, 1);
    holds.advance(2);
    assertTrue(holds.isDown("a"));
    assertFalse(holds.isDown("b"));
    holds.clear();
    assertFalse(holds.isDown("a"));
  }
}
