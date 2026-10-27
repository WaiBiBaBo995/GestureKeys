package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.util.ArrayList;
import java.util.List;

/** Display-only corner-preserving smoothing. Never fed back into recognition or saved samples. */
public final class TrailMesh {
  private TrailMesh() {}

  public static List<GesturePoint> smooth(List<GesturePoint> input) {
    if (input.size() < 3) return input;
    List<GesturePoint> out = new ArrayList<>(input.size() * 2);
    for (int i = 0; i < input.size(); i++) {
      GesturePoint b = input.get(i);
      if (i == 0 || i == input.size() - 1
          || input.get(i - 1).stroke() != b.stroke() || input.get(i + 1).stroke() != b.stroke()) {
        out.add(b);
        continue;
      }
      GesturePoint a = input.get(i - 1), c = input.get(i + 1);
      double ax = b.x() - a.x(), ay = b.y() - a.y(), bx = c.x() - b.x(), by = c.y() - b.y();
      double la = Math.hypot(ax, ay), lb = Math.hypot(bx, by);
      // Keep turns of 45 degrees or sharper, plus endpoints, exactly where drawn.
      if (la < .01 || lb < .01 || (ax * bx + ay * by) / (la * lb) < .70710678) out.add(b);
      else {
        double trim = Math.min(3, Math.min(la, lb) * .22);
        double sx = b.x() - ax * trim / la, sy = b.y() - ay * trim / la;
        double ex = b.x() + bx * trim / lb, ey = b.y() + by * trim / lb;
        out.add(new GesturePoint(sx, sy, b.stroke()));
        out.add(new GesturePoint((sx + 2 * b.x() + ex) / 4, (sy + 2 * b.y() + ey) / 4, b.stroke()));
        out.add(new GesturePoint(ex, ey, b.stroke()));
      }
    }
    return out;
  }
}
