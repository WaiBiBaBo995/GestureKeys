package com.pureneko.gesturekeys.config;

import static org.junit.jupiter.api.Assertions.*;

import com.pureneko.gesturekeys.gesture.GesturePoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {
  @TempDir Path directory;

  @Test
  void roundTripPreservesMultistrokesAndTicks() throws Exception {
    Path path = directory.resolve("gesturekeys.json");
    var store = new ConfigStore(path);
    var b = new GestureConfig.Binding();
    b.name = "背包手势";
    b.action.mode = GestureConfig.PressMode.HOLD;
    b.action.ticks = 1;
    b.samples.add(
        new ArrayList<>(
            List.of(
                new GesturePoint(0, 0, 1),
                new GesturePoint(20, 0, 1),
                new GesturePoint(50, 20, 2),
                new GesturePoint(80, 20, 2))));
    store.get().gestures.add(b);
    store.save();
    var loaded = new ConfigStore(path);
    loaded.load();
    assertEquals("背包手势", loaded.get().gestures.getFirst().name);
    assertEquals(1, loaded.get().gestures.getFirst().action.ticks);
    assertEquals(2, loaded.get().gestures.getFirst().samples.getFirst().getLast().stroke());
    assertFalse(Files.exists(directory.resolve("gesturekeys.json.tmp")));
  }

  @Test
  void brokenConfigIsBackedUpBeforeReplacement() throws Exception {
    Path path = directory.resolve("gesturekeys.json");
    Files.writeString(path, "{invalid");
    var store = new ConfigStore(path);
    store.load();
    assertNotNull(store.loadWarning());
    assertEquals("{invalid", Files.readString(Path.of(store.loadWarning())));
    store.save();
    assertEquals("{invalid", Files.readString(Path.of(store.loadWarning())));
  }

  @Test
  void invalidFieldsAreSanitized() throws Exception {
    Path path = directory.resolve("gesturekeys.json");
    Files.writeString(
        path,
        "{\"schemaVersion\":1,\"threshold\":5,\"gestures\":[{\"id\":\"x\",\"action\":{\"ticks\":0},\"samples\":null},{\"id\":\"x\",\"action\":{\"ticks\":500}}]}");
    var store = new ConfigStore(path);
    store.load();
    assertEquals(.98, store.get().threshold);
    assertEquals(1, store.get().gestures.getFirst().action.ticks);
    assertEquals(200, store.get().gestures.getLast().action.ticks);
    assertNotEquals(store.get().gestures.getFirst().id, store.get().gestures.getLast().id);
  }

  @Test
  void editCopyDoesNotMutateSavedSamples() {
    var original = new GestureConfig.Binding();
    original.samples.add(new ArrayList<>(List.of(new GesturePoint(0, 0, 1))));
    var draft = ConfigStore.copy(original);
    draft.samples.getFirst().clear();
    assertEquals(1, original.samples.getFirst().size());
  }
  @Test
  void migratesLegacyOnAndOffWithoutLosingGestures() throws Exception {
    Path path = directory.resolve("legacy.json");
    for (boolean enabled : new boolean[] {true, false}) {
      Files.writeString(path, "{\"schemaVersion\":1,\"glassEnabled\":" + enabled + ",\"gestures\":[{\"name\":\"old\"}]}");
      ConfigStore store = new ConfigStore(path);
      store.load();
      assertEquals(enabled ? GestureConfig.GlassStyle.LIQUID : GestureConfig.GlassStyle.OFF, store.get().glassStyle);
      assertEquals("old", store.get().gestures.getFirst().name);
      assertNull(store.loadWarning());
    }
  }

  @Test
  void roundTripsAllStylesAndBothColors() throws Exception {
    Path path = directory.resolve("styles.json");
    for (var style : GestureConfig.GlassStyle.values()) {
      ConfigStore store = new ConfigStore(path);
      store.get().glassStyle = style;
      store.get().interfaceColor = 0x1122DD;
      store.get().trailColor = 0xED5566;
      store.save();
      ConfigStore read = new ConfigStore(path);
      read.load();
      assertEquals(style, read.get().glassStyle);
      assertEquals(style != GestureConfig.GlassStyle.OFF, read.get().glassEnabled);
      assertEquals(0x1122DD, read.get().interfaceColor);
      assertEquals(0xED5566, read.get().trailColor);
    }
  }

  @Test
  void invalidColorsAndMissingStyleRecoverWithoutDroppingConfig() throws Exception {
    Path path = directory.resolve("invalid-colors.json");
    Files.writeString(path, "{\"schemaVersion\":1,\"glassStyle\":\"unknown\",\"interfaceColor\":-1,\"trailColor\":16777216}");
    ConfigStore store = new ConfigStore(path);
    store.load();
    assertNull(store.loadWarning());
    assertEquals(GestureConfig.GlassStyle.LIQUID, store.get().glassStyle);
    assertEquals(0x80D9EA, store.get().interfaceColor);
    assertEquals(0xAAF3FF, store.get().trailColor);
  }
}
