package com.pureneko.gesturekeys.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class GlassRenderer {
  private static ShaderInstance shader;
  private static RenderTarget scene;
  private static RenderTarget blurHorizontal;
  private static RenderTarget blurVertical;
  private static boolean failed;
  private static int guiWidth, guiHeight;
  private static Appearance appearance;

  public static Appearance appearance() {
    return appearance == null ? Appearance.from(GestureKeys.STORE.get()) : appearance;
  }

  public static int accent(int alpha, double brightness) {
    int rgb = appearance().interfaceColor();
    int r = (int) ((rgb >> 16 & 255) * brightness);
    int g = (int) ((rgb >> 8 & 255) * brightness);
    int b = (int) ((rgb & 255) * brightness);
    return alpha << 24 | Math.clamp(r, 0, 255) << 16 | Math.clamp(g, 0, 255) << 8 | Math.clamp(b, 0, 255);
  }

  private GlassRenderer() {}

  public static void registerShader(RegisterShadersEvent event) {
    shader = null;
    failed = false;
    try {
      event.registerShader(
          new ShaderInstance(
              event.getResourceProvider(),
              ResourceLocation.fromNamespaceAndPath(GestureKeys.ID, "liquid_glass"),
              DefaultVertexFormat.POSITION_TEX),
          value -> shader = value);
    } catch (IOException | RuntimeException e) {
      GestureKeys.LOGGER.error("Liquid glass shader unavailable; using translucent controls", e);
      failed = true;
    }
  }

  public static void begin(GuiGraphics g, int width, int height, float progress, Appearance draft) {
    appearance = draft;
    guiWidth = width;
    guiHeight = height;
    if (available()) {
      g.flush();
      RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
      if (scene == null || scene.width != main.width || scene.height != main.height) {
        if (scene != null) scene.destroyBuffers();
        if (blurHorizontal != null) blurHorizontal.destroyBuffers();
        if (blurVertical != null) blurVertical.destroyBuffers();
        scene = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        blurHorizontal = new TextureTarget(Math.max(1, main.width / 2), Math.max(1, main.height / 2), false, Minecraft.ON_OSX);
        blurVertical = new TextureTarget(Math.max(1, main.width / 2), Math.max(1, main.height / 2), false, Minecraft.ON_OSX);
        scene.setFilterMode(GL11.GL_LINEAR);
        blurHorizontal.setFilterMode(GL11.GL_LINEAR);
        blurVertical.setFilterMode(GL11.GL_LINEAR);
      }
      GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
      GL30.glBlitFramebuffer(
          0,
          0,
          main.width,
          main.height,
          0,
          0,
          scene.width,
          scene.height,
          GL11.GL_COLOR_BUFFER_BIT,
          GL11.GL_NEAREST);
      try {
        blurHorizontal.bindWrite(true);
        draw(g, 0, 0, width, height, 0, 2, 0, 1);
        blurVertical.bindWrite(true);
        draw(g, 0, 0, width, height, 0, 3, 0, 1);
      } finally {
        main.bindWrite(true);
      }
    }
    if (!draw(g, 0, 0, width, height, 0, 0, 0, progress)) {
      g.fill(0, 0, width, height, accent(0xD8, .11));
      g.fillGradient(0, 0, width, height, accent(0x25, .55), accent(0x25, .12));
    }
  }

  private static boolean available() {
    return shader != null && !failed && appearance().style() != com.pureneko.gesturekeys.config.GestureConfig.GlassStyle.OFF;
  }

  public static void panel(
      GuiGraphics g, int x, int y, int width, int height, int radius, float highlight) {
    if (!draw(g, x, y, width, height, radius, 1, highlight, 1)) {
      rounded(g, x, y, width, height, radius, highlight > .5 ? accent(0xD5, .32) : accent(0xC5, .16));
      g.fill(x + radius, y, x + width - radius, y + 1, accent(0x66, 1));
      g.fill(x + radius, y + height - 1, x + width - radius, y + height, accent(0x33, .7));
    }
  }

  private static boolean draw(
      GuiGraphics g,
      int x,
      int y,
      int width,
      int height,
      int radius,
      int kind,
      float highlight,
      float progress) {
    if (!available() || scene == null || width <= 0 || height <= 0) return false;
    g.flush();
    int previousTexture = RenderSystem.getShaderTexture(0);
    int previousTexture1 = RenderSystem.getShaderTexture(1);
    int previousTexture2 = RenderSystem.getShaderTexture(2);
    ShaderInstance previousShader = RenderSystem.getShader();
    try {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(() -> shader);
      RenderSystem.setShaderTexture(0, scene.getColorTextureId());
      RenderSystem.setShaderTexture(
          1, kind == 3 ? blurHorizontal.getColorTextureId() : scene.getColorTextureId());
      RenderSystem.setShaderTexture(
          2, kind >= 2 ? scene.getColorTextureId() : blurVertical.getColorTextureId());
      shader.setSampler("Sampler0", scene.getColorTextureId());
      uniform("RenderSize", kind >= 2 ? blurHorizontal.width : scene.width, kind >= 2 ? blurHorizontal.height : scene.height);
      uniform("GuiSize", guiWidth, guiHeight);
      shader.safeGetUniform("Rect").set((float) x, (float) y, (float) width, (float) height);
      shader.safeGetUniform("Radius").set((float) radius);
      shader.safeGetUniform("Kind").set((float) kind);
      shader.safeGetUniform("Highlight").set(highlight);
      shader.safeGetUniform("Strength").set((float) appearance().strength());
      shader.safeGetUniform("Style").set((float) appearance().style().ordinal());
      int tint = appearance().interfaceColor();
      shader.safeGetUniform("Tint").set((tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f);
      shader.safeGetUniform("Time").set((System.nanoTime() % 120_000_000_000L) / 1_000_000_000f);
      shader.safeGetUniform("Progress").set(progress);
      Matrix4f matrix = g.pose().last().pose();
      BufferBuilder buffer =
          Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      buffer.addVertex(matrix, x, y, 0).setUv(0, 1);
      buffer.addVertex(matrix, x, y + height, 0).setUv(0, 0);
      buffer.addVertex(matrix, x + width, y + height, 0).setUv(1, 0);
      buffer.addVertex(matrix, x + width, y, 0).setUv(1, 1);
      BufferUploader.drawWithShader(buffer.buildOrThrow());
      return true;
    } catch (RuntimeException exception) {
      failed = true;
      GestureKeys.LOGGER.error("Disabling liquid glass after a render failure", exception);
      return false;
    } finally {
      RenderSystem.setShaderTexture(0, previousTexture);
      RenderSystem.setShaderTexture(1, previousTexture1);
      RenderSystem.setShaderTexture(2, previousTexture2);
      RenderSystem.setShader(() -> previousShader);
      RenderSystem.enableDepthTest();
      RenderSystem.disableBlend();
    }
  }

  private static void uniform(String name, int x, int y) {
    shader.safeGetUniform(name).set((float) x, (float) y);
  }

  public static void rounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
    r = Math.min(r, Math.min(w, h) / 2);
    for (int row = 0; row < h; row++) {
      int inset = 0;
      if (row < r) inset = (int) Math.ceil(r - Math.sqrt(r * r - Math.pow(r - row - .5, 2)));
      else if (row >= h - r)
        inset = (int) Math.ceil(r - Math.sqrt(r * r - Math.pow(row - (h - r) + .5, 2)));
      g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
    }
  }

  public static void line(
      GuiGraphics g, double x1, double y1, double x2, double y2, float thickness, int color) {
    float length = (float) Math.hypot(x2 - x1, y2 - y1);
    if (length < .001) return;
    g.pose().pushPose();
    g.pose().translate(x1, y1, 0);
    g.pose().mulPose(new Quaternionf().rotationZ((float) Math.atan2(y2 - y1, x2 - x1)));
    g.fill(
        0,
        -(int) Math.ceil(thickness / 2),
        (int) Math.ceil(length),
        (int) Math.ceil(thickness / 2),
        color);
    g.pose().popPose();
  }

  public static void trail(GuiGraphics g, List<GesturePoint> points) {
    TrailRenderer.draw(g, points, 1.15f, true);
  }
}
