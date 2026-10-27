package com.pureneko.gesturekeys.client;

import static org.junit.jupiter.api.Assertions.*;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class VisualMathTest {
  @Test void hsvRoundTripIncludesBlackWhiteAndSaturatedColors() {
    for (int rgb : new int[] {0, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x80D9EA, 0xED5566}) {
      float[] hsv = ColorMath.hsv(rgb);
      assertEquals(rgb, ColorMath.hsv(hsv[0], hsv[1], hsv[2]));
    }
    assertEquals("#00AABB", ColorMath.hex(0xAABB));
  }

  @Test void smoothingPreservesSharpCornersEndpointsAndPenLifts() {
    var points = List.of(new GesturePoint(0, 0, 1), new GesturePoint(0, 20, 1),
        new GesturePoint(20, 20, 1), new GesturePoint(100, 100, 2), new GesturePoint(120, 100, 2));
    assertEquals(points, TrailMesh.smooth(points));
  }

  @Test void smoothingNeverMutatesRecognitionCoordinatesOrMovesEndpoints() {
    var points = List.of(new GesturePoint(0, 0, 1), new GesturePoint(10, 2, 1), new GesturePoint(20, 5, 1));
    var smooth = TrailMesh.smooth(points);
    assertEquals(points.getFirst(), smooth.getFirst());
    assertEquals(points.getLast(), smooth.getLast());
    assertEquals(3, points.size());
    assertEquals(new GesturePoint(10, 2, 1), points.get(1));
    assertTrue(smooth.size() <= points.size() * 3);
    assertTrue(smooth.stream().allMatch(p -> p.stroke() == 1));
  }
}
