package com.pureneko.gesturekeys.gesture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GestureRecognizerTest {
  @Test
  void moderateHumanAspectVariationStillMatches() {
    var sample =
        List.of(
            new GesturePoint(0, 0, 1),
            new GesturePoint(0, 100, 1),
            new GesturePoint(100, 100, 2),
            new GesturePoint(0, 100, 2));
    var drawn =
        List.of(
            new GesturePoint(168, 85, 1),
            new GesturePoint(168, 150, 1),
            new GesturePoint(248, 150, 2),
            new GesturePoint(168, 150, 2));
    assertTrue(score(sample, drawn) >= .78);
  }

  @Test
  void spatialSearchMatchesExhaustiveDistance() {
    var a = GestureRecognizer.normalize(l());
    var b =
        GestureRecognizer.normalize(
            List.of(
                new GesturePoint(0, 0, 1),
                new GesturePoint(30, 20, 1),
                new GesturePoint(100, 100, 1)));
    double distance = (brute(a, b) + brute(b, a)) * .5;
    assertEquals(Math.clamp(1 - distance * 3.5, 0, 1), GestureRecognizer.similarity(a, b), 1e-12);
  }

  private double brute(GestureRecognizer.Cloud a, GestureRecognizer.Cloud b) {
    double sum = 0, squared = 0;
    for (var p : a.points()) {
      double closest =
          b.points().stream()
              .mapToDouble(q -> Math.pow(p.x() - q.x(), 2) + Math.pow(p.y() - q.y(), 2))
              .min()
              .orElseThrow();
      sum += Math.sqrt(closest);
      squared += closest;
    }
    return .65 * sum / a.points().size() + .35 * Math.sqrt(squared / a.points().size());
  }

  private static List<GesturePoint> l() {
    return List.of(
        new GesturePoint(0, 0, 1), new GesturePoint(0, 100, 1), new GesturePoint(75, 100, 1));
  }

  private static GestureRecognizer.Template template(String id, List<GesturePoint> points) {
    return new GestureRecognizer.Template(id, List.of(GestureRecognizer.normalize(points)));
  }

  private static double score(List<GesturePoint> a, List<GesturePoint> b) {
    return GestureRecognizer.similarity(
        GestureRecognizer.normalize(a), GestureRecognizer.normalize(b));
  }

  @Test
  void translationScaleAndReverseAreInvariant() {
    var transformed =
        new ArrayList<>(
            l().stream().map(p -> new GesturePoint(p.x() * 3 + 90, p.y() * 3 - 30, 1)).toList());
    Collections.reverse(transformed);
    assertTrue(score(l(), transformed) > .97);
  }

  @Test
  void rotationAndMirrorAreDifferent() {
    var rotated = l().stream().map(p -> new GesturePoint(-p.y(), p.x(), 1)).toList();
    var mirrored = l().stream().map(p -> new GesturePoint(-p.x(), p.y(), 1)).toList();
    assertTrue(score(l(), rotated) < .78, "rotation must remain distinct");
    assertTrue(score(l(), mirrored) < .78, "mirror must remain distinct");
  }

  @Test
  void strokeOrderAndReversalAreIgnored() {
    var a =
        List.of(
            new GesturePoint(-50, 0, 1),
            new GesturePoint(50, 0, 1),
            new GesturePoint(0, -50, 2),
            new GesturePoint(0, 50, 2));
    var b =
        List.of(
            new GesturePoint(0, 50, 5),
            new GesturePoint(0, -50, 5),
            new GesturePoint(50, 0, 9),
            new GesturePoint(-50, 0, 9));
    assertTrue(score(a, b) > .97);
  }

  @Test
  void splittingOnePathIntoStrokesIsTolerated() {
    var b =
        List.of(
            new GesturePoint(0, 0, 1),
            new GesturePoint(0, 50, 1),
            new GesturePoint(0, 50, 2),
            new GesturePoint(0, 100, 2),
            new GesturePoint(0, 100, 3),
            new GesturePoint(75, 100, 3));
    assertTrue(score(l(), b) > .97);
  }

  @Test
  void penLiftsNeverBecomeSegments() {
    var points =
        List.of(
            new GesturePoint(0, 0, 1),
            new GesturePoint(10, 0, 1),
            new GesturePoint(100, 0, 2),
            new GesturePoint(110, 0, 2));
    assertEquals(20, GestureRecognizer.pathLength(points));
    var cloud = GestureRecognizer.normalize(points);
    assertTrue(cloud.points().stream().noneMatch(p -> p.x() > .15 && p.x() < .85));
  }

  @Test
  void circleStartAndWindingAreIgnored() {
    var a = new ArrayList<GesturePoint>();
    var b = new ArrayList<GesturePoint>();
    for (int i = 0; i <= 100; i++) {
      double t = 2 * Math.PI * i / 100;
      a.add(new GesturePoint(100 * Math.cos(t), 100 * Math.sin(t), 1));
      b.add(new GesturePoint(50 * Math.cos(1.3 - t) + 300, 50 * Math.sin(1.3 - t) - 40, 2));
    }
    assertTrue(score(a, b) > .94);
  }

  @Test
  void duplicateGesturesAreRejectedAsAmbiguous() {
    var result =
        GestureRecognizer.recognize(l(), List.of(template("a", l()), template("b", l())), .78, .06);
    assertEquals(GestureRecognizer.Status.AMBIGUOUS, result.status());
    assertFalse(result.accepted());
  }

  @Test
  void samplesOfSameGestureDoNotCompete() {
    var cloud = GestureRecognizer.normalize(l());
    var result =
        GestureRecognizer.recognize(
            l(), List.of(new GestureRecognizer.Template("a", List.of(cloud, cloud))), .78, .06);
    assertTrue(result.accepted());
    assertEquals("a", result.id());
  }

  @Test
  void unrelatedAndEmptyGesturesAreRejected() {
    var line = List.of(new GesturePoint(0, 0, 1), new GesturePoint(100, 0, 1));
    assertFalse(
        GestureRecognizer.recognize(line, List.of(template("a", l())), .78, .06).accepted());
    assertEquals(
        GestureRecognizer.Status.EMPTY,
        GestureRecognizer.recognize(List.of(), List.of(template("a", l())), .78, .06).status());
  }

  @Test
  void nonFinitePointsCannotPoisonScores() {
    assertTrue(
        GestureRecognizer.normalize(List.of(new GesturePoint(Double.NaN, 1, 0)))
            .points()
            .isEmpty());
  }

  @Test
  void drawingAccumulatesAndClears() {
    Drawing d = new Drawing();
    d.begin(0, 0);
    d.move(20, 0);
    d.end();
    d.begin(40, 0);
    d.move(60, 0);
    d.end();
    assertTrue(d.usable());
    assertEquals(40, GestureRecognizer.pathLength(d.points()));
    d.clear();
    assertFalse(d.usable());
  }
}
