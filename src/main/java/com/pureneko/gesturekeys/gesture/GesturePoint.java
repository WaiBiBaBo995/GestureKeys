package com.pureneko.gesturekeys.gesture;

/** Stroke IDs prevent pen-up movement from becoming an artificial line. */
public record GesturePoint(double x, double y, int stroke) {
  public boolean finite() {
    return Double.isFinite(x) && Double.isFinite(y);
  }
}
