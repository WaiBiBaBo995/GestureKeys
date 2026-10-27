package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.input.ActionExecutor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends GlassScreen {
  private int page;
  private int perPage;
  private int pages = 1;

  public SettingsScreen(Screen parent) {
    super(Component.translatable("gesturekeys.settings"), parent);
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth();
    perPage = Math.max(1, (height - 143) / 47);
    pages = Math.max(1, (GestureKeys.STORE.get().gestures.size() + perPage - 1) / perPage);
    page = Math.clamp(page, 0, pages - 1);
    button(
        x,
        48,
        Math.min(120, w / 2 - 4),
        24,
        "add",
        () -> minecraft.setScreen(new EditGestureScreen(this, null)));
    button(
        x + w / 2,
        48,
        w / 2,
        24,
        "preferences",
        () -> minecraft.setScreen(new PreferencesScreen(this)));
    var list = GestureKeys.STORE.get().gestures;
    for (int i = 0; i < perPage && page * perPage + i < list.size(); i++) {
      var binding = list.get(page * perPage + i);
      Component name = Component.literal(binding.name + (binding.enabled ? "" : " [OFF]"));
      addRenderableWidget(
          new GlassButton(
                  x,
                  83 + i * 47,
                  w,
                  41,
                  name,
                  () -> minecraft.setScreen(new EditGestureScreen(this, binding)))
              .secondary(
                  ActionExecutor.describe(binding.action),
                  binding.samples.isEmpty() ? null : binding.samples.getFirst()));
    }
    button(
                x,
                height - 32,
                48,
                22,
                "previous",
                () -> changePage(-1))
            .active =
        page > 0;
    button(
                x + 54,
                height - 32,
                48,
                22,
                "next",
                () -> changePage(1))
            .active =
        page < pages - 1;
    button(x + w - 100, height - 32, 100, 22, "done", this::onClose);
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    heading(g);
    if (GestureKeys.STORE.get().gestures.isEmpty())
      g.drawCenteredString(font, tr("settings.empty"), width / 2, height / 2, 0xFFC1DCE7);
    g.drawCenteredString(
        font,
        tr("settings.count", GestureKeys.STORE.get().gestures.size(), page + 1, pages),
        width / 2,
        height - 25,
        0xFF9DBCCC);
    if (GestureKeys.STORE.loadWarning() != null)
      g.drawString(
          font,
          GlassScreen.clip(font, tr("config.recovered"), contentWidth()),
          left(),
          height - 47,
          0xFFFFC48C,
          false);
  }

  private void changePage(int direction) {
    int target = Math.clamp(page + direction, 0, pages - 1);
    if (target == page) return;
    page = target;
    rebuildWidgets();
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
    changePage(vertical < 0 ? 1 : -1);
    return true;
  }
}
