package com.pureneko.gesturekeys.check;

import com.pureneko.gesturekeys.config.GestureConfig.Action;
import com.pureneko.gesturekeys.config.GestureConfig.Kind;
import com.pureneko.gesturekeys.input.ActionExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.network.chat.SignableCommand;

/** Verifies normal command packets and feedback against the integrated server. */
final class CommandRoutingCheck {
  private static int stage, elapsed, localPriority, tails;
  private static volatile boolean ready;
  private static final AtomicInteger serverPriority = new AtomicInteger();
  private static final AtomicInteger hidden = new AtomicInteger();
  private static final AtomicInteger serverTails = new AtomicInteger();


  static boolean tick(List<String> checks) throws Exception {
    Minecraft mc = Minecraft.getInstance();
    elapsed++;
    if (stage == 0) {
      mc.gui.getChat().clearMessages(false);
      var client = ClientCommandManager.getActiveDispatcher();
      client.register(ClientCommandManager.literal("gk_routing_priority").executes(ctx -> { localPriority++; return 1; }));
      client.register(ClientCommandManager.literal("gk_routing_tail").executes(ctx -> { tails++; return 1; }));
      mc.getSingleplayerServer().execute(() -> {
        var server = mc.getSingleplayerServer().getCommands().getDispatcher();
        server.register(Commands.literal("gk_routing_priority").executes(ctx -> serverPriority.incrementAndGet()));
        server.register(Commands.literal("gk_routing_tail").executes(ctx -> serverTails.incrementAndGet()));
        server.register(Commands.literal("gk_routing_hidden").executes(ctx -> hidden.incrementAndGet()));
        server.register(Commands.literal("gk_routing_failure").executes(ctx -> {
          ctx.getSource().sendFailure(Component.literal("gk_routing_server_failure"));
          return 0;
        }));
        ready = true;
      });
      next();
    } else if (stage == 1 && ready) {
      check(checks, "help and msg pass server command preflight", ActionExecutor.validate(
          sequence(command("/help"), command("/msg @s 111"))) == null);
      check(checks, "server command test root is absent from advertised client tree",
          mc.getConnection().getCommands().getRoot().getChild("gk_routing_hidden") == null);
      check(checks, "unadvertised server commands pass preflight",
          ActionExecutor.validate(command("gk_routing_hidden")) == null);
      check(checks, "malformed owned local command is rejected instead of forwarded",
          ActionExecutor.validate(command("gesturekeys no_such_argument")) != null);
      ActionExecutor.queue(sequence(command("/help"), command("/msg @s 111"),
          command("gk_routing_priority"), command("gk_routing_tail")));
      next();
    } else if (stage == 2 && elapsed > 20) {
      if ((!chatContains("111") || tails < 1) && elapsed < 120) return false;
      check(checks, "help is owned by server command tree",
          mc.getConnection().getCommands().getRoot().getChild("help") != null
              && ClientCommandManager.getActiveDispatcher().getRoot().getChild("help") == null);
      check(checks, "help feedback is displayed in normal chat", chatMessages().stream()
          .anyMatch(message -> message.content().getString().startsWith("/")));
      check(checks, "msg arguments require the normal signed command path", SignableCommand.hasSignableArguments(
          mc.getConnection().getCommands().parse("msg @s 111", mc.getConnection().getSuggestionsProvider())));
      check(checks, "msg self message appears in chat", chatContains("111"));
      check(checks, "client command wins a server name collision", localPriority == 1 && serverPriority.get() == 0
);
      check(checks, "sequence continues after sending server commands", tails == 1);
      check(checks, "local commands are not forwarded to server", serverTails.get() == 0);
      ActionExecutor.queue(sequence(command("gk_routing_hidden"), command("gk_routing_tail")));
      next();
    } else if (stage == 3 && elapsed > 10) {
      if ((hidden.get() == 0 || tails < 2) && elapsed < 120) return false;
      check(checks, "unadvertised server command executes and sequence continues", hidden.get() == 1 && tails == 2);
      ActionExecutor.queue(sequence(command("gk_routing_failure"), command("gk_routing_tail")));
      next();
    } else if (stage == 4 && elapsed > 10) {
      if ((!chatContains("gk_routing_server_failure") || tails < 3) && elapsed < 120) return false;
      check(checks, "server failure feedback uses normal chat", chatContains("gk_routing_server_failure"));
      check(checks, "asynchronous server failure does not stop sequence", tails == 3);
      ActionExecutor.queue(sequence(command("gk_routing_missing_command"), command("gk_routing_tail")));
      next();
    } else if (stage == 5 && elapsed > 10) {
      if ((!chatContains("gk_routing_missing_command") || tails < 4) && elapsed < 120) return false;
      check(checks, "unknown server command is sent and receives vanilla feedback", chatContains("gk_routing_missing_command"));
      check(checks, "unknown server command does not stop later local step", tails == 4);
      check(checks, "command routing preserves active connection", mc.getConnection().getConnection().isConnected());
      ActionExecutor.cancel();
      return true;
    }
    if (elapsed > 180) throw new AssertionError("Command routing smoke timeout at stage " + stage);
    return false;
  }

  @SuppressWarnings("unchecked")
  private static List<GuiMessage> chatMessages() throws Exception {
    var field = ChatComponent.class.getDeclaredField("allMessages");
    field.setAccessible(true);
    return List.copyOf((List<GuiMessage>) field.get(Minecraft.getInstance().gui.getChat()));
  }

  private static boolean chatContains(String text) throws Exception {
    return chatMessages().stream().anyMatch(message -> message.content().getString().contains(text));
  }

  private static Action command(String command) {
    var action = new Action();
    action.kind = Kind.COMMAND;
    action.command = command;
    return action;
  }

  private static Action sequence(Action... steps) {
    var action = new Action();
    action.kind = Kind.SEQUENCE;
    action.steps = new ArrayList<>(List.of(steps));
    return action;
  }

  private static void next() {
    stage++;
    elapsed = 0;
  }

  private static void check(List<String> checks, String label, boolean passed) {
    checks.add((passed ? "PASS " : "FAIL ") + label);
    if (!passed) throw new AssertionError(label);
  }
}
