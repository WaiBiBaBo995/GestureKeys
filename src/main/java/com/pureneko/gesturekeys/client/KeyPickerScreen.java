package com.pureneko.gesturekeys.client;

import com.pureneko.gesturekeys.GestureKeys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class KeyPickerScreen extends GlassScreen {
  private final Consumer<KeyMapping> choose;
  private final List<GlassButton> rows = new ArrayList<>();
  private String search = "";
  private int page, count, perPage;
  private int pages = 1;
  private GlassButton previous, next;

  public KeyPickerScreen(EditGestureScreen parent, Consumer<KeyMapping> choose) {
    super(Component.translatable("gesturekeys.key.pick"), parent);
    this.choose = choose;
  }

  @Override
  protected void init() {
    int x = left(), w = contentWidth();
    perPage = Math.max(1, (height - 131) / 38);
    EditBox box = new EditBox(font, x + 8, 53, w - 16, 14, tr("key.search"));
    box.setBordered(false);
    box.setMaxLength(100);
    box.setValue(search);
    box.setHint(tr("key.search"));
    box.setResponder(
        value -> {
          search = value;
          page = 0;
          refresh();
        });
    addRenderableWidget(box);
    setInitialFocus(box);
    previous =
        button(
            x,
            height - 32,
            55,
            22,
            "previous",
            () -> changePage(-1));
    next =
        button(
            x + 61,
            height - 32,
            55,
            22,
            "next",
            () -> changePage(1));
    button(x + w - 100, height - 32, 100, 22, "cancel", this::onClose);
    rows.clear();
    refresh();
  }

  private void refresh() {
    rows.forEach(this::removeWidget);
    rows.clear();
    String query = search.toLowerCase(Locale.ROOT);
    var keys =
        Arrays.stream(minecraft.options.keyMappings)
            .filter(k -> k != GestureKeys.OPEN)
            .filter(
                k ->
                    (Component.translatable(k.getName()).getString()
                            + " "
                            + k.getName()
                            + " "
                            + Component.translatable(k.getCategory()).getString())
                        .toLowerCase(Locale.ROOT)
                        .contains(query))
            .sorted(Comparator.comparing(k -> Component.translatable(k.getName()).getString()))
            .toList();
    count = keys.size();
    pages = Math.max(1, (count + perPage - 1) / perPage);
    page = Math.clamp(page, 0, pages - 1);
    for (int i = 0; i < perPage && page * perPage + i < count; i++) {
      KeyMapping key = keys.get(page * perPage + i);
      var row =
          new GlassButton(
                  left(),
                  79 + i * 38,
                  contentWidth(),
                  33,
                  Component.translatable(key.getName()),
                  () -> {
                    choose.accept(key);
                    onClose();
                  })
              .secondary(
                  Component.translatable(key.getCategory())
                      .append(" · ")
                      .append(key.getTranslatedKeyMessage()),
                  null);
      addRenderableWidget(row);
      rows.add(row);
    }
    previous.active = page > 0;
    next.active = page < pages - 1;
  }

  private void changePage(int direction) {
    int target = Math.clamp(page + direction, 0, pages - 1);
    if (target == page) return;
    page = target;
    refresh();
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (vertical == 0) return super.mouseScrolled(x, y, horizontal, vertical);
    changePage(vertical < 0 ? 1 : -1);
    return true;
  }

  @Override
  protected void content(GuiGraphics g, int mx, int my, float partial) {
    heading(g);
    GlassRenderer.panel(g, left(), 47, contentWidth(), 25, 8, 0);
    g.drawCenteredString(
        font, tr("key.count", count, page + 1, pages), width / 2, height - 47, 0xFFB1CDD9);
  }
}
