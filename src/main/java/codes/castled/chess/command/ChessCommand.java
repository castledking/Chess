package codes.castled.chess.command;

import codes.castled.chess.Chess;
import codes.castled.chess.util.Scheduler;
import codes.castled.chess.bot.BotDifficulty;
import codes.castled.chess.net.ChessNetwork;
import codes.castled.chess.net.RemotePlayer;
import codes.castled.chess.engine.api.game.TimeMode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The {@code /chess} command. Dispatch runs on the global region thread (Folia-safe). */
public final class ChessCommand implements CommandExecutor, TabCompleter {

  private final Chess plugin;
  private final ChessCommandHandler handler;
  private final ChessNetwork network;

  public ChessCommand(Chess plugin, ChessNetwork network, ChessCommandHandler handler) {
    this.plugin = plugin;
    this.network = network;
    this.handler = handler;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player) || !command.getName().equalsIgnoreCase("chess")) {
      return true;
    }
    // Serialize all game/duel mutations on the global region thread.
    Scheduler.global(plugin, () -> dispatch(player, args));
    return true;
  }

  private void dispatch(Player player, String[] args) {
    switch (args.length) {
      case 1 -> {
        if (args[0].equalsIgnoreCase("open")) {
          handler.handleOpen(player);
        } else if (args[0].equalsIgnoreCase("help")) {
          handler.sendHelp(player, "1");
        } else {
          handler.sendUsage(player);
        }
      }
      case 2 -> {
        if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline")) {
          handler.handleAcceptOrDecline(player, args[0], args[1]);
        } else if (args[0].equalsIgnoreCase("watch")) {
          handler.handleWatch(player, args[1]);
        } else if (args[0].equalsIgnoreCase("help")) {
          handler.sendHelp(player, args[1]);
        } else {
          handler.sendUsage(player);
        }
      }
      case 3 -> {
        if (args[0].equalsIgnoreCase("duel")) {
          handler.handleDuel(player, args[1], args[2]);
        } else if (args[0].equalsIgnoreCase("duelcpu")) {
          handler.handleDuelCpu(player, args[1], args[2]);
        } else {
          handler.sendUsage(player);
        }
      }
      default -> handler.sendUsage(player);
    }
  }

  /** The engine difficulty scale, offered as completions for {@code /chess duelcpu}. */
  private static final List<String> DIFFICULTIES =
      java.util.stream.IntStream.rangeClosed(BotDifficulty.MIN, BotDifficulty.MAX)
          .mapToObj(String::valueOf)
          .toList();

  /**
   * @param sender the player completing a command
   * @return everyone they could name, on this server and on every other server on the network
   *     <p>Read from the network's local cache, never from the network itself: this runs on the
   *     main thread for every keystroke, and must not wait on anything.
   */
  private List<String> challengeableNames(CommandSender sender) {
    List<String> names = new ArrayList<>();
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (!online.getName().equals(sender.getName())) {
        names.add(online.getName());
      }
    }
    for (RemotePlayer remote : network.remotePlayers()) {
      if (!remote.name().equals(sender.getName())) {
        names.add(remote.name());
      }
    }
    return names;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
    List<String> otherPlayerNames = challengeableNames(sender);

    if (args.length == 1) {
      return StringUtil.copyPartialMatches(
          args[0],
          Arrays.asList("duel", "duelcpu", "open", "accept", "decline", "watch", "help"),
          new ArrayList<>());
    } else if (args.length == 2 && (args[0].equalsIgnoreCase("duel") || args[0].equalsIgnoreCase("watch"))) {
      return StringUtil.copyPartialMatches(args[1], otherPlayerNames, new ArrayList<>());
    } else if (args.length == 2 && args[0].equalsIgnoreCase("duelcpu")) {
      return StringUtil.copyPartialMatches(args[1], DIFFICULTIES, new ArrayList<>());
    } else if (args.length == 3
        && (args[0].equalsIgnoreCase("duel") || args[0].equalsIgnoreCase("duelcpu"))) {
      return StringUtil.copyPartialMatches(args[2], TimeMode.getAllDisplayNames(), new ArrayList<>());
    } else if (args.length == 2
        && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline"))) {
      return StringUtil.copyPartialMatches(args[1], otherPlayerNames, new ArrayList<>());
    }
    return new ArrayList<>();
  }
}
