package com.pureneko.gesturekeys.gesture;

import java.util.ArrayList;
import java.util.List;

public final class Drawing {
  public static final int MAX_POINTS = 4096;
  private final List<GesturePoint> points = new ArrayList<>();
  private int stroke;
  private boolean drawing;
  private long revision;

  public void begin(double x, double y) {
    if (points.size() >= MAX_POINTS) return;
    stroke++;
    drawing = true;
    append(x, y);
  }

  public void move(double x, double y) {
    if (!drawing || points.size() >= MAX_POINTS) return;
    GesturePoint last = points.getLast();
    if (Math.hypot(last.x() - x, last.y() - y) >= 1.5) append(x, y);
  }

  private void append(double x, double y) {
    if (!Double.isFinite(x) || !Double.isFinite(y)) return;
    points.add(new GesturePoint(x, y, stroke));
    revision++;
  }

  public void end() {
    drawing = false;
  }

  public void clear() {
    points.clear();
    drawing = false;
    stroke = 0;
    revision++;
  }

  public List<GesturePoint> points() {
    return List.copyOf(points);
  }

  public boolean isDrawing() {
    return drawing;
  }

  public long revision() {
    return revision;
  }

  public boolean usable() {
    return GestureRecognizer.pathLength(points) >= 14;
  }
}
