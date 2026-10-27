package com.pureneko.gesturekeys.gesture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Orientation-preserving, symmetric point-cloud matcher for unordered multistrokes. */
public final class GestureRecognizer {
  private GestureRecognizer() {}

  public static final class Cloud {
    private final List<GesturePoint> points;
    private final Node tree;

    public Cloud(List<GesturePoint> points) {
      this.points = List.copyOf(points);
      this.tree = Node.build(points, 0);
    }

    public List<GesturePoint> points() {
      return points;
    }
  }

  /** Exact nearest-neighbour queries keep live previews responsive with many templates. */
  private record Node(GesturePoint point, int axis, Node left, Node right) {
    static Node build(List<GesturePoint> points, int depth) {
      if (points.isEmpty()) return null;
      var sorted = new ArrayList<>(points);
      int axis = depth % 2;
      sorted.sort(Comparator.comparingDouble(p -> axis == 0 ? p.x() : p.y()));
      int middle = sorted.size() / 2;
      return new Node(
          sorted.get(middle),
          axis,
          build(sorted.subList(0, middle), depth + 1),
          build(sorted.subList(middle + 1, sorted.size()), depth + 1));
    }

    double nearest(GesturePoint p, double best) {
      double dx = p.x() - point.x(), dy = p.y() - point.y();
      best = Math.min(best, dx * dx + dy * dy);
      double delta = axis == 0 ? dx : dy;
      Node near = delta < 0 ? left : right, far = delta < 0 ? right : left;
      if (near != null) best = near.nearest(p, best);
      if (far != null && delta * delta < best) best = far.nearest(p, best);
      return best;
    }
  }

  public record Template(String id, List<Cloud> samples) {}

  public enum Status {
    MATCH,
    EMPTY,
    LOW_CONFIDENCE,
    AMBIGUOUS
  }

  public record Result(String id, double score, double runnerUp, Status status) {
    public boolean accepted() {
      return status == Status.MATCH;
    }
  }

  public static double pathLength(List<GesturePoint> points) {
    double length = 0;
    for (int i = 1; i < points.size(); i++) {
      GesturePoint a = points.get(i - 1), b = points.get(i);
      if (a.finite() && b.finite() && a.stroke() == b.stroke())
        length += Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
    return length;
  }

  public static Cloud normalize(List<GesturePoint> raw) {
    List<GesturePoint> points = raw.stream().filter(GesturePoint::finite).toList();
    double total = pathLength(points);
    if (points.size() < 2 || total < 1e-6) return new Cloud(List.of());
    double minX = points.stream().mapToDouble(GesturePoint::x).min().orElse(0);
    double minY = points.stream().mapToDouble(GesturePoint::y).min().orElse(0);
    double maxX = points.stream().mapToDouble(GesturePoint::x).max().orElse(0);
    double maxY = points.stream().mapToDouble(GesturePoint::y).max().orElse(0);
    double scale = Math.max(maxX - minX, maxY - minY);
    if (scale < 1e-6) return new Cloud(List.of());
    double step = total / 112.0;
    // Resample each stroke independently; no connecting segment crosses a pen lift.
    List<GesturePoint> sampled = new ArrayList<>();
    double remaining = 0;
    for (int i = 0; i < points.size(); i++) {
      GesturePoint p = points.get(i);
      if (i == 0 || p.stroke() != points.get(i - 1).stroke()) {
        sampled.add(p);
        remaining = step;
        continue;
      }
      GesturePoint a = points.get(i - 1);
      double length = Math.hypot(p.x() - a.x(), p.y() - a.y());
      double travelled = remaining;
      while (travelled <= length && sampled.size() < 512) {
        double t = length == 0 ? 0 : travelled / length;
        sampled.add(new GesturePoint(a.x() + (p.x() - a.x()) * t, a.y() + (p.y() - a.y()) * t, 0));
        travelled += step;
      }
      remaining = travelled - length;
      if (i == points.size() - 1 || points.get(i + 1).stroke() != p.stroke()) sampled.add(p);
    }
    // Deduplication removes dependence on endpoints, drawing direction and retracing.
    TreeMap<Integer, GesturePoint> unique = new TreeMap<>();
    for (GesturePoint p : sampled) {
      double x = .5 + (p.x() - (minX + maxX) * .5) / scale;
      double y = .5 + (p.y() - (minY + maxY) * .5) / scale;
      int gx = (int) Math.round(x * 80), gy = (int) Math.round(y * 80);
      unique.putIfAbsent(gx * 128 + gy, new GesturePoint(x, y, 0));
    }
    var cloud = new ArrayList<>(unique.values());
    if (cloud.size() > 256) {
      var limited = new ArrayList<GesturePoint>(256);
      for (int i = 0; i < 256; i++) limited.add(cloud.get(i * (cloud.size() - 1) / 255));
      cloud = limited;
    }
    return new Cloud(cloud);
  }

  public static double similarity(Cloud a, Cloud b) {
    if (a.points.isEmpty() || b.points.isEmpty()) return 0;
    double distance = (directed(a, b) + directed(b, a)) * .5;
    return Math.clamp(1 - distance * 3.5, 0, 1);
  }

  private static double directed(Cloud a, Cloud b) {
    double sum = 0, squared = 0;
    for (GesturePoint p : a.points) {
      double closest = b.tree.nearest(p, Double.POSITIVE_INFINITY);
      sum += Math.sqrt(closest);
      squared += closest;
    }
    return .65 * sum / a.points.size() + .35 * Math.sqrt(squared / a.points.size());
  }

  public static Result recognize(
      List<GesturePoint> points, List<Template> templates, double threshold, double margin) {
    Cloud input = normalize(points);
    if (input.points.isEmpty() || templates.isEmpty()) return new Result(null, 0, 0, Status.EMPTY);
    record Candidate(String id, double score) {}
    List<Candidate> candidates = new ArrayList<>();
    for (Template template : templates) {
      double best =
          template.samples.stream().mapToDouble(s -> similarity(input, s)).max().orElse(0);
      if (!template.samples.isEmpty()) candidates.add(new Candidate(template.id, best));
    }
    candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
    if (candidates.isEmpty()) return new Result(null, 0, 0, Status.EMPTY);
    Candidate best = candidates.getFirst();
    double second = candidates.size() > 1 ? candidates.get(1).score : 0;
    Status status =
        best.score < threshold
            ? Status.LOW_CONFIDENCE
            : best.score - second < margin ? Status.AMBIGUOUS : Status.MATCH;
    return new Result(best.id, best.score, second, status);
  }
}
