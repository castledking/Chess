package codes.castled.chess.command;

import codes.castled.chess.config.MessageConfig;
import codes.castled.chess.game.ChessGameHolder;
import codes.castled.chess.game.GameService;
import codes.castled.chess.request.DuelRequestService;
import codes.castled.chess.bot.BotDifficulty;
import codes.castled.chess.net.ChessNetwork;
import codes.castled.chess.engine.api.game.TimeMode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/** Runs the actions behind the /chess subcommands. */
public final class ChessCommandHandler {

  private final MessageConfig messageConfig;
  private final GameService gameService;
  private final DuelRequestService duelRequestService;
  private final ChessNetwork network;

  public ChessCommandHandler(
      MessageConfig messageConfig,
      GameService gameService,
      DuelRequestService duelRequestService,
      ChessNetwork network) {
    this.messageConfig = messageConfig;
    this.gameService = gameService;
    this.duelRequestService = duelRequestService;
    this.network = network;
  }

  /** Reopens the board if the player is in a game. */
  public void handleOpen(Player player) {
    ChessGameHolder game = gameService.getGameByPlayer(player.getUniqueId());
    if (game != null) {
      game.getView().openBoard(player.getUniqueId());
    } else {
      player.sendMessage(messageConfig.getNotPlaying());
    }
  }

  /**
   * Reacts to a duel request.
   *
   * @param player the player who reacts
   * @param reaction either accept or decline
   * @param requestName the name of the requesting player
   */
  public void handleAcceptOrDecline(Player player, String reaction, String requestName) {
    Player duelRequestSender = Bukkit.getPlayer(requestName);
    if (duelRequestSender != null) {
      if (reaction.equalsIgnoreCase("accept")) {
        duelRequestService.acceptRequest(player.getUniqueId(), duelRequestSender.getUniqueId());
      } else {
        duelRequestService.declineRequest(player.getUniqueId(), duelRequestSender.getUniqueId());
      }
    } else {
      player.sendMessage(messageConfig.getDuelRequestSenderOffline());
    }
  }

  /**
   * Requests a duel.
   *
   * @param player the requesting player
   * @param requestName the name of the player to request
   * @param timeMode the time mode to play
   */
  public void handleDuel(Player player, String requestName, String timeMode) {
    Player duelPlayer = Bukkit.getPlayer(requestName);
    if (duelPlayer != null) {
      if (TimeMode.containsMode(timeMode)) {
        duelRequestService.sendRequest(
            player.getUniqueId(),
            duelPlayer.getUniqueId(),
            player.getName(),
            duelPlayer.getName(),
            TimeMode.fromDisplayName(timeMode));
      } else {
        player.sendMessage(messageConfig.getInvalidTimeMode());
      }
    } else if (network.findRemotePlayer(requestName) != null) {
      // They are on the network but not on this server. Playing across servers is not wired up
      // yet, so say what is actually true rather than claiming they are offline.
      player.sendMessage(messageConfig.getOpponentOnAnotherServer());
    } else {
      player.sendMessage(messageConfig.getOpponentNotOnline());
    }
  }

  public void sendUsage(Player player) {
    player.sendMessage(messageConfig.getInvalidArgsCmd());
  }

  /** Spectates the game the target player is in. */
  public void handleWatch(Player player, String targetName) {
    Player target = Bukkit.getPlayer(targetName);
    if (target == null) {
      player.sendMessage(messageConfig.getOpponentNotOnline());
      return;
    }
    ChessGameHolder game = gameService.getGameByPlayer(target.getUniqueId());
    if (game == null) {
      player.sendMessage(messageConfig.getTargetNotInGame());
      return;
    }
    if (!game.getView().addSpectator(player.getUniqueId())) {
      // The inventory board refuses spectators: it borrows the viewer's own inventory for the
      // two nearest ranks, which is not a price to charge someone who is not even playing.
      player.sendMessage(messageConfig.getSpectatingUnavailable());
    }
  }

  /**
   * Starts a game against an engine opponent.
   *
   * @param player the challenger, who plays white
   * @param level the requested difficulty, 1 to 10
   * @param timeMode the time control
   */
  public void handleDuelCpu(Player player, String level, String timeMode) {
    BotDifficulty difficulty = BotDifficulty.parse(level);
    if (difficulty == null) {
      player.sendMessage(messageConfig.getInvalidDifficulty());
      return;
    }

    if (!TimeMode.containsMode(timeMode)) {
      player.sendMessage(messageConfig.getInvalidTimeMode());
      return;
    }

    if (gameService.isPlaying(player.getUniqueId())) {
      player.sendMessage(messageConfig.getYouAlreadyInGame());
      return;
    }

    gameService.createCpuGame(player, difficulty, TimeMode.fromDisplayName(timeMode));
  }

  public void sendHelp(Player player, String indexString) {
    try {
      int index = Integer.parseInt(indexString);
      player.sendMessage(
          switch (index) {
            case 1 ->
                new String[] {
                  ChatColor.YELLOW + "--------- " + ChatColor.WHITE + "Help: Index (1/1) "
                      + ChatColor.YELLOW + "------------------",
                  ChatColor.GRAY + "Use /chess help <n> to get page n of help.",
                  ChatColor.GOLD + "/chess duel <Player> <TimeMode>" + ChatColor.GRAY + ": "
                      + ChatColor.WHITE + "Sends a chess duel with the given time mode.",
                  ChatColor.GOLD + "/chess duelcpu <1-10> <TimeMode>" + ChatColor.GRAY + ": "
                      + ChatColor.WHITE + "Plays the computer at the given difficulty.",
                  ChatColor.GOLD + "/chess open" + ChatColor.GRAY + ": " + ChatColor.WHITE
                      + "Reopens the chess board if a game is being played.",
                  ChatColor.GOLD + "/chess accept <Player>" + ChatColor.GRAY + ": " + ChatColor.WHITE
                      + "Accepts a chess duel offer from the given player.",
                  ChatColor.GOLD + "/chess decline <Player>" + ChatColor.GRAY + ": " + ChatColor.WHITE
                      + "Declines a chess duel offer from the given player."
                };
            default -> new String[] {ChatColor.RED + "Invalid index"};
          });
    } catch (NumberFormatException ex) {
      player.sendMessage(ChatColor.RED + "Invalid index");
    }
  }
}
