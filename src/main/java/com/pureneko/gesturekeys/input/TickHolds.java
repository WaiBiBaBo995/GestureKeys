package com.pureneko.gesturekeys.input;

import java.util.HashMap;
import java.util.Map;

/** Deadlines are client ticks, so even a one-tick hold spans one complete input cycle. */
public final class TickHolds<K> {
  private final Map<K, Long> deadlines = new HashMap<>();

  public void press(K key, int duration, long tick) {
    deadlines.put(key, tick + Math.clamp(duration, 1, 200));
  }

  public void advance(long tick) {
    deadlines.values().removeIf(deadline -> tick >= deadline);
  }

  public boolean isDown(K key) {
    return deadlines.containsKey(key);
  }

  public void clear() {
    deadlines.clear();
  }
}
