package codes.castled.chess.command;

import codes.castled.chess.Chess;
import codes.castled.chess.util.Scheduler;
import com.dxzell.pocketchess.api.game.TimeMode;
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

  public ChessCommand(Chess plugin, ChessCommandHandler handler) {
    this.plugin = plugin;
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
        } else {
          handler.sendUsage(player);
        }
      }
      default -> handler.sendUsage(player);
    }
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
    List<String> otherPlayerNames =
        Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> !name.equals(sender.getName()))
            .toList();
    if (args.length == 1) {
      return StringUtil.copyPartialMatches(
          args[0], Arrays.asList("duel", "open", "accept", "decline", "watch", "help"), new ArrayList<>());
    } else if (args.length == 2 && (args[0].equalsIgnoreCase("duel") || args[0].equalsIgnoreCase("watch"))) {
      return StringUtil.copyPartialMatches(args[1], otherPlayerNames, new ArrayList<>());
    } else if (args.length == 3 && args[0].equalsIgnoreCase("duel")) {
      return StringUtil.copyPartialMatches(args[2], TimeMode.getAllDisplayNames(), new ArrayList<>());
    } else if (args.length == 2
        && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline"))) {
      return StringUtil.copyPartialMatches(args[1], otherPlayerNames, new ArrayList<>());
    }
    return new ArrayList<>();
  }
}
