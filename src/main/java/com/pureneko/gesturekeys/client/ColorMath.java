package com.pureneko.gesturekeys.client;

/** Small RGB/HSV helpers; colors are opaque 24-bit RGB in the configuration. */
public final class ColorMath {
  private ColorMath() {}

  public static int hsv(float hue, float saturation, float value) {
    float h = (hue - (float) Math.floor(hue)) * 6;
    int sector = (int) h;
    float f = h - sector, p = value * (1 - saturation);
    float q = value * (1 - f * saturation), t = value * (1 - (1 - f) * saturation);
    return switch (sector) {
      case 0 -> rgb(value, t, p);
      case 1 -> rgb(q, value, p);
      case 2 -> rgb(p, value, t);
      case 3 -> rgb(p, q, value);
      case 4 -> rgb(t, p, value);
      default -> rgb(value, p, q);
    };
  }

  private static int rgb(float r, float g, float b) {
    return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
  }

  public static float[] hsv(int rgb) {
    float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
    float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
    float h = d == 0 ? 0 : max == r ? ((g - b) / d + 6) % 6
        : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
    return new float[] {h / 6, max == 0 ? 0 : d / max, max};
  }

  public static String hex(int rgb) {
    return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
  }
}
