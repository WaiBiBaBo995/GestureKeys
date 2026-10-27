package com.pureneko.gesturekeys.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.*;
import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback.RegistrationContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Cached display path, one coverage batch and one composite; MAX prevents overlap seams. */
public final class TrailRenderer {
  private static ShaderInstance shader, composite;
  private static RenderTarget mask;
  private static boolean failed;
  private static final Map<List<GesturePoint>, List<GesturePoint>> CACHE =
      new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<List<GesturePoint>, List<GesturePoint>> e) {
          return size() > 24;
        }
      };

  private TrailRenderer() {}

  public static void registerShader(RegistrationContext context) {
    shader = null;
    composite = null;
    failed = false;
    CACHE.clear();
    try {
      context.register(
          ResourceLocation.fromNamespaceAndPath(GestureKeys.ID, "gesture_trail"),
          DefaultVertexFormat.POSITION_TEX, value -> shader = value);
      context.register(
          ResourceLocation.fromNamespaceAndPath(GestureKeys.ID, "trail_composite"),
          DefaultVertexFormat.POSITION_TEX, value -> composite = value);
    } catch (IOException | RuntimeException e) {
      failed = true;
      GestureKeys.LOGGER.error("Trail shader unavailable; using rounded geometry", e);
    }
  }

  public static boolean available() { return shader != null && composite != null && !failed; }

  public static void draw(GuiGraphics g, List<GesturePoint> points, float radius, boolean glow) {
    if (points.isEmpty()) return;
    List<GesturePoint> smooth = CACHE.computeIfAbsent(points, TrailMesh::smooth);
    int color = GlassRenderer.appearance().trailColor();
    if (!available()) {
      for (int i = 1; i < smooth.size(); i++) {
        var a = smooth.get(i - 1); var b = smooth.get(i);
        if (a.stroke() == b.stroke())
          GlassRenderer.line(g, a.x(), a.y(), b.x(), b.y(), radius * 2, 0xEE000000 | color);
      }
      for (var p : smooth) GlassRenderer.rounded(g, (int) (p.x() - radius), (int) (p.y() - radius),
          (int) (radius * 2 + 1), (int) (radius * 2 + 1), (int) (radius + 1), 0xEE000000 | color);
      return;
    }
    g.flush();
    ShaderInstance previous = RenderSystem.getShader();
    int previousTexture = RenderSystem.getShaderTexture(0);
    float[] clearColor = new float[4];
    GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
    RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
    if (mask == null || mask.width != main.width || mask.height != main.height) {
      if (mask != null) mask.destroyBuffers();
      mask = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
      mask.setClearColor(0, 0, 0, 0);
    }
    float extent = radius * (glow ? 3 : 1.65f);
    float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
    for (var p : smooth) {
      minX = Math.min(minX, (float) p.x() - extent - 1);
      minY = Math.min(minY, (float) p.y() - extent - 1);
      maxX = Math.max(maxX, (float) p.x() + extent + 1);
      maxY = Math.max(maxY, (float) p.y() + extent + 1);
    }
    // Clear only the affected physical-pixel rectangle, retaining the GUI's scissor.
    boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    int[] box = new int[4];
    GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
    double scale = Minecraft.getInstance().getWindow().getGuiScale();
    int sx = Math.max(0, (int) Math.floor(minX * scale));
    int sy = Math.max(0, main.height - (int) Math.ceil(maxY * scale));
    int ex = Math.min(main.width, (int) Math.ceil(maxX * scale));
    int ey = Math.min(main.height, main.height - (int) Math.floor(minY * scale));
    if (scissor) {
      sx = Math.max(sx, box[0]); sy = Math.max(sy, box[1]);
      ex = Math.min(ex, box[0] + box[2]); ey = Math.min(ey, box[1] + box[3]);
    }
    if (ex <= sx || ey <= sy) return;
    try {
      mask.bindWrite(true);
      GL11.glEnable(GL11.GL_SCISSOR_TEST);
      GL11.glScissor(sx, sy, ex - sx, ey - sy);
      GL11.glClearColor(0, 0, 0, 0);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
      // MAX only combines coverage in a transparent mask, never against the scene.
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(() -> shader);
      shader.safeGetUniform("TrailColor").set((color >> 16 & 255) / 255f,
          (color >> 8 & 255) / 255f, (color & 255) / 255f);
      shader.safeGetUniform("Glow").set(glow ? 1f : 0f);
      // MAX is alpha-independent, so intersections do not brighten or darken repeatedly.
      GL14.glBlendEquation(GL14.GL_MAX);

      shader.safeGetUniform("Core").set(radius / extent);
      Matrix4f matrix = g.pose().last().pose();
      BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      for (int i = 1; i < smooth.size(); i++) {
        var a = smooth.get(i - 1); var c = smooth.get(i);
        if (a.stroke() != c.stroke()) continue;
        double dx = c.x() - a.x(), dy = c.y() - a.y(), length = Math.hypot(dx, dy);
        if (length < .001) continue;
        float nx = (float) (-dy / length * extent), ny = (float) (dx / length * extent);
        vertex(b, matrix, (float) a.x() + nx, (float) a.y() + ny, 1, 0);
        vertex(b, matrix, (float) c.x() + nx, (float) c.y() + ny, 1, 0);
        vertex(b, matrix, (float) c.x() - nx, (float) c.y() - ny, -1, 0);
        vertex(b, matrix, (float) a.x() - nx, (float) a.y() - ny, -1, 0);
      }
      for (var p : smooth) {
        float x = (float) p.x(), y = (float) p.y();
        vertex(b, matrix, x - extent, y - extent, -1, -1);
        vertex(b, matrix, x - extent, y + extent, -1, 1);
        vertex(b, matrix, x + extent, y + extent, 1, 1);
        vertex(b, matrix, x + extent, y - extent, 1, -1);
      }
      BufferUploader.drawWithShader(b.buildOrThrow());
      main.bindWrite(true);
      GL14.glBlendEquation(GL14.GL_FUNC_ADD);
      RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      RenderSystem.setShader(() -> composite);
      RenderSystem.setShaderTexture(0, mask.getColorTextureId());
      composite.setSampler("Sampler0", mask.getColorTextureId());
      BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      vertex(quad, matrix, minX, minY, (float) (minX * scale / main.width), (float) (1 - minY * scale / main.height));
      vertex(quad, matrix, minX, maxY, (float) (minX * scale / main.width), (float) (1 - maxY * scale / main.height));
      vertex(quad, matrix, maxX, maxY, (float) (maxX * scale / main.width), (float) (1 - maxY * scale / main.height));
      vertex(quad, matrix, maxX, minY, (float) (maxX * scale / main.width), (float) (1 - minY * scale / main.height));
      BufferUploader.drawWithShader(quad.buildOrThrow());
    } catch (RuntimeException e) {
      failed = true;
      GestureKeys.LOGGER.error("Trail rendering failed", e);
    } finally {
      main.bindWrite(true);
      GL14.glBlendEquation(GL14.GL_FUNC_ADD);
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderTexture(0, previousTexture);
      RenderSystem.setShader(() -> previous);
      GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
      GL11.glScissor(box[0], box[1], box[2], box[3]);
      if (!scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
    }
  }

  private static void vertex(BufferBuilder b, Matrix4f matrix, float x, float y, float u, float v) {
    b.addVertex(matrix, x, y, 0).setUv(u, v);
  }
}
