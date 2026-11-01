package com.pureneko.gesturekeys.smoke;

import com.mojang.blaze3d.platform.InputConstants;
import com.pureneko.gesturekeys.GestureKeys;
import com.pureneko.gesturekeys.client.GlassButton;
import com.pureneko.gesturekeys.client.SequenceEditorScreen;
import com.pureneko.gesturekeys.client.SettingsScreen;
import com.pureneko.gesturekeys.client.StepEditorScreen;
import com.pureneko.gesturekeys.config.GestureConfig.Action;
import com.pureneko.gesturekeys.config.GestureConfig.Kind;
import com.pureneko.gesturekeys.config.GestureConfig.PressMode;
import com.pureneko.gesturekeys.input.ActionExecutor;
import com.pureneko.gesturekeys.mixin.KeyMappingAccessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Real client/server input cycles and draft UI checks; excluded from release builds. */
@EventBusSubscriber(modid = GestureKeys.ID, value = Dist.CLIENT)
public final class SequenceSmoke {
  private static int stage, elapsed, pressesA, pressesB, oldTail;
  private static KeyMapping[] original;
  private static KeyMapping a, b;
  private static boolean selected, swapped, usedOffhand;
  private static volatile Boolean serverVerified;
  private static String screenshot;

  static boolean tick(List<String> checks) throws Exception {
    Minecraft mc = Minecraft.getInstance();
    elapsed++;
    if (stage == 9) {
      if (a.consumeClick()) pressesA++;
      if (b.consumeClick()) pressesB++;
    }
    if (stage == 0) {
      verifyEditor(checks);
      screenshot = "10-sequence.png";
      next();
    } else if (stage == 1 && elapsed > 10) {
      Action hold = key("key.use");
      hold.mode = PressMode.HOLD;
      hold.ticks = 17;
      mc.setScreen(new StepEditorScreen(mc.screen, hold, 2, ignored -> {}));
      screenshot = "11-sequence-step.png";
      next();
    } else if (stage == 2 && elapsed > 10) {
      mc.options.guiScale().set(2);
      mc.resizeDisplay();
      var widgets = mc.screen.children().stream().filter(AbstractWidget.class::isInstance)
          .map(AbstractWidget.class::cast).toList();
      check(checks, "compact step controls stay above footer", widgets.stream()
          .filter(w -> w instanceof EditBox).allMatch(w -> w.getY() + w.getHeight() < mc.screen.height - 46));
      screenshot = "12-sequence-step-compact.png";
      next();
    } else if (stage == 3 && elapsed > 10) {
      mc.setScreen(null);
      mc.options.guiScale().set(1);
      mc.resizeDisplay();
      original = mc.options.keyMappings;
      a = new KeyMapping("key.gesturekeys.sequence.a", KeyConflictContext.IN_GAME,
          InputConstants.UNKNOWN, KeyMapping.CATEGORY_MISC);
      b = new KeyMapping("key.gesturekeys.sequence.b", KeyConflictContext.IN_GAME,
          InputConstants.UNKNOWN, KeyMapping.CATEGORY_MISC);
      mc.options.keyMappings = Arrays.copyOf(original, original.length + 2);
      mc.options.keyMappings[original.length] = a;
      mc.options.keyMappings[original.length + 1] = b;
      ClientCommandHandler.getDispatcher().register(Commands.literal("gk_sequence_smoke")
          .then(Commands.literal("tail").executes(ctx -> { oldTail++; return 1; }))
          .then(Commands.literal("fail").executes(ctx -> 0)));
      var uuid = mc.player.getUUID();
      mc.getSingleplayerServer().execute(() -> {
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(Items.STONE));
        player.getInventory().setItem(1, new ItemStack(Items.SPYGLASS));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STICK));
        player.inventoryMenu.sendAllDataToRemote();
      });
      next();
    } else if (stage == 4) {
      if (!mc.player.getInventory().getItem(1).is(Items.SPYGLASS)
          || !mc.player.getOffhandItem().is(Items.STICK)) {
        if (elapsed > 120) throw new AssertionError("sequence inventory setup sync timeout");
        return false;
      }
      mc.player.getInventory().selected = 0;
      mc.player.setXRot(-90);
      ActionExecutor.queue(sequence(key("key.hotbar.2"), key("key.swapOffhand"), key("key.use")));
      next();
    } else if (stage == 5) {
      selected |= mc.player.getInventory().selected == 1;
      swapped |= mc.player.getOffhandItem().is(Items.SPYGLASS)
          && mc.player.getMainHandItem().is(Items.STICK);
      usedOffhand |= mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND;
      if (elapsed > 25) {
        check(checks, "sequence selects second hotbar slot", selected);
        check(checks, "sequence swaps selected item into offhand", swapped);
        check(checks, "sequence uses offhand spyglass through vanilla use", usedOffhand);
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
          var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
          serverVerified = player.getInventory().selected == 1 && player.getOffhandItem().is(Items.SPYGLASS)
              && player.getMainHandItem().is(Items.STICK);
        });
        next();
      }
    } else if (stage == 6) {
      if (serverVerified == null) {
        if (elapsed > 120) throw new AssertionError("sequence server verification timeout");
        return false;
      }
      check(checks, "server agrees with selected slot and offhand swap", serverVerified);
      Action hold = key(a.getName());
      hold.mode = PressMode.HOLD;
      hold.ticks = 20;
      ActionExecutor.queue(sequence(hold, command("gk_sequence_smoke tail")));
      next();
    } else if (stage == 7 && elapsed == 1) {
      check(checks, "sequence hold starts", a.isDown());
      ActionExecutor.queue(key(b.getName()));
      check(checks, "new sequence immediately releases old hold", !a.isDown());
    } else if (stage == 7 && elapsed > 30) {
      check(checks, "replaced sequence never executes its tail", oldTail == 0 && b.consumeClick());
      ActionExecutor.queue(sequence(key(a.getName()), key("key.missing.sequence_action")));
      next();
    } else if (stage == 8 && elapsed > 3) {
      check(checks, "invalid later key prevents all earlier side effects", !a.consumeClick());
      check(checks, "incomplete later command is rejected during preflight",
          ActionExecutor.validate(sequence(key(a.getName()), command("gk_sequence_smoke"))) != null);
      ActionExecutor.queue(sequence(key(a.getName()), command("gk_sequence_smoke fail"), key(b.getName())));
      next();
    } else if (stage == 9 && elapsed > 15) {
      check(checks, "runtime command failure aborts remaining steps", pressesA == 1 && pressesB == 0);
      ActionExecutor.queue(sequence(command("gesturekeys config"), key(b.getName())));
      next();
    } else if (stage == 10 && elapsed > 3) {
      check(checks, "sequence command opens screen", mc.screen instanceof SettingsScreen);
      mc.setScreen(null);
      next();
    } else if (stage == 11 && elapsed > 8) {
      check(checks, "screen-opening command permanently cancels tail", !b.consumeClick());
      ((KeyMappingAccessor) a).gesturekeys$setClicks(2);
      ActionExecutor.queue(key(a.getName()));
      next();
    } else if (stage == 12 && elapsed == 1) {
      check(checks, "preexisting click can be consumed during sequence", a.consumeClick());
      ActionExecutor.cancel();
      check(checks, "cancel removes synthetic click and preserves earlier physical clicks", a.consumeClick() && !a.consumeClick());
      ActionExecutor.queue(sequence(key("key.inventory"), key(b.getName())));
      next();
    } else if (stage == 13 && elapsed > 3) {
      check(checks, "game key opens inventory and cancels sequence", mc.screen != null);
      mc.player.closeContainer();
      mc.setScreen(null);
      next();
    } else if (stage == 14 && elapsed > 8) {
      check(checks, "closing inventory does not resume cancelled tail", !b.consumeClick());
      Action first = key(a.getName()), second = key(a.getName());
      second.delayTicks = 0;
      ActionExecutor.queue(sequence(first, second));
      next();
    } else if (stage == 15 && elapsed == 1) {
      // A physical click arrives between two unconsumed synthetic clicks of the same mapping.
      KeyMappingAccessor clicks = (KeyMappingAccessor) a;
      clicks.gesturekeys$setClicks(clicks.gesturekeys$getClicks() + 1);
    } else if (stage == 15 && elapsed == 2) {
      check(checks, "interleaved clicks consume first synthetic and physical input", a.consumeClick() && a.consumeClick());
      ActionExecutor.cancel();
      check(checks, "cancel removes second synthetic click after interleaved physical input", !a.consumeClick());
      ActionExecutor.queue(sequence(key(a.getName()), key(b.getName())));
      next();
    } else if (stage == 16 && elapsed == 1) {
      check(checks, "sequence starts before focus loss", a.isDown());
      mc.setWindowActive(false);
      ActionExecutor.tick();
      mc.setWindowActive(true);
    } else if (stage == 16 && elapsed > 8) {
      check(checks, "focus loss releases sequence and permanently discards tail", !a.isDown() && !b.consumeClick());
      ActionExecutor.queue(key(a.getName()));
      next();
    } else if (stage == 17 && elapsed == 1) {
      KeyMapping.releaseAll();
      ((KeyMappingAccessor) a).gesturekeys$setClicks(1);
      ActionExecutor.cancel();
      check(checks, "native click reset clears synthetic ownership before new physical input", a.consumeClick() && !a.consumeClick());
      mc.options.keyMappings = original;
      return true;
    }
    return false;
  }

  private static void verifyEditor(List<String> checks) {
    Minecraft mc = Minecraft.getInstance();
    Action original = sequence(key("key.hotbar.2"), key("key.swapOffhand"), key("key.use"));
    AtomicReference<Action> saved = new AtomicReference<>();
    mc.setScreen(new SequenceEditorScreen(new SettingsScreen(null), original, saved::set));
    press(mc.screen, "sequence.down");
    press(mc.screen, "done");
    check(checks, "sequence reorder saves draft without mutating original",
        saved.get().steps.getFirst().keyId.equals("key.swapOffhand")
            && original.steps.getFirst().keyId.equals("key.hotbar.2"));
    saved.set(null);
    mc.setScreen(new SequenceEditorScreen(mc.screen, original, saved::set));
    press(mc.screen, "sequence.remove");
    mc.screen.onClose();
    check(checks, "cancel discards sequence deletion", saved.get() == null && original.steps.size() == 3);
    mc.setScreen(new SequenceEditorScreen(mc.screen, original, saved::set));
    press(mc.screen, "sequence.add_wait");
    var fields = mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
    fields.getFirst().setValue("0.03");
    press(mc.screen, "done");
    check(checks, "step rejects times not aligned to 0.05 seconds", mc.screen instanceof StepEditorScreen);
    fields.getFirst().setValue("0.1");
    fields.getLast().setValue("0.25");
    press(mc.screen, "done");
    press(mc.screen, "done");
    check(checks, "wait step retains independent duration and gap", saved.get().steps.size() == 4
        && saved.get().steps.getLast().ticks == 2 && saved.get().steps.getLast().delayTicks == 5);
    mc.setScreen(new SequenceEditorScreen(mc.screen, saved.get(), ignored -> {}));
  }

  private static void press(Screen screen, String key) {
    screen.children().stream().filter(GlassButton.class::isInstance).map(GlassButton.class::cast)
        .filter(button -> button.active && button.getMessage().getContents() instanceof TranslatableContents tr
            && tr.getKey().equals("gesturekeys." + key)).findFirst().orElseThrow().onPress();
  }

  private static Action key(String id) {
    Action action = new Action();
    action.keyId = id;
    return action;
  }

  private static Action command(String command) {
    Action action = new Action();
    action.kind = Kind.COMMAND;
    action.command = command;
    return action;
  }

  private static Action sequence(Action... steps) {
    Action action = new Action();
    action.kind = Kind.SEQUENCE;
    action.steps = new ArrayList<>(List.of(steps));
    return action;
  }

  private static void next() {
    stage++;
    elapsed = 0;
  }

  private static void check(List<String> checks, String label, boolean result) {
    checks.add((result ? "PASS " : "FAIL ") + label);
    if (!result) throw new AssertionError(label);
  }

  @SubscribeEvent
  public static void frame(RenderFrameEvent.Post event) {
    if (screenshot == null) return;
    try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
      Path out = Path.of(System.getProperty("gesturekeys.smoke.output", "../build/smoke"));
      Files.createDirectories(out);
      image.writeToFile(out.resolve(screenshot));
      screenshot = null;
    } catch (Exception e) {
      throw new IllegalStateException("Sequence screenshot failed", e);
    }
  }
}
